package com.example.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Coordinate(val x: Int, val y: Int) {
  val isValid: Boolean
    get() = x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE

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
  NONE("", 1, 1, "Standard Washi Square"),
  DL("2L", 2, 1, "Double Letter Score"),
  TL("3L", 3, 1, "Triple Letter Score"),
  DW("2W", 1, 2, "Double Word Score"),
  TW("3W", 1, 3, "Triple Word Score"),
  CENTER_STAR("★", 1, 2, "Center Origami Star (2x Word)"),
}

enum class TileBonusEffect(val badge: String, val title: String, val description: String) {
  NONE("", "Standard Tile", "Classic letter tile"),
  GOLD_FOIL("+4", "Gold Leaf", "Adds +4 flat bonus points when scored"),
  CRANE_BLESSING("×1.5", "Crane Fold", "Boosts total word score by 1.5x"),
  SAKURA_SPARK("+10s", "Sakura Spark", "Restores +10s to the blitz turn timer"),
}

@Immutable
data class Tile(
  val id: String,
  val char: Char,
  val baseScore: Int,
  val bonusEffect: TileBonusEffect = TileBonusEffect.NONE,
  val isLockedOnBoard: Boolean = false,
)

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
data class RackState(
  val tiles: List<Tile>,
  val selectedTileId: String? = null,
  val draggingTileId: String? = null,
)

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
  CAMPAIGN("Campaign Masters", "Battle themed Origami AI scribes with rogue-lite relics"),
  PASS_AND_PLAY("Pass & Play Duel", "2-Player local tabletop showdown on one device"),
  DAILY_BLITZ("Daily Crane Blitz", "Pre-seeded board puzzle with 35s rapid turn clock"),
  ASYNC_DUEL("Cloud Saved Arena", "Persistent cloud-synced matches stored to your account"),
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
  val lastMoveSummary: String = "Tap or drag tiles onto the Center Star (★) to fold your first word!",
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
        name = "Apprentice Momo",
        title = "Blossom Folder",
        quote = "Swift 3-letter folds keep the paper crisp!",
        style = AiVocabularyStyle.AGILE_SHORT,
        targetScore = 120,
        rewardCoins = 45,
        primaryRelic = RogueRelic.VOWEL_LOTUS,
      ),
      AiOpponent(
        stage = 2,
        name = "Kitsune Scribe",
        title = "Trickster of Multipliers",
        quote = "Every Triple Letter square belongs to the fox.",
        style = AiVocabularyStyle.MULTIPLIER_HUNTER,
        targetScore = 165,
        rewardCoins = 75,
        primaryRelic = RogueRelic.EDGE_MASTER,
      ),
      AiOpponent(
        stage = 3,
        name = "Sensei Haiku",
        title = "Keeper of Five Syllables",
        quote = "True mastery unfolds in long, intersecting verses.",
        style = AiVocabularyStyle.POETIC_LONG,
        targetScore = 210,
        rewardCoins = 110,
        primaryRelic = RogueRelic.SHOGUN_SEAL,
      ),
      AiOpponent(
        stage = 4,
        name = "Grandmaster Tsuru",
        title = "Thousand-Crane Sovereign",
        quote = "Show me a word worthy of imperial gold leaf.",
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
        name = "Classic Cream Washi",
        rarity = "COMMON",
        description = "Hand-pressed ivory mulberry paper over warm cedar wood.",
        costCoins = 0,
        baseColorHex = 0xFFFFF6E5,
        borderColorHex = 0xFFD9B382,
        textColorHex = 0xFF2B1E16,
        accentGlowHex = 0xFFE6A145,
      ),
      CosmeticTileSkin(
        id = "sakura_foil",
        name = "Sakura Gold Leaf",
        rarity = "RARE",
        description = "Petal-pink lacquer inlaid with Kyoto metallic gold trim.",
        costCoins = 100,
        baseColorHex = 0xFFFFE6EE,
        borderColorHex = 0xFFE06D94,
        textColorHex = 0xFF4A152B,
        accentGlowHex = 0xFFFFB703,
      ),
      CosmeticTileSkin(
        id = "obsidian_shogun",
        name = "Obsidian Shogun",
        rarity = "EPIC",
        description = "Charcoal volcanic slate etched with vermilion cinnabar ink.",
        costCoins = 220,
        baseColorHex = 0xFF1F2430,
        borderColorHex = 0xFFFF5A36,
        textColorHex = 0xFFFFF4EC,
        accentGlowHex = 0xFFFF5A36,
      ),
      CosmeticTileSkin(
        id = "celestial_jade",
        name = "Mythic Celestial Jade",
        rarity = "MYTHIC",
        description = "Luminous imperial celadon jade with animated golden-cyan border.",
        costCoins = 360,
        baseColorHex = 0xFFE2FCEF,
        borderColorHex = 0xFF00B495,
        textColorHex = 0xFF073B32,
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
