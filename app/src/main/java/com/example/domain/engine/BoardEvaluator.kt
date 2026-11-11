package com.example.domain.engine

import com.example.domain.model.AiOpponent
import com.example.domain.model.AiVocabularyStyle
import com.example.domain.model.Coordinate
import com.example.domain.model.LexiBoardState
import com.example.domain.model.MovePreview
import com.example.domain.model.RogueRelic
import com.example.domain.model.SquareMultiplier
import com.example.domain.model.Tile
import com.example.domain.model.TileBonusEffect
import com.example.domain.model.WordBreakdown
import kotlin.math.roundToInt

/**
 * Pure Kotlin crossword placement validator, multiplier score breakdown calculator,
 * and AI candidate move generator.
 */
class BoardEvaluator(private val trie: WordTrieEngine) {

  data class AiCandidateMove(
    val placements: Map<Coordinate, Tile>,
    val preview: MovePreview,
    val primaryWord: String,
  )

  fun evaluatePendingMove(
    board: LexiBoardState,
    activeRelics: List<RogueRelic>,
    comboMultiplier: Float,
    turnTimeRemainingSec: Int,
  ): MovePreview {
    val pending = board.pendingPlacements
    if (pending.isEmpty()) {
      return MovePreview(
        isValidPosition = false,
        placementError = "Drag or tap tiles onto the 15×15 board",
      )
    }

    val coords = pending.keys.toList()
    val allSameRow = coords.all { it.y == coords.first().y }
    val allSameCol = coords.all { it.x == coords.first().x }
    if (!allSameRow && !allSameCol) {
      return MovePreview(
        isValidPosition = false,
        placementError = "Tiles must be aligned in a single row or column",
      )
    }

    val hasExistingTiles = board.hasAnyLockedTiles()
    if (!hasExistingTiles) {
      if (Coordinate.CENTER !in pending) {
        return MovePreview(
          isValidPosition = false,
          placementError = "First word must cover the Center Star (★) at H8",
        )
      }
      if (pending.size < 2) {
        return MovePreview(
          isValidPosition = false,
          placementError = "First word must be at least 2 letters long",
        )
      }
    }

    // Verify contiguous line (including locked tiles in between)
    if (allSameRow) {
      val y = coords.first().y
      val minX = coords.minOf { it.x }
      val maxX = coords.maxOf { it.x }
      for (x in minX..maxX) {
        if (board.effectiveTileAt(Coordinate(x, y)) == null) {
          return MovePreview(
            isValidPosition = false,
            placementError = "Tiles must form a continuous unbroken line",
          )
        }
      }
    } else {
      val x = coords.first().x
      val minY = coords.minOf { it.y }
      val maxY = coords.maxOf { it.y }
      for (y in minY..maxY) {
        if (board.effectiveTileAt(Coordinate(x, y)) == null) {
          return MovePreview(
            isValidPosition = false,
            placementError = "Tiles must form a continuous unbroken line",
          )
        }
      }
    }

    // Verify connection to existing locked tiles if board is non-empty
    if (hasExistingTiles) {
      var touchesLocked = false
      for (c in coords) {
        val neighbors =
          listOf(
            Coordinate(c.x - 1, c.y),
            Coordinate(c.x + 1, c.y),
            Coordinate(c.x, c.y - 1),
            Coordinate(c.x, c.y + 1),
          )
        if (neighbors.any { it.isValid && board.squareAt(it)?.occupyingTile != null }) {
          touchesLocked = true
          break
        }
      }
      if (!touchesLocked) {
        return MovePreview(
          isValidPosition = false,
          placementError = "New tiles must connect to an existing word on the board",
        )
      }
    }

    // Extract connected words
    val formedWords = ArrayList<WordBreakdown>()
    val seenWordSpans = HashSet<String>()

    val isHorizontalPrimary =
      if (coords.size > 1) {
        allSameRow
      } else {
        val single = coords.first()
        val hasHorizontalNeighbor =
          board.effectiveTileAt(Coordinate(single.x - 1, single.y)) != null ||
            board.effectiveTileAt(Coordinate(single.x + 1, single.y)) != null
        hasHorizontalNeighbor
      }

    // Primary word along main axis
    val firstCoord = coords.first()
    val primarySpan = extractLineSpan(board, firstCoord, horizontal = isHorizontalPrimary)
    if (primarySpan.size >= 2) {
      val key =
        "H:$isHorizontalPrimary:${primarySpan.first().x},${primarySpan.first().y}-${primarySpan.last().x},${primarySpan.last().y}"
      seenWordSpans.add(key)
      formedWords.add(scoreWordSpan(board, primarySpan, activeRelics))
    }

    // Cross words along perpendicular axis for each newly placed tile
    for (placedCoord in coords.sortedWith(compareBy({ it.y }, { it.x }))) {
      val crossSpan = extractLineSpan(board, placedCoord, horizontal = !isHorizontalPrimary)
      if (crossSpan.size >= 2) {
        val key =
          "H:${!isHorizontalPrimary}:${crossSpan.first().x},${crossSpan.first().y}-${crossSpan.last().x},${crossSpan.last().y}"
        if (seenWordSpans.add(key)) {
          formedWords.add(scoreWordSpan(board, crossSpan, activeRelics))
        }
      }
    }

    if (formedWords.isEmpty()) {
      return MovePreview(
        isValidPosition = false,
        placementError = "Place at least a 2-letter connected word",
      )
    }

    val invalidWords = formedWords.map { it.word }.filter { !trie.isValidWord(it) }
    val allValid = invalidWords.isEmpty()

    val rawWordsTotal = formedWords.sumOf { it.totalPoints }
    val bingoBonus = if (pending.size >= 7) 50 else 0
    val speedBonus =
      if (RogueRelic.QUICK_FOLD in activeRelics && turnTimeRemainingSec >= 25) 12 else 0
    val comboScaled = ((rawWordsTotal + bingoBonus + speedBonus) * comboMultiplier).roundToInt()
    val comboExtra = maxOf(0, comboScaled - (rawWordsTotal + bingoBonus + speedBonus))

    val highestMult =
      formedWords
        .flatMap { it.triggeredMultipliers }
        .maxByOrNull {
          when (it) {
            SquareMultiplier.TW -> 5
            SquareMultiplier.DW, SquareMultiplier.CENTER_STAR -> 4
            SquareMultiplier.TL -> 3
            SquareMultiplier.DL -> 2
            SquareMultiplier.NONE -> 1
          }
        } ?: SquareMultiplier.NONE

    return MovePreview(
      isValidPosition = true,
      placementError =
        if (allValid) null else "Not in Origami Lexicon: ${invalidWords.joinToString(", ")}",
      allWordsValid = allValid,
      invalidWords = invalidWords,
      formedWords = formedWords,
      comboBonusPoints = comboExtra + speedBonus,
      bingoBonusPoints = bingoBonus,
      totalTurnScore = if (allValid) comboScaled else 0,
      maxSquareMultiplier = highestMult,
    )
  }

