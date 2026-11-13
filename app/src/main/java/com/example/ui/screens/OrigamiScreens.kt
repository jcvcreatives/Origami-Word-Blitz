package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.engine.LexiFillEngine
import com.example.domain.model.GameCatalog
import com.example.domain.model.LevelTemplate
import com.example.domain.model.PuzzleState
import com.example.domain.model.TargetWord
import com.example.domain.model.WordDirection
import com.example.ui.components.LevelClearFanfareOverlay
import com.example.ui.components.LexiFillBoardCanvas
import com.example.ui.components.LexiFillDraggableRack
import com.example.ui.components.LiveIntersectionPreviewBanner
import com.example.ui.components.SkeletonBoardGeometry
import com.example.ui.feedback.AudioPlayer
import com.example.ui.theme.CeladonJade
import com.example.ui.theme.CompletedWordBg
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoElevated
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.IndigoSurface
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import com.example.ui.theme.WashiMuted
import com.example.ui.theme.paperGrainOverlay
import com.example.ui.viewmodel.GameViewModel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class LexiFillDestination(val label: String) {
  PUZZLE_GRID("Skeleton Grid"),
  PUZZLE_VAULT("Levels"),
  CUSTOM_STUDIO("Builder & Skins"),
  TRIE_EXPLORER("Trie Solver"),
}

