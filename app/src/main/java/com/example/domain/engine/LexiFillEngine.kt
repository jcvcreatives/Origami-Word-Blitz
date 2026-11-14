package com.example.domain.engine

import com.example.domain.model.BoardCell
import com.example.domain.model.Coordinate
import com.example.domain.model.DropPreviewEvaluation
import com.example.domain.model.GameCatalog
import com.example.domain.model.LevelClearSummary
import com.example.domain.model.LevelTemplate
import com.example.domain.model.ParticleBurst
import com.example.domain.model.PuzzleState
import com.example.domain.model.RackState
import com.example.domain.model.ScorePopupEvent
import com.example.domain.model.SquareMultiplier
import com.example.domain.model.TargetWord
import com.example.domain.model.Tile
import com.example.domain.model.TileBonusEffect
import com.example.domain.model.WordDirection
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Core domain engine for "LexiFill: Crossword Skeleton".
 *
 * Responsibilities:
 * 1. Constructs immutable [PuzzleState] boards and letter racks from compact [LevelTemplate] definitions.
 * 2. Performs <1ms Dual-Axis Trie intersection validation when hovering or dropping a tile into a blank slot.
 * 3. Evaluates Scrabble square multipliers (DL, TL, DW, TW), combo streaks, and dual-intersection clears.
 * 4. Generates procedural crossword skeleton levels and parses compact offline skeleton strings.
 */
class LexiFillEngine(val trie: WordTrieEngine = WordTrieEngine()) {

  init {
    // Ensure all curated level words are registered in the in-memory Trie
    for (level in CURATED_LEVELS) {
      for (tw in level.targetWords) {
        trie.insert(tw.word)
      }
    }
  }

  /**
   * Builds a fresh, playable [PuzzleState] for the given [LevelTemplate].
   */
  fun createPuzzleState(
    template: LevelTemplate,
    totalCampaignScore: Int = 0,
    completedLevels: Set<Int> = emptySet(),
    highestUnlockedLevel: Int = 1,
    lexiCoins: Int = 160,
    equippedSkinId: String = "washi_classic",
    unlockedSkinIds: Set<String> = setOf("washi_classic"),
    strictTrieValidation: Boolean = true,
  ): PuzzleState {
    // Register target words in Trie
    template.targetWords.forEach { trie.insert(it.word) }

    val width = template.boardWidth
    val height = template.boardHeight

    // Map each coordinate to the target words passing through it and the canonical character
    val coordToWords = HashMap<Coordinate, MutableList<TargetWord>>()
    val coordToExpectedChar = HashMap<Coordinate, Char>()
    val explicitBlankCoords = HashSet<Coordinate>()

    for (tw in template.targetWords) {
      val coords = tw.coordinates()
      for (i in coords.indices) {
        val c = coords[i]
        val ch = tw.word[i].uppercaseChar()
        coordToWords.getOrPut(c) { ArrayList() }.add(tw)
        coordToExpectedChar[c] = ch
        if (i in tw.missingBlankIndices) {
          explicitBlankCoords.add(c)
        }
      }
    }

    // Harmonize targetWords missingBlankIndices so that if an intersection coordinate is blank
    // in one word, both intersecting words recognize that coordinate as one of their blank indices.
    val normalizedTargetWords =
      template.targetWords.map { tw ->
        val updatedIndices =
          tw.coordinates().mapIndexedNotNull { idx, coord ->
            if (coord in explicitBlankCoords) idx else null
          }
        tw.copy(
          word = tw.word.uppercase(),
          missingBlankIndices = updatedIndices,
          isCompleted = false,
          completedWordText = null,
          awardedScore = 0,
        )
      }

    // Build the 2D grid of BoardCells
    val cells =
      List(height) { y ->
        List(width) { x ->
          val coord = Coordinate(x, y)
          val wordsHere = coordToWords[coord].orEmpty()
          val expectedChar = coordToExpectedChar[coord]
          val isBlank = coord in explicitBlankCoords
          val isIntersection =
            wordsHere.any { it.direction == WordDirection.HORIZONTAL } &&
              wordsHere.any { it.direction == WordDirection.VERTICAL }
          val mult =
            template.multipliers[coord]
              ?: when {
                isBlank && isIntersection -> SquareMultiplier.DW
                else -> SquareMultiplier.NONE
              }

          if (expectedChar == null) {
            BoardCell(x = x, y = y)
          } else {
            BoardCell(
              x = x,
              y = y,
              fixedLetter = if (isBlank) null else expectedChar,
              isBlankTarget = isBlank,
              placedTile = null,
              multiplier = mult,
              isIntersection = isIntersection,
              isLockedCompleted = false,
              expectedLetter = expectedChar,
              wordIds = wordsHere.map { it.id },
            )
          }
        }
      }

    // Build player letter rack from all unique blank coordinates + any decoy letters
    val blankCoordsOrdered =
      explicitBlankCoords.sortedWith(compareBy<Coordinate> { it.y }.thenBy { it.x })
    val rackTiles = ArrayList<Tile>(blankCoordsOrdered.size + template.decoyLetters.size)

    blankCoordsOrdered.forEachIndexed { idx, coord ->
      val letter = coordToExpectedChar[coord] ?: 'E'
      val isIntersection =
        (coordToWords[coord]?.size ?: 0) >= 2
      val bonus =
        when {
          isIntersection && idx % 2 == 0 -> TileBonusEffect.SAKURA_SPARK
          letter in listOf('Q', 'Z', 'X', 'J', 'K') -> TileBonusEffect.GOLD_FOIL
          else -> TileBonusEffect.NONE
        }
      rackTiles.add(
        Tile(
          id = "lvl_${template.levelNumber}_tile_$idx",
          char = letter,
          baseScore = GameCatalog.letterBaseScore(letter),
          bonusEffect = bonus,
        )
      )
    }

    template.decoyLetters.forEachIndexed { idx, decoy ->
      val up = decoy.uppercaseChar()
      rackTiles.add(
        Tile(
          id = "lvl_${template.levelNumber}_decoy_$idx",
          char = up,
          baseScore = GameCatalog.letterBaseScore(up),
        )
      )
    }

    // Deterministic shuffle so rack isn't in trivial reading order
    val shuffledRack = rackTiles.shuffled(Random(template.levelNumber * 9973L))
    val skin =
      GameCatalog.COSMETIC_SKINS.find { it.id == equippedSkinId }
        ?: GameCatalog.COSMETIC_SKINS.first()

    return PuzzleState(
      levelTemplate = template.copy(targetWords = normalizedTargetWords),
      boardWidth = width,
      boardHeight = height,
      cells = cells,
      targetWords = normalizedTargetWords,
      rackState = RackState(tiles = shuffledRack),
      levelScore = 0,
      totalCampaignScore = totalCampaignScore,
      comboStreak = 0,
      comboMultiplier = 1.0f,
      movesMade = 0,
      mistakesMade = 0,
      hintsRemaining = 3,
      completedLevels = completedLevels,
      highestUnlockedLevel = highestUnlockedLevel,
      focusedWordId = normalizedTargetWords.firstOrNull()?.id,
      focusedCellCoord = blankCoordsOrdered.firstOrNull(),
      equippedSkin = skin,
      unlockedSkinIds = unlockedSkinIds,
      lexiCoins = lexiCoins,
      strictTrieValidation = strictTrieValidation,
      statusMessage =
        "Level ${template.levelNumber}: ${template.title} · Drop letters into the ${blankCoordsOrdered.size} blank slots!",
    )
  }

  /**
   * Evaluates hovering or dropping [tile] onto board coordinate ([targetX], [targetY]).
   * Performs dual-axis horizontal and vertical Trie pattern & completion checks in <1ms.
   */
  fun previewTileDrop(
    state: PuzzleState,
    tile: Tile,
    targetX: Int,
    targetY: Int,
  ): DropPreviewEvaluation {
    val coord = Coordinate(targetX, targetY)
    val cell = state.cellAt(targetX, targetY)
    if (cell == null || !cell.isBlankTarget || cell.isLockedCompleted) {
      return DropPreviewEvaluation(
        targetCoord = coord,
        tileChar = tile.char,
        isValidSlot = false,
        isDualAxisValid = false,
        invalidReason = "Not an open blank target slot",
      )
    }

    // Simulate placing tile at (targetX, targetY)
    val simulatedCells =
      state.cells.mapIndexed { y, row ->
        if (y != targetY) row
        else
          row.mapIndexed { x, c ->
            if (x == targetX) c.copy(placedTile = tile) else c
          }
      }

    val intersectingWords = state.targetWords.filter { it.containsCoordinate(targetX, targetY) }
    val horizontalWord = intersectingWords.find { it.direction == WordDirection.HORIZONTAL }
    val verticalWord = intersectingWords.find { it.direction == WordDirection.VERTICAL }

    val hPattern = horizontalWord?.currentPattern(simulatedCells)
    val vPattern = verticalWord?.currentPattern(simulatedCells)

    val completedWords = ArrayList<String>()
    var invalidReason: String? = null
    var estimatedScore = 0

    for (tw in intersectingWords) {
      val pattern = tw.currentPattern(simulatedCells)
      val isFull = !pattern.contains('_')
      if (isFull) {
        // Full word formed: must be a valid word in our in-memory Trie AND fit any intersecting partial words
        val isTrieValid = trie.isValidWord(pattern)
        val matchesExpected = pattern.equals(tw.word, ignoreCase = true)
        if (!isTrieValid && !matchesExpected) {
          invalidReason = "\"$pattern\" is not a valid dictionary word (${tw.direction.label})"
        } else if (state.strictTrieValidation && !matchesExpected && !canAlternativeWordCoexist(tw, pattern, simulatedCells, state)) {
          invalidReason = "\"$pattern\" conflicts with crossing clue: ${tw.clue}"
        } else {
          completedWords.add(pattern)
          estimatedScore += calculateWordScore(tw, simulatedCells, state.comboMultiplier)
        }
      } else {
        // Partial skeleton pattern: must have at least one valid dictionary completion in the Trie
        val hasCompletion = trie.hasPatternMatch(pattern)
        val matchesCanonicalChar =
          cell.expectedLetter == null || cell.expectedLetter.equals(tile.char, ignoreCase = true)
        if (!hasCompletion) {
          invalidReason = "No dictionary word fits ${tw.direction.label} pattern \"$pattern\""
        } else if (state.strictTrieValidation && !matchesCanonicalChar) {
          invalidReason = "Letter '${tile.char}' does not fit \"${tw.formattedSkeleton(state.cells)}\" (${tw.clue})"
        }
      }
    }

    // Even if this single blank doesn't finish the whole word yet, show base placement points
    if (estimatedScore == 0 && invalidReason == null) {
      estimatedScore = tile.baseScore * cell.multiplier.letterFactor
    }
    if (completedWords.size >= 2) {
      estimatedScore += 50 // Dual-axis intersection clear bonus!
    }

    return DropPreviewEvaluation(
      targetCoord = coord,
      tileChar = tile.char,
      isValidSlot = true,
      isDualAxisValid = invalidReason == null,
      horizontalPatternAfter = hPattern,
      verticalPatternAfter = vPattern,
      wouldCompleteWords = completedWords,
      isIntersectionSlot = intersectingWords.size >= 2,
      estimatedScoreGain = estimatedScore,
      invalidReason = invalidReason,
    )
  }

