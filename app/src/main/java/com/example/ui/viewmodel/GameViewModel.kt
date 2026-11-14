package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.SavedMatchDocument
import com.example.data.remote.UserProfileDocument
import com.example.data.repository.OrigamiCloudRepository
import com.example.domain.engine.LexiFillEngine
import com.example.domain.engine.WordTrieEngine
import com.example.domain.model.Coordinate
import com.example.domain.model.CosmeticTileSkin
import com.example.domain.model.GameCatalog
import com.example.domain.model.LevelTemplate
import com.example.domain.model.PuzzleState
import com.example.domain.model.Tile
import com.example.ui.feedback.AudioPlayer
import com.example.ui.feedback.SoundAndHapticsEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
  data object Loading : CloudUiState<Nothing>
  data class Success<T>(val data: T) : CloudUiState<T>
  data class Error(val message: String) : CloudUiState<Nothing>
}

/**
 * Unidirectional Data Flow (UDF) ViewModel for "LexiFill: Crossword Skeleton".
 *
 * Offline-First & Zero Network Quota by default:
 * - All puzzle levels, Trie dictionary lookups, dual-axis intersection validations, and
 *   procedural level generation run 100% locally in <1ms.
 * - Optional cloud sync is only active when [repository] and [currentUserId] are non-null.
 */