@Composable
fun LexiFillMainScreen(
  viewModel: GameViewModel,
  userEmail: String? = null,
  isGuestUser: Boolean = false,
  onSignOut: (() -> Unit)? = null,
) {
  val puzzleState by viewModel.puzzleState.collectAsStateWithLifecycle()
  val haptics = LocalHapticFeedback.current
  var currentTab by rememberSaveable { mutableStateOf(LexiFillDestination.PUZZLE_GRID) }

  if (currentTab != LexiFillDestination.PUZZLE_GRID) {
    BackHandler { currentTab = LexiFillDestination.PUZZLE_GRID }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().background(IndigoNight),
    containerColor = IndigoNight,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = {
      NavigationBar(
        containerColor = IndigoSurface,
        contentColor = WashiCream,
      ) {
        NavigationBarItem(
          selected = currentTab == LexiFillDestination.PUZZLE_GRID,
          onClick = { currentTab = LexiFillDestination.PUZZLE_GRID },
          icon = { Icon(Icons.Filled.GridOn, contentDescription = "Skeleton Grid") },
          label = { Text("Skeleton") },
          modifier = Modifier.testTag("nav_tab_skeleton"),
        )
        NavigationBarItem(
          selected = currentTab == LexiFillDestination.PUZZLE_VAULT,
          onClick = { currentTab = LexiFillDestination.PUZZLE_VAULT },
          icon = { Icon(Icons.Filled.Extension, contentDescription = "Puzzle Vault") },
          label = { Text("Levels") },
          modifier = Modifier.testTag("nav_tab_levels"),
        )
        NavigationBarItem(
          selected = currentTab == LexiFillDestination.CUSTOM_STUDIO,
          onClick = { currentTab = LexiFillDestination.CUSTOM_STUDIO },
          icon = { Icon(Icons.Filled.Build, contentDescription = "Builder & Skins") },
          label = { Text("Studio") },
          modifier = Modifier.testTag("nav_tab_studio"),
        )
        NavigationBarItem(
          selected = currentTab == LexiFillDestination.TRIE_EXPLORER,
          onClick = { currentTab = LexiFillDestination.TRIE_EXPLORER },
          icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Trie Solver") },
          label = { Text("Trie") },
          modifier = Modifier.testTag("nav_tab_trie"),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .background(IndigoNight)
          .paperGrainOverlay(alpha = 0.055f, fiberCount = 92, speckleCount = 148, seed = 101),
      contentAlignment = Alignment.TopCenter,
    ) {
      when (currentTab) {
        LexiFillDestination.PUZZLE_GRID -> {
          SkeletonPuzzleArena(
            puzzleState = puzzleState,
            userEmail = userEmail,
            isGuestUser = isGuestUser,
            onSignOut = onSignOut,
            onSelectClueWord = { wordId -> viewModel.selectClueWord(wordId, haptics) },
            onBoardCellTapped = { x, y -> viewModel.onBoardCellTapped(x, y, haptics) },
            onRackTileTapped = { tile -> viewModel.onRackTileTapped(tile, haptics) },
            onTileDragStart = { tile -> viewModel.onTileDragStart(tile, haptics) },
            onTileDragHover = { tile, coord -> viewModel.onTileDragHover(tile, coord, haptics) },
            onTileDropOnCoord = { tileId, coord -> viewModel.onTileDropOnCoord(tileId, coord, haptics) },
            onTileDragCancel = { viewModel.onTileDragCancel() },
            onShuffleRack = { viewModel.shuffleRack(haptics) },
            onTriggerHint = { viewModel.triggerSmartHint(haptics) },
            onRecallUnlocked = { viewModel.recallAllUnlockedTiles(haptics) },
            onReplayLevel = { viewModel.replayCurrentLevel() },
            onNextLevel = { viewModel.advanceToNextLevel() },
            onToggleSoundMute = { viewModel.toggleSoundMute(haptics) },
            onShakeConsumed = { viewModel.clearShake() },
          )
        }
        LexiFillDestination.PUZZLE_VAULT -> {
          PuzzleVaultScreen(
            puzzleState = puzzleState,
            onSelectLevel = { levelNum ->
              viewModel.loadLevel(levelNum)
              currentTab = LexiFillDestination.PUZZLE_GRID
            },
          )
        }
        LexiFillDestination.CUSTOM_STUDIO -> {
          CustomStudioAndSkinsScreen(
            puzzleState = puzzleState,
            onLoadCompactCode = { code ->
              viewModel.loadCustomSkeleton(code)
              currentTab = LexiFillDestination.PUZZLE_GRID
            },
            onUnlockOrEquipSkin = { skin -> viewModel.unlockOrEquipSkin(skin, haptics) },
            onAuditionSoundEffect = { effect -> viewModel.audioPlayer.play(effect) },
          )
        }
        LexiFillDestination.TRIE_EXPLORER -> {
          TriePatternSolverScreen(
            viewModel = viewModel,
            puzzleState = puzzleState,
            userEmail = userEmail,
            isGuestUser = isGuestUser,
            onSignOut = onSignOut,
          )
        }
      }
    }
  }
}

@Composable
private fun SkeletonPuzzleArena(
  puzzleState: PuzzleState,
  userEmail: String?,
  isGuestUser: Boolean,
  onSignOut: (() -> Unit)?,
  onSelectClueWord: (String) -> Unit,
  onBoardCellTapped: (Int, Int) -> Unit,
  onRackTileTapped: (com.example.domain.model.Tile) -> Unit,
  onTileDragStart: (com.example.domain.model.Tile) -> Unit,
  onTileDragHover: (com.example.domain.model.Tile, com.example.domain.model.Coordinate?) -> Unit,
  onTileDropOnCoord: (String, com.example.domain.model.Coordinate?) -> Boolean,
  onTileDragCancel: () -> Unit,
  onShuffleRack: () -> Unit,
  onTriggerHint: () -> Unit,
  onRecallUnlocked: () -> Unit,
  onReplayLevel: () -> Unit,
  onNextLevel: () -> Unit,
  onToggleSoundMute: () -> Unit,
  onShakeConsumed: () -> Unit,
) {
  var boardGeometry by remember { mutableStateOf(SkeletonBoardGeometry()) }
  val scrollState = rememberScrollState()
  val hasUnlockedPlacedTiles =
    remember(puzzleState.cells) {
      puzzleState.cells.any { row ->
        row.any { it.isBlankTarget && it.placedTile != null && !it.isLockedCompleted }
      }
    }

  val isDraggingAnyTile = puzzleState.rackState.draggingTileId != null

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val maxBoardHeight = (maxHeight * 0.43f).coerceIn(210.dp, 420.dp)
    val compactSpacing = if (maxHeight < 700.dp) 6.dp else 8.dp

    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 600.dp)
          .align(Alignment.TopCenter)
          .verticalScroll(scrollState, enabled = !isDraggingAnyTile)
          .padding(horizontal = 12.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(compactSpacing),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // 1. Top HUD: Level Title, Animated Score, Combo Streak Badge, Account Pill, Progress Bar
      LexiFillTopHud(
        puzzleState = puzzleState,
        userEmail = userEmail,
        isGuestUser = isGuestUser,
        onSignOut = onSignOut,
        onToggleSoundMute = onToggleSoundMute,
        onResetLevel = onReplayLevel,
        onSkipToNextLevel = onNextLevel,
      )

      // 2. Interactive Intersecting Clues Strip (Tap any clue to highlight its corridor on the board)
      IntersectingCluesStrip(
        targetWords = puzzleState.targetWords,
        focusedWordId = puzzleState.focusedWordId,
        cells = puzzleState.cells,
        onSelectClueWord = onSelectClueWord,
      )

      // 3. 100% Compose Canvas Crossword Skeleton Grid (adaptively sized to fit single screen)
      LexiFillBoardCanvas(
        puzzleState = puzzleState,
        onGeometryUpdated = { boardGeometry = it },
        onBlankSlotTapped = onBoardCellTapped,
        onShakeConsumed = onShakeConsumed,
        modifier = Modifier.heightIn(max = maxBoardHeight),
      )

      // 4. Live Dual-Axis Intersection & Active Clue Spotlight Banner
      LiveIntersectionPreviewBanner(puzzleState = puzzleState)

      // 5. Spring-Physics Draggable Letter Rack with Multiplier Legend & Recessed Tray Well
      LexiFillDraggableRack(
        tiles = puzzleState.rackState.tiles,
        selectedTileId = puzzleState.rackState.selectedTileId,
        equippedSkin = puzzleState.equippedSkin,
        hintsRemaining = puzzleState.hintsRemaining,
        hasUnlockedPlacedTiles = hasUnlockedPlacedTiles,
        onTileTapped = onRackTileTapped,
        onTileDragStart = onTileDragStart,
        onTileDragMove = { tile, rootPoint ->
          val (magneticSlot, pullVector) = boardGeometry.resolveMagneticPull(rootPoint, puzzleState)
          onTileDragHover(tile, magneticSlot)
          pullVector
        },
        onTileDragDrop = { tile, rootPoint ->
          val magneticSlot = boardGeometry.resolveMagneticSlot(rootPoint, puzzleState)
          onTileDropOnCoord(tile.id, magneticSlot)
        },
        onTileDragCancel = onTileDragCancel,
        onShuffleRack = onShuffleRack,
        onTriggerHint = onTriggerHint,
        onRecallUnlocked = onRecallUnlocked,
      )
    }

    // 6. Celebratory Level Clear Fanfare Modal with Automatic Next-Puzzle Transition
    LevelClearFanfareOverlay(
      visible = puzzleState.isLevelCleared,
      summary = puzzleState.levelClearSummary,
      countdownSec = puzzleState.autoAdvanceCountdownSec,
      onNextLevelNow = onNextLevel,
      onReplayLevel = onReplayLevel,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@Composable
private fun LexiFillTopHud(
  puzzleState: PuzzleState,
  userEmail: String?,
  isGuestUser: Boolean,
  onSignOut: (() -> Unit)?,
  onToggleSoundMute: () -> Unit,
  onResetLevel: () -> Unit,
  onSkipToNextLevel: () -> Unit,
) {
  val animatedScore by
    animateIntAsState(
      targetValue = puzzleState.levelScore,
      animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
      label = "hud_rolling_score",
    )

  val completionFraction =
    if (puzzleState.totalBlanksCount > 0) {
      (puzzleState.filledBlanksCount.toFloat() / puzzleState.totalBlanksCount.toFloat()).coerceIn(
        0f,
        1f,
      )
    } else 0f

  val animatedProgress by
    animateFloatAsState(
      targetValue = completionFraction,
      animationSpec =
        spring(
          dampingRatio = Spring.DampingRatioLowBouncy,
          stiffness = Spring.StiffnessMediumLow,
        ),
      label = "hud_completion_progress",
    )

  Surface(
    shape = RoundedCornerShape(18.dp),
    color = IndigoSurface,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, IndigoElevated, RoundedCornerShape(18.dp))
        .paperGrainOverlay(alpha = 0.046f, fiberCount = 36, speckleCount = 54, seed = 202)
        .testTag("lexifill_top_hud"),
  ) {
    Column {
      Row(
        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(6.dp))
                  .background(SkyCyan.copy(alpha = 0.18f))
                  .border(1.dp, SkyCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                  .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = "LVL ${puzzleState.levelTemplate.levelNumber}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = SkyCyan,
              )
            }
            Text(
              text = puzzleState.levelTemplate.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              color = WashiCream,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text =
                "Words ${puzzleState.completedWordsCount}/${puzzleState.targetWords.size} · Blanks ${puzzleState.filledBlanksCount}/${puzzleState.totalBlanksCount}",
              style = MaterialTheme.typography.bodySmall,
              color = WashiMuted,
            )
            Box(
              modifier =
                Modifier.clip(RoundedCornerShape(5.dp))
                  .background(KyotoGold.copy(alpha = 0.14f))
                  .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
              Text(
                text = "🪙 ${puzzleState.lexiCoins}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = KyotoGold,
              )
            }
            if (onSignOut != null) {
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(5.dp))
                    .background(
                      if (isGuestUser) IndigoCard
                      else CeladonJade.copy(alpha = 0.14f)
                    )
                    .border(
                      0.8.dp,
                      if (isGuestUser) IndigoElevated else CeladonJade.copy(alpha = 0.45f),
                      RoundedCornerShape(5.dp),
                    )
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
                    .testTag("hud_account_badge")
              ) {
                val shortLabel =
                  userEmail
                    ?.substringBefore(" (")
                    ?.substringBefore("@")
                    ?.take(12)
                    ?.ifBlank { "Account" } ?: "Account"
                Text(
                  text = if (isGuestUser) "👤 Guest · Account" else "🟢 $shortLabel",
                  fontSize = 9.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (isGuestUser) WashiMuted else CeladonJade,
                )
              }
            }
          }
        }

        // Reactive Combo Streak HUD Badge with Particle Burst & Bounce on 2x/3x/4x
        ComboStreakHudBadge(
          comboStreak = puzzleState.comboStreak,
          comboMultiplier = puzzleState.comboMultiplier,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Rolling Score Counter Pill
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = "$animatedScore PTS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = KyotoGold,
          )
          Text(
            text = "Career ${puzzleState.totalCampaignScore}",
            style = MaterialTheme.typography.labelSmall,
            color = WashiMuted,
          )
        }

        // Audio Mute/Unmute, Reset & Next puzzle icon buttons (48dp accessibility targets)
        IconButton(
          onClick = onToggleSoundMute,
          modifier = Modifier.size(48.dp).testTag("btn_toggle_sound"),
        ) {
          Icon(
            imageVector =
              if (puzzleState.isSoundMuted) Icons.AutoMirrored.Filled.VolumeOff
              else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription =
              if (puzzleState.isSoundMuted) "Unmute sound effects" else "Mute sound effects",
            tint = if (puzzleState.isSoundMuted) WashiMuted else CeladonJade,
          )
        }
        IconButton(
          onClick = onResetLevel,
          modifier = Modifier.size(48.dp).testTag("btn_reset_grid"),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Reset current puzzle grid",
            tint = WashiMuted,
          )
        }
        IconButton(
          onClick = onSkipToNextLevel,
          modifier = Modifier.size(48.dp).testTag("btn_skip_level"),
        ) {
          Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = "Skip to next puzzle level",
            tint = SkyCyan,
          )
        }
      }

      // Smooth Animated Level Completion Progress Bar at bottom of HUD card
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(4.dp)
            .background(IndigoNight.copy(alpha = 0.85f))
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth(animatedProgress)
              .fillMaxHeight()
              .background(
                Brush.horizontalGradient(
                  listOf(SkyCyan, CeladonJade, KyotoGold),
                )
              )
        )
      }
    }
  }
}