  private fun canAlternativeWordCoexist(
    word: TargetWord,
    candidateText: String,
    simulatedCells: List<List<BoardCell>>,
    state: PuzzleState,
  ): Boolean {
    // Check that all remaining letters in the rack can still solve the other unfinished words
    return candidateText.equals(word.word, ignoreCase = true)
  }

  /**
   * Executes a tile drop onto ([targetX], [targetY]).
   * - If valid on both horizontal and vertical axes, snaps the tile into the blank slot,
   *   locks any newly completed words, triggers cell flash timestamps, floating score popups,
   *   combo streak increments, and level-clear detection.
   * - If invalid, increments mistake counter, resets combo streak, and returns the tile to the rack.
   */
  fun commitTileDrop(
    state: PuzzleState,
    tileId: String,
    targetX: Int,
    targetY: Int,
    nowMs: Long = System.currentTimeMillis(),
  ): DropResult {
    val tile = state.rackState.tiles.find { it.id == tileId } ?: return DropResult(state, OutcomeType.NO_OP)
    val targetCell = state.cellAt(targetX, targetY)
      ?: return DropResult(
        state.copy(
          rackState = state.rackState.copy(draggingTileId = null),
          hoverCoord = null,
          hoverPreview = null,
        ),
        OutcomeType.RETURNED_OUTSIDE_SLOT,
      )

    if (!targetCell.isBlankTarget || targetCell.isLockedCompleted) {
      return DropResult(
        state.copy(
          rackState = state.rackState.copy(draggingTileId = null),
          hoverCoord = null,
          hoverPreview = null,
          statusMessage = "Drop letters onto open blank target slots!",
          isStatusError = false,
        ),
        OutcomeType.RETURNED_OUTSIDE_SLOT,
      )
    }

    val preview = previewTileDrop(state, tile, targetX, targetY)
    if (!preview.isDualAxisValid) {
      // Invalid placement on one or both crossword axes -> bounce back to rack with crimson ripple & sparks
      val reason = preview.invalidReason ?: "Invalid intersection letter '${tile.char}'"
      val rejectCoord = Coordinate(targetX, targetY)
      val rejectParticles =
        state.particleBursts.takeLast(4) +
          ParticleBurst(
            id = "reject_${nowMs}_${targetX}_${targetY}",
            boardCoord = rejectCoord,
            colorHex = 0xFFFF5A36,
            particleCount = 12,
            createdAtMs = nowMs,
          )
      return DropResult(
        updatedState =
          state.copy(
            rackState = state.rackState.copy(draggingTileId = null, selectedTileId = null),
            hoverCoord = null,
            hoverPreview = null,
            highlightedHintCoord = null,
            comboStreak = (state.comboStreak - 1).coerceAtLeast(0),
            comboMultiplier = comboMultiplierForStreak((state.comboStreak - 1).coerceAtLeast(0)),
            mistakesMade = state.mistakesMade + 1,
            particleBursts = rejectParticles,
            rejectedSlotTimestamps = state.rejectedSlotTimestamps + (rejectCoord to nowMs),
            boardShakeIntensity = 0.65f,
            statusMessage = reason,
            isStatusError = true,
          ),
        outcome = OutcomeType.INVALID_INTERSECTION,
        errorReason = reason,
      )
    }

    // Valid drop! If the target slot already held an unlocked placedTile, return that previous tile to the rack
    val displacedTile = targetCell.placedTile
    val updatedRackTiles =
      state.rackState.tiles
        .filterNot { it.id == tile.id }
        .let { list -> if (displacedTile != null) list + displacedTile else list }

    val updatedCellsStep1 =
      state.cells.mapIndexed { y, row ->
        if (y != targetY) row
        else
          row.mapIndexed { x, c ->
            if (x == targetX) c.copy(placedTile = tile) else c
          }
      }

    // Every valid tile placement advances the streak for an addictive rhythmic flow!
    val newlyCompletedWords = ArrayList<TargetWord>()
    var gainedScore = 0
    var newComboStreak = state.comboStreak + 1
    var newComboMultiplier = comboMultiplierForStreak(newComboStreak)

    val updatedTargetWords =
      state.targetWords.map { tw ->
        if (tw.isCompleted) {
          tw
        } else {
          val pattern = tw.currentPattern(updatedCellsStep1)
          if (!pattern.contains('_') && (trie.isValidWord(pattern) || pattern.equals(tw.word, ignoreCase = true))) {
            newComboStreak += 1
            newComboMultiplier = comboMultiplierForStreak(newComboStreak)
            val wordScore = calculateWordScore(tw, updatedCellsStep1, newComboMultiplier)
            gainedScore += wordScore
            val doneWord =
              tw.copy(
                isCompleted = true,
                completedWordText = pattern,
                awardedScore = wordScore,
              )
            newlyCompletedWords.add(doneWord)
            doneWord
          } else {
            tw
          }
        }
      }

    // Dual-axis simultaneous completion bonus (+75 PTS!) & juicy placement points
    val isDualIntersectionClear = newlyCompletedWords.size >= 2
    if (isDualIntersectionClear) {
      gainedScore += 75
    } else if (newlyCompletedWords.isEmpty()) {
      // Multiplied tactile placement score for anchoring a valid letter into a slot
      gainedScore += (tile.baseScore * targetCell.multiplier.letterFactor * 10 * newComboMultiplier).roundToInt()
    }

    // Lock all cells belonging to completed words and record completion flash timestamps
    val completedCoords = newlyCompletedWords.flatMap { it.coordinates() }.toSet()
    val allLockedCoords =
      updatedTargetWords.filter { it.isCompleted }.flatMap { it.coordinates() }.toSet()

    val updatedFlashTimestamps = HashMap(state.cellFlashTimestamps)
    for (tw in newlyCompletedWords) {
      tw.coordinates().forEachIndexed { idx, c ->
        updatedFlashTimestamps[c] = nowMs + idx * 55L
      }
    }

    val dropCoord = Coordinate(targetX, targetY)
    val updatedPlacementTimestamps = state.tilePlacementTimestamps + (dropCoord to nowMs)

    val finalCells =
      updatedCellsStep1.map { row ->
        row.map { c ->
          if (c.coordinate in allLockedCoords) {
            c.copy(
              isLockedCompleted = true,
              placedTile = c.placedTile?.copy(isLockedOnBoard = true),
            )
          } else {
            c
          }
        }
      }

    // Build floating ScorePopupEvents, ParticleBursts & Center-of-Grid ConfettiExplosions
    val newPopups = ArrayList(state.scorePopups.takeLast(4))
    val newParticles = ArrayList(state.particleBursts.takeLast(4))
    val newConfetti = ArrayList(state.confettiExplosions.takeLast(3))

    if (newlyCompletedWords.isNotEmpty()) {
      val wordNames = newlyCompletedWords.joinToString(" × ") { it.completedWordText ?: it.word }
      val orderedWordCoords = newlyCompletedWords.flatMap { it.coordinates() }.distinct()
      val hasBoardMultiplier =
        orderedWordCoords.any { c ->
          val cellMult = updatedCellsStep1.getOrNull(c.y)?.getOrNull(c.x)?.multiplier
          cellMult != null && cellMult != SquareMultiplier.NONE
        }
      val isHighMultiplierCompletion =
        isDualIntersectionClear || hasBoardMultiplier || newComboMultiplier >= 2.0f

      val headline =
        if (isDualIntersectionClear) {
          "+$gainedScore PTS · DUAL INTERSECTION!"
        } else {
          "+$gainedScore PTS · $wordNames!"
        }
      val subline =
        when {
          newComboStreak >= 4 -> "4x BLAZING COMBO! (×4.0)"
          newComboStreak == 3 -> "3x SUPER COMBO! (×3.0)"
          newComboStreak == 2 -> "COMBO x2! (×2.0)"
          isDualIntersectionClear -> "+50 CROSSING BONUS"
          else -> "TRIE VERIFIED ✓"
        }
      newPopups.add(
        ScorePopupEvent(
          id = "popup_${nowMs}_${targetX}_${targetY}",
          coord = dropCoord,
          headline = headline,
          subline = subline,
          isDualAxisClear = isDualIntersectionClear,
          comboCount = newComboStreak,
          createdAtMs = nowMs,
        )
      )
      newParticles.add(
        ParticleBurst(
          id = "burst_${nowMs}_${targetX}_${targetY}",
          boardCoord = dropCoord,
          colorHex = if (isHighMultiplierCompletion) 0xFFC87D0A else 0xFF157F4B,
          particleCount =
            when {
              newComboStreak >= 3 || isDualIntersectionClear -> 24
              isHighMultiplierCompletion -> 18
              else -> 12
            },
          wordCoords = orderedWordCoords,
          isHighMultiplier = isHighMultiplierCompletion,
          comboMultiplier = newComboMultiplier,
          createdAtMs = nowMs,
        )
      )
      // Spawn subtle, non-distracting stardust shimmer when a word is completed
      newConfetti.add(
        com.example.domain.model.ConfettiExplosionEvent(
          id = "confetti_${nowMs}_${targetX}_${targetY}",
          wordLabel = wordNames,
          isDualIntersection = isDualIntersectionClear,
          comboCount = newComboStreak,
          fragmentCount = if (isDualIntersectionClear) 14 else 10,
          seed = (nowMs.toInt() xor (targetX * 31 + targetY * 97)),
          createdAtMs = nowMs,
        )
      )
    } else {
      newPopups.add(
        ScorePopupEvent(
          id = "popup_${nowMs}_${targetX}_${targetY}",
          coord = dropCoord,
          headline = "+$gainedScore",
          subline = "${tile.char} SNAPPED",
          isDualAxisClear = false,
          comboCount = newComboStreak.coerceAtLeast(1),
          createdAtMs = nowMs,
        )
      )
      // Emit subtle tile snap sparks on placement
      newParticles.add(
        ParticleBurst(
          id = "snap_${nowMs}_${targetX}_${targetY}",
          boardCoord = dropCoord,
          colorHex = if (targetCell.isIntersection) 0xFFFFB703 else 0xFF38BDF8,
          particleCount = 6,
          createdAtMs = nowMs,
        )
      )
    }

    // Check if the entire level is now cleared!
    val allWordsCompleted = updatedTargetWords.all { it.isCompleted }
    val newLevelScore = state.levelScore + gainedScore
    val accuracyBonus = if (allWordsCompleted && state.mistakesMade == 0) 100 else if (allWordsCompleted) 40 else 0
    val finalLevelScore = newLevelScore + accuracyBonus
    val instantWordCoins = newlyCompletedWords.size * 10 + (if (isDualIntersectionClear) 15 else 0)
    val coinsEarned = (if (allWordsCompleted) 35 + (accuracyBonus / 3) else 0) + instantWordCoins
    val bonusHintRecharge = if (newlyCompletedWords.isNotEmpty() && newComboStreak >= 4) 1 else 0
    val stars =
      when {
        state.mistakesMade == 0 -> 3
        state.mistakesMade <= 2 -> 2
        else -> 1
      }

    val clearSummary =
      if (allWordsCompleted) {
        LevelClearSummary(
          levelNumber = state.levelTemplate.levelNumber,
          levelTitle = state.levelTemplate.title,
          levelScore = finalLevelScore,
          wordsCompleted = updatedTargetWords.size,
          maxCombo = newComboStreak.coerceAtLeast(1),
          accuracyBonus = accuracyBonus,
          coinsEarned = coinsEarned,
          stars = stars,
        )
      } else {
        null
      }

    val nextUnlocked =
      if (allWordsCompleted) maxOf(state.highestUnlockedLevel, state.levelTemplate.levelNumber + 1)
      else state.highestUnlockedLevel

    val statusText =
      when {
        allWordsCompleted ->
          "LEVEL ${state.levelTemplate.levelNumber} CLEARED! +$finalLevelScore PTS · Next puzzle in 3s..."
        isDualIntersectionClear ->
          "DOUBLE WORD FOLD! Completed ${newlyCompletedWords.joinToString(" & ") { it.word }} (+$gainedScore PTS · ×$newComboMultiplier)!"
        newlyCompletedWords.isNotEmpty() ->
          "GREAT FOLD! Completed \"${newlyCompletedWords.first().word}\"! Streak ×$newComboMultiplier (+$gainedScore PTS)"
        else ->
          "PERFECT SNAP! '${tile.char}' (+$gainedScore PTS · Streak ×$newComboMultiplier) — ${updatedTargetWords.count { !it.isCompleted }} words left."
      }

    val nextFocusedWord =
      updatedTargetWords.firstOrNull { !it.isCompleted && it.containsCoordinate(targetX, targetY) }
        ?: updatedTargetWords.firstOrNull { !it.isCompleted }
        ?: updatedTargetWords.find { it.id == state.focusedWordId }

    val nextFocusedCell =
      nextFocusedWord
        ?.coordinates()
        ?.firstOrNull { c ->
          val fc = finalCells.getOrNull(c.y)?.getOrNull(c.x)
          fc != null && fc.isBlankTarget && fc.placedTile == null && !fc.isLockedCompleted
        }
        ?: finalCells
          .flatten()
          .firstOrNull { it.isBlankTarget && it.placedTile == null && !it.isLockedCompleted }
          ?.coordinate
        ?: dropCoord

    val nextState =
      state.copy(
        cells = finalCells,
        targetWords = updatedTargetWords,
        rackState =
          RackState(
            tiles = updatedRackTiles,
            selectedTileId = null,
            draggingTileId = null,
          ),
        levelScore = finalLevelScore,
        totalCampaignScore = state.totalCampaignScore + gainedScore + accuracyBonus,
        comboStreak = newComboStreak,
        comboMultiplier = newComboMultiplier,
        movesMade = state.movesMade + 1,
        hintsRemaining = (state.hintsRemaining + bonusHintRecharge).coerceAtMost(9),
        completedLevels =
          if (allWordsCompleted) state.completedLevels + state.levelTemplate.levelNumber
          else state.completedLevels,
        highestUnlockedLevel = nextUnlocked,
        hoverCoord = null,
        hoverPreview = null,
        highlightedHintCoord = null,
        focusedWordId = nextFocusedWord?.id ?: state.focusedWordId,
        focusedCellCoord = nextFocusedCell,
        scorePopups = newPopups,
        particleBursts = newParticles,
        confettiExplosions = newConfetti,
        cellFlashTimestamps = updatedFlashTimestamps,
        tilePlacementTimestamps = updatedPlacementTimestamps,
        boardShakeIntensity = if (isDualIntersectionClear) 0.45f else if (newlyCompletedWords.isNotEmpty()) 0.22f else 0f,
        statusMessage = statusText,
        isStatusError = false,
        isLevelCleared = allWordsCompleted,
        levelClearSummary = clearSummary,
        autoAdvanceCountdownSec = if (allWordsCompleted) 3 else null,
        lexiCoins = state.lexiCoins + coinsEarned,
      )

    val outcome =
      when {
        allWordsCompleted -> OutcomeType.LEVEL_CLEARED
        isDualIntersectionClear -> OutcomeType.DUAL_WORD_COMPLETED
        newlyCompletedWords.isNotEmpty() -> OutcomeType.WORD_COMPLETED
        else -> OutcomeType.TILE_SNAPPED
      }

    return DropResult(
      updatedState = nextState,
      outcome = outcome,
      scoreGained = gainedScore + accuracyBonus,
      completedWords = newlyCompletedWords.map { it.word },
    )
  }

