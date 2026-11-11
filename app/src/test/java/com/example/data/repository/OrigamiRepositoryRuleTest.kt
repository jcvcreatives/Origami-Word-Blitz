package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrigamiRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureOrCreateProfile_andCreateMatch_succeedsForAuthenticatedOwner() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = OrigamiCloudRepository(firestore)

    val profileResult = withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureOrCreateProfile("Alice") }
    assertTrue(profileResult.isSuccess)
    assertEquals(uid, profileResult.getOrThrow().userId)

    val matchIdResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createMatch(
          mode = "CAMPAIGN",
          opponentName = "Sensei Haiku",
          boardSnapshot = ".".repeat(225),
          rackSnapshot = "ORIGAMI",
        )
      }
    assertTrue(matchIdResult.isSuccess)
    val matchId = matchIdResult.getOrThrow()
    assertTrue(matchId.isNotEmpty())

    val observedMatches =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeMatches(uid).first { list -> list.any { it.id == matchId } }
      }
    assertTrue(observedMatches.any { it.id == matchId })
  }

  @Test
  fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = OrigamiCloudRepository(firestore)
    val matchId =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceRepo
          .createMatch(
            mode = "CAMPAIGN",
            opponentName = "Sensei Haiku",
            boardSnapshot = ".".repeat(225),
            rackSnapshot = "ORIGAMI",
          )
          .getOrThrow()
      }

    signInTestUser(BOB_EMAIL)
    val bobRepo = OrigamiCloudRepository(firestore)
    val crossResult =
      withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getMatchById(aliceUid, matchId) }
    assertTrue(crossResult.isFailure)
    val rootEx = crossResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(rootEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, rootEx?.code)
  }

  @Test
  fun observeMatches_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = OrigamiCloudRepository(firestore)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeMatches("some_user_123").first() }
    } catch (e: Throwable) {
      caught = e
    }
    assertNotNull(caught)
    val firestoreEx = caught?.unwrapFirestoreException()
    assertNotNull("Expected FirebaseFirestoreException, got $caught", firestoreEx)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_origami@test.com"
    const val BOB_EMAIL = "bob_origami@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