  private fun extractLineSpan(
    board: LexiBoardState,
    origin: Coordinate,
    horizontal: Boolean,
  ): List<Coordinate> {
    var startX = origin.x
    var startY = origin.y
    if (horizontal) {
      while (startX > 0 && board.effectiveTileAt(Coordinate(startX - 1, startY)) != null) {
        startX--
      }
      val span = ArrayList<Coordinate>()
      var currX = startX
      while (
        currX < Coordinate.BOARD_SIZE && board.effectiveTileAt(Coordinate(currX, startY)) != null
      ) {
        span.add(Coordinate(currX, startY))
        currX++
      }
      return span
    } else {
      while (startY > 0 && board.effectiveTileAt(Coordinate(startX, startY - 1)) != null) {
        startY--
      }
      val span = ArrayList<Coordinate>()
      var currY = startY
      while (
        currY < Coordinate.BOARD_SIZE && board.effectiveTileAt(Coordinate(startX, currY)) != null
      ) {
        span.add(Coordinate(startX, currY))
        currY++
      }
      return span
    }
  }

  private fun scoreWordSpan(
    board: LexiBoardState,
    span: List<Coordinate>,
    activeRelics: List<RogueRelic>,
  ): WordBreakdown {
    val sb = StringBuilder(span.size)
    var letterSum = 0
    var wordMultiplier = 1
    var craneBlessingCount = 0
    var relicBonus = 0
    val triggeredMultipliers = ArrayList<SquareMultiplier>()

    val hasVowelLotus = RogueRelic.VOWEL_LOTUS in activeRelics
    val hasEdgeMaster = RogueRelic.EDGE_MASTER in activeRelics
    val hasShogunSeal = RogueRelic.SHOGUN_SEAL in activeRelics

    for (coord in span) {
      val pendingTile = board.pendingPlacements[coord]
      val lockedTile = board.squareAt(coord)?.occupyingTile
      val tile = pendingTile ?: lockedTile ?: continue
      sb.append(tile.char.uppercaseChar())

      var tileScore = tile.baseScore
      if (hasVowelLotus && tile.char.uppercaseChar() in VOWELS) {
        tileScore += 2
        relicBonus += 2
      }

      if (pendingTile != null) {
        // Newly placed tile activates board square multiplier and tile bonus effects
        val square = board.squareAt(coord)
        val sqMult =
          if (square != null && !square.isMultiplierConsumed) {
            square.multiplier
          } else {
            SquareMultiplier.NONE
          }
        if (sqMult != SquareMultiplier.NONE) {
          triggeredMultipliers.add(sqMult)
        }
        tileScore *= sqMult.letterFactor
        wordMultiplier *= sqMult.wordFactor

        when (tile.bonusEffect) {
          TileBonusEffect.GOLD_FOIL -> tileScore += 4
          TileBonusEffect.CRANE_BLESSING -> craneBlessingCount++
          else -> {}
        }

        if (
          hasEdgeMaster &&
            (coord.x == 0 ||
              coord.x == Coordinate.BOARD_SIZE - 1 ||
              coord.y == 0 ||
              coord.y == Coordinate.BOARD_SIZE - 1)
        ) {
          tileScore += 3
          relicBonus += 3
        }
      }

      letterSum += tileScore
    }

    var total = letterSum * wordMultiplier
    repeat(craneBlessingCount) { total = (total * 1.5f).roundToInt() }
    if (hasShogunSeal && span.size >= 5) {
      val boosted = (total * 1.5f).roundToInt()
      relicBonus += (boosted - total)
      total = boosted
    }

    return WordBreakdown(
      word = sb.toString(),
      basePoints = letterSum,
      wordMultiplier = wordMultiplier,
      relicBonusPoints = relicBonus,
      totalPoints = total,
      coordinates = span,
      triggeredMultipliers = triggeredMultipliers,
    )
  }