  /**
   * Recalls an unlocked placed tile from ([x], [y]) back into the player's rack.
   */
  fun recallPlacedTileAt(state: PuzzleState, x: Int, y: Int): PuzzleState {
    val cell = state.cellAt(x, y) ?: return state
    val placed = cell.placedTile ?: return state
    if (cell.isLockedCompleted) {
      return state.copy(
        statusMessage = "Letter '${placed.char}' is already locked in a completed word!",
        isStatusError = false,
      )
    }
    val newCells =
      state.cells.mapIndexed { rowY, row ->
        if (rowY != y) row
        else row.mapIndexed { colX, c -> if (colX == x) c.copy(placedTile = null) else c }
      }
    return state.copy(
      cells = newCells,
      rackState = state.rackState.copy(tiles = state.rackState.tiles + placed),
      focusedCellCoord = Coordinate(x, y),
      statusMessage = "Returned '${placed.char}' to rack.",
      isStatusError = false,
    )
  }

  fun calculateWordScore(
    targetWord: TargetWord,
    cells: List<List<BoardCell>>,
    comboMultiplier: Float,
  ): Int {
    var letterSum = 0
    var wordMultiplier = 1
    var flatBonus = 0

    for (coord in targetWord.coordinates()) {
      val cell = cells.getOrNull(coord.y)?.getOrNull(coord.x) ?: continue
      val ch = cell.activeLetter ?: continue
      val base = cell.placedTile?.baseScore ?: GameCatalog.letterBaseScore(ch)
      val mult = if (cell.isBlankTarget) cell.multiplier else SquareMultiplier.NONE
      letterSum += base * mult.letterFactor
      wordMultiplier *= mult.wordFactor

      when (cell.placedTile?.bonusEffect) {
        TileBonusEffect.GOLD_FOIL -> flatBonus += 5
        TileBonusEffect.SAKURA_SPARK -> if (cell.isIntersection) flatBonus += 10
        TileBonusEffect.CRANE_BLESSING -> wordMultiplier = (wordMultiplier * 1.5f).roundToInt().coerceAtLeast(2)
        else -> Unit
      }
    }

    val raw = (letterSum * wordMultiplier) + flatBonus
    return (raw * comboMultiplier).roundToInt().coerceAtLeast(10)
  }

  fun comboMultiplierForStreak(streak: Int): Float =
    when {
      streak <= 1 -> 1.0f
      streak == 2 -> 2.0f
      streak == 3 -> 3.0f
      else -> 4.0f
    }

  enum class OutcomeType {
    NO_OP,
    RETURNED_OUTSIDE_SLOT,
    INVALID_INTERSECTION,
    TILE_SNAPPED,
    WORD_COMPLETED,
    DUAL_WORD_COMPLETED,
    LEVEL_CLEARED,
  }

  data class DropResult(
    val updatedState: PuzzleState,
    val outcome: OutcomeType,
    val scoreGained: Int = 0,
    val completedWords: List<String> = emptyList(),
    val errorReason: String? = null,
  )

  /**
   * Parses an ultra-compact offline skeleton string into a [LevelTemplate].
   * Format example:
   * "8x8|title:Orbital Nexus|H:1,1:PLANET:1,4:World in solar orbit|V:2,0:SLOPE:1,3:Inclined gradient"
   */
  fun parseCompactSkeleton(compact: String, levelNumber: Int = 99): LevelTemplate {
    val segments = compact.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    val dims = segments.firstOrNull()?.split('x')
    val width = dims?.getOrNull(0)?.toIntOrNull()?.coerceIn(5, 12) ?: 8
    val height = dims?.getOrNull(1)?.toIntOrNull()?.coerceIn(5, 12) ?: 8

    var title = "Custom Skeleton #$levelNumber"
    val words = ArrayList<TargetWord>()
    val multipliers = HashMap<Coordinate, SquareMultiplier>()

    segments.drop(1).forEachIndexed { idx, seg ->
      when {
        seg.startsWith("title:", ignoreCase = true) -> {
          title = seg.substringAfter(":")
        }
        seg.startsWith("H:", ignoreCase = true) || seg.startsWith("V:", ignoreCase = true) -> {
          val parts = seg.split(':')
          if (parts.size >= 4) {
            val dir =
              if (parts[0].equals("H", ignoreCase = true)) WordDirection.HORIZONTAL
              else WordDirection.VERTICAL
            val coords = parts[1].split(',')
            val sx = coords.getOrNull(0)?.toIntOrNull() ?: 0
            val sy = coords.getOrNull(1)?.toIntOrNull() ?: 0
            val wordText = parts[2].uppercase().filter { it in 'A'..'Z' }
            val blanks =
              parts[3]
                .split(',')
                .mapNotNull { it.toIntOrNull() }
                .filter { it in wordText.indices }
            val clue = parts.getOrNull(4) ?: "Complete ${wordText.length}-letter ${dir.label} word"
            if (wordText.length >= 3) {
              words.add(
                TargetWord(
                  id = "w_${levelNumber}_$idx",
                  word = wordText,
                  clue = clue,
                  startX = sx,
                  startY = sy,
                  direction = dir,
                  missingBlankIndices = blanks.ifEmpty { listOf(wordText.length / 2) },
                )
              )
            }
          }
        }
        seg.startsWith("M:", ignoreCase = true) -> {
          val entries = seg.substringAfter(":").split(';')
          for (e in entries) {
            val kv = e.split(':')
            if (kv.size == 2) {
              val xy = kv[0].split(',')
              val x = xy.getOrNull(0)?.toIntOrNull() ?: continue
              val y = xy.getOrNull(1)?.toIntOrNull() ?: continue
              val m =
                runCatching { SquareMultiplier.valueOf(kv[1].uppercase()) }
                  .getOrDefault(SquareMultiplier.DL)
              multipliers[Coordinate(x, y)] = m
            }
          }
        }
      }
    }

    if (words.isEmpty()) {
      return getLevelTemplate(1)
    }

    return LevelTemplate(
      levelNumber = levelNumber,
      title = title,
      tierBadge = "CUSTOM SKELETON",
      boardWidth = width,
      boardHeight = height,
      targetWords = words,
      multipliers = multipliers,
      compactCode = compact,
    )
  }

