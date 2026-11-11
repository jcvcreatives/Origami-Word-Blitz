package com.example.ui.viewmodel

import android.util.Log
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.remote.SavedMatchDocument
import com.example.data.remote.UserProfileDocument
import com.example.data.repository.OrigamiCloudRepository
import com.example.domain.engine.BoardEvaluator
import com.example.domain.engine.WordTrieEngine
import com.example.domain.model.AiOpponent
import com.example.domain.model.BoardSquare
import com.example.domain.model.BurstColorType
import com.example.domain.model.Coordinate
import com.example.domain.model.CosmeticTileSkin
import com.example.domain.model.FloatingScoreBurst
import com.example.domain.model.GameCatalog
import com.example.domain.model.GameMode
import com.example.domain.model.GameState
import com.example.domain.model.LexiBoardState
import com.example.domain.model.ParticleBurst
import com.example.domain.model.RackState
import com.example.domain.model.RogueRelic
import com.example.domain.model.ScoreState
import com.example.domain.model.SquareMultiplier
import com.example.domain.model.Tile
import com.example.domain.model.TileBonusEffect
import com.example.domain.model.TurnStatus
import com.example.ui.feedback.SoundAndHapticsEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CloudUiState<out T> {
  data object Loading : CloudUiState<Nothing>
  data class Success<T>(val data: T) : CloudUiState<T>
  data class Error(val message: String) : CloudUiState<Nothing>
}