  /**
   * Searches the 15x15 board for a legal crossword move using the AI opponent's rack and
   * selects the candidate that matches the AI opponent's personality.
   */
  fun findBestAiMove(
    board: LexiBoardState,
    aiRack: List<Tile>,
    opponent: AiOpponent,
  ): AiCandidateMove? {
    if (aiRack.isEmpty()) return null
    val cleanBoard = board.copy(pendingPlacements = emptyMap(), movePreview = null)
    val candidates = ArrayList<AiCandidateMove>()

    if (!cleanBoard.hasAnyLockedTiles()) {
      // First move on empty board across Center Star (7, 7)
      val anagrams = trie.findAnagrams(aiRack.map { it.char }, minLength = 2, maxResults = 50)
      for (word in anagrams) {
        val placements = buildHorizontalPlacementOnEmptyBoard(word, aiRack) ?: continue
        val testBoard = cleanBoard.copy(pendingPlacements = placements)
        val preview =
          evaluatePendingMove(
            board = testBoard,
            activeRelics = listOf(opponent.primaryRelic),
            comboMultiplier = 1.0f,
            turnTimeRemainingSec = 30,
          )
        if (preview.isValidPosition && preview.allWordsValid) {
          candidates.add(AiCandidateMove(placements, preview, word))
        }
      }
    } else {
      // Collect existing locked tiles as anchor hooks
      val anchors = ArrayList<Pair<Coordinate, Tile>>()
      for (y in 0 until Coordinate.BOARD_SIZE) {
        for (x in 0 until Coordinate.BOARD_SIZE) {
          val coord = Coordinate(x, y)
          val tile = cleanBoard.squareAt(coord)?.occupyingTile
          if (tile != null) {
            anchors.add(coord to tile)
          }
        }
      }

      val rackChars = aiRack.map { it.char }
      for ((anchorCoord, anchorTile) in anchors.shuffled().take(18)) {
        val pool = rackChars + anchorTile.char
        val possibleWords = trie.findAnagrams(pool, minLength = 2, maxResults = 45)
        for (word in possibleWords) {
          for (idx in word.indices) {
            if (word[idx].equals(anchorTile.char, ignoreCase = true)) {
              // Try placing horizontally through anchorCoord at index idx
              tryPlaceThroughAnchor(
                  board = cleanBoard,
                  word = word,
                  anchorIndex = idx,
                  anchorCoord = anchorCoord,
                  horizontal = true,
                  aiRack = aiRack,
                  opponent = opponent,
                )
                ?.let { candidates.add(it) }

              // Try placing vertically through anchorCoord at index idx
              tryPlaceThroughAnchor(
                  board = cleanBoard,
                  word = word,
                  anchorIndex = idx,
                  anchorCoord = anchorCoord,
                  horizontal = false,
                  aiRack = aiRack,
                  opponent = opponent,
                )
                ?.let { candidates.add(it) }
            }
          }
          if (candidates.size >= 40) break
        }
        if (candidates.size >= 40) break
      }
    }

    if (candidates.isEmpty()) return null

    return when (opponent.style) {
      AiVocabularyStyle.AGILE_SHORT ->
        candidates
          .sortedWith(
            compareBy<AiCandidateMove> { it.primaryWord.length }
              .thenByDescending { it.preview.totalTurnScore }
          )
          .first()
      AiVocabularyStyle.MULTIPLIER_HUNTER ->
        candidates.maxByOrNull {
          val multBonus =
            it.preview.formedWords.sumOf { w -> w.triggeredMultipliers.size * 15 }
          it.preview.totalTurnScore + multBonus
        }
      AiVocabularyStyle.POETIC_LONG ->
        candidates
          .sortedWith(
            compareByDescending<AiCandidateMove> { it.primaryWord.length }
              .thenByDescending { it.preview.totalTurnScore }
          )
          .first()
      AiVocabularyStyle.GRANDMASTER_OPTIMAL ->
        candidates.maxByOrNull { it.preview.totalTurnScore }
    }
  }