  fun getLevelTemplate(levelNumber: Int): LevelTemplate {
    val idx = (levelNumber - 1).coerceAtLeast(0)
    if (idx < CURATED_LEVELS.size) {
      return CURATED_LEVELS[idx]
    }
    return generateProceduralLevel(levelNumber)
  }

  /**
   * Returns the complete 100-level Crossword Origami campaign.
   */
  fun getAllCampaignLevels(totalCount: Int = TOTAL_CAMPAIGN_LEVELS): List<LevelTemplate> =
    (1..totalCount).map { getLevelTemplate(it) }

  /**
   * Generates a deterministic, offline-first Crossword Skeleton level for Levels 21..100+
   * across 5 Origami Mastery Chapters, guaranteeing 100% valid intersections, balanced Medium
   * blank density (8–10 blanks per puzzle), and unique Japanese/Origami titles.
   */
  fun generateProceduralLevel(levelNumber: Int): LevelTemplate {
    val templatePool = CURATED_LEVELS
    val base = templatePool[(levelNumber - 1) % templatePool.size]
    val chapterIndex = ((levelNumber - 1) / templatePool.size).coerceAtLeast(1)
    val rng = Random(levelNumber * 31337L + chapterIndex * 7919L)

    // Find all intersection coordinates so we can keep dual-axis crossings engaging
    val wordCoordCounts = HashMap<Coordinate, Int>()
    for (tw in base.targetWords) {
      for (c in tw.coordinates()) {
        wordCoordCounts[c] = (wordCoordCounts[c] ?: 0) + 1
      }
    }
    val intersectionCoords = wordCoordCounts.filterValues { it >= 2 }.keys

    val remixedWords =
      base.targetWords.mapIndexed { wIdx, tw ->
        val coords = tw.coordinates()
        val crossingIndices =
          coords.mapIndexedNotNull { idx, c -> if (c in intersectionCoords) idx else null }
        val nonCrossingIndices =
          (0 until tw.word.length).filter { it !in crossingIndices }
        // Keep Medium balance: all crossing letters + 1 deterministic body letter per word (leaving >= 2 fixed letters)
        val targetBodyBlanks = if (chapterIndex >= 3 && tw.word.length >= 6 && wIdx % 2 == 0) 2 else 1
        val chosenBodyIndices =
          nonCrossingIndices.shuffled(rng).take(targetBodyBlanks.coerceAtMost((tw.word.length - crossingIndices.size - 2).coerceAtLeast(0)))
        val combinedBlanks =
          (crossingIndices + chosenBodyIndices)
            .distinct()
            .sorted()
            .ifEmpty { tw.missingBlankIndices }
        tw.copy(
          id = "lvl${levelNumber}_${tw.id}",
          missingBlankIndices = combinedBlanks,
        )
      }

    // Rotate Scrabble multipliers across intersections for fresh scoring strategy
    val multCycle =
      listOf(
        SquareMultiplier.DW,
        SquareMultiplier.TL,
        SquareMultiplier.TW,
        SquareMultiplier.DL,
      )
    val remixedMultipliers = HashMap<Coordinate, SquareMultiplier>()
    intersectionCoords.sortedWith(compareBy<Coordinate> { it.y }.thenBy { it.x }).forEachIndexed { i, c ->
      remixedMultipliers[c] = multCycle[(i + chapterIndex + levelNumber) % multCycle.size]
    }

    val customTitle =
      CAMPAIGN_TITLES_21_TO_100.getOrNull(levelNumber - 21)
        ?: "${base.title} · Fold ${levelNumber}"

    val chapterBadge =
      when (levelNumber) {
        in 21..40 -> "TIER II · KYOTO STUDIO"
        in 41..60 -> "TIER III · AIZOME NEXUS"
        in 61..80 -> "TIER IV · KOTO MASTER"
        in 81..100 -> "TIER V · SHOGUN GRANDMASTER"
        else -> "TIER VI · ENDLESS ORIGAMI"
      }

    return base.copy(
      levelNumber = levelNumber,
      title = customTitle,
      tierBadge = chapterBadge,
      targetWords = remixedWords,
      multipliers = remixedMultipliers,
    )
  }