class GameViewModel(
  private val repository: OrigamiCloudRepository,
  private val currentUserId: String,
  private val soundEngine: SoundAndHapticsEngine? = null,
) : ViewModel() {

  val trieEngine = WordTrieEngine()
  val boardEvaluator = BoardEvaluator(trieEngine)

  private val tileBag = ArrayList<Tile>()
  private var timerJob: Job? = null

  private val _gameState = MutableStateFlow(createInitialGameState())
  val gameState: StateFlow<GameState> = _gameState.asStateFlow()

  val cloudMatchesState: StateFlow<CloudUiState<List<SavedMatchDocument>>> =
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
        Log.w(TAG, "Error observing matches", rootError)
        emit(CloudUiState.Error(rootError.message ?: "Failed to load cloud matches"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = CloudUiState.Loading,
      )

  init {
    observeCloudProfile()
    startNewMatch(
      mode = GameMode.CAMPAIGN,
      opponent = GameCatalog.CAMPAIGN_OPPONENTS.first(),
    )
  }

  private fun observeCloudProfile() {
    viewModelScope.launch {
      repository.ensureOrCreateProfile()
      repository
        .observeUserProfile(currentUserId)
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
            _gameState.update { state ->
              state.copy(
                paperCoins = profile.paperCoins.toInt(),
                unlockedSkinIds = unlocked,
                equippedSkin = equipped,
                campaignStageUnlocked = profile.campaignStage.toInt().coerceIn(1, 4),
                bestWordPlayed =
                  if (profile.bestWord.isNotBlank()) profile.bestWord else state.bestWordPlayed,
                highestSingleTurnScore =
                  maxOf(state.highestSingleTurnScore, profile.highestWordScore.toInt()),
              )
            }
          }
        }
    }
  }

  private fun createInitialGameState(): GameState {
    val initialSquares = GameCatalog.createStandardBoard()
    return GameState(
      boardState = LexiBoardState(squares = initialSquares),
      rackState = RackState(tiles = emptyList()),
    )
  }

  fun startNewMatch(
    mode: GameMode,
    opponent: AiOpponent = _gameState.value.opponent,
    customRelics: List<RogueRelic>? = null,
  ) {
    refillTileBag()
    var baseSquares = GameCatalog.createStandardBoard()

    // In DAILY_BLITZ mode, pre-seed "ORIGAMI" horizontally across the Center Star (★)
    if (mode == GameMode.DAILY_BLITZ) {
      val seedWord = "ORIGAMI"
      val startX = 4
      val y = 7
      baseSquares =
        baseSquares.mapIndexed { rowIdx, row ->
          if (rowIdx != y) {
            row
          } else {
            row.mapIndexed { colIdx, sq ->
              val charIndex = colIdx - startX
              if (charIndex in seedWord.indices) {
                val ch = seedWord[charIndex]
                sq.copy(
                  occupyingTile =
                    Tile(
                      id = "seed_${colIdx}_$rowIdx",
                      char = ch,
                      baseScore = GameCatalog.letterBaseScore(ch),
                      isLockedOnBoard = true,
                    ),
                  isMultiplierConsumed = true,
                )
              } else {
                sq
              }
            }
          }
        }
    }

    val p1Rack = drawTilesFromBag(7, guaranteedVowelCount = 2)
    val p2Rack = drawTilesFromBag(7, guaranteedVowelCount = 2)
    val turnLimit = if (mode == GameMode.DAILY_BLITZ) 35 else 45
    val activeRelics =
      customRelics
        ?: listOf(opponent.primaryRelic, RogueRelic.QUICK_FOLD).distinct()

    val newBoard = LexiBoardState(squares = baseSquares)
    _gameState.update { current ->
      current.copy(
        mode = mode,
        opponent = opponent,
        activeRelics = activeRelics,
        boardState = newBoard,
        rackState = RackState(tiles = p1Rack),
        playerTwoRack = p2Rack,
        scoreState =
          ScoreState(
            playerScore = 0,
            opponentScore = 0,
            comboStreak = 0,
            comboMultiplier = 1.0f,
            turnTimeRemainingSec = turnLimit,
            maxTurnTimeSec = turnLimit,
            tileBagRemaining = tileBag.size,
          ),
        turnStatus = TurnStatus.PLAYER_ONE_TURN,
        turnNumber = 1,
        floatingBursts = emptyList(),
        particleBursts = emptyList(),
        hintSuggestion = null,
        consecutivePasses = 0,
        lastMoveSummary =
          when (mode) {
            GameMode.DAILY_BLITZ ->
              "Daily Blitz! Build off 'ORIGAMI' before the 35s clock runs out!"
            GameMode.PASS_AND_PLAY ->
              "Player 1's Turn! Place your first word across the Center Star (★)."
            else ->
              "Duel vs ${opponent.name}! Fold your first word across the Center Star (★)."
          },
      )
    }

    startTurnTimer()

    viewModelScope.launch {
      val boardSnap = serializeBoard(newBoard)
      val rackSnap = p1Rack.joinToString("") { it.char.toString() }
      val createdId =
        repository
          .createMatch(
            mode = mode.name,
            opponentName =
              if (mode == GameMode.PASS_AND_PLAY) "Player 2" else opponent.name,
            boardSnapshot = boardSnap,
            rackSnapshot = rackSnap,
          )
          .getOrNull()
      if (createdId != null) {
        _gameState.update { it.copy(activeMatchId = createdId) }
      }
    }
  }

  private fun startTurnTimer() {
    timerJob?.cancel()
    timerJob =
      viewModelScope.launch {
        while (true) {
          delay(1000L)
          val state = _gameState.value
          if (state.turnStatus != TurnStatus.PLAYER_ONE_TURN) continue
          val remaining = state.scoreState.turnTimeRemainingSec
          if (remaining > 1) {
            _gameState.update { s ->
              val updatedScore = s.scoreState.copy(turnTimeRemainingSec = remaining - 1)
              val preview =
                if (s.boardState.pendingPlacements.isNotEmpty()) {
                  boardEvaluator.evaluatePendingMove(
                    board = s.boardState,
                    activeRelics = s.activeRelics,
                    comboMultiplier = updatedScore.comboMultiplier,
                    turnTimeRemainingSec = updatedScore.turnTimeRemainingSec,
                  )
                } else {
                  null
                }
              s.copy(
                scoreState = updatedScore,
                boardState = s.boardState.copy(movePreview = preview),
              )
            }
          } else {
            // Timer expired -> auto recall pending tiles and reset combo streak
            recallAllPendingTiles()
            passTurn(reason = "Turn clock expired! Combo streak reset.")
          }
        }
      }
  }

  fun selectRackTile(tileId: String, haptics: HapticFeedback? = null) {
    soundEngine?.onTilePickup(haptics)
    _gameState.update { state ->
      val currentSelected = state.rackState.selectedTileId
      state.copy(
        rackState =
          state.rackState.copy(
            selectedTileId = if (currentSelected == tileId) null else tileId
          )
      )
    }
  }

  fun setDraggingTile(tileId: String?, haptics: HapticFeedback? = null) {
    if (tileId != null) {
      soundEngine?.onTilePickup(haptics)
    }
    _gameState.update { state ->
      state.copy(
        rackState =
          state.rackState.copy(
            draggingTileId = tileId,
            selectedTileId = tileId ?: state.rackState.selectedTileId,
          )
      )
    }
  }

  /**
   * Places a tile from the player's rack (or moves an already-placed pending tile) onto
   * [targetCoord] on the 15x15 board, immediately recalculating the live [MovePreview].
   */
  fun placeTileOnBoard(
    tileId: String,
    targetCoord: Coordinate,
    haptics: HapticFeedback? = null,
  ): Boolean {
    if (!targetCoord.isValid) return false
    val state = _gameState.value
    if (
      state.turnStatus != TurnStatus.PLAYER_ONE_TURN &&
        !(state.mode == GameMode.PASS_AND_PLAY &&
          state.turnStatus == TurnStatus.PLAYER_TWO_OR_AI_TURN)
    ) {
      return false
    }

    // Cannot drop on a locked square
    if (state.boardState.squareAt(targetCoord)?.occupyingTile != null) return false

    // Find the tile either in the rack or already in pendingPlacements
    val rackTile = state.rackState.tiles.find { it.id == tileId }
    val existingPendingEntry =
      state.boardState.pendingPlacements.entries.find { it.value.id == tileId }
    val tileToPlace = rackTile ?: existingPendingEntry?.value ?: return false

    val updatedPending = state.boardState.pendingPlacements.toMutableMap()
    if (existingPendingEntry != null) {
      updatedPending.remove(existingPendingEntry.key)
    }

    // If another pending tile was at targetCoord, return that displaced tile to the rack
    val displacedTile = updatedPending.put(targetCoord, tileToPlace)
    val updatedRack = state.rackState.tiles.filterNot { it.id == tileId }.toMutableList()
    if (displacedTile != null && displacedTile.id != tileId) {
      updatedRack.add(displacedTile)
    }

    val updatedBoard =
      state.boardState.copy(
        pendingPlacements = updatedPending,
        hintCoords = emptySet(),
      )
    val preview =
      boardEvaluator.evaluatePendingMove(
        board = updatedBoard,
        activeRelics = state.activeRelics,
        comboMultiplier = state.scoreState.comboMultiplier,
        turnTimeRemainingSec = state.scoreState.turnTimeRemainingSec,
      )

    val pitch = 1.0f + (updatedPending.size * 0.05f)
    soundEngine?.onTileSnapToGrid(haptics, pitch)

    _gameState.update { s ->
      s.copy(
        boardState = updatedBoard.copy(movePreview = preview),
        rackState =
          s.rackState.copy(
            tiles = updatedRack,
            selectedTileId = null,
            draggingTileId = null,
          ),
        hintSuggestion = null,
      )
    }
    return true
  }

  /**
   * Handles tapping a square on the 15x15 board:
   * - If the square has a pending tile placed this turn, recalls that tile back to the rack.
   * - Otherwise, if a tile is selected in the rack (or defaults to the first rack tile when
   *   double-tapping), places it on [coord].
   */
  fun onBoardSquareTapped(coord: Coordinate, haptics: HapticFeedback? = null) {
    val state = _gameState.value
    val pendingTile = state.boardState.pendingPlacements[coord]
    if (pendingTile != null) {
      recallSingleTileFromBoard(coord, haptics)
      return
    }
    if (state.boardState.squareAt(coord)?.occupyingTile != null) return

    val selectedId =
      state.rackState.selectedTileId ?: state.rackState.tiles.firstOrNull()?.id ?: return
    placeTileOnBoard(selectedId, coord, haptics)
  }

  fun recallSingleTileFromBoard(coord: Coordinate, haptics: HapticFeedback? = null) {
    val state = _gameState.value
    val removed = state.boardState.pendingPlacements[coord] ?: return
    soundEngine?.onTilePickup(haptics)

    val updatedPending = state.boardState.pendingPlacements.toMutableMap()
    updatedPending.remove(coord)
    val updatedRack = state.rackState.tiles + removed
    val updatedBoard = state.boardState.copy(pendingPlacements = updatedPending)
    val preview =
      if (updatedPending.isNotEmpty()) {
        boardEvaluator.evaluatePendingMove(
          board = updatedBoard,
          activeRelics = state.activeRelics,
          comboMultiplier = state.scoreState.comboMultiplier,
          turnTimeRemainingSec = state.scoreState.turnTimeRemainingSec,
        )
      } else {
        null
      }

    _gameState.update { s ->
      s.copy(
        boardState = updatedBoard.copy(movePreview = preview),
        rackState = s.rackState.copy(tiles = updatedRack, selectedTileId = removed.id),
      )
    }
  }

  fun recallAllPendingTiles(haptics: HapticFeedback? = null) {
    val state = _gameState.value
    if (state.boardState.pendingPlacements.isEmpty()) return
    soundEngine?.onTilePickup(haptics)
    val recalled = state.boardState.pendingPlacements.values.toList()
    _gameState.update { s ->
      s.copy(
        boardState =
          s.boardState.copy(
            pendingPlacements = emptyMap(),
            movePreview = null,
          ),
        rackState =
          s.rackState.copy(
            tiles = s.rackState.tiles + recalled,
            selectedTileId = null,
            draggingTileId = null,
          ),
      )
    }
  }

  fun shuffleRack(haptics: HapticFeedback? = null) {
    soundEngine?.onTilePickup(haptics)
    _gameState.update { s ->
      s.copy(rackState = s.rackState.copy(tiles = s.rackState.tiles.shuffled()))
    }
  }

  fun toggleRelic(relic: RogueRelic) {
    _gameState.update { s ->
      val current = s.activeRelics.toMutableList()
      if (relic in current) {
        if (current.size > 1) current.remove(relic)
      } else {
        if (current.size >= 3) current.removeAt(0)
        current.add(relic)
      }
      val preview =
        if (s.boardState.pendingPlacements.isNotEmpty()) {
          boardEvaluator.evaluatePendingMove(
            board = s.boardState,
            activeRelics = current,
            comboMultiplier = s.scoreState.comboMultiplier,
            turnTimeRemainingSec = s.scoreState.turnTimeRemainingSec,
          )
        } else {
          null
        }
      s.copy(
        activeRelics = current,
        boardState = s.boardState.copy(movePreview = preview),
      )
    }
  }

  /**
   * Uses the <2ms Trie solver to find and highlight a valid high-scoring play on the board
   * using the player's current 7-tile rack.
   */
  fun requestTrieHint(haptics: HapticFeedback? = null) {
    val state = _gameState.value
    val fullRack = state.rackState.tiles + state.boardState.pendingPlacements.values
    val candidate =
      boardEvaluator.findBestAiMove(
        board = state.boardState.copy(pendingPlacements = emptyMap()),
        aiRack = fullRack,
        opponent =
          state.opponent.copy(
            style = com.example.domain.model.AiVocabularyStyle.GRANDMASTER_OPTIMAL
          ),
      )
    soundEngine?.onTilePickup(haptics)
    if (candidate != null) {
      _gameState.update { s ->
        s.copy(
          boardState = s.boardState.copy(hintCoords = candidate.placements.keys),
          hintSuggestion =
            "Trie Hint: Fold '${candidate.primaryWord}' (${candidate.preview.totalTurnScore} pts) on highlighted squares!",
        )
      }
    } else {
      val anagrams = trieEngine.findAnagrams(fullRack.map { it.char }, minLength = 2, maxResults = 5)
      _gameState.update { s ->
        s.copy(
          hintSuggestion =
            if (anagrams.isNotEmpty()) {
              "Rack Anagrams: ${anagrams.take(4).joinToString(", ")}"
            } else {
              "No valid moves found — try shuffling or passing to refresh tiles!"
            }
        )
      }
    }
  }

  /**
   * Auto-places the best Trie-discovered move onto the board as pending tiles so the user can
   * preview the multiplier breakdown before submitting!
   */
  fun autoFoldBestHintMove(haptics: HapticFeedback? = null) {
    recallAllPendingTiles()
    val state = _gameState.value
    val candidate =
      boardEvaluator.findBestAiMove(
        board = state.boardState,
        aiRack = state.rackState.tiles,
        opponent =
          state.opponent.copy(
            style = com.example.domain.model.AiVocabularyStyle.GRANDMASTER_OPTIMAL
          ),
      ) ?: return

    val placedIds = candidate.placements.values.map { it.id }.toSet()
    val remainingRack = state.rackState.tiles.filterNot { it.id in placedIds }
    val updatedBoard =
      state.boardState.copy(
        pendingPlacements = candidate.placements,
        hintCoords = emptySet(),
      )
    val preview =
      boardEvaluator.evaluatePendingMove(
        board = updatedBoard,
        activeRelics = state.activeRelics,
        comboMultiplier = state.scoreState.comboMultiplier,
        turnTimeRemainingSec = state.scoreState.turnTimeRemainingSec,
      )
    soundEngine?.onTileSnapToGrid(haptics, 1.2f)
    _gameState.update { s ->
      s.copy(
        boardState = updatedBoard.copy(movePreview = preview),
        rackState = s.rackState.copy(tiles = remainingRack, selectedTileId = null),
        hintSuggestion = "Ready to fold '${candidate.primaryWord}' for +${preview.totalTurnScore} pts!",
      )
    }
  }

  /**
   * Commits the pending tiles to the 15x15 board if all formed horizontal and vertical words
   * are valid in the Trie, triggering particle bursts, floating score popups, screen micro-shake,
   * SoundPool audio, and progressive haptics.
   */
  fun submitTurn(haptics: HapticFeedback? = null) {
    val state = _gameState.value
    val pending = state.boardState.pendingPlacements
    if (pending.isEmpty()) return

    val preview =
      boardEvaluator.evaluatePendingMove(
        board = state.boardState,
        activeRelics = state.activeRelics,
        comboMultiplier = state.scoreState.comboMultiplier,
        turnTimeRemainingSec = state.scoreState.turnTimeRemainingSec,
      )

    if (!preview.isValidPosition || !preview.allWordsValid) {
      soundEngine?.onInvalidWord(haptics)
      val errorText = preview.placementError ?: "Invalid word placement!"
      triggerInvalidShake(errorText)
      return
    }

    // Valid play! Lock tiles onto the 15x15 grid
    val newSquares: List<List<BoardSquare>> =
      state.boardState.squares.map { row ->
        row.map { sq ->
          val placedTile = pending[sq.coordinate]
          if (placedTile != null) {
            sq.copy(
              occupyingTile = placedTile.copy(isLockedOnBoard = true),
              isMultiplierConsumed = true,
            )
          } else {
            sq
          }
        }
      }

    val newComboStreak = state.scoreState.comboStreak + 1
    val nextComboMult =
      when {
        newComboStreak >= 4 -> 2.0f
        newComboStreak == 3 -> 1.5f
        newComboStreak == 2 -> 1.25f
        else -> 1.0f
      }

    val turnPoints = preview.totalTurnScore
    val isPlayerOne = state.turnStatus == TurnStatus.PLAYER_ONE_TURN
    val updatedP1Score =
      if (isPlayerOne) state.scoreState.playerScore + turnPoints else state.scoreState.playerScore
    val updatedP2Score =
      if (!isPlayerOne) {
        state.scoreState.opponentScore + turnPoints
      } else {
        state.scoreState.opponentScore
      }

    val primaryWordStr = preview.formedWords.firstOrNull()?.word ?: ""
    val isTripleWord = preview.maxSquareMultiplier == SquareMultiplier.TW
    val isDoubleWord =
      preview.maxSquareMultiplier == SquareMultiplier.DW ||
        preview.maxSquareMultiplier == SquareMultiplier.CENTER_STAR

    soundEngine?.onWordScored(
      score = turnPoints,
      isTripleWord = isTripleWord,
      comboStreak = newComboStreak,
      haptics = haptics,
    )

    // Create Game Juice floating burst & particles
    val burstHeadline =
      buildString {
        append("+").append(turnPoints).append("! ")
        when {
          isTripleWord -> append("TRIPLE WORD!")
          preview.bingoBonusPoints > 0 -> append("ORIGAMI BINGO!")
          isDoubleWord -> append("DOUBLE WORD!")
          newComboStreak >= 2 -> append("COMBO ×${state.scoreState.comboMultiplier}!")
          else -> append(primaryWordStr)
        }
      }
    val burstSubline =
      preview.formedWords.joinToString(" • ") {
        if (it.wordMultiplier > 1) "${it.word} (${it.basePoints}×${it.wordMultiplier})"
        else "${it.word} (+${it.totalPoints})"
      }

    val anchorCoord = pending.keys.firstOrNull() ?: Coordinate.CENTER
    val burst =
      FloatingScoreBurst(
        id = UUID.randomUUID().toString(),
        headline = burstHeadline,
        subline = burstSubline,
        boardCoord = anchorCoord,
        colorType =
          when {
            isTripleWord -> BurstColorType.TRIPLE_WORD
            isDoubleWord -> BurstColorType.DOUBLE_WORD
            newComboStreak >= 2 -> BurstColorType.COMBO_STREAK
            else -> BurstColorType.STANDARD
          },
      )

    val particles =
      pending.keys.map { coord ->
        ParticleBurst(
          id = UUID.randomUUID().toString(),
          boardCoord = coord,
          colorHex =
            when {
              isTripleWord -> 0xFFFF5A36
              isDoubleWord -> 0xFFFFB703
              else -> 0xFF00D6A3
            },
          particleCount = if (isTripleWord || turnPoints >= 35) 24 else 14,
        )
      }

    val drawnTiles = drawTilesFromBag(7 - state.rackState.tiles.size)
    val replenishedCurrentRack = state.rackState.tiles + drawnTiles

    val sakuraExtraTime =
      pending.values.count { it.bonusEffect == TileBonusEffect.SAKURA_SPARK } * 10
    val nextMaxTime = state.scoreState.maxTurnTimeSec
    val nextRemainingTime = (nextMaxTime + sakuraExtraTime).coerceAtMost(60)

    val coinsEarned = maxOf(5, turnPoints / 4) + (if (isTripleWord) 10 else 0)
    val bestWord =
      if (turnPoints >= state.highestSingleTurnScore && primaryWordStr.isNotBlank()) {
        primaryWordStr
      } else {
        state.bestWordPlayed
      }
    val bestTurnScore = maxOf(state.highestSingleTurnScore, turnPoints)

    val shakeAmount =
      when {
        isTripleWord || turnPoints >= 45 -> 14f
        isDoubleWord || turnPoints >= 25 -> 7f
        else -> 3f
      }

    val nextTurnStatus =
      when (state.mode) {
        GameMode.PASS_AND_PLAY -> TurnStatus.PASS_DEVICE_INTERSTITIAL
        else -> TurnStatus.PLAYER_TWO_OR_AI_TURN
      }

    val updatedBoardState =
      LexiBoardState(
        squares = newSquares,
        pendingPlacements = emptyMap(),
        movePreview = null,
        lastPlayedCoords = pending.keys,
        hintCoords = emptySet(),
      )

    _gameState.update { s ->
      s.copy(
        boardState = updatedBoardState,
        rackState =
          if (state.mode == GameMode.PASS_AND_PLAY && isPlayerOne) {
            RackState(tiles = s.playerTwoRack)
          } else {
            RackState(tiles = replenishedCurrentRack)
          },
        playerTwoRack =
          if (state.mode == GameMode.PASS_AND_PLAY && isPlayerOne) {
            replenishedCurrentRack
          } else {
            s.playerTwoRack
          },
        scoreState =
          s.scoreState.copy(
            playerScore = updatedP1Score,
            opponentScore = updatedP2Score,
            comboStreak = newComboStreak,
            comboMultiplier = nextComboMult,
            turnTimeRemainingSec = nextRemainingTime,
            tileBagRemaining = tileBag.size,
          ),
        turnStatus = nextTurnStatus,
        turnNumber = s.turnNumber + 1,
        paperCoins = s.paperCoins + coinsEarned,
        floatingBursts = (s.floatingBursts + burst).takeLast(3),
        particleBursts = particles,
        boardShakeIntensity = shakeAmount,
        lastMoveSummary =
          "You folded '$primaryWordStr' for +$turnPoints pts! (+$coinsEarned Paper Coins)",
        hintSuggestion = null,
        bestWordPlayed = bestWord,
        highestSingleTurnScore = bestTurnScore,
        consecutivePasses = 0,
      )
    }

    // Clear shake and transient bursts after animation window
    viewModelScope.launch {
      delay(450L)
      _gameState.update { it.copy(boardShakeIntensity = 0f) }
      delay(1600L)
      _gameState.update { s ->
        s.copy(
          floatingBursts = s.floatingBursts.filterNot { it.id == burst.id },
          particleBursts = emptyList(),
        )
      }
    }

    // Sync profile & match progress to Firestore
    syncTurnToCloud(
      scoreDelta = turnPoints,
      wordScore = turnPoints,
      playedWord = primaryWordStr,
      coinsDelta = coinsEarned,
    )

    if (state.mode != GameMode.PASS_AND_PLAY) {
      executeAiOpponentTurn()
    }
  }

  fun confirmPassDeviceReady() {
    _gameState.update { s ->
      val nextPlayerTurn =
        if (s.turnNumber % 2 == 0) TurnStatus.PLAYER_TWO_OR_AI_TURN
        else TurnStatus.PLAYER_ONE_TURN
      s.copy(
        turnStatus = nextPlayerTurn,
        lastMoveSummary =
          if (nextPlayerTurn == TurnStatus.PLAYER_ONE_TURN) {
            "Player 1's Turn — Fold your word!"
          } else {
            "Player 2's Turn — Fold your word!"
          },
      )
    }
  }

  fun passTurn(reason: String = "Passed turn & refreshed 3 rack tiles.") {
    recallAllPendingTiles()
    val state = _gameState.value
    val currentTiles = state.rackState.tiles
    val kept = currentTiles.drop(3)
    val swappedOut = currentTiles.take(3)
    tileBag.addAll(swappedOut.map { it.copy(isLockedOnBoard = false) })
    val newlyDrawn = drawTilesFromBag(swappedOut.size)

    val newPasses = state.consecutivePasses + 1
    if (newPasses >= 4) {
      finishMatch()
      return
    }

    _gameState.update { s ->
      s.copy(
        rackState = RackState(tiles = kept + newlyDrawn),
        scoreState =
          s.scoreState.copy(
            comboStreak = 0,
            comboMultiplier = 1.0f,
            turnTimeRemainingSec = s.scoreState.maxTurnTimeSec,
          ),
        turnStatus =
          if (s.mode == GameMode.PASS_AND_PLAY) TurnStatus.PASS_DEVICE_INTERSTITIAL
          else TurnStatus.PLAYER_TWO_OR_AI_TURN,
        turnNumber = s.turnNumber + 1,
        lastMoveSummary = reason,
        consecutivePasses = newPasses,
      )
    }

    if (state.mode != GameMode.PASS_AND_PLAY) {
      executeAiOpponentTurn()
    }
  }

  private fun executeAiOpponentTurn() {
    viewModelScope.launch {
      delay(650L)
      val state = _gameState.value
      if (state.turnStatus != TurnStatus.PLAYER_TWO_OR_AI_TURN) return@launch

      // Check if player reached target score in Campaign mode
      if (
        state.mode == GameMode.CAMPAIGN &&
          state.scoreState.playerScore >= state.opponent.targetScore
      ) {
        finishMatch()
        return@launch
      }

      val aiMove =
        boardEvaluator.findBestAiMove(
          board = state.boardState,
          aiRack = state.playerTwoRack,
          opponent = state.opponent,
        )

      if (aiMove != null) {
        val placedCoords = aiMove.placements.keys
        val newSquares =
          state.boardState.squares.map { row ->
            row.map { sq ->
              val aiTile = aiMove.placements[sq.coordinate]
              if (aiTile != null) {
                sq.copy(
                  occupyingTile = aiTile.copy(isLockedOnBoard = true),
                  isMultiplierConsumed = true,
                )
              } else {
                sq
              }
            }
          }

        val usedIds = aiMove.placements.values.map { it.id }.toSet()
        val remainingAiRack = state.playerTwoRack.filterNot { it.id in usedIds }
        val replenishedAiRack = remainingAiRack + drawTilesFromBag(7 - remainingAiRack.size)
        val aiPoints = aiMove.preview.totalTurnScore
        val newOpponentScore = state.scoreState.opponentScore + aiPoints

        val aiBurst =
          FloatingScoreBurst(
            id = UUID.randomUUID().toString(),
            headline = "${state.opponent.name}: +$aiPoints",
            subline = "Folded '${aiMove.primaryWord}'",
            boardCoord = placedCoords.firstOrNull(),
            colorType = BurstColorType.AI_MOVE,
          )

        _gameState.update { s ->
          s.copy(
            boardState =
              s.boardState.copy(
                squares = newSquares,
                lastPlayedCoords = placedCoords,
              ),
            playerTwoRack = replenishedAiRack,
            scoreState =
              s.scoreState.copy(
                opponentScore = newOpponentScore,
                turnTimeRemainingSec = s.scoreState.maxTurnTimeSec,
                tileBagRemaining = tileBag.size,
              ),
            turnStatus = TurnStatus.PLAYER_ONE_TURN,
            turnNumber = s.turnNumber + 1,
            floatingBursts = (s.floatingBursts + aiBurst).takeLast(3),
            lastMoveSummary =
              "${state.opponent.name} folded '${aiMove.primaryWord}' for +$aiPoints pts! Your turn.",
            consecutivePasses = 0,
          )
        }

        delay(1500L)
        _gameState.update { s ->
          s.copy(floatingBursts = s.floatingBursts.filterNot { it.id == aiBurst.id })
        }

        if (
          state.mode == GameMode.CAMPAIGN &&
            newOpponentScore >= state.opponent.targetScore
        ) {
          finishMatch()
        }
      } else {
        // AI passes and refreshes rack
        val newAiRack = drawTilesFromBag(7)
        _gameState.update { s ->
          s.copy(
            playerTwoRack = newAiRack,
            turnStatus = TurnStatus.PLAYER_ONE_TURN,
            turnNumber = s.turnNumber + 1,
            lastMoveSummary = "${state.opponent.name} exchanged tiles. Your turn!",
          )
        }
      }
    }
  }

  private fun finishMatch() {
    timerJob?.cancel()
    val state = _gameState.value
    val p1Won = state.scoreState.playerScore >= state.scoreState.opponentScore
    val rewardBonus = if (p1Won) state.opponent.rewardCoins else 15
    val nextStage =
      if (p1Won && state.mode == GameMode.CAMPAIGN) {
        (state.opponent.stage + 1).coerceAtMost(4)
      } else {
        state.campaignStageUnlocked
      }

    _gameState.update { s ->
      s.copy(
        turnStatus = TurnStatus.MATCH_OVER,
        paperCoins = s.paperCoins + rewardBonus,
        campaignStageUnlocked = maxOf(s.campaignStageUnlocked, nextStage),
        lastMoveSummary =
          if (p1Won) {
            "Victory! You defeated ${s.opponent.name} (${s.scoreState.playerScore} to ${s.scoreState.opponentScore})! +$rewardBonus Coins"
          } else {
            "Match Complete! ${s.opponent.name} edged ahead (${s.scoreState.opponentScore} to ${s.scoreState.playerScore}). +$rewardBonus Coins"
          },
      )
    }

    syncTurnToCloud(
      scoreDelta = 0,
      wordScore = 0,
      playedWord = state.bestWordPlayed,
      coinsDelta = rewardBonus,
      advanceCampaignTo = maxOf(state.campaignStageUnlocked, nextStage),
      matchStatusOverride = if (p1Won) "WON" else "LOST",
    )
  }

  fun unlockOrEquipSkin(skin: CosmeticTileSkin, haptics: HapticFeedback? = null) {
    val state = _gameState.value
    soundEngine?.onTileSnapToGrid(haptics, 1.15f)
    if (skin.id in state.unlockedSkinIds) {
      _gameState.update { it.copy(equippedSkin = skin) }
      viewModelScope.launch {
        repository.updateProfileStats(
          scoreDelta = 0,
          wordScore = 0,
          playedWord = "",
          coinsDelta = 0,
          equipSkin = skin.id,
        )
      }
    } else if (state.paperCoins >= skin.costCoins) {
      val updatedUnlocked = state.unlockedSkinIds + skin.id
      _gameState.update { s ->
        s.copy(
          paperCoins = s.paperCoins - skin.costCoins,
          unlockedSkinIds = updatedUnlocked,
          equippedSkin = skin,
        )
      }
      viewModelScope.launch {
        repository.updateProfileStats(
          scoreDelta = 0,
          wordScore = 0,
          playedWord = "",
          coinsDelta = -skin.costCoins,
          equipSkin = skin.id,
          unlockSkin = skin.id,
        )
      }
    }
  }

  private fun triggerInvalidShake(errorMessage: String) {
    _gameState.update { s ->
      s.copy(
        boardShakeIntensity = 10f,
        lastMoveSummary = errorMessage,
      )
    }
    viewModelScope.launch {
      delay(380L)
      _gameState.update { it.copy(boardShakeIntensity = 0f) }
    }
  }

  private fun syncTurnToCloud(
    scoreDelta: Int,
    wordScore: Int,
    playedWord: String,
    coinsDelta: Int,
    advanceCampaignTo: Int? = null,
    matchStatusOverride: String? = null,
  ) {
    val snapshotState = _gameState.value
    viewModelScope.launch {
      repository.updateProfileStats(
        scoreDelta = scoreDelta,
        wordScore = wordScore,
        playedWord = playedWord,
        coinsDelta = coinsDelta,
        advanceCampaignTo = advanceCampaignTo,
      )
      val matchId = snapshotState.activeMatchId
      if (matchId != null) {
        repository.updateMatchState(
          matchId = matchId,
          playerScore = snapshotState.scoreState.playerScore,
          opponentScore = snapshotState.scoreState.opponentScore,
          turnNumber = snapshotState.turnNumber,
          maxCombo = maxOf(1, snapshotState.scoreState.comboStreak),
          bestPlayedWord = snapshotState.bestWordPlayed,
          status = matchStatusOverride ?: "ACTIVE",
          boardSnapshot = serializeBoard(snapshotState.boardState),
          rackSnapshot =
            snapshotState.rackState.tiles.joinToString("") { it.char.toString() },
        )
      }
    }
  }

  private fun serializeBoard(board: LexiBoardState): String {
    val sb = StringBuilder(225)
    for (y in 0 until Coordinate.BOARD_SIZE) {
      for (x in 0 until Coordinate.BOARD_SIZE) {
        val ch = board.squares[y][x].occupyingTile?.char ?: '.'
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  private fun refillTileBag() {
    tileBag.clear()
    val distribution =
      listOf(
        'A' to 9,
        'B' to 2,
        'C' to 2,
        'D' to 4,
        'E' to 12,
        'F' to 2,
        'G' to 3,
        'H' to 2,
        'I' to 9,
        'J' to 1,
        'K' to 1,
        'L' to 4,
        'M' to 2,
        'N' to 6,
        'O' to 8,
        'P' to 2,
        'Q' to 1,
        'R' to 6,
        'S' to 4,
        'T' to 6,
        'U' to 4,
        'V' to 2,
        'W' to 2,
        'X' to 1,
        'Y' to 2,
        'Z' to 1,
      )
    var index = 0
    for ((ch, count) in distribution) {
      repeat(count) {
        val bonus =
          when {
            index % 11 == 0 -> TileBonusEffect.GOLD_FOIL
            index % 17 == 0 -> TileBonusEffect.CRANE_BLESSING
            index % 19 == 0 -> TileBonusEffect.SAKURA_SPARK
            else -> TileBonusEffect.NONE
          }
        tileBag.add(
          Tile(
            id = "tile_${ch}_${index++}_${Random.nextInt(1000, 9999)}",
            char = ch,
            baseScore = GameCatalog.letterBaseScore(ch),
            bonusEffect = bonus,
          )
        )
      }
    }
    tileBag.shuffle()
  }

  private fun drawTilesFromBag(count: Int, guaranteedVowelCount: Int = 0): List<Tile> {
    if (count <= 0 || tileBag.isEmpty()) return emptyList()
    val drawn = ArrayList<Tile>(count)
    val vowels = setOf('A', 'E', 'I', 'O', 'U')

    repeat(guaranteedVowelCount.coerceAtMost(count)) {
      val vIdx = tileBag.indexOfFirst { it.char in vowels }
      if (vIdx != -1) {
        drawn.add(tileBag.removeAt(vIdx))
      }
    }

    while (drawn.size < count && tileBag.isNotEmpty()) {
      drawn.add(tileBag.removeAt(tileBag.lastIndex))
    }
    return drawn
  }

  override fun onCleared() {
    super.onCleared()
    timerJob?.cancel()
    soundEngine?.release()
  }

  private companion object {
    const val TAG = "GameViewModel"
  }
}
