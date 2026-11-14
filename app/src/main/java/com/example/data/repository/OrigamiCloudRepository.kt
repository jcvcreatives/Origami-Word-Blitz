package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.remote.OperationType
import com.example.data.remote.SavedMatchDocument
import com.example.data.remote.UserProfileDocument
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OrigamiCloudRepository(private val db: FirebaseFirestore) {

  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  @Volatile private var cachedUserId: String? = null
  @Volatile private var cachedProfile: UserProfileDocument? = null
  @Volatile var sessionReadsCount: Int = 0
    private set
  @Volatile var sessionWritesCount: Int = 0
    private set

  fun getCachedProfile(): UserProfileDocument? = cachedProfile

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException(
        "User must be signed in with Google before accessing Firestore."
      )
  }

  fun observeUserProfile(userId: String = requireUserId()): Flow<UserProfileDocument?> = flow {
    val path = "users/$userId"
    emitAll(
      db
        .collection("users")
        .document(userId)
        .snapshots()
        .map { snapshot ->
          sessionReadsCount++
          if (snapshot.exists()) {
            snapshot.toObject(
              UserProfileDocument::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )?.also {
              cachedUserId = userId
              cachedProfile = it
            }
          } else {
            null
          }
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val exceptionToLog = firestoreError ?: (error as? Exception)
          if (exceptionToLog != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(exceptionToLog, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  suspend fun ensureOrCreateProfile(
    displayName: String = "Origami Strategist"
  ): Result<UserProfileDocument> = runCatching {
    val uid = requireUserId()
    val safeName = displayName.trim().ifBlank { "Origami Strategist" }.take(50)

    // Free-Tier Quota Guard: Reuse in-memory cached profile if already fetched this session
    val memoryProfile = cachedProfile
    if (cachedUserId == uid && memoryProfile != null) {
      if (
        safeName == "Origami Strategist" ||
          safeName == "Origami Apprentice" ||
          memoryProfile.displayName == safeName
      ) {
        return@runCatching memoryProfile
      }
    }

    val docRef = db.collection("users").document(uid)
    try {
      sessionReadsCount++
      val existing = docRef.get().await()
      if (existing.exists()) {
        val loaded =
          existing.toObject(
            UserProfileDocument::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
        if (loaded != null) {
          if (
            safeName != "Origami Strategist" &&
              safeName != "Origami Apprentice" &&
              loaded.displayName != safeName
          ) {
            sessionWritesCount++
            docRef
              .update(
                mapOf(
                  "displayName" to safeName,
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              )
              .await()
            val updated = loaded.copy(displayName = safeName)
            cachedUserId = uid
            cachedProfile = updated
            return@runCatching updated
          }
          cachedUserId = uid
          cachedProfile = loaded
          return@runCatching loaded
        }
      }
      val payload =
        mapOf(
          "userId" to uid,
          "displayName" to safeName,
          "totalScore" to 0L,
          "highestWordScore" to 0L,
          "bestWord" to "",
          "campaignStage" to 1L,
          "dailyStreak" to 1L,
          "paperCoins" to 150L,
          "equippedTileSkin" to "washi_classic",
          "unlockedSkins" to listOf("washi_classic"),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      sessionWritesCount++
      docRef.set(payload).await()
      // Free-Tier Quota Guard: Construct profile in memory instead of issuing a second .get() read
      val createdDoc =
        UserProfileDocument(
          id = uid,
          userId = uid,
          displayName = safeName,
          totalScore = 0L,
          highestWordScore = 0L,
          bestWord = "",
          campaignStage = 1L,
          dailyStreak = 1L,
          paperCoins = 150L,
          equippedTileSkin = "washi_classic",
          unlockedSkins = listOf("washi_classic"),
        )
      cachedUserId = uid
      cachedProfile = createdDoc
      createdDoc
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  suspend fun getProfileById(targetUserId: String): Result<UserProfileDocument?> = runCatching {
    if (cachedUserId == targetUserId && cachedProfile != null) {
      return@runCatching cachedProfile
    }
    val docRef = db.collection("users").document(targetUserId)
    try {
      sessionReadsCount++
      val snap = docRef.get().await()
      snap.toObject(
        UserProfileDocument::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      throw e
    }
  }

  suspend fun updateProfileStats(
    scoreDelta: Int,
    wordScore: Int,
    playedWord: String,
    coinsDelta: Int,
    advanceCampaignTo: Int? = null,
    equipSkin: String? = null,
    unlockSkin: String? = null,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid)
    try {
      // Free-Tier Quota Guard: Use cached in-memory profile to avoid a redundant .get() read before every update
      val current =
        if (cachedUserId == uid && cachedProfile != null) {
          cachedProfile!!
        } else {
          ensureOrCreateProfile().getOrThrow()
        }

      val newTotalScore = maxOf(0L, current.totalScore + scoreDelta.toLong())
      val newHighestWordScore = maxOf(current.highestWordScore, wordScore.toLong())
      val newBestWord =
        if (wordScore.toLong() >= current.highestWordScore && playedWord.isNotBlank()) {
          playedWord.take(20)
        } else {
          current.bestWord.take(20)
        }
      val newCampaignStage =
        if (advanceCampaignTo != null) {
          maxOf(current.campaignStage, advanceCampaignTo.toLong()).coerceIn(1L, 100L)
        } else {
          current.campaignStage.coerceIn(1L, 100L)
        }
      val updatedUnlocked =
        if (unlockSkin != null && !current.unlockedSkins.contains(unlockSkin)) {
          (current.unlockedSkins + unlockSkin).take(20)
        } else {
          current.unlockedSkins.take(20)
        }
      val updatedEquipped = (equipSkin ?: current.equippedTileSkin).take(40)
      val newCoins = maxOf(0L, current.paperCoins + coinsDelta.toLong())

      // Skip network write if nothing meaningfully changed
      if (
        newTotalScore == current.totalScore &&
          newHighestWordScore == current.highestWordScore &&
          newCampaignStage == current.campaignStage &&
          newCoins == current.paperCoins &&
          updatedEquipped == current.equippedTileSkin &&
          updatedUnlocked == current.unlockedSkins
      ) {
        return@runCatching
      }

      val updates =
        mapOf(
          "totalScore" to newTotalScore,
          "highestWordScore" to newHighestWordScore,
          "bestWord" to newBestWord,
          "campaignStage" to newCampaignStage,
          "paperCoins" to newCoins,
          "equippedTileSkin" to updatedEquipped,
          "unlockedSkins" to updatedUnlocked,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      sessionWritesCount++
      docRef.update(updates).await()
      cachedUserId = uid
      cachedProfile =
        current.copy(
          totalScore = newTotalScore,
          highestWordScore = newHighestWordScore,
          bestWord = newBestWord,
          campaignStage = newCampaignStage,
          paperCoins = newCoins,
          equippedTileSkin = updatedEquipped,
          unlockedSkins = updatedUnlocked,
        )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  fun observeMatches(userId: String? = auth.currentUser?.uid): Flow<List<SavedMatchDocument>> =
    flow {
      val targetUid = userId ?: "unauthenticated_uid"
      val path = "users/$targetUid/matches"
      emitAll(
        db
          .collection("users")
          .document(targetUid)
          .collection("matches")
          .snapshots()
          .map { snapshot ->
            snapshot
              .toObjects(
                SavedMatchDocument::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
              )
              .sortedByDescending { it.updatedAt?.seconds ?: 0L }
          }
          .catch { error ->
            val firestoreError =
              generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val exceptionToLog = firestoreError ?: (error as? Exception)
            if (exceptionToLog != null && auth.currentUser?.uid == targetUid) {
              handleFirestoreError(exceptionToLog, OperationType.LIST, path)
            }
            throw error
          }
      )
    }

  suspend fun createMatch(
    mode: String,
    opponentName: String,
    boardSnapshot: String,
    rackSnapshot: String,
  ): Result<String> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("matches").document()
    val payload =
      mapOf(
        "userId" to uid,
        "mode" to mode,
        "opponentName" to opponentName.take(50).ifBlank { "Sensei Haiku" },
        "playerScore" to 0L,
        "opponentScore" to 0L,
        "turnNumber" to 1L,
        "maxCombo" to 1L,
        "bestPlayedWord" to "",
        "status" to "ACTIVE",
        "boardSnapshot" to boardSnapshot.take(2000),
        "rackSnapshot" to rackSnapshot.take(100),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
      docRef.id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  suspend fun updateMatchState(
    matchId: String,
    playerScore: Int,
    opponentScore: Int,
    turnNumber: Int,
    maxCombo: Int,
    bestPlayedWord: String,
    status: String,
    boardSnapshot: String,
    rackSnapshot: String,
  ): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection("users").document(uid).collection("matches").document(matchId)
    val updates =
      mapOf(
        "playerScore" to maxOf(0, playerScore).toLong(),
        "opponentScore" to maxOf(0, opponentScore).toLong(),
        "turnNumber" to maxOf(1, turnNumber).toLong(),
        "maxCombo" to maxOf(1, maxCombo).toLong(),
        "bestPlayedWord" to bestPlayedWord.take(20),
        "status" to status,
        "boardSnapshot" to boardSnapshot.take(2000),
        "rackSnapshot" to rackSnapshot.take(100),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.update(updates).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  suspend fun getMatchById(ownerUid: String, matchId: String): Result<SavedMatchDocument?> =
    runCatching {
      val docRef = db.collection("users").document(ownerUid).collection("matches").document(matchId)
      try {
        val snap = docRef.get().await()
        snap.toObject(
          SavedMatchDocument::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path)
        throw e
      }
    }
}
