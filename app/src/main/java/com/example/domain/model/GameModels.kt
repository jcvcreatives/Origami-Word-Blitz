package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Coordinate(val x: Int, val y: Int) {
  val isValid: Boolean
    get() = x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE

  fun isWithin(width: Int, height: Int): Boolean = x in 0 until width && y in 0 until height

  companion object {
    const val BOARD_SIZE = 15
    val CENTER = Coordinate(7, 7)
  }
}

enum class SquareMultiplier(
  val label: String,
  val letterFactor: Int,
  val wordFactor: Int,
  val description: String,
) {
  NONE("", 1, 1, "Standard Skeleton Slot"),
  DL("2L", 2, 1, "Double Letter Score"),
  TL("3L", 3, 1, "Triple Letter Score"),
  DW("2W", 1, 2, "Double Word Score"),
  TW("3W", 1, 3, "Triple Word Score"),
  CENTER_STAR("★", 1, 2, "Center Nexus Star (2x Word)"),
}

enum class TileBonusEffect(val badge: String, val title: String, val description: String) {
  NONE("", "Standard Tile", "Classic letter tile"),
  GOLD_FOIL("+5", "Gilded Edge", "Adds +5 flat bonus points when completing a word"),
  CRANE_BLESSING("×1.5", "Nexus Boost", "Boosts completed word score by 1.5x"),
  SAKURA_SPARK("+10", "Spark Core", "Adds +10 bonus points on intersection clears"),
}

@Immutable
data class Tile(
  val id: String,
  val char: Char,
  val baseScore: Int,
  val bonusEffect: TileBonusEffect = TileBonusEffect.NONE,
  val isLockedOnBoard: Boolean = false,
) {
  val letter: Char
    get() = char
}

/**
 * LexiFill: Crossword Skeleton core cell model.
 * Represents a single cell on the puzzle board:
 * - [fixedLetter]: Permanent pre-placed clue letter (null if void or unfilled blank target).
 * - [isBlankTarget]: True when this cell is an intentional blank slot needing a player tile.
 * - [placedTile]: The player's tile dropped into this blank target.
 * - [multiplier]: Scrabble-style score multiplier (NONE, DL, TL, DW, TW).
 */
@Immutable
data class BoardCell(
  val x: Int,
  val y: Int,
  val fixedLetter: Char? = null,
  val isBlankTarget: Boolean = false,
  val placedTile: Tile? = null,
  val multiplier: SquareMultiplier = SquareMultiplier.NONE,
  val isIntersection: Boolean = false,
  val isLockedCompleted: Boolean = false,
  val expectedLetter: Char? = null,
  val wordIds: List<String> = emptyList(),
) {
  val coordinate: Coordinate
    get() = Coordinate(x, y)

  val isPlayable: Boolean
    get() = fixedLetter != null || isBlankTarget

  val activeLetter: Char?
    get() = fixedLetter ?: placedTile?.char

  val isOpenBlank: Boolean
    get() = isBlankTarget && placedTile == null
}

enum class WordDirection(val shortCode: String, val label: String) {
  HORIZONTAL("H", "Across"),
  VERTICAL("V", "Down"),
}

/**
 * Represents an intersecting target word in a LexiFill: Crossword Skeleton level.
 */