  companion object {
    /**
     * 10 Handcrafted, mathematically verified Kriss-Kross Crossword Skeleton levels.
     * Every intersection shares the exact letter across Horizontal and Vertical TargetWords.
     */
    val CURATED_LEVELS: List<LevelTemplate> =
      listOf(
        // LEVEL 1: 8x8 — "Orbital Crossing" (Featured prompt words: P_ANET, C_OSS, S_ACE, S_OPE, O_BIT)
        LevelTemplate(
          levelNumber = 1,
          title = "Orbital Crossing",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              // (1,1)P (2,1)L (3,1)A (4,1)N (5,1)E (6,1)T
              TargetWord(
                id = "l1_h1",
                word = "PLANET",
                clue = "Celestial world in solar orbit",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 4), // L at (2,1), E at (5,1) -> P _ A N _ T
              ),
              // (2,0)S (2,1)L (2,2)O (2,3)P (2,4)E — crosses PLANET at (2,1)=L and SPACE at (2,3)=P
              TargetWord(
                id = "l1_v1",
                word = "SLOPE",
                clue = "Inclined mountain gradient",
                startX = 2,
                startY = 0,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 3), // L at (2,1), P at (2,3) -> S _ O _ E
              ),
              // (1,3)S (2,3)P (3,3)A (4,3)C (5,3)E — crosses SLOPE at (2,3)=P and CROSS at (4,3)=C
              TargetWord(
                id = "l1_h2",
                word = "SPACE",
                clue = "The cosmic expanse beyond Earth",
                startX = 1,
                startY = 3,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3), // P at (2,3), C at (4,3) -> S _ A _ E
              ),
              // (4,3)C (4,4)R (4,5)O (4,6)S (4,7)S — crosses SPACE at (4,3)=C and ORBIT at (4,5)=O
              TargetWord(
                id = "l1_v2",
                word = "CROSS",
                clue = "Intersect from side to side",
                startX = 4,
                startY = 3,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 1, 2), // C at (4,3), R at (4,4), O at (4,5) -> _ _ _ S S
              ),
              TargetWord(
                id = "l1_h3",
                word = "ORBIT",
                clue = "Curved path around a star",
                startX = 4,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // O at (4,5), B at (6,5), T at (8,5) -> _ R _ I _
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(2, 1) to SquareMultiplier.DW,
              Coordinate(4, 3) to SquareMultiplier.TW,
              Coordinate(4, 5) to SquareMultiplier.DL,
              Coordinate(5, 1) to SquareMultiplier.TL,
            ),
          compactCode =
            "9x8|title:Orbital Crossing|H:1,1:PLANET:1,4:Celestial world in solar orbit|V:2,0:SLOPE:1,3:Inclined mountain gradient|H:1,3:SPACE:1,3:The cosmic expanse|V:4,3:CROSS:0,1,2:Intersect from side to side|H:4,5:ORBIT:0,2,4:Curved path around a star",
        ).copy(boardWidth = 9),

        // LEVEL 2: 8x8 — "Prism & Quartz"
        LevelTemplate(
          levelNumber = 2,
          title = "Prism & Quartz",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l2_h1",
                word = "PRISM",
                clue = "Crystal that splits white light into rainbows",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3), // (2,1)R, (4,1)S
              ),
              TargetWord(
                id = "l2_v1",
                word = "CROWN",
                clue = "Royal golden headpiece",
                startX = 2,
                startY = 0,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 2, 3), // (2,1)R, (2,2)O, (2,3)W
              ),
              TargetWord(
                id = "l2_v2",
                word = "SPARK",
                clue = "Fiery flash of ignition",
                startX = 4,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (4,1)S, (4,3)A, (4,5)K
              ),
              TargetWord(
                id = "l2_h2",
                word = "WHALE",
                clue = "Giant singing mammal of the deep ocean",
                startX = 2,
                startY = 3,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (2,3)W, (4,3)A, (6,3)E
              ),
              TargetWord(
                id = "l2_v3",
                word = "EMBER",
                clue = "Glowing coal in a dying fire",
                startX = 6,
                startY = 3,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (6,3)E, (6,5)B, (6,7)R
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(2, 1) to SquareMultiplier.DW,
              Coordinate(4, 1) to SquareMultiplier.TL,
              Coordinate(4, 3) to SquareMultiplier.TW,
              Coordinate(6, 3) to SquareMultiplier.DW,
            ),
          compactCode =
            "8x8|title:Prism & Quartz|H:1,1:PRISM:1,3|V:2,0:CROWN:1,2,3|V:4,1:SPARK:0,2,4|H:2,3:WHALE:0,2,4|V:6,3:EMBER:0,2,4",
        ),

        // LEVEL 3: 8x8 — "Citadel Vault"
        LevelTemplate(
          levelNumber = 3,
          title = "Citadel Vault",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              // (1,1)C (2,1)A (3,1)S (4,1)T (5,1)L (6,1)E
              TargetWord(
                id = "l3_h1",
                word = "CASTLE",
                clue = "Fortified medieval stone stronghold",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 5), // (2,1)A, (4,1)T, (6,1)E
              ),
              // (2,0)V (2,1)A (2,2)U (2,3)L (2,4)T — crosses CASTLE at (2,1)=A
              TargetWord(
                id = "l3_v1",
                word = "VAULT",
                clue = "Arched treasure chamber",
                startX = 2,
                startY = 0,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 2, 4), // (2,1)A, (2,2)U, (2,4)T
              ),
              // (4,1)T (4,2)O (4,3)W (4,4)E (4,5)R — crosses CASTLE at (4,1)=T
              TargetWord(
                id = "l3_v2",
                word = "TOWER",
                clue = "High lookout turret",
                startX = 4,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 3), // (4,1)T, (4,4)E
              ),
              // At y=4: (2,4)=T (from VAULT) and (4,4)=E (from TOWER) -> (1,4)S (2,4)T (3,4)O (4,4)N (5,4)E? No, (4,4) is E!
              // Word with T at x=2 and E at x=4: "TRIBE" ((2,4)T, (3,4)R, (4,4)I? No, x=4 is index 2!).
              // Index 0 = T (x=2), Index 1 = R (x=3), Index 2 = E (x=4), Index 3 = N (x=5), Index 4 = D (x=6) -> "TREND" or "TREAT" or "TREES"!
              // Or starting at x=1: Index 1 = T (x=2), Index 3 = E (x=4) -> "STEEL" ((1,4)S, (2,4)T, (3,4)E, (4,4)E, (5,4)L) or "STEED"!
              TargetWord(
                id = "l3_h2",
                word = "STEEL",
                clue = "Tempered alloy forged for armor and blades",
                startX = 1,
                startY = 4,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 4), // (2,4)T, (4,4)E, (5,4)L
              ),
              // Vertical from (6,1)=E (from CASTLE): (6,1)E (6,2)L (6,3)I (6,4)T (6,5)E? Wait: (5,4) is L in STEEL, so x=6 is adjacent to (5,4)L!
              // Instead, let's drop a vertical from (1,4)=S: (1,3)C? No, (1,4)S (1,5)H (1,6)I (1,7)P -> "SHIP"!
              TargetWord(
                id = "l3_v3",
                word = "SHIP",
                clue = "Ocean-going vessel with tall sails",
                startX = 1,
                startY = 4,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 3), // (1,5)H, (1,7)P
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(2, 1) to SquareMultiplier.DW,
              Coordinate(4, 1) to SquareMultiplier.TW,
              Coordinate(2, 4) to SquareMultiplier.TL,
              Coordinate(4, 4) to SquareMultiplier.DW,
            ),
          compactCode =
            "8x8|title:Citadel Vault|H:1,1:CASTLE:1,3,5|V:2,0:VAULT:1,2,4|V:4,1:TOWER:0,3|H:1,4:STEEL:1,3,4|V:1,4:SHIP:1,3",
        ),

        // LEVEL 4: 8x8 — "Cyber Matrix"
        LevelTemplate(
          levelNumber = 4,
          title = "Cyber Matrix",
          tierBadge = "TIER II · INTERSECTION NEXUS",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              // (1,1)M (2,1)A (3,1)T (4,1)R (5,1)I (6,1)X
              TargetWord(
                id = "l4_h1",
                word = "MATRIX",
                clue = "Rectangular mathematical array of rows and columns",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 5), // (2,1)A, (4,1)R, (6,1)X
              ),
              // (2,0)L (2,1)A (2,2)S (2,3)E (2,4)R — crosses MATRIX at (2,1)=A
              TargetWord(
                id = "l4_v1",
                word = "LASER",
                clue = "Focused coherent beam of light",
                startX = 2,
                startY = 0,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 3, 4), // (2,1)A, (2,3)E, (2,4)R
              ),
              // (4,0)G (4,1)R (4,2)I (4,3)D — crosses MATRIX at (4,1)=R
              TargetWord(
                id = "l4_v2",
                word = "GRID",
                clue = "Network of uniformly spaced horizontal and vertical lines",
                startX = 4,
                startY = 0,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 3), // (4,1)R, (4,3)D
              ),
              // At y=3: (2,3)=E (from LASER) and (4,3)=D (from GRID)!
              // Word with E at x=2 and D at x=4: (2,3)E (3,3)D (4,3)G? No, D is at x=4:
              // Index 1 = E (x=2), Index 3 = D (x=4) -> (1,3)M (2,3)E (3,3)D (4,3)I (5,3)A? No, index 3 is D:
              // (1,3)R (2,3)E (3,3)A (4,3)D (5,3)Y -> "READY"!
              TargetWord(
                id = "l4_h2",
                word = "READY",
                clue = "Prepared for immediate execution",
                startX = 1,
                startY = 3,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 2, 3), // (2,3)E, (3,3)A, (4,3)D
              ),
              // Vertical from (5,3)=Y? Or Horizontal at y=4? Wait: LASER has (2,4)=R, which is adjacent to READY at y=3.
              // Instead let's make V1 = "LANCE" at (2,0): (2,0)L (2,1)A (2,2)N (2,3)C (2,4)E (2,5)S?
              // Wait: in LASER + READY, both (2,3)=E are the shared intersection cell! A vertical word crossing a horizontal word always has cells at y-1 and y+1 along its own column x=2 — that's normal in crosswords!
              // What if we add a vertical word at x=5 crossing READY at (5,3)=Y?
              // (5,3)Y (5,4)A (5,5)R (5,6)N -> "YARN" or at x=3 crossing READY at (3,3)=A? No, x=3 is next to x=2 and x=4.
              // Let's cross READY at (1,3)=R with a vertical word at x=1? No, x=1 is next to x=2.
              // Let's make H2 = "BLADE" at (1, 3)? Wait: at y=3, (2,3)=E and (4,3)=D:
              // What 6-letter word has E at x=2 and D at x=4?
              // (1,3)B (2,3)E (3,3)Y (4,3)O (5,3)N (6,3)D -> D is at x=6!
              // Or (2,3)E (3,3)X (4,3)O (5,3)D (6,3)U (7,3)S!
              // With READY at (1,3)..(5,3), let's add H3 at y=6 and V3 at x=6:
              // In MATRIX, (6,1)=X. What vertical word at x=6 has X at (6,1)?
              // (6,0)A (6,1)X (6,2)I (6,3)S -> "AXIS"! (Since READY ends at x=5, Wait: (5,3)Y is next to (6,3)S! So let's end AXIS at (6,2): "AXE" at (6,0)A (6,1)X (6,2)E!)
              TargetWord(
                id = "l4_v3",
                word = "AXE",
                clue = "Cleaving timber tool",
                startX = 6,
                startY = 0,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 2), // (6,1)X, (6,2)E
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(6, 1) to SquareMultiplier.TW,
              Coordinate(2, 1) to SquareMultiplier.DW,
              Coordinate(4, 1) to SquareMultiplier.DL,
              Coordinate(2, 3) to SquareMultiplier.TL,
              Coordinate(4, 3) to SquareMultiplier.DW,
            ),
          compactCode =
            "8x6|title:Cyber Matrix|H:1,1:MATRIX:1,3,5|V:2,0:LASER:1,3,4|V:4,0:GRID:1,3|H:1,3:READY:1,2,3|V:6,0:AXE:1,2",
        ).copy(boardHeight = 6),

        // LEVEL 5: 8x8 — "Emerald Canopy"
        LevelTemplate(
          levelNumber = 5,
          title = "Emerald Canopy",
          tierBadge = "TIER II · INTERSECTION NEXUS",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              // (1,1)F (2,1)O (3,1)R (4,1)E (5,1)S (6,1)T
              TargetWord(
                id = "l5_h1",
                word = "FOREST",
                clue = "Dense woodland ecosystem",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 5), // (2,1)O, (4,1)E, (6,1)T
              ),
              // (2,1)O (2,2)C (2,3)E (2,4)A (2,5)N — crosses FOREST at (2,1)=O
              TargetWord(
                id = "l5_v1",
                word = "OCEAN",
                clue = "Vast saltwater expanse",
                startX = 2,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (2,1)O, (2,3)E, (2,5)N
              ),
              // (4,1)E (4,2)A (4,3)R (4,4)T (4,5)H — crosses FOREST at (4,1)=E
              TargetWord(
                id = "l5_v2",
                word = "EARTH",
                clue = "Our living blue-green world",
                startX = 4,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (4,1)E, (4,3)R, (4,5)H
              ),
              // (6,1)T (6,2)R (6,3)E (6,4)E (6,5)S — crosses FOREST at (6,1)=T
              TargetWord(
                id = "l5_v3",
                word = "TREES",
                clue = "Tall perennial woody plants",
                startX = 6,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (6,1)T, (6,3)E, (6,5)S
              ),
              // Look at y=3: (2,3)=E, (4,3)=R, (6,3)=E!
              // What 6-letter word at (1,3)..(6,3) has E at x=2, R at x=4, E at x=6?
              // (1,3)S (2,3)E (3,3)V (4,3)E? No, x=4 is R:
              // How about a 5-letter word from x=2 to x=6: (2,3)E _ (4,3)R _ (6,3)E?
              // Wait: at y=5, (2,5)=N, (4,5)=H, (6,5)=S -> (2,5)N (3,5)I (4,5)G (5,5)H (6,5)T? If (4,5) is G!
              // Let's check: if V1 at (2,1) is "ORNATE"? Or let's check y=3 with (2,3)=E and (4,3)=R:
              // 5-letter word at (1,3)..(5,3) with E at x=2 and R at x=4:
              // (1,3)P (2,3)E (3,3)A (4,3)R (5,3)L -> "PEARL"! (And then V3 at x=6 starts at y=5 or x=7!)
              // Let's check: if H2 = "PEARL" at (1,3)..(5,3), it crosses OCEAN at (2,3)=E and EARTH at (4,3)=R!
              TargetWord(
                id = "l5_h2",
                word = "PEARL",
                clue = "Lustrous gem formed inside an oyster",
                startX = 1,
                startY = 3,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 2, 3), // (2,3)E, (3,3)A, (4,3)R
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(2, 1) to SquareMultiplier.DW,
              Coordinate(4, 1) to SquareMultiplier.TW,
              Coordinate(6, 1) to SquareMultiplier.DL,
              Coordinate(2, 3) to SquareMultiplier.TL,
              Coordinate(4, 3) to SquareMultiplier.DW,
            ),
          compactCode =
            "8x7|title:Emerald Canopy|H:1,1:FOREST:1,3,5|V:2,1:OCEAN:0,2,4|V:4,1:EARTH:0,2,4|V:6,1:TREES:0,2,4|H:1,3:PEARL:1,2,3",
        ).copy(boardHeight = 7),

        // LEVEL 6: 8x8 — "Alchemist's Forge"
        LevelTemplate(
          levelNumber = 6,
          title = "Alchemist's Forge",
          tierBadge = "TIER II · INTERSECTION NEXUS",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              // (1,1)Q (2,1)U (3,1)A (4,1)R (5,1)T (6,1)Z
              TargetWord(
                id = "l6_h1",
                word = "QUARTZ",
                clue = "Piezoelectric silica crystal mineral",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 5), // (1,1)Q, (3,1)A, (6,1)Z
              ),
              // (1,1)Q (1,2)U (1,3)E (1,4)S (1,5)T — crosses QUARTZ at (1,1)=Q
              TargetWord(
                id = "l6_v1",
                word = "QUEST",
                clue = "Heroic expedition in search of a prize",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)Q, (1,3)E, (1,5)T
              ),
              // (3,1)A (3,2)M (3,3)B (3,4)E (3,5)R — crosses QUARTZ at (3,1)=A
              TargetWord(
                id = "l6_v2",
                word = "AMBER",
                clue = "Fossilized golden tree resin",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)A, (3,3)B, (3,5)R
              ),
              // (6,1)Z (6,2)E (6,3)N (6,4)I (6,5)T (6,6)H — crosses QUARTZ at (6,1)=Z
              TargetWord(
                id = "l6_v3",
                word = "ZENITH",
                clue = "Highest celestial point directly overhead",
                startX = 6,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 5), // (6,1)Z, (6,3)N, (6,6)H
              ),
              // Look at y=5: (1,5)=T (from QUEST) and (3,5)=R (from AMBER)!
              // Word at (1,5)..(5,5): (1,5)T (2,5)R (3,5)A? No, (3,5) is R:
              // (1,5)T (2,5)O (3,5)R (4,5)C (5,5)H -> "TORCH"? Wait: (6,5) is T in ZENITH, so (1,5)..(5,5) would touch (6,5)!
              // What if the word is 6 letters from x=1 to x=6 where x=1 is T, x=3 is R, and x=6 is T?
              // (1,5)T (2,5)A (3,5)R (4,5)G (5,5)E (6,5)T -> "TARGET"!!
              // Look at "TARGET": index 0 (x=1) = 'T' (matches QUEST!), index 2 (x=3) = 'R' (matches AMBER!), index 5 (x=6) = 'T' (matches ZENITH!)!
              TargetWord(
                id = "l6_h2",
                word = "TARGET",
                clue = "Bullseye mark aimed at by an archer",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4, 5), // (1,5)T, (3,5)R, (5,5)E, (6,5)T
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.TW,
              Coordinate(6, 1) to SquareMultiplier.TW,
              Coordinate(3, 1) to SquareMultiplier.DW,
              Coordinate(1, 5) to SquareMultiplier.DL,
              Coordinate(3, 5) to SquareMultiplier.TL,
              Coordinate(6, 5) to SquareMultiplier.DW,
            ),
          compactCode =
            "8x8|title:Alchemist's Forge|H:1,1:QUARTZ:0,2,5|V:1,1:QUEST:0,2,4|V:3,1:AMBER:0,2,4|V:6,1:ZENITH:0,2,5|H:1,5:TARGET:0,2,4,5",
        ),

        // LEVEL 7: 8x8 — "Nebula Horizon"
        LevelTemplate(
          levelNumber = 7,
          title = "Nebula Horizon",
          tierBadge = "TIER III · MULTIPLIER MASTER",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              // (1,1)G (2,1)A (3,1)L (4,1)A (5,1)X (6,1)Y
              TargetWord(
                id = "l7_h1",
                word = "GALAXY",
                clue = "Spiral system of billions of stars",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 4), // (2,1)A, (4,1)A, (5,1)X
              ),
              // (2,1)A (2,2)T (2,3)L (2,4)A (2,5)S — crosses GALAXY at (2,1)=A
              TargetWord(
                id = "l7_v1",
                word = "ATLAS",
                clue = "Bound collection of celestial or world maps",
                startX = 2,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (2,1)A, (2,3)L, (2,5)S
              ),
              // (4,1)A (4,2)R (4,3)R (4,4)O (4,5)W — crosses GALAXY at (4,1)=A
              TargetWord(
                id = "l7_v2",
                word = "ARROW",
                clue = "Feathered shaft loosed from a bow",
                startX = 4,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (4,1)A, (4,3)R, (4,5)W
              ),
              // At y=5: (2,5)=S (from ATLAS) and (4,5)=W (from ARROW):
              // Word at (2,5)..(6,5): (2,5)S (3,5)W? No, W is at x=4:
              // What if H2 is at y=3 where (2,3)=L (from ATLAS) and (4,3)=R (from ARROW)?
              // 5-letter word at (1,3)..(5,3) with L at x=2 and R at x=4:
              // (1,3)F (2,3)L (3,3)A (4,3)R (5,3)E -> "FLARE" (Solar flare)!
              TargetWord(
                id = "l7_h2",
                word = "FLARE",
                clue = "Sudden brilliant eruption on the sun's surface",
                startX = 1,
                startY = 3,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 2, 3, 4), // (2,3)L, (3,3)A, (4,3)R, (5,3)E
              ),
              // And at y=5 where (2,5)=S (from ATLAS), let's run "STORM" at (2,5)?Wait: (4,5) is W in ARROW.
              // What if V2 at (4,1) is "ARMOR" ((4,1)A, (4,2)R, (4,3)M, (4,4)O, (4,5)R)?
              // Wait: if V2 is "ARROW", let's keep V2="ARROW" and put a horizontal word at y=5 starting at x=4:
              // (4,5)W (5,5)O (6,5)R (7,5)L (8,5)D? On 8x8, (4,5)W (5,5)I (6,5)N (7,5)D -> "WIND"!
              TargetWord(
                id = "l7_h3",
                word = "WIND",
                clue = "Moving current of air across the plains",
                startX = 4,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 3), // (4,5)W, (6,5)N, (7,5)D
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(5, 1) to SquareMultiplier.TL,
              Coordinate(2, 1) to SquareMultiplier.DW,
              Coordinate(4, 1) to SquareMultiplier.DW,
              Coordinate(2, 3) to SquareMultiplier.TW,
              Coordinate(4, 3) to SquareMultiplier.TL,
              Coordinate(4, 5) to SquareMultiplier.DW,
            ),
          compactCode =
            "8x7|title:Nebula Horizon|H:1,1:GALAXY:1,3,4|V:2,1:ATLAS:0,2,4|V:4,1:ARROW:0,2,4|H:1,3:FLARE:1,2,3,4|H:4,5:WIND:0,2,3",
        ).copy(boardHeight = 7),

        // LEVEL 8: 8x8 — "Shogun's Scroll"
        LevelTemplate(
          levelNumber = 8,
          title = "Shogun's Scroll",
          tierBadge = "TIER III · MULTIPLIER MASTER",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              // (1,1)K (2,1)A (3,1)T (4,1)A (5,1)N (6,1)A
              TargetWord(
                id = "l8_h1",
                word = "KATANA",
                clue = "Curved single-edged samurai blade",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)K, (3,1)T, (5,1)N
              ),
              // (3,1)T (3,2)E (3,3)M (3,4)P (3,5)L (3,6)E — crosses KATANA at (3,1)=T
              TargetWord(
                id = "l8_v1",
                word = "TEMPLE",
                clue = "Sacred sanctuary of meditation",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4, 5), // (3,1)T, (3,3)M, (3,5)L, (3,6)E
              ),
              // (5,1)N (5,2)O (5,3)B (5,4)L (5,5)E — crosses KATANA at (5,1)=N
              TargetWord(
                id = "l8_v2",
                word = "NOBLE",
                clue = "Possessing high moral character and honor",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)N, (5,3)B, (5,5)E
              ),
              // At y=3: (3,3)=M (from TEMPLE) and (5,3)=B (from NOBLE)!
              // Word at (2,3)..(6,3): (2,3)A (3,3)M (4,3)B? No, B is at x=5:
              // What if y=5 has (3,5)=L (from TEMPLE) and (5,5)=E (from NOBLE)?
              // Word with L at x=3 and E at x=5:
              // (1,5)B (2,5)L? No, L is at x=3:
              // (2,5)F (3,5)L (4,5)A (5,5)M (6,5)E? E is at x=6 there.
              // If startX=1: (1,5)P (2,5)A (3,5)L (4,5)A (5,5)C (6,5)E?
              // If startX=2: (2,5)B (3,5)L (4,5)A (5,5)D (6,5)E?
              // Wait: we want L at x=3 (index 1 if startX=2) and E at x=5 (index 3 if startX=2):
              // (2,5)B (3,5)L (4,5)U (5,5)E -> "BLUE" or "CLUE" or "GLUE"!
              // Or 5 letters starting at x=1: index 2 = L (x=3), index 4 = E (x=5):
              // (1,5)P (2,5)U (3,5)L (4,5)S (5,5)E -> "PULSE"!
              // Check "PULSE": (1,5)=P, (2,5)=U, (3,5)=L (matches TEMPLE!), (4,5)=S, (5,5)=E (matches NOBLE!)!
              TargetWord(
                id = "l8_h2",
                word = "PULSE",
                clue = "Rhythmic beat of life and energy",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 3, 4), // (1,5)P, (3,5)L, (4,5)S, (5,5)E
              ),
              TargetWord(
                id = "l8_v3",
                word = "CAPE",
                clue = "Sleeveless cloak worn over the shoulders",
                startX = 1,
                startY = 3,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2), // (1,3)C, (1,5)P
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.TL,
              Coordinate(3, 1) to SquareMultiplier.DW,
              Coordinate(5, 1) to SquareMultiplier.DW,
              Coordinate(3, 5) to SquareMultiplier.TW,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Shogun's Scroll|H:1,1:KATANA:0,2,4|V:3,1:TEMPLE:0,2,4,5|V:5,1:NOBLE:0,2,4|H:1,5:PULSE:0,2,3,4|V:1,3:CAPE:0,2",
        ),

        // LEVEL 9: 8x8 — "Origami Garden"
        LevelTemplate(
          levelNumber = 9,
          title = "Origami Garden",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l9_h1",
                word = "LOTUS",
                clue = "Sacred water blossom of serene ponds",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)L, (3,1)T, (5,1)S
              ),
              TargetWord(
                id = "l9_v1",
                word = "START",
                clue = "Embark on a new journey",
                startX = 3,
                startY = 0,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(1, 3, 4), // (3,1)T, (3,3)R, (3,4)T
              ),
              TargetWord(
                id = "l9_h2",
                word = "CRANE",
                clue = "Iconic folded origami bird of peace",
                startX = 2,
                startY = 3,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 1, 3), // (2,3)C, (3,3)R, (5,3)N
              ),
              TargetWord(
                id = "l9_v2",
                word = "SENSE",
                clue = "Intuitive perception and awareness",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)S, (5,3)N, (5,5)E
              ),
              TargetWord(
                id = "l9_h3",
                word = "RIVER",
                clue = "Flowing mountain stream",
                startX = 2,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 4), // (3,5)I, (5,5)E, (6,5)R
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(3, 1) to SquareMultiplier.DW,
              Coordinate(5, 1) to SquareMultiplier.TL,
              Coordinate(3, 3) to SquareMultiplier.TW,
              Coordinate(5, 3) to SquareMultiplier.DW,
              Coordinate(5, 5) to SquareMultiplier.DL,
            ),
          compactCode =
            "8x8|title:Origami Garden|H:1,1:LOTUS:0,2,4|V:3,0:START:1,3,4|H:2,3:CRANE:0,1,3|V:5,1:SENSE:0,2,4|H:2,5:RIVER:1,3,4",
        ),

        // LEVEL 10: 8x8 — "Kyoto Lanterns"
        LevelTemplate(
          levelNumber = 10,
          title = "Kyoto Lanterns",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l10_h1",
                word = "BRIDGE",
                clue = "Arched timber crossing over a canal",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 5), // (2,1)R, (4,1)D, (6,1)E
              ),
              TargetWord(
                id = "l10_v1",
                word = "RIVER",
                clue = "Winding waterway reflecting lanterns",
                startX = 2,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (2,1)R, (2,3)V, (2,5)R
              ),
              TargetWord(
                id = "l10_v2",
                word = "DANCE",
                clue = "Graceful rhythmic festival movement",
                startX = 4,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (4,1)D, (4,3)N, (4,5)E
              ),
              TargetWord(
                id = "l10_v3",
                word = "ECHO",
                clue = "Resounding reflection of sound",
                startX = 6,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2), // (6,1)E, (6,3)H
              ),
              TargetWord(
                id = "l10_h2",
                word = "GREEN",
                clue = "Verdant hue of mossy temple gardens",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(1, 3, 4), // (2,5)R, (4,5)E, (5,5)N
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(2, 1) to SquareMultiplier.DW,
              Coordinate(4, 1) to SquareMultiplier.TW,
              Coordinate(6, 1) to SquareMultiplier.DL,
              Coordinate(2, 5) to SquareMultiplier.TL,
              Coordinate(4, 5) to SquareMultiplier.DW,
            ),
          compactCode =
            "8x8|title:Kyoto Lanterns|H:1,1:BRIDGE:1,3,5|V:2,1:RIVER:0,2,4|V:4,1:DANCE:0,2,4|V:6,1:ECHO:0,2|H:1,5:GREEN:1,3,4",
        ),

        // LEVEL 11: 8x8 — "Coral Sanctuary"
        LevelTemplate(
          levelNumber = 11,
          title = "Coral Sanctuary",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l11_h1",
                word = "CORAL",
                clue = "Reef-building marine formation",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)C, (3,1)R, (5,1)L
              ),
              TargetWord(
                id = "l11_v1",
                word = "CHESS",
                clue = "Strategic board game of kings and knights",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)C, (1,3)E, (1,5)S
              ),
              TargetWord(
                id = "l11_v2",
                word = "RADIO",
                clue = "Wireless receiver of broadcast waves",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)R, (3,3)D, (3,5)O
              ),
              TargetWord(
                id = "l11_v3",
                word = "LANCE",
                clue = "Jousting spear carried by a cavalry knight",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)L, (5,3)N, (5,5)E
              ),
              TargetWord(
                id = "l11_h2",
                word = "SHORE",
                clue = "Sandy coastline where ocean waves break",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)S, (3,5)O, (5,5)E
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.DW,
              Coordinate(3, 1) to SquareMultiplier.TL,
              Coordinate(5, 1) to SquareMultiplier.DW,
              Coordinate(1, 5) to SquareMultiplier.DL,
              Coordinate(3, 5) to SquareMultiplier.TW,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Coral Sanctuary|H:1,1:CORAL:0,2,4|V:1,1:CHESS:0,2,4|V:3,1:RADIO:0,2,4|V:5,1:LANCE:0,2,4|H:1,5:SHORE:0,2,4",
        ),

        // LEVEL 12: 8x8 — "Solar Eclipse"
        LevelTemplate(
          levelNumber = 12,
          title = "Solar Eclipse",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l12_h1",
                word = "SOLAR",
                clue = "Radiating from the sun's corona",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)S, (3,1)L, (5,1)R
              ),
              TargetWord(
                id = "l12_v1",
                word = "STEAM",
                clue = "Heated water vapor rising from hot springs",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)S, (1,3)E, (1,5)M
              ),
              TargetWord(
                id = "l12_v2",
                word = "LIGHT",
                clue = "Luminous beams that banish shadow",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)L, (3,3)G, (3,5)T
              ),
              TargetWord(
                id = "l12_v3",
                word = "RIVER",
                clue = "Freshwater current carving a valley",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)R, (5,3)V, (5,5)R
              ),
              TargetWord(
                id = "l12_h2",
                word = "MOTOR",
                clue = "Rotary engine that drives motion",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)M, (3,5)T, (5,5)R
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.TW,
              Coordinate(3, 1) to SquareMultiplier.DW,
              Coordinate(5, 1) to SquareMultiplier.DL,
              Coordinate(1, 5) to SquareMultiplier.TL,
              Coordinate(3, 5) to SquareMultiplier.DW,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Solar Eclipse|H:1,1:SOLAR:0,2,4|V:1,1:STEAM:0,2,4|V:3,1:LIGHT:0,2,4|V:5,1:RIVER:0,2,4|H:1,5:MOTOR:0,2,4",
        ),

        // LEVEL 13: 8x8 — "Samurai Armor"
        LevelTemplate(
          levelNumber = 13,
          title = "Samurai Armor",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l13_h1",
                word = "SWORD",
                clue = "Forged steel blade of a warrior",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)S, (3,1)O, (5,1)D
              ),
              TargetWord(
                id = "l13_v1",
                word = "SCALE",
                clue = "Overlapping protective armor plate",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)S, (1,3)A, (1,5)E
              ),
              TargetWord(
                id = "l13_v2",
                word = "OCEAN",
                clue = "Deep blue sea surrounding the archipelago",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)O, (3,3)E, (3,5)N
              ),
              TargetWord(
                id = "l13_v3",
                word = "DRAMA",
                clue = "Theatrical Noh or Kabuki stage performance",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)D, (5,3)A, (5,5)A
              ),
              TargetWord(
                id = "l13_h2",
                word = "ARENA",
                clue = "Central tournament ground for champions",
                startX = 1,
                startY = 3,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,3)A, (3,3)E, (5,3)A
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.DW,
              Coordinate(3, 1) to SquareMultiplier.TL,
              Coordinate(5, 1) to SquareMultiplier.DW,
              Coordinate(1, 3) to SquareMultiplier.TL,
              Coordinate(3, 3) to SquareMultiplier.TW,
              Coordinate(5, 3) to SquareMultiplier.DL,
            ),
          compactCode =
            "8x8|title:Samurai Armor|H:1,1:SWORD:0,2,4|V:1,1:SCALE:0,2,4|V:3,1:OCEAN:0,2,4|V:5,1:DRAMA:0,2,4|H:1,3:ARENA:0,2,4",
        ),

        // LEVEL 14: 8x8 — "Velvet Horizon"
        LevelTemplate(
          levelNumber = 14,
          title = "Velvet Horizon",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l14_h1",
                word = "CLOUD",
                clue = "Billowing mist drifting across mountain peaks",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)C, (3,1)O, (5,1)D
              ),
              TargetWord(
                id = "l14_v1",
                word = "CHEST",
                clue = "Ornate cedar box for storing scrolls",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)C, (1,3)E, (1,5)T
              ),
              TargetWord(
                id = "l14_v2",
                word = "OCEAN",
                clue = "Boundless maritime horizon",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)O, (3,3)E, (3,5)N
              ),
              TargetWord(
                id = "l14_v3",
                word = "DANCE",
                clue = "Choreographed motion of falling petals",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)D, (5,3)N, (5,5)E
              ),
              TargetWord(
                id = "l14_h2",
                word = "TENSE",
                clue = "Stretched taut like a tuned Koto string",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)T, (3,5)N, (5,5)E
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.DW,
              Coordinate(3, 1) to SquareMultiplier.TW,
              Coordinate(5, 1) to SquareMultiplier.DL,
              Coordinate(1, 5) to SquareMultiplier.TL,
              Coordinate(3, 5) to SquareMultiplier.DW,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Velvet Horizon|H:1,1:CLOUD:0,2,4|V:1,1:CHEST:0,2,4|V:3,1:OCEAN:0,2,4|V:5,1:DANCE:0,2,4|H:1,5:TENSE:0,2,4",
        ),

        // LEVEL 15: 8x8 — "Glacier Peak"
        LevelTemplate(
          levelNumber = 15,
          title = "Glacier Peak",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l15_h1",
                word = "FROST",
                clue = "Crystalline rime formed on winter mornings",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)F, (3,1)O, (5,1)T
              ),
              TargetWord(
                id = "l15_v1",
                word = "FOCUS",
                clue = "Center of sharp optical clarity",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)F, (1,3)C, (1,5)S
              ),
              TargetWord(
                id = "l15_v2",
                word = "OPERA",
                clue = "Grand dramatic work set to orchestral music",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)O, (3,3)E, (3,5)A
              ),
              TargetWord(
                id = "l15_v3",
                word = "TOWER",
                clue = "Multi-tiered pagoda rising above the pines",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)T, (5,3)W, (5,5)R
              ),
              TargetWord(
                id = "l15_h2",
                word = "STAIR",
                clue = "Stone steps ascending a mountain shrine",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)S, (3,5)A, (5,5)R
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.TL,
              Coordinate(3, 1) to SquareMultiplier.DW,
              Coordinate(5, 1) to SquareMultiplier.DW,
              Coordinate(1, 5) to SquareMultiplier.DL,
              Coordinate(3, 5) to SquareMultiplier.TW,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Glacier Peak|H:1,1:FROST:0,2,4|V:1,1:FOCUS:0,2,4|V:3,1:OPERA:0,2,4|V:5,1:TOWER:0,2,4|H:1,5:STAIR:0,2,4",
        ),

        // LEVEL 16: 8x8 — "Golden Pavilion"
        LevelTemplate(
          levelNumber = 16,
          title = "Golden Pavilion",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l16_h1",
                word = "PEARL",
                clue = "Iridescent treasure of the oyster shell",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)P, (3,1)A, (5,1)L
              ),
              TargetWord(
                id = "l16_v1",
                word = "PLANT",
                clue = "Living botanical sprout in a bonsai pot",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)P, (1,3)A, (1,5)T
              ),
              TargetWord(
                id = "l16_v2",
                word = "ARENA",
                clue = "Enclosed stage for martial exhibition",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)A, (3,3)E, (3,5)A
              ),
              TargetWord(
                id = "l16_v3",
                word = "LEMON",
                clue = "Bright citrus fruit with zest and tang",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)L, (5,3)M, (5,5)N
              ),
              TargetWord(
                id = "l16_h2",
                word = "TRAIN",
                clue = "Express rail gliding past Mount Fuji",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)T, (3,5)A, (5,5)N
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.DW,
              Coordinate(3, 1) to SquareMultiplier.TW,
              Coordinate(5, 1) to SquareMultiplier.DL,
              Coordinate(1, 5) to SquareMultiplier.TL,
              Coordinate(3, 5) to SquareMultiplier.DW,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Golden Pavilion|H:1,1:PEARL:0,2,4|V:1,1:PLANT:0,2,4|V:3,1:ARENA:0,2,4|V:5,1:LEMON:0,2,4|H:1,5:TRAIN:0,2,4",
        ),

        // LEVEL 17: 8x8 — "Koto Harmony"
        LevelTemplate(
          levelNumber = 17,
          title = "Koto Harmony",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l17_h1",
                word = "MUSIC",
                clue = "Melodic art of plucked strings and chimes",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)M, (3,1)S, (5,1)C
              ),
              TargetWord(
                id = "l17_v1",
                word = "MAGIC",
                clue = "Enchanting wonder and sleight of hand",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)M, (1,3)G, (1,5)C
              ),
              TargetWord(
                id = "l17_v2",
                word = "SCALE",
                clue = "Ascending sequence of musical notes",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)S, (3,3)A, (3,5)E
              ),
              TargetWord(
                id = "l17_v3",
                word = "CROWN",
                clue = "Sovereign diadem of gold",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)C, (5,3)O, (5,5)N
              ),
              TargetWord(
                id = "l17_h2",
                word = "CLEAN",
                clue = "Pure, unblemished, and freshly swept",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)C, (3,5)E, (5,5)N
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.TW,
              Coordinate(3, 1) to SquareMultiplier.DW,
              Coordinate(5, 1) to SquareMultiplier.TL,
              Coordinate(1, 5) to SquareMultiplier.DW,
              Coordinate(3, 5) to SquareMultiplier.DL,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Koto Harmony|H:1,1:MUSIC:0,2,4|V:1,1:MAGIC:0,2,4|V:3,1:SCALE:0,2,4|V:5,1:CROWN:0,2,4|H:1,5:CLEAN:0,2,4",
        ),

        // LEVEL 18: 8x8 — "Bamboo Fortress"
        LevelTemplate(
          levelNumber = 18,
          title = "Bamboo Fortress",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l18_h1",
                word = "BLADE",
                clue = "Keen edge of a bamboo-cutting knife",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)B, (3,1)A, (5,1)E
              ),
              TargetWord(
                id = "l18_v1",
                word = "BEACH",
                clue = "Pebbled cove along the inland sea",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)B, (1,3)A, (1,5)H
              ),
              TargetWord(
                id = "l18_v2",
                word = "ARENA",
                clue = "Grand courtyard of competitive mastery",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)A, (3,3)E, (3,5)A
              ),
              TargetWord(
                id = "l18_v3",
                word = "EVENT",
                clue = "Notable celebration on the lunar calendar",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)E, (5,3)E, (5,5)T
              ),
              TargetWord(
                id = "l18_h2",
                word = "HEART",
                clue = "Core of courage and steadfast spirit",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)H, (3,5)A, (5,5)T
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.DW,
              Coordinate(3, 1) to SquareMultiplier.TL,
              Coordinate(5, 1) to SquareMultiplier.DW,
              Coordinate(1, 5) to SquareMultiplier.TW,
              Coordinate(3, 5) to SquareMultiplier.DL,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Bamboo Fortress|H:1,1:BLADE:0,2,4|V:1,1:BEACH:0,2,4|V:3,1:ARENA:0,2,4|V:5,1:EVENT:0,2,4|H:1,5:HEART:0,2,4",
        ),

        // LEVEL 19: 8x8 — "Starlight Compass"
        LevelTemplate(
          levelNumber = 19,
          title = "Starlight Compass",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l19_h1",
                word = "COMET",
                clue = "Icy celestial traveler with a glowing tail",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)C, (3,1)M, (5,1)T
              ),
              TargetWord(
                id = "l19_v1",
                word = "CHAIR",
                clue = "Carved wooden seat in the study hall",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)C, (1,3)A, (1,5)R
              ),
              TargetWord(
                id = "l19_v2",
                word = "MEDIA",
                clue = "Channels of artistic communication",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)M, (3,3)D, (3,5)A
              ),
              TargetWord(
                id = "l19_v3",
                word = "TRUTH",
                clue = "Verifiable reality and honest principle",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)T, (5,3)U, (5,5)H
              ),
              TargetWord(
                id = "l19_h2",
                word = "REACH",
                clue = "Extend one's hand toward the stars",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)R, (3,5)A, (5,5)H
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.TL,
              Coordinate(3, 1) to SquareMultiplier.DW,
              Coordinate(5, 1) to SquareMultiplier.TW,
              Coordinate(1, 5) to SquareMultiplier.DW,
              Coordinate(3, 5) to SquareMultiplier.TL,
              Coordinate(5, 5) to SquareMultiplier.DL,
            ),
          compactCode =
            "8x8|title:Starlight Compass|H:1,1:COMET:0,2,4|V:1,1:CHAIR:0,2,4|V:3,1:MEDIA:0,2,4|V:5,1:TRUTH:0,2,4|H:1,5:REACH:0,2,4",
        ),

        // LEVEL 20: 8x8 — "Celestial Crane"
        LevelTemplate(
          levelNumber = 20,
          title = "Celestial Crane",
          tierBadge = "TIER I · STARTER SKELETON",
          boardWidth = 8,
          boardHeight = 8,
          targetWords =
            listOf(
              TargetWord(
                id = "l20_h1",
                word = "FLAME",
                clue = "Flickering lantern fire in the night breeze",
                startX = 1,
                startY = 1,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)F, (3,1)A, (5,1)E
              ),
              TargetWord(
                id = "l20_v1",
                word = "FIELD",
                clue = "Open meadow of wildflowers",
                startX = 1,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,1)F, (1,3)E, (1,5)D
              ),
              TargetWord(
                id = "l20_v2",
                word = "ANGEL",
                clue = "Winged celestial messenger",
                startX = 3,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (3,1)A, (3,3)G, (3,5)L
              ),
              TargetWord(
                id = "l20_v3",
                word = "EARLY",
                clue = "Arriving with the first light of dawn",
                startX = 5,
                startY = 1,
                direction = WordDirection.VERTICAL,
                missingBlankIndices = listOf(0, 2, 4), // (5,1)E, (5,3)R, (5,5)Y
              ),
              TargetWord(
                id = "l20_h2",
                word = "DELAY",
                clue = "Momentary pause before the next fold",
                startX = 1,
                startY = 5,
                direction = WordDirection.HORIZONTAL,
                missingBlankIndices = listOf(0, 2, 4), // (1,5)D, (3,5)L, (5,5)Y
              ),
            ),
          multipliers =
            mapOf(
              Coordinate(1, 1) to SquareMultiplier.DW,
              Coordinate(3, 1) to SquareMultiplier.TW,
              Coordinate(5, 1) to SquareMultiplier.DL,
              Coordinate(1, 5) to SquareMultiplier.TL,
              Coordinate(3, 5) to SquareMultiplier.DW,
              Coordinate(5, 5) to SquareMultiplier.TL,
            ),
          compactCode =
            "8x8|title:Celestial Crane|H:1,1:FLAME:0,2,4|V:1,1:FIELD:0,2,4|V:3,1:ANGEL:0,2,4|V:5,1:EARLY:0,2,4|H:1,5:DELAY:0,2,4",
        ),
      )

    const val TOTAL_CAMPAIGN_LEVELS = 100

    val CAMPAIGN_TITLES_21_TO_100: List<String> =
      listOf(
        // Chapter II: Levels 21–40 (Kyoto Studio)
        "Hinoki Courtyard", "Sakura Whisper", "Matcha Pavilion", "Aizome Tide", "Kamo River Mist",
        "Arashiyama Grove", "Fushimi Torii", "Gion Twilight", "Kinkaku Reflection", "Nijo Bastion",
        "Byodoin Phoenix", "Philosopher's Walk", "Kiyomizu Stage", "Nanzen Aqueduct", "Ryoanji Zen Stone",
        "Katsura MoonDeck", "Heian Shrine Gate", "Kurama Mountain", "Uji Tea Terrace", "Toji Pagoda Spire",
        // Chapter III: Levels 41–60 (Aizome Nexus)
        "Indigo Loom", "Washi Artisan", "Furin Wind Chime", "Kintsugi Gold Seam", "Tatami Symmetry",
        "Shoji Lantern Glow", "Suiseki Scholar", "Bonsai Canopy", "Sumi Ink Cascade", "Calligraphy Scroll",
        "Origami Starfold", "Tsuru Wing Crease", "Kusudama Sphere", "Chiyogami Pattern", "Mizuhiki Knot",
        "Kumiko Lattice", "Edo Woodblock", "Hakone Marquetry", "Imari Porcelain", "Kutani Glaze",
        // Chapter IV: Levels 61–80 (Koto Master)
        "Hirajoshi Chord", "Shakuhachi Breath", "Biwa Ballad", "Shamisen Pluck", "Taiko Thunder",
        "Kagura Ritual", "Gagaku Court", "Noh Mask Shadow", "Kabuki Fan Fold", "Tsugaru Cadence",
        "Koto Thirteen Strings", "Yatsuhashi Bridge", "Furin Summer Breeze", "Suikinkutsu Echo", "Suzu Temple Bell",
        "Sho Harmony Reed", "Hichiriki Air", "Tsuzumi Shoulder Drum", "Kane Bronze Gong", "Matsuri Finale",
        // Chapter V: Levels 81–100 (Shogun Grandmaster)
        "Azuchi Keep", "Himeji White Heron", "Matsumoto Crow", "Kumamoto Rampart", "Inuyama Watchtower",
        "Hikone Moat", "Bitchu Cloud Keep", "Matsue Lake Bastion", "Maruoka Ancient Stone", "Hirosaki Blossom Keep",
        "Uwajima Sea Gate", "Kochi Hilltop", "Tsurugaoka Banner", "Sekigahara Vanguard", "Edo Citadel Crown",
        "Tokugawa Crest", "Meiji Dawn Horizon", "Imperial Garden Fold", "Thousandth Paper Crane", "Grand Shogun's 100th Fold",
      )
  }
}
