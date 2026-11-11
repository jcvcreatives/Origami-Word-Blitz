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
  private val soundEngine: SoundAndHapticsEngine? = null,
) : ViewModel() {

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
      repo.ensureOrCreateProfile()
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

  fun onRackTileTapped(tile: Tile, haptics: HapticFeedback? = null) {
    soundEngine?.onTilePickup(haptics)
    _puzzleState.update { s ->
      val nextSelected = if (s.rackState.selectedTileId == tile.id) null else tile.id
      s.copy(
        rackState = s.rackState.copy(selectedTileId = nextSelected),
        statusMessage =
          if (nextSelected != null) {
            "Selected '${tile.char}' (${tile.baseScore} pts) — tap an open blank slot on the grid!"
          } else {
            "Drag or tap a tile to fill an intersecting blank slot."
          },
        isStatusError = false,
      )
    }
  }

  fun onTileDragStart(tile: Tile, haptics: HapticFeedback? = null) {
    lastHoverCoord = null
    soundEngine?.onTilePickup(haptics)
    _puzzleState.update { s ->
      s.copy(
        rackState =
          s.rackState.copy(
            draggingTileId = tile.id,
            selectedTileId = tile.id,
          ),
        highlightedHintCoord = null,
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

    // Subtle haptic tick when magnetically snapping onto a new open blank target
    soundEngine?.onTilePickup(haptics)
    val preview = lexiFillEngine.previewTileDrop(state, tile, targetCoord.x, targetCoord.y)
    _puzzleState.update { s ->
      s.copy(
        hoverCoord = targetCoord,
        hoverPreview = preview,
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
      onTileDragCancel()
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

    _puzzleState.value = result.updatedState

    return when (result.outcome) {
      LexiFillEngine.OutcomeType.NO_OP,
      LexiFillEngine.OutcomeType.RETURNED_OUTSIDE_SLOT -> {
        false
      }
      LexiFillEngine.OutcomeType.INVALID_INTERSECTION -> {
        soundEngine?.onInvalidWord(haptics)
        false
      }
      LexiFillEngine.OutcomeType.TILE_SNAPPED -> {
        soundEngine?.onTileSnapToGrid(haptics, 1.05f)
        true
      }
      LexiFillEngine.OutcomeType.WORD_COMPLETED -> {
        soundEngine?.onWordScored(
          score = result.scoreGained,
          isTripleWord = false,
          comboStreak = result.updatedState.comboStreak,
          haptics = haptics,
        )
        true
      }
      LexiFillEngine.OutcomeType.DUAL_WORD_COMPLETED -> {
        soundEngine?.onWordScored(
          score = result.scoreGained,
          isTripleWord = true,
          comboStreak = result.updatedState.comboStreak,
          haptics = haptics,
        )
        true
      }
      LexiFillEngine.OutcomeType.LEVEL_CLEARED -> {
        soundEngine?.onWordScored(
          score = result.scoreGained,
          isTripleWord = true,
          comboStreak = result.updatedState.comboStreak,
          haptics = haptics,
        )
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
  fun onBoardCellTapped(x: Int, y: Int, haptics: HapticFeedback? = null) {
    val state = _puzzleState.value
    val cell = state.cellAt(x, y) ?: return

    // Recall unlocked placed tile if tapped
    if (cell.placedTile != null && !cell.isLockedCompleted) {
      soundEngine?.onTilePickup(haptics)
      _puzzleState.value = lexiFillEngine.recallPlacedTileAt(state, x, y)
      return
    }

    if (!cell.isBlankTarget || cell.isLockedCompleted) return

    val selectedTileId =
      state.rackState.selectedTileId ?: state.rackState.tiles.firstOrNull()?.id ?: return
    onTileDropOnCoord(selectedTileId, Coordinate(x, y), haptics)
  }

  fun shuffleRack(haptics: HapticFeedback? = null) {
    soundEngine?.onTilePickup(haptics)
    _puzzleState.update { s ->
      s.copy(
        rackState = s.rackState.copy(tiles = s.rackState.tiles.shuffled()),
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
    soundEngine?.onTilePickup(haptics)
    _puzzleState.update { s ->
      s.copy(
        cells = updatedCells,
        rackState = s.rackState.copy(tiles = s.rackState.tiles + unlockedTiles),
        statusMessage = "Recalled ${unlockedTiles.size} unlocked tile(s) to rack.",
        isStatusError = false,
      )
    }
  }

  /**
   * Smart Intersection Hint:
   * Prioritizes open intersection blank slots (`isIntersection && isOpenBlank`) first,
   * highlights the target coordinate with a golden ghost letter, and selects the matching tile in the rack.
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

    soundEngine?.onTileSnapToGrid(haptics, 1.25f)
    _puzzleState.update { s ->
      s.copy(
        hintsRemaining = (s.hintsRemaining - 1).coerceAtLeast(0),
        highlightedHintCoord = targetBlank.coordinate,
        rackState = s.rackState.copy(selectedTileId = matchingTile?.id),
        statusMessage =
          "Hint: Drop '$expectedChar' at (${targetBlank.x + 1}, ${targetBlank.y + 1}) — tap the glowing gold slot to place!",
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
    soundEngine?.onTileSnapToGrid(haptics, 1.15f)
    if (skin.id in state.unlockedSkinIds) {
      _puzzleState.update { it.copy(equippedSkin = skin) }
    } else if (state.lexiCoins >= skin.costCoins) {
      _puzzleState.update { s ->
        s.copy(
          lexiCoins = s.lexiCoins - skin.costCoins,
          unlockedSkinIds = s.unlockedSkinIds + skin.id,
          equippedSkin = skin,
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
    soundEngine?.release()
  }

  private companion object {
    const val TAG = "GameViewModel"
  }
}