@Immutable
data class TargetWord(
  val id: String,
  val word: String,
  val clue: String,
  val startX: Int,
  val startY: Int,
  val direction: WordDirection,
  val missingBlankIndices: List<Int>,
  val isCompleted: Boolean = false,
  val completedWordText: String? = null,
  val awardedScore: Int = 0,
) {
  fun coordinates(): List<Coordinate> =
    List(word.length) { index ->
      when (direction) {
        WordDirection.HORIZONTAL -> Coordinate(startX + index, startY)
        WordDirection.VERTICAL -> Coordinate(startX, startY + index)
      }
    }

  fun blankCoordinates(): List<Coordinate> =
    missingBlankIndices.map { index ->
      when (direction) {
        WordDirection.HORIZONTAL -> Coordinate(startX + index, startY)
        WordDirection.VERTICAL -> Coordinate(startX, startY + index)
      }
    }

  fun containsCoordinate(x: Int, y: Int): Boolean {
    return when (direction) {
      WordDirection.HORIZONTAL -> y == startY && x in startX until (startX + word.length)
      WordDirection.VERTICAL -> x == startX && y in startY until (startY + word.length)
    }
  }

  fun charIndexAt(x: Int, y: Int): Int {
    if (!containsCoordinate(x, y)) return -1
    return when (direction) {
      WordDirection.HORIZONTAL -> x - startX
      WordDirection.VERTICAL -> y - startY
    }
  }

  /**
   * Builds the current pattern string on the board (e.g., "P_ANET" or "PLANET").
   */
  fun currentPattern(cells: List<List<BoardCell>>): String {
    val sb = StringBuilder(word.length)
    for (coord in coordinates()) {
      val ch = cells.getOrNull(coord.y)?.getOrNull(coord.x)?.activeLetter
      sb.append(ch ?: '_')
    }
    return sb.toString()
  }

  /**
   * Display skeleton with spaced blanks (e.g., "P _ A N E T") for clue cards.
   */
  fun formattedSkeleton(cells: List<List<BoardCell>>): String {
    return coordinates()
      .map { coord -> cells.getOrNull(coord.y)?.getOrNull(coord.x)?.activeLetter ?: '_' }
      .joinToString(" ")
  }

  fun isFullyFilled(cells: List<List<BoardCell>>): Boolean {
    return coordinates().all { coord ->
      cells.getOrNull(coord.y)?.getOrNull(coord.x)?.activeLetter != null
    }
  }
}

/**
 * Compact, zero-network-quota puzzle definition with target words, board dimensions,
 * multiplier coordinates, and player letter inventory.
 */
@Immutable
data class LevelTemplate(
  val levelNumber: Int,
  val title: String,
  val tierBadge: String,
  val boardWidth: Int,
  val boardHeight: Int,
  val targetWords: List<TargetWord>,
  val multipliers: Map<Coordinate, SquareMultiplier> = emptyMap(),
  val decoyLetters: List<Char> = emptyList(),
  val compactCode: String = "",
)

/**
 * Live evaluation when hovering or dropping a tile onto a target blank cell (x, y).
 */
@Immutable
data class DropPreviewEvaluation(
  val targetCoord: Coordinate,
  val tileChar: Char,
  val isValidSlot: Boolean,
  val isDualAxisValid: Boolean,
  val horizontalPatternAfter: String? = null,
  val verticalPatternAfter: String? = null,
  val wouldCompleteWords: List<String> = emptyList(),
  val isIntersectionSlot: Boolean = false,
  val estimatedScoreGain: Int = 0,
  val invalidReason: String? = null,
)