@Composable
private fun IntersectingCluesStrip(
  targetWords: List<TargetWord>,
  focusedWordId: String?,
  cells: List<List<com.example.domain.model.BoardCell>>,
  onSelectClueWord: (String) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .testTag("intersecting_clues_strip"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (tw in targetWords) {
      val isFocused = tw.id == focusedWordId
      val bg =
        when {
          tw.isCompleted -> CompletedWordBg
          isFocused -> Color(0xFFDFECF6)
          else -> IndigoSurface
        }
      val border =
        when {
          tw.isCompleted -> CeladonJade
          isFocused -> SkyCyan
          else -> IndigoElevated
        }
      val dirTag = if (tw.direction == WordDirection.HORIZONTAL) "ACROSS →" else "DOWN ↓"

      Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier =
          Modifier.border(
              width = if (isFocused || tw.isCompleted) 1.6.dp else 1.dp,
              color = border,
              shape = RoundedCornerShape(12.dp),
            )
            .paperGrainOverlay(
              alpha = 0.042f,
              fiberCount = 14,
              speckleCount = 22,
              seed = 303 + tw.id.hashCode(),
            )
            .clickable { onSelectClueWord(tw.id) }
            .testTag("clue_chip_${tw.id}"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (tw.isCompleted) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = "Completed word",
              tint = CeladonJade,
              modifier = Modifier.size(16.dp),
            )
          }
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = dirTag,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color =
                  when {
                    tw.isCompleted -> CeladonJade
                    isFocused -> KyotoGold
                    else -> SkyCyan
                  },
              )
              Text(
                text = tw.formattedSkeleton(cells),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = WashiCream,
              )
              if (tw.isCompleted && tw.awardedScore > 0) {
                Text(
                  text = "+${tw.awardedScore}",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = KyotoGold,
                )
              }
            }
            Text(
              text = tw.clue,
              fontSize = 10.sp,
              color = if (isFocused) WashiCream.copy(alpha = 0.9f) else WashiMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

/**
 * Animated HUD Combo Streak Badge with spring icon-bounce and radial Canvas particle burst
 * when reaching 2x, 3x, or 4x combo streaks.
 */
@Composable
fun ComboStreakHudBadge(
  comboStreak: Int,
  comboMultiplier: Float,
  modifier: Modifier = Modifier,
) {
  val isHighCombo = comboStreak >= 2 || comboMultiplier >= 2.0f
  val isSuperCombo = comboStreak >= 3 || comboMultiplier >= 3.0f
  val isMaxCombo = comboStreak >= 4 || comboMultiplier >= 4.0f

  val badgeScale = remember { Animatable(1f) }
  val iconBounceY = remember { Animatable(0f) }
  val iconRotation = remember { Animatable(0f) }
  val burstProgress = remember { Animatable(0f) }
  var lastAnimatedStreak by remember { mutableIntStateOf(comboStreak) }

  LaunchedEffect(comboStreak, comboMultiplier) {
    if (comboStreak > lastAnimatedStreak && isHighCombo) {
      val peakScale = if (isMaxCombo) 1.44f else if (isSuperCombo) 1.32f else 1.22f
      launch {
        badgeScale.snapTo(1.06f)
        badgeScale.animateTo(
          targetValue = peakScale,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioHighBouncy,
              stiffness = Spring.StiffnessMedium,
            ),
        )
        badgeScale.animateTo(
          targetValue = 1f,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessLow,
            ),
        )
      }
      launch {
        iconBounceY.snapTo(0f)
        iconBounceY.animateTo(
          targetValue = 0f,
          animationSpec =
            keyframes {
              durationMillis = 680
              (-11f) at 110 using FastOutSlowInEasing
              3f at 250 using FastOutSlowInEasing
              (-7f) at 390 using FastOutSlowInEasing
              0f at 680 using FastOutSlowInEasing
            },
        )
      }
      launch {
        iconRotation.snapTo(0f)
        iconRotation.animateTo(
          targetValue = 0f,
          animationSpec =
            keyframes {
              durationMillis = 640
              (-22f) at 90
              22f at 210
              (-14f) at 340
              10f at 470
              0f at 640
            },
        )
      }
      launch {
        burstProgress.snapTo(0.01f)
        burstProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing),
        )
        burstProgress.snapTo(0f)
      }
    }
    lastAnimatedStreak = comboStreak
  }

  val infinite = rememberInfiniteTransition(label = "combo_hud_infinite")
  val orbitPhase by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "combo_orbit",
    )

  Box(
    modifier = modifier.testTag("hud_combo_streak_badge"),
    contentAlignment = Alignment.Center,
  ) {
    if (burstProgress.value > 0f || isSuperCombo) {
      Canvas(modifier = Modifier.size(64.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = size.minDimension * 0.56f
        val bp = burstProgress.value
        if (bp > 0f && bp < 1f) {
          val count = if (isMaxCombo) 18 else 12
          for (i in 0 until count) {
            val angle = (2.0 * PI * i) / count
            val dist = maxRadius * bp * (0.65f + (i % 3) * 0.2f)
            val px = center.x + (cos(angle) * dist).toFloat()
            val py = center.y + (sin(angle) * dist).toFloat()
            val col =
              when (i % 3) {
                0 -> KyotoGold
                1 -> VermilionRed
                else -> CeladonJade
              }
            drawCircle(
              color = col.copy(alpha = (1f - bp).coerceIn(0f, 1f)),
              radius = (3.5f * (1f - bp * 0.5f)).dp.toPx(),
              center = Offset(px, py),
            )
          }
        }
        if (isSuperCombo) {
          for (i in 0 until 4) {
            val angle = orbitPhase + (i * PI / 2.0).toFloat()
            val rx = size.width * 0.44f
            val ry = size.height * 0.34f
            drawCircle(
              color = if (i % 2 == 0) KyotoGold else VermilionRed,
              radius = 2.2.dp.toPx(),
              center = Offset(center.x + cos(angle) * rx, center.y + sin(angle) * ry),
            )
          }
        }
      }
    }

    val pillBg =
      when {
        isMaxCombo -> Brush.horizontalGradient(listOf(VermilionRed, KyotoGold))
        isSuperCombo -> Brush.horizontalGradient(listOf(Color(0xFFD97706), KyotoGold))
        isHighCombo -> Brush.horizontalGradient(listOf(Color(0xFFF7E9C8), Color(0xFFF3D592)))
        else -> Brush.horizontalGradient(listOf(IndigoCard, IndigoCard))
      }

    Row(
      modifier =
        Modifier.scale(badgeScale.value)
          .clip(RoundedCornerShape(12.dp))
          .background(pillBg)
          .border(
            1.dp,
            if (isHighCombo) KyotoGold else IndigoElevated,
            RoundedCornerShape(12.dp),
          )
          .padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.LocalFireDepartment,
        contentDescription = "Combo multiplier",
        tint = if (isSuperCombo) IndigoNight else KyotoGold,
        modifier =
          Modifier.size(16.dp)
            .offset { IntOffset(0, iconBounceY.value.roundToInt()) }
            .rotate(iconRotation.value),
      )
      Text(
        text = "×${comboMultiplier}",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = if (isSuperCombo) IndigoNight else WashiCream,
      )
    }
  }
}

