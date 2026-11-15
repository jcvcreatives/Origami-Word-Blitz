package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import android.app.Activity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
  val view = LocalView.current
  var currentTab by rememberSaveable { mutableStateOf(LexiFillDestination.PUZZLE_GRID) }
  var isFullscreenMode by rememberSaveable { mutableStateOf(false) }
  var isMusicPlaying by remember { mutableStateOf(viewModel.audioPlayer.isMusicEnabled) }

  LaunchedEffect(isFullscreenMode) {
    val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
    val controller = WindowCompat.getInsetsController(window, view)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (isFullscreenMode) {
      controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
      controller.show(WindowInsetsCompat.Type.systemBars())
    }
  }

  if (currentTab != LexiFillDestination.PUZZLE_GRID) {
    BackHandler { currentTab = LexiFillDestination.PUZZLE_GRID }
  } else if (isFullscreenMode) {
    BackHandler { isFullscreenMode = false }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().background(IndigoNight),
    containerColor = IndigoNight,
    contentWindowInsets = WindowInsets.safeDrawing,
    bottomBar = {
      if (!isFullscreenMode) {
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
            label = { Text("Account") },
            modifier = Modifier.testTag("nav_tab_trie"),
          )
        }
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
            isFullscreenMode = isFullscreenMode,
            isMusicPlaying = isMusicPlaying,
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
            onToggleSoundMute = {
              viewModel.toggleSoundMute(haptics)
              isMusicPlaying = viewModel.audioPlayer.isMusicEnabled && !viewModel.audioPlayer.isMuted
            },
            onToggleMusic = {
              isMusicPlaying = viewModel.audioPlayer.toggleMusic()
            },
            onToggleFullscreen = {
              isFullscreenMode = !isFullscreenMode
            },
            onShakeConsumed = { viewModel.clearShake() },
            onMoveFocusedCell = { dx, dy -> viewModel.moveFocusedCell(dx, dy, haptics) },
            onTypeLetter = { ch -> viewModel.typeLetterInFocusedCell(ch, haptics) },
            onBackspaceCell = { viewModel.backspaceInFocusedCell(haptics) },
          )
        }
        LexiFillDestination.PUZZLE_VAULT -> {
          PuzzleVaultScreen(
            puzzleState = puzzleState,
            onSelectLevel = { levelNum ->
              if (viewModel.loadLevel(levelNum)) {
                currentTab = LexiFillDestination.PUZZLE_GRID
              }
            },
          )
        }
        LexiFillDestination.CUSTOM_STUDIO -> {
          CustomStudioAndSkinsScreen(
            puzzleState = puzzleState,
            audioPlayer = viewModel.audioPlayer,
            isMusicPlaying = isMusicPlaying,
            onToggleMusic = { isMusicPlaying = viewModel.audioPlayer.toggleMusic() },
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
  isFullscreenMode: Boolean,
  isMusicPlaying: Boolean,
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
  onToggleMusic: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onShakeConsumed: () -> Unit,
  onMoveFocusedCell: (Int, Int) -> Unit,
  onTypeLetter: (Char) -> Boolean,
  onBackspaceCell: () -> Boolean,
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
    val maxBoardHeight =
      if (isFullscreenMode) {
        (maxHeight * 0.54f).coerceIn(240.dp, 520.dp)
      } else {
        (maxHeight * 0.45f).coerceIn(210.dp, 430.dp)
      }
    val compactSpacing = if (maxHeight < 700.dp || isFullscreenMode) 5.dp else 7.dp

    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 600.dp)
          .align(Alignment.TopCenter)
          .verticalScroll(scrollState, enabled = !isDraggingAnyTile)
          .padding(horizontal = 10.dp, vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(compactSpacing),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // 1. Editorial Kyoto Print HUD with Hanko Seal, Music Toggle & Full Screen Toggle
      LexiFillTopHud(
        puzzleState = puzzleState,
        userEmail = userEmail,
        isGuestUser = isGuestUser,
        isFullscreenMode = isFullscreenMode,
        isMusicPlaying = isMusicPlaying,
        onSignOut = onSignOut,
        onToggleSoundMute = onToggleSoundMute,
        onToggleMusic = onToggleMusic,
        onToggleFullscreen = onToggleFullscreen,
        onResetLevel = onReplayLevel,
      )

      // 2. Interactive Intersecting Clues Strip (hidden in Full Screen Mode for maximum canvas space)
      if (!isFullscreenMode) {
        IntersectingCluesStrip(
          targetWords = puzzleState.targetWords,
          focusedWordId = puzzleState.focusedWordId,
          cells = puzzleState.cells,
          onSelectClueWord = onSelectClueWord,
        )
      }

      // 3. 100% Compose Canvas Crossword Skeleton Grid (adaptively sized to fit single screen)
      LexiFillBoardCanvas(
        puzzleState = puzzleState,
        onGeometryUpdated = { boardGeometry = it },
        onBlankSlotTapped = onBoardCellTapped,
        onShakeConsumed = onShakeConsumed,
        onMoveFocusedCell = onMoveFocusedCell,
        onTypeLetter = onTypeLetter,
        onBackspaceCell = onBackspaceCell,
        modifier = Modifier.heightIn(max = maxBoardHeight),
      )

      // 4. Unified Active Clue & Dual-Axis Intersection Spotlight Bar
      LiveIntersectionPreviewBanner(puzzleState = puzzleState)

      // 5. Spring-Physics Draggable Letter Rack in a Hinoki Wood Tray
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
  isFullscreenMode: Boolean,
  isMusicPlaying: Boolean,
  onSignOut: (() -> Unit)?,
  onToggleSoundMute: () -> Unit,
  onToggleMusic: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onResetLevel: () -> Unit,
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
    shape = RoundedCornerShape(14.dp),
    color = IndigoSurface,
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, IndigoElevated, RoundedCornerShape(14.dp))
        .paperGrainOverlay(alpha = 0.046f, fiberCount = 36, speckleCount = 54, seed = 202)
        .testTag("lexifill_top_hud"),
  ) {
    Column {
      Row(
        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        // Vermilion Hanko Seal Stamp for Level Number
        Box(
          modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
              .background(VermilionRed)
              .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
          Text(
            text = "第${puzzleState.levelTemplate.levelNumber}",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = puzzleState.levelTemplate.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              color = WashiCream,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (puzzleState.comboStreak >= 2 || puzzleState.comboMultiplier > 1.0f) {
              ComboStreakHudBadge(
                comboStreak = puzzleState.comboStreak,
                comboMultiplier = puzzleState.comboMultiplier,
              )
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text =
                "${puzzleState.completedWordsCount}/${puzzleState.targetWords.size} Words · $animatedScore PTS · ${puzzleState.lexiCoins} Coins",
              style = MaterialTheme.typography.bodySmall,
              color = WashiMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (onSignOut != null && !isFullscreenMode) {
              val shortLabel =
                userEmail
                  ?.substringBefore(" (")
                  ?.substringBefore("@")
                  ?.take(10)
                  ?.ifBlank { "Account" } ?: "Account"
              Text(
                text = if (isGuestUser) "· Guest" else "· $shortLabel",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGuestUser) SkyCyan else CeladonJade,
                modifier =
                  Modifier.clickable(onClick = onSignOut)
                    .testTag("hud_account_badge"),
              )
            }
          }
        }

        // Kyoto Chamber Music Toggle, Sound Mute, Reset & Full Screen Icon Buttons
        IconButton(
          onClick = onToggleMusic,
          modifier = Modifier.size(40.dp).testTag("btn_toggle_music"),
        ) {
          Icon(
            imageVector = if (isMusicPlaying) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
            contentDescription =
              if (isMusicPlaying) "Pause Kyoto chamber music" else "Play Kyoto chamber music",
            tint = if (isMusicPlaying) KyotoGold else WashiMuted,
            modifier = Modifier.size(19.dp),
          )
        }
        IconButton(
          onClick = onToggleSoundMute,
          modifier = Modifier.size(40.dp).testTag("btn_toggle_sound"),
        ) {
          Icon(
            imageVector =
              if (puzzleState.isSoundMuted) Icons.AutoMirrored.Filled.VolumeOff
              else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription =
              if (puzzleState.isSoundMuted) "Unmute sound effects" else "Mute sound effects",
            tint = if (puzzleState.isSoundMuted) WashiMuted else CeladonJade,
            modifier = Modifier.size(19.dp),
          )
        }
        IconButton(
          onClick = onResetLevel,
          modifier = Modifier.size(40.dp).testTag("btn_reset_grid"),
        ) {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Reset current puzzle grid",
            tint = WashiMuted,
            modifier = Modifier.size(19.dp),
          )
        }
        IconButton(
          onClick = onToggleFullscreen,
          modifier = Modifier.size(40.dp).testTag("btn_toggle_fullscreen"),
        ) {
          Icon(
            imageVector =
              if (isFullscreenMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription =
              if (isFullscreenMode) "Exit full screen mode" else "Enter full screen mode",
            tint = if (isFullscreenMode) SkyCyan else WashiCream,
            modifier = Modifier.size(20.dp),
          )
        }
      }

      // Smooth Animated Level Completion Progress Bar at bottom of HUD card
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(3.dp)
            .background(IndigoNight.copy(alpha = 0.85f))
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth(animatedProgress)
              .fillMaxHeight()
              .background(
                Brush.horizontalGradient(
                  listOf(VermilionRed, KyotoGold, CeladonJade),
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
  val allCompleted = remember(targetWords) { targetWords.isNotEmpty() && targetWords.all { it.isCompleted } }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .testTag("intersecting_clues_strip"),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (allCompleted) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = CompletedWordBg,
        modifier =
          Modifier.border(1.2.dp, CeladonJade, RoundedCornerShape(10.dp))
            .testTag("all_clues_folded_badge"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "All clues folded",
            tint = CeladonJade,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "All Origami Clues Folded!",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = CeladonJade,
          )
        }
      }
    }

    for (tw in targetWords) {
      key(tw.id) {
        val foldAngleX by
          animateFloatAsState(
            targetValue = if (tw.isCompleted) -82f else 0f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            label = "clue_fold_angle_${tw.id}",
          )
        val foldScaleY by
          animateFloatAsState(
            targetValue = if (tw.isCompleted) 0.18f else 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            label = "clue_fold_scale_${tw.id}",
          )

        AnimatedVisibility(
          visible = !tw.isCompleted,
          enter =
            fadeIn(tween(220)) +
              expandHorizontally(tween(260, easing = FastOutSlowInEasing)) +
              scaleIn(initialScale = 0.85f, animationSpec = tween(240)),
          exit =
            shrinkHorizontally(
              animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
              shrinkTowards = Alignment.Start,
            ) +
              scaleOut(
                targetScale = 0.25f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
              ) +
              fadeOut(tween(durationMillis = 320)),
        ) {
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
              Modifier.graphicsLayer {
                  rotationX = foldAngleX
                  scaleY = foldScaleY
                  cameraDistance = 12f * density
                }
                .border(
                  width = if (isFocused) 1.6.dp else 1.dp,
                  color = border,
                  shape = RoundedCornerShape(12.dp),
                )
                .clickable { onSelectClueWord(tw.id) }
                .testTag("clue_chip_${tw.id}"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Column {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = dirTag,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFocused) KyotoGold else SkyCyan,
                  )
                  Text(
                    text = tw.formattedSkeleton(cells),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color = WashiCream,
                  )
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
    val engine = LexiFillEngine()
    engine.getAllCampaignLevels(LexiFillEngine.TOTAL_CAMPAIGN_LEVELS)
  }
  var selectedChapter by rememberSaveable { mutableIntStateOf(0) } // 0 = All 100, 1..5 = Chapters
  val filteredLevels =
    remember(allLevels, selectedChapter) {
      when (selectedChapter) {
        1 -> allLevels.filter { it.levelNumber in 1..20 }
        2 -> allLevels.filter { it.levelNumber in 21..40 }
        3 -> allLevels.filter { it.levelNumber in 41..60 }
        4 -> allLevels.filter { it.levelNumber in 61..80 }
        5 -> allLevels.filter { it.levelNumber in 81..100 }
        else -> allLevels
      }
    }
  val chapters =
    listOf(
      0 to "All (100)",
      1 to "Ch. I (1–20)",
      2 to "Ch. II (21–40)",
      3 to "Ch. III (41–60)",
      4 to "Ch. IV (61–80)",
      5 to "Ch. V (81–100)",
    )

  LazyColumn(
    modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item {
      Text(
        text = "Crossword Skeleton Vault (100 Levels)",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        color = WashiCream,
      )
      Text(
        text = "100 Kriss-Kross Origami Puzzles Across 5 Mastery Chapters · Fold Intersecting Words",
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
            value = "${puzzleState.lexiCoins} Coins",
            valueColor = SkyCyan,
          )
        }
      }
    }

    // Chapter Filter Strip
    item {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for ((chId, chLabel) in chapters) {
          val isSelected = selectedChapter == chId
          val isChDone = chId in 1..5 && puzzleState.isChapterCompleted(chId)
          Surface(
            shape = RoundedCornerShape(999.dp),
            color =
              when {
                isSelected -> SkyCyan
                isChDone -> CompletedWordBg
                else -> IndigoSurface
              },
            modifier =
              Modifier.border(
                  1.dp,
                  when {
                    isSelected -> SkyCyan
                    isChDone -> CeladonJade
                    else -> IndigoCard
                  },
                  RoundedCornerShape(999.dp),
                )
                .clickable { selectedChapter = chId }
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("vault_chapter_filter_$chId"),
          ) {
            Text(
              text = if (isChDone) "✦ $chLabel ✓" else chLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.ExtraBold,
              color =
                when {
                  isSelected -> IndigoNight
                  isChDone -> CeladonJade
                  else -> WashiCream
                },
            )
          }
        }
      }
    }

    if (selectedChapter in 1..5) {
      item(key = "chapter_header_$selectedChapter") {
        ChapterMasteryHeaderCard(
          chapterId = selectedChapter,
          solvedCount = puzzleState.solvedCountInChapter(selectedChapter),
          isChapterCompleted = puzzleState.isChapterCompleted(selectedChapter),
        )
      }
    }

    items(filteredLevels, key = { it.levelNumber }) { template: LevelTemplate ->
      val chIdForLevel = puzzleState.chapterForLevel(template.levelNumber)
      if (selectedChapter == 0 && (template.levelNumber - 1) % 20 == 0) {
        ChapterMasteryHeaderCard(
          chapterId = chIdForLevel,
          solvedCount = puzzleState.solvedCountInChapter(chIdForLevel),
          isChapterCompleted = puzzleState.isChapterCompleted(chIdForLevel),
          modifier = Modifier.padding(bottom = 10.dp),
        )
      }
      val isSolved = template.levelNumber in puzzleState.completedLevels
      val isUnlocked = isSolved || template.levelNumber <= puzzleState.highestUnlockedLevel
      val isCurrent = template.levelNumber == puzzleState.levelTemplate.levelNumber

      Surface(
        shape = RoundedCornerShape(16.dp),
        color =
          when {
            !isUnlocked -> IndigoCard.copy(alpha = 0.65f)
            isCurrent -> IndigoElevated
            else -> IndigoSurface
          },
        modifier =
          Modifier.fillMaxWidth()
            .border(
              width = if (isCurrent) 2.dp else 1.dp,
              color =
                when {
                  isSolved -> CeladonJade
                  isCurrent -> SkyCyan
                  isUnlocked -> SkyCyan.copy(alpha = 0.55f)
                  else -> IndigoCard
                },
              shape = RoundedCornerShape(16.dp),
            )
            .paperGrainOverlay(
              alpha = if (isUnlocked) 0.044f else 0.025f,
              fiberCount = 26,
              speckleCount = 40,
              seed = 500 + template.levelNumber,
            )
            .clickable(enabled = isUnlocked) { onSelectLevel(template.levelNumber) }
            .testTag("vault_level_${template.levelNumber}"),
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                color =
                  when {
                    isSolved -> CeladonJade
                    isUnlocked -> SkyCyan
                    else -> WashiMuted
                  },
              )
              Text(
                text = template.tierBadge,
                fontSize = 10.sp,
                color = if (isUnlocked) KyotoGold else WashiMuted,
              )
              if (isSolved) {
                Text(
                  text = "★★★",
                  fontSize = 11.sp,
                  color = KyotoGold,
                  fontWeight = FontWeight.Bold,
                )
              } else if (!isUnlocked) {
                Text(
                  text = "LOCKED",
                  fontSize = 9.5.sp,
                  color = WashiMuted,
                  fontWeight = FontWeight.ExtraBold,
                )
              }
            }
            Text(
              text = template.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = if (isUnlocked) WashiCream else WashiMuted,
            )
            Text(
              text =
                if (isUnlocked) {
                  "${template.boardWidth}×${template.boardHeight} Grid · ${template.targetWords.size} Intersecting Words (${template.targetWords.joinToString(", ") { it.word }})"
                } else {
                  "Win Level ${template.levelNumber - 1} to unlock this ${template.boardWidth}×${template.boardHeight} origami puzzle"
                },
              style = MaterialTheme.typography.bodySmall,
              color = WashiMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }

          Button(
            onClick = { onSelectLevel(template.levelNumber) },
            enabled = isUnlocked,
            shape = RoundedCornerShape(10.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (isSolved) CeladonJade else SkyCyan,
                contentColor = IndigoNight,
                disabledContainerColor = IndigoCard,
                disabledContentColor = WashiMuted,
              ),
            modifier = Modifier.testTag("vault_level_btn_${template.levelNumber}"),
          ) {
            Icon(
              imageVector = if (isUnlocked) Icons.Filled.PlayArrow else Icons.Filled.Lock,
              contentDescription = if (isUnlocked) "Play level" else "Locked level",
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
              text =
                when {
                  isSolved -> "Replay"
                  isUnlocked -> "Play"
                  else -> "Locked"
                },
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

/**
 * Chapter Header Card with an interactive & automatic Paper-Folding Animation + Particle Burst
 * over the chapter header when all 20 levels in the chapter are completed (or when the user
 * taps the "✦ Fold Seal" preview button).
 */
@Composable
private fun ChapterMasteryHeaderCard(
  chapterId: Int,
  solvedCount: Int,
  isChapterCompleted: Boolean,
  modifier: Modifier = Modifier,
) {
  val chapterInfo =
    remember(chapterId) {
      when (chapterId) {
        1 -> Triple("Chapter I · Apprentice Washi Folds", "Levels 1–20 · Gentle 4–5 Letter Crossings", Color(0xFF1F4E78))
        2 -> Triple("Chapter II · Courtyard Bamboo Creases", "Levels 21–40 · Balanced 5–6 Letter Grids", Color(0xFF1B6B45))
        3 -> Triple("Chapter III · Golden Pavilion Symmetry", "Levels 41–60 · Interlocking Multi-Word Skeletons", Color(0xFFB87314))
        4 -> Triple("Chapter IV · Arashiyama Lantern Weaves", "Levels 61–80 · Advanced Scholar Crossings", Color(0xFFC83720))
        else -> Triple("Chapter V · Grandmaster Kyoto Crane", "Levels 81–100 · Imperial 100-Level Mastery", Color(0xFF8B3A62))
      }
    }

  val scope = rememberCoroutineScope()
  val foldProgress = remember { Animatable(if (isChapterCompleted) 1f else 0f) }
  val particleBurstProgress = remember { Animatable(0f) }
  var manualTriggerCount by remember { mutableIntStateOf(0) }
  val foldFacetPath = remember { Path() }
  val particlePath = remember { Path() }

  LaunchedEffect(isChapterCompleted, manualTriggerCount) {
    if (isChapterCompleted || manualTriggerCount > 0) {
      foldProgress.snapTo(0f)
      particleBurstProgress.snapTo(0.01f)
      kotlinx.coroutines.coroutineScope {
        launch {
          foldProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1050, easing = FastOutSlowInEasing),
          )
        }
        launch {
          particleBurstProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1350, easing = FastOutSlowInEasing),
          )
          particleBurstProgress.snapTo(0f)
        }
      }
    }
  }

  val fp = foldProgress.value
  val bp = particleBurstProgress.value
  val isAnimatingFold = bp > 0f && bp < 1f
  val headerTiltX = if (isAnimatingFold) sin(fp * PI).toFloat() * 9f else 0f

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (isChapterCompleted) CompletedWordBg else IndigoSurface,
    modifier =
      modifier
        .fillMaxWidth()
        .graphicsLayer {
          rotationX = headerTiltX
          cameraDistance = 14f * density
        }
        .border(
          width = if (isChapterCompleted || isAnimatingFold) 1.8.dp else 1.2.dp,
          brush =
            if (isChapterCompleted || isAnimatingFold) {
              Brush.horizontalGradient(listOf(KyotoGold, CeladonJade, SkyCyan))
            } else {
              Brush.horizontalGradient(listOf(IndigoElevated, IndigoCard))
            },
          shape = RoundedCornerShape(16.dp),
        )
        .testTag("vault_chapter_header_$chapterId"),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Origami Paper-Folding Crest Canvas over the left of the Chapter Header
        Box(
          modifier =
            Modifier.size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(IndigoCard.copy(alpha = 0.7f))
              .border(1.dp, if (isChapterCompleted) KyotoGold else IndigoElevated, RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center,
        ) {
          Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension * 0.46f
            val foldStage = fp.coerceIn(0f, 1f)

            // Base Washi Square that folds its 4 corners inward along diagonal mountain/valley creases
            val cornerPull = 1f - 0.42f * sin(foldStage * PI).toFloat()
            val crestSpin = foldStage * 45f

            withTransform({
              rotate(degrees = crestSpin, pivot = Offset(cx, cy))
            }) {
              // Top-Left Origami Facet
              foldFacetPath.reset()
              foldFacetPath.moveTo(cx, cy)
              foldFacetPath.lineTo(cx - r * cornerPull, cy - r * cornerPull)
              foldFacetPath.lineTo(cx, cy - r)
              foldFacetPath.close()
              drawPath(foldFacetPath, color = if (isChapterCompleted) KyotoGold else chapterInfo.third)

              // Top-Right Origami Facet
              foldFacetPath.reset()
              foldFacetPath.moveTo(cx, cy)
              foldFacetPath.lineTo(cx, cy - r)
              foldFacetPath.lineTo(cx + r * cornerPull, cy - r * cornerPull)
              foldFacetPath.close()
              drawPath(foldFacetPath, color = CeladonJade.copy(alpha = 0.85f))

              // Bottom-Right Origami Facet
              foldFacetPath.reset()
              foldFacetPath.moveTo(cx, cy)
              foldFacetPath.lineTo(cx + r * cornerPull, cy + r * cornerPull)
              foldFacetPath.lineTo(cx, cy + r)
              foldFacetPath.close()
              drawPath(foldFacetPath, color = SkyCyan.copy(alpha = 0.85f))

              // Bottom-Left Origami Facet
              foldFacetPath.reset()
              foldFacetPath.moveTo(cx, cy)
              foldFacetPath.lineTo(cx, cy + r)
              foldFacetPath.lineTo(cx - r * cornerPull, cy + r * cornerPull)
              foldFacetPath.close()
              drawPath(foldFacetPath, color = VermilionRed.copy(alpha = 0.80f))

              // Crisp white origami crease lines
              drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(cx - r, cy),
                end = Offset(cx + r, cy),
                strokeWidth = 1.4.dp.toPx(),
              )
              drawLine(
                color = Color.White.copy(alpha = 0.85f),
                start = Offset(cx, cy - r),
                end = Offset(cx, cy + r),
                strokeWidth = 1.4.dp.toPx(),
              )
            }
          }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = chapterInfo.first,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              color = WashiCream,
            )
            if (isChapterCompleted) {
              Text(
                text = "MASTERED",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = KyotoGold,
              )
            }
          }
          Text(
            text = "${chapterInfo.second} · $solvedCount/20 Solved",
            style = MaterialTheme.typography.bodySmall,
            color = if (isChapterCompleted) CeladonJade else WashiMuted,
          )
          // Chapter Progress Bar
          Box(
            modifier =
              Modifier.fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(IndigoCard)
          ) {
            Box(
              modifier =
                Modifier.fillMaxWidth((solvedCount / 20f).coerceIn(0f, 1f))
                  .fillMaxHeight()
                  .background(
                    Brush.horizontalGradient(listOf(SkyCyan, CeladonJade, KyotoGold))
                  )
            )
          }
        }

        OutlinedButton(
          onClick = {
            scope.launch {
              manualTriggerCount++
            }
          },
          shape = RoundedCornerShape(10.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.height(36.dp).testTag("btn_chapter_fold_anim_$chapterId"),
        ) {
          Text(
            text = if (isChapterCompleted) "✦ Replay Fold" else "✦ Fold Seal",
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isChapterCompleted) KyotoGold else SkyCyan,
          )
        }
      }

      // Paper-Folding Crease Sweep + Origami Particle Burst Canvas Overlay over the Chapter Header!
      if (isAnimatingFold) {
        Canvas(
          modifier =
            Modifier.matchParentSize()
              .testTag("chapter_header_particle_canvas_$chapterId")
        ) {
          // 1. Diagonal Paper-Folding Crease Wave sweeping across the Chapter Header
          val sweepX = size.width * fp
          drawLine(
            color = KyotoGold.copy(alpha = (1f - fp) * 0.75f),
            start = Offset((sweepX - 28.dp.toPx()).coerceAtLeast(0f), 0f),
            end = Offset(sweepX.coerceAtMost(size.width), size.height),
            strokeWidth = 3.dp.toPx(),
          )

          // 2. 28 Origami Paper-Triangle/Diamond Particles bursting across the Chapter Header
          val palette = listOf(KyotoGold, CeladonJade, VermilionRed, SkyCyan, Color(0xFFD65A7A))
          val count = 28
          val fadeAlpha = sin(bp * PI).toFloat().coerceIn(0f, 1f)
          for (i in 0 until count) {
            val originX = size.width * (0.12f + 0.76f * ((i % 7) / 6f))
            val originY = size.height * 0.5f
            val angle = (2.0 * PI * i) / count + (i * 0.37)
            val dist = size.height * (0.35f + 0.85f * (i % 3) * 0.35f) * bp
            val px = originX + (cos(angle) * dist).toFloat()
            val py = originY + (sin(angle) * dist * 0.65f).toFloat() - (size.height * 0.28f * bp)
            val pSize = (4.5f + (i % 3) * 2f).dp.toPx() * (1f - bp * 0.3f)
            val col = palette[i % palette.size].copy(alpha = (fadeAlpha * 0.88f).coerceIn(0f, 1f))

            withTransform({
              translate(left = px, top = py)
              rotate(degrees = (i * 47f + bp * 220f) % 360f, pivot = Offset.Zero)
            }) {
              particlePath.reset()
              particlePath.moveTo(0f, -pSize)
              particlePath.lineTo(pSize * 0.85f, 0f)
              particlePath.lineTo(0f, pSize)
              particlePath.lineTo(-pSize * 0.85f, 0f)
              particlePath.close()
              drawPath(path = particlePath, color = col)
            }
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
  audioPlayer: AudioPlayer,
  isMusicPlaying: Boolean,
  onToggleMusic: () -> Unit,
  onLoadCompactCode: (String) -> Unit,
  onUnlockOrEquipSkin: (com.example.domain.model.CosmeticTileSkin) -> Unit,
  onAuditionSoundEffect: (AudioPlayer.SoundEffect) -> Unit,
) {
  var selectedKit by remember { mutableStateOf(audioPlayer.activeKit) }
  var selectedMood by remember { mutableStateOf(audioPlayer.activeMusicMood) }
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
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Kyoto Chamber Music & Foley Studio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = WashiCream,
              )
              Text(
                text =
                  "Paulownia wood Koto plucks, Shakuhachi bamboo flute breath, and Japanese paper foley:",
                style = MaterialTheme.typography.bodySmall,
                color = WashiMuted,
              )
            }
            OutlinedButton(
              onClick = onToggleMusic,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.testTag("btn_studio_toggle_music"),
            ) {
              Icon(
                imageVector = if (isMusicPlaying) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                contentDescription = "Toggle chamber music",
                tint = if (isMusicPlaying) KyotoGold else WashiMuted,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (isMusicPlaying) "Music ON" else "Music OFF",
                fontWeight = FontWeight.ExtraBold,
                color = if (isMusicPlaying) KyotoGold else WashiMuted,
              )
            }
          }

          // Kyoto Chamber Music Mood Selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            for (mood in AudioPlayer.MusicMood.entries) {
              val isMoodSelected = selectedMood == mood
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isMoodSelected) KyotoGold.copy(alpha = 0.16f) else IndigoCard,
                modifier =
                  Modifier.weight(1f)
                    .border(
                      width = if (isMoodSelected) 1.6.dp else 1.dp,
                      color = if (isMoodSelected) KyotoGold else IndigoElevated,
                      shape = RoundedCornerShape(10.dp),
                    )
                    .clickable {
                      selectedMood = mood
                      audioPlayer.selectMusicMood(mood)
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("btn_mood_${mood.id}"),
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = mood.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isMoodSelected) KyotoGold else WashiCream,
                    maxLines = 1,
                  )
                  Text(
                    text = mood.subtitle,
                    fontSize = 9.sp,
                    color = WashiMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }

          // Acoustic Material Kit Selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            for (kit in AudioPlayer.AcousticKit.entries) {
              val isSelected = selectedKit == kit
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) SkyCyan.copy(alpha = 0.16f) else IndigoCard,
                modifier =
                  Modifier.weight(1f)
                    .border(
                      width = if (isSelected) 1.6.dp else 1.dp,
                      color = if (isSelected) SkyCyan else IndigoElevated,
                      shape = RoundedCornerShape(10.dp),
                    )
                    .clickable {
                      selectedKit = kit
                      audioPlayer.selectAcousticKit(kit)
                      onAuditionSoundEffect(AudioPlayer.SoundEffect.SNAP)
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("btn_kit_${kit.id}"),
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(
                    text = kit.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) SkyCyan else WashiCream,
                    maxLines = 1,
                  )
                }
              }
            }
          }

          // 2x2 Tactile Foley Audition Pads
          val effects = AudioPlayer.SoundEffect.entries
          for (rowEffects in effects.chunked(2)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              for (sfx in rowEffects) {
                OutlinedButton(
                  onClick = { onAuditionSoundEffect(sfx) },
                  modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("btn_sfx_${sfx.id}"),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                      text = sfx.label,
                      style = MaterialTheme.typography.labelMedium,
                      fontWeight = FontWeight.ExtraBold,
                      color = SkyCyan,
                    )
                    Text(
                      text = sfx.subtitle,
                      fontSize = 9.5.sp,
                      color = WashiMuted,
                      maxLines = 1,
                    )
                  }
                }
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
          text = "LexiCoins: ${puzzleState.lexiCoins} Coins",
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
                  else -> "${skin.costCoins} Coins"
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
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }
  val cloudProfile by viewModel.cloudProfileState.collectAsStateWithLifecycle()
  val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsStateWithLifecycle()
  var isLinkingFirebase by remember { mutableStateOf(false) }
  var linkMessage by remember { mutableStateOf<String?>(null) }
  var registeredAccounts by remember {
    mutableStateOf(com.example.ui.auth.LocalAccountStore.getAllRegisteredAccounts(context))
  }

  LaunchedEffect(cloudProfile, userEmail) {
    registeredAccounts = com.example.ui.auth.LocalAccountStore.getAllRegisteredAccounts(context)
  }

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
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
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
                  "Career Score: ${puzzleState.totalCampaignScore} PTS · LexiCoins: ${puzzleState.lexiCoins} Coins",
                style = MaterialTheme.typography.bodySmall,
                color = WashiMuted,
              )
              Text(
                text = cloudSyncStatus,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (cloudProfile != null) CeladonJade else SkyCyan,
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

          if (cloudProfile != null) {
            val cp = cloudProfile!!
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = CompletedWordBg,
              modifier =
                Modifier.fillMaxWidth()
                  .border(1.dp, CeladonJade.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text = "Firebase Firestore Profile (users/${cp.userId.take(10)}…)",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.ExtraBold,
                  color = CeladonJade,
                )
                Text(
                  text =
                    "Display Name: ${cp.displayName} · Stage: ${cp.campaignStage} · Cloud Coins: ${cp.paperCoins} · Score: ${cp.totalScore}",
                  fontSize = 11.sp,
                  color = WashiCream,
                )
              }
            }
          } else if (!isGuestUser) {
            OutlinedButton(
              onClick = {
                isLinkingFirebase = true
                linkMessage = null
                com.example.ui.auth.onGoogleSignInClicked(
                  context = context,
                  credentialManager = credentialManager,
                  onAuthSuccess = {
                    isLinkingFirebase = false
                    val fbUser =
                      try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                      } catch (e: Exception) {
                        null
                      }
                    if (fbUser != null) {
                      val activeLocal = com.example.ui.auth.LocalAccountStore.getActiveAccount(context)
                      val name = activeLocal?.username ?: fbUser.displayName ?: "Origami Strategist"
                      com.example.ui.auth.LocalAccountStore.recordFirebaseRegistration(
                        context = context,
                        username = name,
                        email = fbUser.email ?: activeLocal?.email.orEmpty(),
                        firebaseUid = fbUser.uid,
                      )
                      registeredAccounts =
                        com.example.ui.auth.LocalAccountStore.getAllRegisteredAccounts(context)
                      linkMessage = "Linked & synced '$name' to Firebase Firestore (users/${fbUser.uid.take(8)}…)!"
                    }
                  },
                  onAuthError = { msg ->
                    isLinkingFirebase = false
                    linkMessage = msg
                  },
                  scope = scope,
                  onAuthCancelled = { isLinkingFirebase = false },
                )
              },
              enabled = !isLinkingFirebase,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().testTag("btn_link_firebase_cloud"),
            ) {
              Text(
                text =
                  if (isLinkingFirebase) "Linking to Firebase Firestore..."
                  else "Link & Sync Account to Firebase Cloud (Google)",
                fontWeight = FontWeight.ExtraBold,
                color = SkyCyan,
              )
            }
          }

          if (linkMessage != null) {
            Text(
              text = linkMessage!!,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = CeladonJade,
            )
          }

          // Registered Accounts Directory
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = IndigoCard,
            modifier =
              Modifier.fillMaxWidth()
                .border(1.dp, IndigoElevated, RoundedCornerShape(12.dp))
                .testTag("cloud_registered_users_card"),
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "REGISTERED ACCOUNTS DIRECTORY (${registeredAccounts.size})",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = SkyCyan,
              )
              if (registeredAccounts.isEmpty()) {
                Text(
                  text = "No registered accounts yet. Tap 'Create Account' on the login screen to register.",
                  fontSize = 11.sp,
                  color = WashiMuted,
                )
              } else {
                registeredAccounts.forEach { acct ->
                  Row(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(IndigoSurface)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = acct.username,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = WashiCream,
                      )
                      Text(
                        text =
                          if (acct.syncedToFirebase) {
                            "Firestore: users/${acct.firebaseUid.take(10)}… · ${acct.email}"
                          } else {
                            "Local Account · ${acct.email}"
                          },
                        fontSize = 10.sp,
                        color = if (acct.syncedToFirebase) CeladonJade else WashiMuted,
                      )
                    }
                    Text(
                      text = if (acct.syncedToFirebase) "Firebase ✓" else "Registered",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.ExtraBold,
                      color = if (acct.syncedToFirebase) CeladonJade else KyotoGold,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    Text(
      text = "Crossword Wordbook & Pattern Finder",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.ExtraBold,
      color = WashiCream,
    )
    Text(
      text =
        "Explore ${viewModel.trieEngine.wordCount} curated dictionary words. Use '_' for open blank tiles to test intersecting words.",
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