  private fun buildHorizontalPlacementOnEmptyBoard(
    word: String,
    rack: List<Tile>,
  ): Map<Coordinate, Tile>? {
    val startX = (Coordinate.CENTER.x - word.length / 2).coerceAtLeast(0)
    if (startX + word.length > Coordinate.BOARD_SIZE) return null
    val available = rack.toMutableList()
    val map = LinkedHashMap<Coordinate, Tile>()
    for (i in word.indices) {
      val targetChar = word[i].uppercaseChar()
      val tileIdx = available.indexOfFirst { it.char.uppercaseChar() == targetChar }
      if (tileIdx == -1) return null
      val tile = available.removeAt(tileIdx)
      map[Coordinate(startX + i, Coordinate.CENTER.y)] = tile
    }
    return map
  }

  private fun tryPlaceThroughAnchor(
    board: LexiBoardState,
    word: String,
    anchorIndex: Int,
    anchorCoord: Coordinate,
    horizontal: Boolean,
    aiRack: List<Tile>,
    opponent: AiOpponent,
  ): AiCandidateMove? {
    val startX = if (horizontal) anchorCoord.x - anchorIndex else anchorCoord.x
    val startY = if (horizontal) anchorCoord.y else anchorCoord.y - anchorIndex
    val endX = if (horizontal) startX + word.length - 1 else startX
    val endY = if (horizontal) startY else startY + word.length - 1

    if (
      startX < 0 ||
        startY < 0 ||
        endX >= Coordinate.BOARD_SIZE ||
        endY >= Coordinate.BOARD_SIZE
    ) {
      return null
    }

    val available = aiRack.toMutableList()
    val placements = LinkedHashMap<Coordinate, Tile>()

    for (i in word.indices) {
      val coord = if (horizontal) Coordinate(startX + i, startY) else Coordinate(startX, startY + i)
      val existingTile = board.squareAt(coord)?.occupyingTile
      val neededChar = word[i].uppercaseChar()
      if (existingTile != null) {
        if (existingTile.char.uppercaseChar() != neededChar) return null
      } else {
        val rackIdx = available.indexOfFirst { it.char.uppercaseChar() == neededChar }
        if (rackIdx == -1) return null
        placements[coord] = available.removeAt(rackIdx)
      }
    }

    if (placements.isEmpty()) return null

    val testBoard = board.copy(pendingPlacements = placements)
    val preview =
      evaluatePendingMove(
        board = testBoard,
        activeRelics = listOf(opponent.primaryRelic),
        comboMultiplier = 1.0f,
        turnTimeRemainingSec = 30,
      )
    return if (preview.isValidPosition && preview.allWordsValid) {
      AiCandidateMove(placements, preview, word)
    } else {
      null
    }
  }

  companion object {
    private val VOWELS = setOf('A', 'E', 'I', 'O', 'U')
  }
}