class GameViewModel(
  private val repository: OrigamiCloudRepository? = null,
  private val currentUserId: String? = null,
  private val preferredDisplayName: String? = null,
  private val soundEngine: SoundAndHapticsEngine = SoundAndHapticsEngine(),
) : ViewModel() {

  val audioPlayer: AudioPlayer
    get() = soundEngine.audioPlayer

  val trieEngine = WordTrieEngine()
  val lexiFillEngine = LexiFillEngine(trieEngine)

  private var autoAdvanceJob: Job? = null
  private var lastHoverCoord: Coordinate? = null

  private val _puzzleState =
    MutableStateFlow(
      lexiFillEngine.createPuzzleState(
        template = lexiFillEngine.getLevelTemplate(1),
      )
    )
  val puzzleState: StateFlow<PuzzleState> = _puzzleState.asStateFlow()

  private val _cloudProfileState = MutableStateFlow<UserProfileDocument?>(null)
  val cloudProfileState: StateFlow<UserProfileDocument?> = _cloudProfileState.asStateFlow()

  private val _cloudSyncStatus =
    MutableStateFlow(
      if (repository != null && !currentUserId.isNullOrBlank()) {
        "Connecting to Firebase Firestore..."
      } else {
        "Local Account Mode (Tap 'Link Firebase' to sync to Cloud Firestore)"
      }
    )
  val cloudSyncStatus: StateFlow<String> = _cloudSyncStatus.asStateFlow()

  val cloudMatchesState: StateFlow<CloudUiState<List<SavedMatchDocument>>> =
    if (repository != null && !currentUserId.isNullOrBlank()) {
      repository
        .observeMatches(currentUserId)
        .map<List<SavedMatchDocument>, CloudUiState<List<SavedMatchDocument>>> {
          CloudUiState.Success(it)
        }
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          val rootError = firestoreError ?: error
          Log.w(TAG, "Error observing cloud history", rootError)
          emit(CloudUiState.Error(rootError.message ?: "Offline mode active"))
        }
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000L),
          initialValue = CloudUiState.Loading,
        )
    } else {
      flowOf<CloudUiState<List<SavedMatchDocument>>>(CloudUiState.Success(emptyList()))
        .stateIn(
          scope = viewModelScope,
          started = SharingStarted.WhileSubscribed(5000L),
          initialValue = CloudUiState.Success(emptyList()),
        )
    }

  init {
    if (repository != null && !currentUserId.isNullOrBlank()) {
      observeCloudProfile(repository, currentUserId)
    }
  }

  private fun observeCloudProfile(repo: OrigamiCloudRepository, uid: String) {
    viewModelScope.launch {
      val resolvedName =
        preferredDisplayName?.trim()?.takeIf { it.isNotBlank() } ?: "Origami Strategist"
      repo
        .ensureOrCreateProfile(resolvedName)
        .onSuccess { createdProfile ->
          _cloudProfileState.value = createdProfile
          _cloudSyncStatus.value =
            "Synced to Firebase Firestore ✓ (users/$uid · ${createdProfile.displayName})"
        }
        .onFailure { err ->
          _cloudSyncStatus.value =
            "Firebase sync error: ${err.localizedMessage ?: "Permission or network issue"}"
        }
      repo
        .observeUserProfile(uid)
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          Log.w(TAG, "Error observing user profile", firestoreError ?: error)
        }
        .collect { profile: UserProfileDocument? ->
          if (profile != null) {
            _cloudProfileState.value = profile
            _cloudSyncStatus.value =
              "Synced to Firebase Firestore ✓ (users/$uid · ${profile.displayName})"
            val unlocked = profile.unlockedSkins.toSet() + "washi_classic"
            val equipped =
              GameCatalog.COSMETIC_SKINS.find { it.id == profile.equippedTileSkin }
                ?: GameCatalog.COSMETIC_SKINS.first()
            _puzzleState.update { state ->
              state.copy(
                lexiCoins = maxOf(state.lexiCoins, profile.paperCoins.toInt()),
                unlockedSkinIds = state.unlockedSkinIds + unlocked,
                equippedSkin = equipped,
                highestUnlockedLevel =
                  maxOf(state.highestUnlockedLevel, profile.campaignStage.toInt()),
              )
            }
          }
        }
    }
  }

  fun loadLevel(levelNumber: Int) {
    autoAdvanceJob?.cancel()
    lastHoverCoord = null
    val current = _puzzleState.value
    val template = lexiFillEngine.getLevelTemplate(levelNumber)
    _puzzleState.value =
      lexiFillEngine.createPuzzleState(
        template = template,
        totalCampaignScore = current.totalCampaignScore,
        completedLevels = current.completedLevels,
        highestUnlockedLevel = maxOf(current.highestUnlockedLevel, levelNumber),
        lexiCoins = current.lexiCoins,
        equippedSkinId = current.equippedSkin.id,
        unlockedSkinIds = current.unlockedSkinIds,
        strictTrieValidation = current.strictTrieValidation,
      )
  }

  fun loadCustomSkeleton(compactCode: String) {
    autoAdvanceJob?.cancel()
    lastHoverCoord = null
    val current = _puzzleState.value
    val template: LevelTemplate =
      lexiFillEngine.parseCompactSkeleton(
        compact = compactCode,
        levelNumber = current.levelTemplate.levelNumber,
      )
    _puzzleState.value =
      lexiFillEngine.createPuzzleState(
        template = template,
        totalCampaignScore = current.totalCampaignScore,
        completedLevels = current.completedLevels,
        highestUnlockedLevel = current.highestUnlockedLevel,
        lexiCoins = current.lexiCoins,
        equippedSkinId = current.equippedSkin.id,
        unlockedSkinIds = current.unlockedSkinIds,
        strictTrieValidation = current.strictTrieValidation,
      )
  }

  fun replayCurrentLevel() {
    loadLevel(_puzzleState.value.levelTemplate.levelNumber)
  }

  fun advanceToNextLevel() {
    val nextNum = _puzzleState.value.levelTemplate.levelNumber + 1
    loadLevel(nextNum)
  }

  fun toggleSoundMute(haptics: HapticFeedback? = null) {
    val muted = audioPlayer.toggleMute()
    if (!muted) {
      soundEngine.onTilePickup(haptics)
    }
    _puzzleState.update { s ->
      s.copy(
        isSoundMuted = muted,
        lastSoundTriggered = if (!muted) AudioPlayer.SoundEffect.CLICK.id else s.lastSoundTriggered,
      )
    }
  }

  fun onRackTileTapped(tile: Tile, haptics: HapticFeedback? = null) {
    soundEngine.onTilePickup(haptics)
    _puzzleState.update { s ->
      val nextSelected = if (s.rackState.selectedTileId == tile.id) null else tile.id
      s.copy(
        rackState = s.rackState.copy(selectedTileId = nextSelected),
        highlightedHintCoord = null,
        lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id,
        statusMessage =
          if (nextSelected != null) {
            "Selected '${tile.char}' (${tile.baseScore} pts) — tap an open blank slot on the grid to place it!"
          } else {
            "Select a letter tile or drag it onto an intersecting blank slot."
          },
        isStatusError = false,
      )
    }
  }

  fun onTileDragStart(tile: Tile, haptics: HapticFeedback? = null) {
    lastHoverCoord = null
    soundEngine.onTilePickup(haptics)
    _puzzleState.update { s ->
      s.copy(
        rackState =
          s.rackState.copy(
            draggingTileId = tile.id,
            selectedTileId = tile.id,
          ),
        highlightedHintCoord = null,
        lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id,
      )
    }
  }

  /**
   * Updates the magnetic ghost highlight and live dual-axis preview when dragging [tile]
   * over [targetCoord].
   */
  fun onTileDragHover(
    tile: Tile,
    targetCoord: Coordinate?,
    haptics: HapticFeedback? = null,
  ) {
    if (targetCoord == lastHoverCoord) return
    lastHoverCoord = targetCoord

    val state = _puzzleState.value
    if (targetCoord == null) {
      _puzzleState.update { s ->
        if (s.hoverCoord == null && s.hoverPreview == null) s
        else s.copy(hoverCoord = null, hoverPreview = null)
      }
      return
    }

    // Subtle 'click' tick when magnetically locking onto a new open blank target
    soundEngine.onTilePickup(haptics, pitchMultiplier = 1.12f)
    val preview = lexiFillEngine.previewTileDrop(state, tile, targetCoord.x, targetCoord.y)
    _puzzleState.update { s ->
      s.copy(
        hoverCoord = targetCoord,
        hoverPreview = preview,
        lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id,
      )
    }
  }

  fun onTileDragCancel() {
    lastHoverCoord = null
    _puzzleState.update { s ->
      s.copy(
        rackState = s.rackState.copy(draggingTileId = null),
        hoverCoord = null,
        hoverPreview = null,
      )
    }
  }

  /**
   * Commits a tile drop onto [targetCoord] (either from drag release or tap-to-place).
   * Returns true if the tile was accepted into the slot, or false if it should spring back to the rack.
   */
  fun onTileDropOnCoord(
    tileId: String,
    targetCoord: Coordinate?,
    haptics: HapticFeedback? = null,
  ): Boolean {
    lastHoverCoord = null
    if (targetCoord == null) {
      soundEngine.onInvalidWord(haptics)
      onTileDragCancel()
      _puzzleState.update { it.copy(lastSoundTriggered = AudioPlayer.SoundEffect.THUD.id) }
      return false
    }

    val current = _puzzleState.value
    val result =
      lexiFillEngine.commitTileDrop(
        state = current,
        tileId = tileId,
        targetX = targetCoord.x,
        targetY = targetCoord.y,
      )

    return when (result.outcome) {
      LexiFillEngine.OutcomeType.NO_OP -> {
        _puzzleState.value = result.updatedState
        false
      }
      LexiFillEngine.OutcomeType.RETURNED_OUTSIDE_SLOT,
      LexiFillEngine.OutcomeType.INVALID_INTERSECTION -> {
        soundEngine.onInvalidWord(haptics)
        _puzzleState.value =
          result.updatedState.copy(lastSoundTriggered = AudioPlayer.SoundEffect.THUD.id)
        false
      }
      LexiFillEngine.OutcomeType.TILE_SNAPPED -> {
        soundEngine.onTileSnapToGrid(haptics, 1.05f)
        _puzzleState.value =
          result.updatedState.copy(lastSoundTriggered = AudioPlayer.SoundEffect.SNAP.id)
        true
      }
      LexiFillEngine.OutcomeType.WORD_COMPLETED -> {
        soundEngine.onWordScored(
          score = result.scoreGained,
          isTripleWord = false,
          comboStreak = result.updatedState.comboStreak,
          haptics = haptics,
        )
        _puzzleState.value =
          result.updatedState.copy(lastSoundTriggered = AudioPlayer.SoundEffect.SNAP.id)
        true
      }
      LexiFillEngine.OutcomeType.DUAL_WORD_COMPLETED -> {
        soundEngine.onWordScored(
          score = result.scoreGained,
          isTripleWord = true,
          comboStreak = result.updatedState.comboStreak,
          haptics = haptics,
        )
        _puzzleState.value =
          result.updatedState.copy(lastSoundTriggered = AudioPlayer.SoundEffect.SNAP.id)
        true
      }
      LexiFillEngine.OutcomeType.LEVEL_CLEARED -> {
        soundEngine.onLevelClearedFanfare(haptics)
        _puzzleState.value =
          result.updatedState.copy(lastSoundTriggered = AudioPlayer.SoundEffect.FANFARE.id)
        syncCompletedLevelIfSignedIn(result.updatedState)
        startAutoAdvanceCountdown()
        true
      }
    }
  }

  /**
   * Handles tapping a cell on the Crossword Skeleton board:
   * - If the cell has an unlocked placed tile, recalls it back to the rack.
   * - If the cell is an open blank target and a rack tile is selected (or uses the matching hint tile),
   *   drops the selected tile into that cell.
   */
  fun selectClueWord(wordId: String, haptics: HapticFeedback? = null) {
    soundEngine.onTilePickup(haptics)
    _puzzleState.update { s ->
      val target = s.targetWords.find { it.id == wordId }
      val nextCellFocus =
        target
          ?.coordinates()
          ?.firstOrNull { c ->
            val cell = s.cellAt(c.x, c.y)
            cell != null && cell.isBlankTarget && cell.placedTile == null && !cell.isLockedCompleted
          }
          ?: target?.coordinates()?.firstOrNull()
          ?: s.focusedCellCoord
      s.copy(
        focusedWordId = wordId,
        focusedCellCoord = nextCellFocus,
        lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id,
        statusMessage =
          if (target != null) {
            "${target.direction.label.uppercase()}: ${target.formattedSkeleton(s.cells)} — ${target.clue}"
          } else {
            s.statusMessage
          },
        isStatusError = false,
      )
    }
  }

  fun onBoardCellTapped(x: Int, y: Int, haptics: HapticFeedback? = null) {
    val state = _puzzleState.value
    val cell = state.cellAt(x, y) ?: return
    if (!cell.isPlayable) return

    val tappedCoord = Coordinate(x, y)
    _puzzleState.update { it.copy(focusedCellCoord = tappedCoord) }

    // Recall unlocked placed tile if tapped
    if (cell.placedTile != null && !cell.isLockedCompleted) {
      soundEngine.onTilePickup(haptics)
      _puzzleState.value =
        lexiFillEngine
          .recallPlacedTileAt(_puzzleState.value, x, y)
          .copy(lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id)
      return
    }

    // If tapping a fixed clue or locked cell, focus/cycle the word passing through (x, y)
    if (!cell.isBlankTarget || cell.isLockedCompleted) {
      val wordsHere = state.targetWords.filter { it.containsCoordinate(x, y) }
      if (wordsHere.isNotEmpty()) {
        val nextWord =
          if (wordsHere.size >= 2 && wordsHere.first().id == state.focusedWordId) {
            wordsHere[1]
          } else {
            wordsHere.first()
          }
        selectClueWord(nextWord.id, haptics)
        _puzzleState.update { it.copy(focusedCellCoord = tappedCoord) }
      }
      return
    }

    // If a rack tile is currently selected, drop it onto this blank slot.
    // Otherwise, focus this cell and cycle/focus the intersecting word clue!
    val selectedTileId = state.rackState.selectedTileId
    if (selectedTileId != null) {
      onTileDropOnCoord(selectedTileId, tappedCoord, haptics)
    } else {
      val wordsHere = state.targetWords.filter { it.containsCoordinate(x, y) }
      if (wordsHere.isNotEmpty()) {
        val nextWord =
          if (wordsHere.size >= 2 && wordsHere.first().id == state.focusedWordId) {
            wordsHere[1]
          } else {
            wordsHere.first()
          }
        selectClueWord(nextWord.id, haptics)
        _puzzleState.update { it.copy(focusedCellCoord = tappedCoord) }
      }
    }
  }

  /**
   * Navigates the focused crossword cell using physical keyboard arrow keys (`dx`, `dy`).
   * Prioritizes playable cells directly along the movement axis, falling back to the nearest
   * playable cell in the requested direction.
   */
  fun moveFocusedCell(dx: Int, dy: Int, haptics: HapticFeedback? = null) {
    if (dx == 0 && dy == 0) return
    val state = _puzzleState.value
    val playableCoords =
      state.cells.flatten().filter { it.isPlayable }.map { it.coordinate }
    if (playableCoords.isEmpty()) return

    val current =
      state.focusedCellCoord
        ?: playableCoords.firstOrNull { c ->
          val cell = state.cellAt(c.x, c.y)
          cell != null && cell.isOpenBlank
        }
        ?: playableCoords.first()

    // 1. Check direct ray along (dx, dy)
    var step = 1
    var directMatch: Coordinate? = null
    while (step <= maxOf(state.boardWidth, state.boardHeight)) {
      val cx = current.x + dx * step
      val cy = current.y + dy * step
      if (cx !in 0 until state.boardWidth || cy !in 0 until state.boardHeight) break
      val candidate = state.cellAt(cx, cy)
      if (candidate != null && candidate.isPlayable) {
        directMatch = candidate.coordinate
        break
      }
      step++
    }

    // 2. Otherwise pick the closest playable cell in the requested directional half-plane
    val nextCoord =
      directMatch
        ?: playableCoords
          .filter { c ->
            c != current && ((c.x - current.x) * dx + (c.y - current.y) * dy) > 0
          }
          .minByOrNull { c ->
            val primaryDist = kotlin.math.abs((c.x - current.x) * dx + (c.y - current.y) * dy)
            val orthoDist = kotlin.math.abs((c.x - current.x) * dy + (c.y - current.y) * dx)
            primaryDist * 10 + orthoDist * 28
          }
        ?: return

    soundEngine.onTilePickup(haptics, pitchMultiplier = 1.08f)
    val wordsAtNext = state.targetWords.filter { it.containsCoordinate(nextCoord.x, nextCoord.y) }
    val preferredDir =
      if (dx != 0) com.example.domain.model.WordDirection.HORIZONTAL
      else com.example.domain.model.WordDirection.VERTICAL
    val matchedWord =
      wordsAtNext.find { it.direction == preferredDir }
        ?: wordsAtNext.find { it.id == state.focusedWordId }
        ?: wordsAtNext.firstOrNull()

    _puzzleState.update { s ->
      s.copy(
        focusedCellCoord = nextCoord,
        focusedWordId = matchedWord?.id ?: s.focusedWordId,
        lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id,
      )
    }
  }

  /**
   * Handles physical keyboard letter key presses (`A`–`Z`).
   * Places a matching letter tile from the rack into the currently focused cell (or the next
   * open blank cell in the focused word if the focused cell is already filled).
   */
  fun typeLetterInFocusedCell(rawChar: Char, haptics: HapticFeedback? = null): Boolean {
    val letter = rawChar.uppercaseChar()
    if (letter !in 'A'..'Z') return false
    val state = _puzzleState.value

    val targetCoord =
      state.focusedCellCoord?.takeIf { c ->
        val cell = state.cellAt(c.x, c.y)
        cell != null && cell.isBlankTarget && !cell.isLockedCompleted
      }
        ?: state.targetWords
          .find { it.id == state.focusedWordId }
          ?.coordinates()
          ?.firstOrNull { c ->
            val cell = state.cellAt(c.x, c.y)
            cell != null && cell.isBlankTarget && cell.placedTile == null && !cell.isLockedCompleted
          }
        ?: state.cells
          .flatten()
          .firstOrNull { it.isBlankTarget && it.placedTile == null && !it.isLockedCompleted }
          ?.coordinate
        ?: return false

    val matchingTile =
      state.rackState.tiles.firstOrNull { it.char.equals(letter, ignoreCase = true) }
    if (matchingTile == null) {
      soundEngine.onInvalidWord(haptics)
      _puzzleState.update { s ->
        s.copy(
          focusedCellCoord = targetCoord,
          lastSoundTriggered = AudioPlayer.SoundEffect.THUD.id,
          statusMessage = "No '$letter' tile available in your rack!",
          isStatusError = true,
        )
      }
      return false
    }

    return onTileDropOnCoord(matchingTile.id, targetCoord, haptics)
  }

  /**
   * Handles `Backspace` / `Delete` key presses on the crossword grid, recalling an unlocked
   * placed tile at the focused cell (or the most recently filled unlocked cell in the focused word).
   */
  fun backspaceInFocusedCell(haptics: HapticFeedback? = null): Boolean {
    val state = _puzzleState.value
    val fc = state.focusedCellCoord
    val directCell = fc?.let { state.cellAt(it.x, it.y) }
    val targetCell =
      if (directCell != null && directCell.placedTile != null && !directCell.isLockedCompleted) {
        directCell
      } else {
        state.targetWords
          .find { it.id == state.focusedWordId }
          ?.coordinates()
          ?.reversed()
          ?.mapNotNull { state.cellAt(it.x, it.y) }
          ?.firstOrNull { it.placedTile != null && !it.isLockedCompleted }
      } ?: return false

    soundEngine.onTilePickup(haptics)
    _puzzleState.value =
      lexiFillEngine
        .recallPlacedTileAt(state, targetCell.x, targetCell.y)
        .copy(lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id)
    return true
  }

  fun shuffleRack(haptics: HapticFeedback? = null) {
    soundEngine.onTilePickup(haptics)
    _puzzleState.update { s ->
      s.copy(
        rackState = s.rackState.copy(tiles = s.rackState.tiles.shuffled()),
        lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id,
        statusMessage = "Shuffled letter inventory.",
        isStatusError = false,
      )
    }
  }

  fun recallAllUnlockedTiles(haptics: HapticFeedback? = null) {
    val state = _puzzleState.value
    val unlockedTiles = ArrayList<Tile>()
    val updatedCells =
      state.cells.map { row ->
        row.map { c ->
          if (c.isBlankTarget && c.placedTile != null && !c.isLockedCompleted) {
            unlockedTiles.add(c.placedTile)
            c.copy(placedTile = null)
          } else {
            c
          }
        }
      }
    if (unlockedTiles.isEmpty()) return
    soundEngine.onTilePickup(haptics)
    _puzzleState.update { s ->
      s.copy(
        cells = updatedCells,
        rackState = s.rackState.copy(tiles = s.rackState.tiles + unlockedTiles),
        lastSoundTriggered = AudioPlayer.SoundEffect.CLICK.id,
        statusMessage = "Recalled ${unlockedTiles.size} unlocked tile(s) to rack.",
        isStatusError = false,
      )
    }
  }

  /**
   * Smart Intersection Hint:
   * Highlights the highest-priority open intersection blank slot and selects the matching
   * letter tile in the rack so the player can snap it in.
   */
  fun triggerSmartHint(haptics: HapticFeedback? = null) {
    val state = _puzzleState.value
    if (state.hintsRemaining <= 0) return

    val openBlanks =
      state.cells.flatten().filter { it.isBlankTarget && it.placedTile == null && !it.isLockedCompleted }
    val targetBlank =
      openBlanks.firstOrNull { it.isIntersection } ?: openBlanks.firstOrNull() ?: return

    val expectedChar = targetBlank.expectedLetter ?: return
    val matchingTile =
      state.rackState.tiles.firstOrNull { it.char.equals(expectedChar, ignoreCase = true) }

    soundEngine.onTileSnapToGrid(haptics, 1.25f)
    _puzzleState.update { s ->
      s.copy(
        hintsRemaining = (s.hintsRemaining - 1).coerceAtLeast(0),
        focusedCellCoord = targetBlank.coordinate,
        highlightedHintCoord = targetBlank.coordinate,
        rackState = s.rackState.copy(selectedTileId = matchingTile?.id ?: s.rackState.selectedTileId),
        lastSoundTriggered = AudioPlayer.SoundEffect.SNAP.id,
        statusMessage =
          "Smart Hint: Place '$expectedChar' at intersection (${targetBlank.x + 1}, ${targetBlank.y + 1})!",
        isStatusError = false,
      )
    }
  }

  fun clearShake() {
    _puzzleState.update { it.copy(boardShakeIntensity = 0f) }
  }

  private fun startAutoAdvanceCountdown() {
    autoAdvanceJob?.cancel()
    autoAdvanceJob =
      viewModelScope.launch {
        for (sec in 3 downTo 1) {
          _puzzleState.update { it.copy(autoAdvanceCountdownSec = sec) }
          delay(1000L)
        }
        if (_puzzleState.value.isLevelCleared) {
          advanceToNextLevel()
        }
      }
  }

  fun unlockOrEquipSkin(skin: CosmeticTileSkin, haptics: HapticFeedback? = null) {
    val state = _puzzleState.value
    soundEngine.onTileSnapToGrid(haptics, 1.15f)
    if (skin.id in state.unlockedSkinIds) {
      _puzzleState.update {
        it.copy(
          equippedSkin = skin,
          lastSoundTriggered = AudioPlayer.SoundEffect.SNAP.id,
        )
      }
    } else if (state.lexiCoins >= skin.costCoins) {
      _puzzleState.update { s ->
        s.copy(
          lexiCoins = s.lexiCoins - skin.costCoins,
          unlockedSkinIds = s.unlockedSkinIds + skin.id,
          equippedSkin = skin,
          lastSoundTriggered = AudioPlayer.SoundEffect.SNAP.id,
        )
      }
    }
  }

  private fun syncCompletedLevelIfSignedIn(state: PuzzleState) {
    val repo = repository ?: return
    if (currentUserId.isNullOrBlank()) return
    val bestWord = state.targetWords.maxByOrNull { it.awardedScore }?.word ?: "PLANET"
    viewModelScope.launch {
      repo.updateProfileStats(
        scoreDelta = state.levelScore,
        wordScore = state.levelScore,
        playedWord = bestWord,
        coinsDelta = state.levelClearSummary?.coinsEarned ?: 30,
        advanceCampaignTo = state.highestUnlockedLevel,
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    autoAdvanceJob?.cancel()
    soundEngine.release()
  }

  private companion object {
    const val TAG = "GameViewModel"
  }
}