@Composable
private fun PuzzleVaultScreen(
  puzzleState: PuzzleState,
  onSelectLevel: (Int) -> Unit,
) {
  val allLevels = remember {
    val curated = LexiFillEngine.CURATED_LEVELS
    val engine = LexiFillEngine()
    val procedural = (9..14).map { engine.generateProceduralLevel(it) }
    curated + procedural
  }

  LazyColumn(
    modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item {
      Text(
        text = "Crossword Skeleton Vault",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = WashiCream,
      )
      Text(
        text = "100% Offline-First Kriss-Kross Fill-In Grids · Zero Network Quota",
        style = MaterialTheme.typography.bodySmall,
        color = SkyCyan,
      )
    }

    // Campaign Mastery Overview Card
    item {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .border(
              1.5.dp,
              Brush.horizontalGradient(listOf(SkyCyan, CeladonJade, KyotoGold)),
              RoundedCornerShape(18.dp),
            )
            .paperGrainOverlay(alpha = 0.048f, fiberCount = 38, speckleCount = 56, seed = 404),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          VaultStatColumn(
            label = "CAREER SCORE",
            value = "${puzzleState.totalCampaignScore}",
            valueColor = KyotoGold,
          )
          VaultStatColumn(
            label = "LEVELS SOLVED",
            value = "${puzzleState.completedLevels.size}/${allLevels.size}",
            valueColor = CeladonJade,
          )
          VaultStatColumn(
            label = "LEXICOINS",
            value = "🪙 ${puzzleState.lexiCoins}",
            valueColor = SkyCyan,
          )
        }
      }
    }

    items(allLevels, key = { it.levelNumber }) { template: LevelTemplate ->
      val isSolved = template.levelNumber in puzzleState.completedLevels
      val isCurrent = template.levelNumber == puzzleState.levelTemplate.levelNumber

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) IndigoElevated else IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .border(
              width = if (isCurrent) 2.dp else 1.dp,
              color =
                when {
                  isSolved -> CeladonJade
                  isCurrent -> SkyCyan
                  else -> IndigoCard
                },
              shape = RoundedCornerShape(16.dp),
            )
            .paperGrainOverlay(
              alpha = 0.044f,
              fiberCount = 26,
              speckleCount = 40,
              seed = 500 + template.levelNumber,
            )
            .clickable { onSelectLevel(template.levelNumber) }
            .testTag("vault_level_${template.levelNumber}"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Text(
                text = "LEVEL ${template.levelNumber}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSolved) CeladonJade else SkyCyan,
              )
              Text(
                text = template.tierBadge,
                fontSize = 10.sp,
                color = KyotoGold,
              )
              if (isSolved) {
                Text(
                  text = "★★★",
                  fontSize = 11.sp,
                  color = KyotoGold,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            Text(
              text = template.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = WashiCream,
            )
            Text(
              text =
                "${template.boardWidth}×${template.boardHeight} Grid · ${template.targetWords.size} Intersecting Words (${template.targetWords.joinToString(", ") { it.word }})",
              style = MaterialTheme.typography.bodySmall,
              color = WashiMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Button(
            onClick = { onSelectLevel(template.levelNumber) },
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (isSolved) CeladonJade else SkyCyan,
                contentColor = IndigoNight,
              ),
          ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Play level", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (isSolved) "Replay" else "Play", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun VaultStatColumn(label: String, value: String, valueColor: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.ExtraBold,
      color = valueColor,
    )
    Text(
      text = label,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = WashiMuted,
    )
  }
}

@Composable
private fun CustomStudioAndSkinsScreen(
  puzzleState: PuzzleState,
  onLoadCompactCode: (String) -> Unit,
  onUnlockOrEquipSkin: (com.example.domain.model.CosmeticTileSkin) -> Unit,
  onAuditionSoundEffect: (AudioPlayer.SoundEffect) -> Unit,
) {
  var compactInput by
    rememberSaveable(puzzleState.levelTemplate.compactCode) {
      mutableStateOf(
        puzzleState.levelTemplate.compactCode.ifBlank {
          "9x8|title:Orbital Crossing|H:1,1:PLANET:1,4:Celestial world|V:2,0:SLOPE:1,3:Mountain gradient|H:1,3:SPACE:1,3:Cosmic expanse|V:4,3:CROSS:0,1,2:Intersect|H:4,5:ORBIT:0,2:Star path"
        }
      )
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, IndigoElevated, RoundedCornerShape(18.dp))
            .paperGrainOverlay(alpha = 0.046f, fiberCount = 32, speckleCount = 48, seed = 601),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "AudioPlayer Sound Synthesizer",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = WashiCream,
          )
          Text(
            text =
              "Audition the 4 real-time synthesized PCM/SoundPool effects triggered on game events:",
            style = MaterialTheme.typography.bodySmall,
            color = WashiMuted,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            for (sfx in AudioPlayer.SoundEffect.entries) {
              OutlinedButton(
                onClick = { onAuditionSoundEffect(sfx) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("btn_sfx_${sfx.id}"),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
              ) {
                Text(
                  text = sfx.id.uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.ExtraBold,
                  color = SkyCyan,
                )
              }
            }
          }
        }
      }
    }

    item {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .border(1.dp, IndigoElevated, RoundedCornerShape(18.dp))
            .paperGrainOverlay(alpha = 0.046f, fiberCount = 34, speckleCount = 52, seed = 602),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "Compact Skeleton Level Builder",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = WashiCream,
          )
          Text(
            text =
              "Define custom Kriss-Kross Fill-In grids using ultra-compact local skeleton strings (Format: WxH|title:Name|H:x,y:WORD:blankIdx1,blankIdx2:Clue).",
            style = MaterialTheme.typography.bodySmall,
            color = WashiMuted,
          )
          OutlinedTextField(
            value = compactInput,
            onValueChange = { compactInput = it },
            modifier = Modifier.fillMaxWidth().testTag("input_compact_skeleton"),
            label = { Text("Compact Skeleton Definition") },
            minLines = 3,
            maxLines = 5,
          )
          Button(
            onClick = { onLoadCompactCode(compactInput) },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_load_custom_skeleton"),
            shape = RoundedCornerShape(12.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = SkyCyan,
                contentColor = IndigoNight,
              ),
          ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "Build skeleton")
            Spacer(Modifier.width(8.dp))
            Text("Generate & Play Skeleton Grid", fontWeight = FontWeight.ExtraBold)
          }
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Cosmetic Tile Materials",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = WashiCream,
        )
        Text(
          text = "LexiCoins: ${puzzleState.lexiCoins} 🪙",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = KyotoGold,
        )
      }
    }

    items(GameCatalog.COSMETIC_SKINS, key = { it.id }) { skin ->
      val isUnlocked = skin.id in puzzleState.unlockedSkinIds
      val isEquipped = skin.id == puzzleState.equippedSkin.id

      Surface(
        shape = RoundedCornerShape(16.dp),
        color = IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .border(
              width = if (isEquipped) 2.dp else 1.dp,
              color = if (isEquipped) KyotoGold else IndigoElevated,
              shape = RoundedCornerShape(16.dp),
            )
            .paperGrainOverlay(
              alpha = 0.044f,
              fiberCount = 24,
              speckleCount = 38,
              seed = 700 + skin.id.hashCode(),
            ),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // Sample Tile Preview
          Box(
            modifier =
              Modifier.size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(skin.baseColorHex))
                .border(2.dp, Color(skin.borderColorHex), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "L",
              fontSize = 24.sp,
              fontWeight = FontWeight.ExtraBold,
              color = Color(skin.textColorHex),
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${skin.name} · ${skin.rarity}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = WashiCream,
            )
            Text(
              text = skin.description,
              style = MaterialTheme.typography.bodySmall,
              color = WashiMuted,
            )
          }

          Button(
            onClick = { onUnlockOrEquipSkin(skin) },
            enabled = isUnlocked || puzzleState.lexiCoins >= skin.costCoins,
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (isEquipped) CeladonJade else KyotoGold,
                contentColor = IndigoNight,
              ),
          ) {
            Text(
              text =
                when {
                  isEquipped -> "Equipped"
                  isUnlocked -> "Equip"
                  else -> "${skin.costCoins} 🪙"
                },
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TriePatternSolverScreen(
  viewModel: GameViewModel,
  puzzleState: PuzzleState,
  userEmail: String?,
  isGuestUser: Boolean,
  onSignOut: (() -> Unit)?,
) {
  var patternQuery by rememberSaveable { mutableStateOf("P_AN_T") }
  val matches =
    remember(patternQuery) {
      viewModel.trieEngine.matchesPattern(patternQuery.trim(), maxResults = 30)
    }

  Column(
    modifier =
      Modifier.fillMaxSize()
        .widthIn(max = 600.dp)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (onSignOut != null) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .border(1.2.dp, IndigoElevated, RoundedCornerShape(16.dp)),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text =
                if (isGuestUser) "Playing as Guest (Local Washi Mode)"
                else "Signed in: ${userEmail ?: "Origami Account"}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.ExtraBold,
              color = if (isGuestUser) WashiCream else CeladonJade,
            )
            Text(
              text =
                "Career Score: ${puzzleState.totalCampaignScore} PTS · LexiCoins: ${puzzleState.lexiCoins} 🪙",
              style = MaterialTheme.typography.bodySmall,
              color = WashiMuted,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onSignOut,
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (isGuestUser) CeladonJade else IndigoCard,
                contentColor = if (isGuestUser) Color.White else WashiCream,
              ),
            modifier = Modifier.testTag("btn_account_action"),
          ) {
            Text(
              text = if (isGuestUser) "Create Account" else "Sign Out",
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }

    Text(
      text = "<1ms In-Memory Kotlin Trie Engine",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.ExtraBold,
      color = WashiCream,
    )
    Text(
      text =
        "Loaded ${viewModel.trieEngine.wordCount} dictionary words in memory. Test wildcard skeleton patterns using '_' for missing blank slots.",
      style = MaterialTheme.typography.bodySmall,
      color = WashiMuted,
    )

    OutlinedTextField(
      value = patternQuery,
      onValueChange = { patternQuery = it.uppercase() },
      modifier = Modifier.fillMaxWidth().testTag("trie_pattern_input"),
      label = { Text("Crossword Skeleton Pattern (e.g. P_AN_T, C_OSS, Q_A_T_)") },
      leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search pattern") },
      singleLine = true,
    )

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = IndigoSurface,
      modifier =
        Modifier.fillMaxWidth()
          .border(1.dp, IndigoElevated, RoundedCornerShape(16.dp))
          .paperGrainOverlay(alpha = 0.046f, fiberCount = 34, speckleCount = 50, seed = 801),
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Matching Dictionary Words (${matches.size}):",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = CeladonJade,
        )
        Text(
          text =
            if (matches.isEmpty()) "No valid words match pattern \"$patternQuery\"."
            else matches.joinToString("  •  "),
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
          color = WashiCream,
        )
      }
    }
  }
}