@Immutable
data class ScorePopupEvent(
  val id: String,
  val coord: Coordinate,
  val headline: String,
  val subline: String,
  val isDualAxisClear: Boolean = false,
  val comboCount: Int = 1,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Immutable
data class LevelClearSummary(
  val levelNumber: Int,
  val levelTitle: String,
  val levelScore: Int,
  val wordsCompleted: Int,
  val maxCombo: Int,
  val accuracyBonus: Int,
  val coinsEarned: Int,
  val stars: Int,
)

@Immutable
data class RackState(
  val tiles: List<Tile>,
  val selectedTileId: String? = null,
  val draggingTileId: String? = null,
)

@Immutable
data class ConfettiExplosionEvent(
  val id: String,
  val wordLabel: String,
  val isDualIntersection: Boolean = false,
  val comboCount: Int = 1,
  val fragmentCount: Int = 56,
  val seed: Int = 1337,
  val createdAtMs: Long = System.currentTimeMillis(),
)

/**
 * Primary immutable UI StateFlow model for LexiFill: Crossword Skeleton.
 */
@Immutable
data class PuzzleState(
  val levelTemplate: LevelTemplate,
  val boardWidth: Int,
  val boardHeight: Int,
  val cells: List<List<BoardCell>>,
  val targetWords: List<TargetWord>,
  val rackState: RackState,
  val levelScore: Int = 0,
  val totalCampaignScore: Int = 0,
  val comboStreak: Int = 0,
  val comboMultiplier: Float = 1.0f,
  val movesMade: Int = 0,
  val mistakesMade: Int = 0,
  val hintsRemaining: Int = 3,
  val completedLevels: Set<Int> = emptySet(),
  val highestUnlockedLevel: Int = 1,
  val hoverCoord: Coordinate? = null,
  val hoverPreview: DropPreviewEvaluation? = null,
  val highlightedHintCoord: Coordinate? = null,
  val scorePopups: List<ScorePopupEvent> = emptyList(),
  val particleBursts: List<ParticleBurst> = emptyList(),
  val confettiExplosions: List<ConfettiExplosionEvent> = emptyList(),
  val cellFlashTimestamps: Map<Coordinate, Long> = emptyMap(),
  val tilePlacementTimestamps: Map<Coordinate, Long> = emptyMap(),
  val rejectedSlotTimestamps: Map<Coordinate, Long> = emptyMap(),
  val boardShakeIntensity: Float = 0f,
  val statusMessage: String = "Drag letter tiles into open blanks to complete intersecting words!",
  val isStatusError: Boolean = false,
  val isLevelCleared: Boolean = false,
  val levelClearSummary: LevelClearSummary? = null,
  val autoAdvanceCountdownSec: Int? = null,
  val equippedSkin: CosmeticTileSkin = GameCatalog.COSMETIC_SKINS.first(),
  val unlockedSkinIds: Set<String> = setOf("washi_classic"),
  val lexiCoins: Int = 160,
  val strictTrieValidation: Boolean = true,
) {
  fun cellAt(x: Int, y: Int): BoardCell? {
    if (y !in 0 until boardHeight || x !in 0 until boardWidth) return null
    return cells[y][x]
  }

  val totalBlanksCount: Int
    get() = cells.sumOf { row -> row.count { it.isBlankTarget } }

  val filledBlanksCount: Int
    get() = cells.sumOf { row -> row.count { it.isBlankTarget && it.placedTile != null } }

  val completedWordsCount: Int
    get() = targetWords.count { it.isCompleted }
}

// --- Legacy Compatibility Models for Existing Tests ---

@Immutable
data class BoardSquare(
  val coordinate: Coordinate,
  val multiplier: SquareMultiplier,
  val occupyingTile: Tile? = null,
  val isMultiplierConsumed: Boolean = false,
)

enum class RogueRelic(
  val id: String,
  val title: String,
  val subtitle: String,
  val badge: String,
) {
  VOWEL_LOTUS(
    id = "vowel_lotus",
    title = "Vowel Lotus",
    subtitle = "All vowels (A, E, I, O, U) gain +2 base points",
    badge = "+2 Vowels",
  ),
  SHOGUN_SEAL(
    id = "shogun_seal",
    title = "Shogun's Seal",
    subtitle = "Words of 5+ letters receive an extra 1.5x multiplier",
    badge = "5L+ ×1.5",
  ),
  EDGE_MASTER(
    id = "edge_master",
    title = "Perimeter Fold",
    subtitle = "Outer-rim board squares grant +3 bonus per tile",
    badge = "Rim +3",
  ),
  QUICK_FOLD(
    id = "quick_fold",
    title = "Swift Crane",
    subtitle = "Submitting with >25s left on the clock adds +12 Blitz pts",
    badge = "Speed +12",
  ),
}

enum class AiVocabularyStyle {
  AGILE_SHORT,
  MULTIPLIER_HUNTER,
  POETIC_LONG,
  GRANDMASTER_OPTIMAL,
}

@Immutable
data class AiOpponent(
  val stage: Int,
  val name: String,
  val title: String,
  val quote: String,
  val style: AiVocabularyStyle,
  val targetScore: Int,
  val rewardCoins: Int,
  val primaryRelic: RogueRelic,
)

@Immutable
data class CosmeticTileSkin(
  val id: String,
  val name: String,
  val rarity: String,
  val description: String,
  val costCoins: Int,
  val baseColorHex: Long,
  val borderColorHex: Long,
  val textColorHex: Long,
  val accentGlowHex: Long,
)

@Immutable
data class WordBreakdown(
  val word: String,
  val basePoints: Int,
  val wordMultiplier: Int,
  val relicBonusPoints: Int,
  val totalPoints: Int,
  val coordinates: List<Coordinate>,
  val triggeredMultipliers: List<SquareMultiplier>,
)

@Immutable
data class MovePreview(
  val isValidPosition: Boolean,
  val placementError: String? = null,
  val allWordsValid: Boolean = false,
  val invalidWords: List<String> = emptyList(),
  val formedWords: List<WordBreakdown> = emptyList(),
  val comboBonusPoints: Int = 0,
  val bingoBonusPoints: Int = 0,
  val totalTurnScore: Int = 0,
  val maxSquareMultiplier: SquareMultiplier = SquareMultiplier.NONE,
)

enum class BurstColorType {
  STANDARD,
  DOUBLE_WORD,
  TRIPLE_WORD,
  COMBO_STREAK,
  AI_MOVE,
}

@Immutable
data class FloatingScoreBurst(
  val id: String,
  val headline: String,
  val subline: String,
  val boardCoord: Coordinate? = null,
  val colorType: BurstColorType = BurstColorType.STANDARD,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Immutable
data class ParticleBurst(
  val id: String,
  val boardCoord: Coordinate,
  val colorHex: Long,
  val particleCount: Int = 18,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Immutable
data class LexiBoardState(
  val squares: List<List<BoardSquare>>,
  val pendingPlacements: Map<Coordinate, Tile> = emptyMap(),
  val movePreview: MovePreview? = null,
  val lastPlayedCoords: Set<Coordinate> = emptySet(),
  val hintCoords: Set<Coordinate> = emptySet(),
) {
  fun squareAt(coord: Coordinate): BoardSquare? {
    if (!coord.isValid) return null
    return squares[coord.y][coord.x]
  }

  fun effectiveTileAt(coord: Coordinate): Tile? {
    return pendingPlacements[coord] ?: squareAt(coord)?.occupyingTile
  }

  fun hasAnyLockedTiles(): Boolean {
    return squares.any { row -> row.any { it.occupyingTile != null } }
  }
}

@Immutable
data class ScoreState(
  val playerScore: Int = 0,
  val opponentScore: Int = 0,
  val comboStreak: Int = 0,
  val comboMultiplier: Float = 1.0f,
  val turnTimeRemainingSec: Int = 45,
  val maxTurnTimeSec: Int = 45,
  val tileBagRemaining: Int = 86,
)

enum class GameMode(val displayName: String, val subtitle: String) {
  CAMPAIGN("Skeleton Campaign", "Progressive Kriss-Kross crossword skeletons"),
  PASS_AND_PLAY("Co-Op Skeleton", "Pass-and-play collaborative crossword fill"),
  DAILY_BLITZ("Daily Skeleton", "Featured high-multiplier intersection grid"),
  ASYNC_DUEL("Cloud Profile", "Optional cloud sync for career stats & skins"),
}

enum class TurnStatus {
  PLAYER_ONE_TURN,
  PLAYER_TWO_OR_AI_TURN,
  PASS_DEVICE_INTERSTITIAL,
  MATCH_OVER,
}

@Immutable
data class GameState(
  val mode: GameMode = GameMode.CAMPAIGN,
  val boardState: LexiBoardState,
  val rackState: RackState,
  val playerTwoRack: List<Tile> = emptyList(),
  val scoreState: ScoreState = ScoreState(),
  val turnStatus: TurnStatus = TurnStatus.PLAYER_ONE_TURN,
  val turnNumber: Int = 1,
  val activeRelics: List<RogueRelic> = listOf(RogueRelic.VOWEL_LOTUS, RogueRelic.QUICK_FOLD),
  val opponent: AiOpponent = GameCatalog.CAMPAIGN_OPPONENTS.first(),
  val equippedSkin: CosmeticTileSkin = GameCatalog.COSMETIC_SKINS.first(),
  val unlockedSkinIds: Set<String> = setOf("washi_classic"),
  val paperCoins: Int = 150,
  val campaignStageUnlocked: Int = 1,
  val floatingBursts: List<FloatingScoreBurst> = emptyList(),
  val particleBursts: List<ParticleBurst> = emptyList(),
  val boardShakeIntensity: Float = 0f,
  val lastMoveSummary: String = "",
  val hintSuggestion: String? = null,
  val bestWordPlayed: String = "",
  val highestSingleTurnScore: Int = 0,
  val activeMatchId: String? = null,
  val consecutivePasses: Int = 0,
)

object GameCatalog {
  val CAMPAIGN_OPPONENTS =
    listOf(
      AiOpponent(
        stage = 1,
        name = "Architect Lyra",
        title = "Skeleton Draftsman",
        quote = "Every intersection anchors two worlds at once.",
        style = AiVocabularyStyle.AGILE_SHORT,
        targetScore = 120,
        rewardCoins = 45,
        primaryRelic = RogueRelic.VOWEL_LOTUS,
      ),
      AiOpponent(
        stage = 2,
        name = "Curator Vance",
        title = "Keeper of Multipliers",
        quote = "Drop high-value letters onto Triple Word intersections.",
        style = AiVocabularyStyle.MULTIPLIER_HUNTER,
        targetScore = 165,
        rewardCoins = 75,
        primaryRelic = RogueRelic.EDGE_MASTER,
      ),
      AiOpponent(
        stage = 3,
        name = "Oracle Kael",
        title = "Weaver of Crossings",
        quote = "One tile, two completed words—true geometric harmony.",
        style = AiVocabularyStyle.POETIC_LONG,
        targetScore = 210,
        rewardCoins = 110,
        primaryRelic = RogueRelic.SHOGUN_SEAL,
      ),
      AiOpponent(
        stage = 4,
        name = "Grandmaster Lexis",
        title = "Sovereign of the Trie",
        quote = "Solve the entire skeleton without a single misplaced letter.",
        style = AiVocabularyStyle.GRANDMASTER_OPTIMAL,
        targetScore = 260,
        rewardCoins = 160,
        primaryRelic = RogueRelic.QUICK_FOLD,
      ),
    )

  val COSMETIC_SKINS =
    listOf(
      CosmeticTileSkin(
        id = "washi_classic",
        name = "Ivory Bone & Brass",
        rarity = "COMMON",
        description = "Tactile satin-ivory letter tiles with warm engraved brass points.",
        costCoins = 0,
        baseColorHex = 0xFFFFF8EC,
        borderColorHex = 0xFFD9B382,
        textColorHex = 0xFF1E293B,
        accentGlowHex = 0xFF38BDF8,
      ),
      CosmeticTileSkin(
        id = "sakura_foil",
        name = "Amber Blueprint",
        rarity = "RARE",
        description = "Gilded drafting vellum trimmed with radiant solar gold.",
        costCoins = 100,
        baseColorHex = 0xFFFFF3D6,
        borderColorHex = 0xFFF59E0B,
        textColorHex = 0xFF451A03,
        accentGlowHex = 0xFFFFB703,
      ),
      CosmeticTileSkin(
        id = "obsidian_shogun",
        name = "Obsidian Matrix",
        rarity = "EPIC",
        description = "Deep volcanic slate tiles etched with neon crimson circuitry.",
        costCoins = 220,
        baseColorHex = 0xFF1E2436,
        borderColorHex = 0xFFFF5A36,
        textColorHex = 0xFFFFF4EC,
        accentGlowHex = 0xFFFF5A36,
      ),
      CosmeticTileSkin(
        id = "celestial_jade",
        name = "Mythic Emerald Rune",
        rarity = "MYTHIC",
        description = "Luminous emerald crystal with cyan magnetic resonance borders.",
        costCoins = 340,
        baseColorHex = 0xFFE2FCEF,
        borderColorHex = 0xFF10B981,
        textColorHex = 0xFF064E3B,
        accentGlowHex = 0xFF00F5D4,
      ),
    )

  fun createStandardBoard(): List<List<BoardSquare>> {
    val twCoords =
      setOf(
        Coordinate(0, 0),
        Coordinate(7, 0),
        Coordinate(14, 0),
        Coordinate(0, 7),
        Coordinate(14, 7),
        Coordinate(0, 14),
        Coordinate(7, 14),
        Coordinate(14, 14),
      )
    val dwCoords =
      setOf(
        Coordinate(1, 1),
        Coordinate(2, 2),
        Coordinate(3, 3),
        Coordinate(4, 4),
        Coordinate(13, 1),
        Coordinate(12, 2),
        Coordinate(11, 3),
        Coordinate(10, 4),
        Coordinate(1, 13),
        Coordinate(2, 12),
        Coordinate(3, 11),
        Coordinate(4, 10),
        Coordinate(13, 13),
        Coordinate(12, 12),
        Coordinate(11, 11),
        Coordinate(10, 10),
      )
    val tlCoords =
      setOf(
        Coordinate(5, 1),
        Coordinate(9, 1),
        Coordinate(1, 5),
        Coordinate(5, 5),
        Coordinate(9, 5),
        Coordinate(13, 5),
        Coordinate(1, 9),
        Coordinate(5, 9),
        Coordinate(9, 9),
        Coordinate(13, 9),
        Coordinate(5, 13),
        Coordinate(9, 13),
      )
    val dlCoords =
      setOf(
        Coordinate(3, 0),
        Coordinate(11, 0),
        Coordinate(6, 2),
        Coordinate(8, 2),
        Coordinate(0, 3),
        Coordinate(7, 3),
        Coordinate(14, 3),
        Coordinate(2, 6),
        Coordinate(6, 6),
        Coordinate(8, 6),
        Coordinate(12, 6),
        Coordinate(3, 7),
        Coordinate(11, 7),
        Coordinate(2, 8),
        Coordinate(6, 8),
        Coordinate(8, 8),
        Coordinate(12, 8),
        Coordinate(0, 11),
        Coordinate(7, 11),
        Coordinate(14, 11),
        Coordinate(6, 12),
        Coordinate(8, 12),
        Coordinate(3, 14),
        Coordinate(11, 14),
      )

    return List(Coordinate.BOARD_SIZE) { y ->
      List(Coordinate.BOARD_SIZE) { x ->
        val coord = Coordinate(x, y)
        val mult =
          when {
            coord == Coordinate.CENTER -> SquareMultiplier.CENTER_STAR
            coord in twCoords -> SquareMultiplier.TW
            coord in dwCoords -> SquareMultiplier.DW
            coord in tlCoords -> SquareMultiplier.TL
            coord in dlCoords -> SquareMultiplier.DL
            else -> SquareMultiplier.NONE
          }
        BoardSquare(coordinate = coord, multiplier = mult)
      }
    }
  }

  fun letterBaseScore(char: Char): Int =
    when (char.uppercaseChar()) {
      'A', 'E', 'I', 'O', 'U', 'L', 'N', 'S', 'T', 'R' -> 1
      'D', 'G' -> 2
      'B', 'C', 'M', 'P' -> 3
      'F', 'H', 'V', 'W', 'Y' -> 4
      'K' -> 5
      'J', 'X' -> 8
      'Q', 'Z' -> 10
      else -> 1
    }
}
