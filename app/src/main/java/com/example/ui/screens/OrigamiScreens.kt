package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.remote.SavedMatchDocument
import com.example.domain.model.AiOpponent
import com.example.domain.model.Coordinate
import com.example.domain.model.CosmeticTileSkin
import com.example.domain.model.GameCatalog
import com.example.domain.model.GameMode
import com.example.domain.model.GameState
import com.example.domain.model.RogueRelic
import com.example.domain.model.Tile
import com.example.domain.model.TurnStatus
import com.example.ui.components.BoardViewportMapper
import com.example.ui.components.DraggablePlayerRack
import com.example.ui.components.FloatingDraggedTileOverlay
import com.example.ui.components.FloatingScoreBurstOverlay
import com.example.ui.components.MultiplierScoreBreakdownBar
import com.example.ui.components.OrigamiBoardCanvas
import com.example.ui.theme.CeladonJade
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoElevated
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.IndigoSurface
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.SakuraPink
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import com.example.ui.theme.WashiMuted
import com.example.ui.viewmodel.CloudUiState
import com.example.ui.viewmodel.GameViewModel

enum class OrigamiTab {
  ARENA,
  CAMPAIGN,
  VAULT,
  LEXICON,
}

@Composable
fun OrigamiMainScreen(
  viewModel: GameViewModel,
  onSignOut: () -> Unit,
) {
  val gameState by viewModel.gameState.collectAsStateWithLifecycle()
  val cloudMatches by viewModel.cloudMatchesState.collectAsStateWithLifecycle()
  var currentTab by remember { mutableStateOf(OrigamiTab.ARENA) }

  if (currentTab != OrigamiTab.ARENA) {
    BackHandler { currentTab = OrigamiTab.ARENA }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = IndigoNight,
    bottomBar = {
      NavigationBar(
        containerColor = IndigoSurface,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
      ) {
        NavigationBarItem(
          selected = currentTab == OrigamiTab.ARENA,
          onClick = { currentTab = OrigamiTab.ARENA },
          icon = { Icon(Icons.Default.GridOn, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_arena)) },
          colors = origamiNavColors(),
          modifier = Modifier.testTag("nav_tab_arena"),
        )
        NavigationBarItem(
          selected = currentTab == OrigamiTab.CAMPAIGN,
          onClick = { currentTab = OrigamiTab.CAMPAIGN },
          icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_campaign)) },
          colors = origamiNavColors(),
          modifier = Modifier.testTag("nav_tab_campaign"),
        )
        NavigationBarItem(
          selected = currentTab == OrigamiTab.VAULT,
          onClick = { currentTab = OrigamiTab.VAULT },
          icon = { Icon(Icons.Default.Palette, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_vault)) },
          colors = origamiNavColors(),
          modifier = Modifier.testTag("nav_tab_vault"),
        )
        NavigationBarItem(
          selected = currentTab == OrigamiTab.LEXICON,
          onClick = { currentTab = OrigamiTab.LEXICON },
          icon = { Icon(Icons.Default.CloudDone, contentDescription = null) },
          label = { Text(stringResource(R.string.nav_lexicon)) },
          colors = origamiNavColors(),
          modifier = Modifier.testTag("nav_tab_lexicon"),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .background(IndigoNight),
      contentAlignment = Alignment.TopCenter,
    ) {
      when (currentTab) {
        OrigamiTab.ARENA ->
          BlitzArenaScreen(
            gameState = gameState,
            viewModel = viewModel,
            onOpenCampaign = { currentTab = OrigamiTab.CAMPAIGN },
          )
        OrigamiTab.CAMPAIGN ->
          CampaignAndModesScreen(
            gameState = gameState,
            onStartMode = { mode, opponent ->
              viewModel.startNewMatch(mode, opponent)
              currentTab = OrigamiTab.ARENA
            },
            onToggleRelic = { viewModel.toggleRelic(it) },
          )
        OrigamiTab.VAULT ->
          CosmeticTileVaultScreen(
            gameState = gameState,
            onSelectOrUnlockSkin = { skin, haptics ->
              viewModel.unlockOrEquipSkin(skin, haptics)
            },
          )
        OrigamiTab.LEXICON ->
          LexiconAndCloudScreen(
            gameState = gameState,
            cloudMatchesState = cloudMatches,
            viewModel = viewModel,
            onSignOut = onSignOut,
          )
      }
    }
  }
}

@Composable
private fun origamiNavColors() =
  NavigationBarItemDefaults.colors(
    selectedIconColor = KyotoGold,
    selectedTextColor = KyotoGold,
    indicatorColor = IndigoElevated,
    unselectedIconColor = WashiMuted,
    unselectedTextColor = WashiMuted,
  )

@Composable
private fun BlitzArenaScreen(
  gameState: GameState,
  viewModel: GameViewModel,
  onOpenCampaign: () -> Unit,
) {
  val haptics = LocalHapticFeedback.current
  val viewportMapper = remember { BoardViewportMapper() }
  var draggedTile by remember { mutableStateOf<Tile?>(null) }
  var fingerRootPos by remember { mutableStateOf(Offset.Zero) }
  var hoveredCoord by remember { mutableStateOf<Coordinate?>(null) }
  var arenaRootOffset by remember { mutableStateOf(Offset.Zero) }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .widthIn(max = 600.dp)
        .onGloballyPositioned { coords -> arenaRootOffset = coords.positionInRoot() }
        .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // 1. Scoreboard, Combo Streak & Blitz Turn Timer HUD
      ScoreAndTimerHud(
        gameState = gameState,
        onSwitchMode = onOpenCampaign,
      )

      // 2. Active Rogue-Lite Relics strip
      ActiveRelicsRow(
        activeRelics = gameState.activeRelics,
        paperCoins = gameState.paperCoins,
        bagRemaining = gameState.scoreState.tileBagRemaining,
      )

      // 3. 15x15 Zoomable Crossword Board + Floating Score Burst Overlay
      Box(
        modifier = Modifier.fillMaxWidth().weight(1f),
        contentAlignment = Alignment.Center,
      ) {
        OrigamiBoardCanvas(
          boardState = gameState.boardState,
          equippedSkin = gameState.equippedSkin,
          particleBursts = gameState.particleBursts,
          shakeIntensity = gameState.boardShakeIntensity,
          hoveredCoord = hoveredCoord,
          viewportMapper = viewportMapper,
          onSquareTapped = { coord -> viewModel.onBoardSquareTapped(coord, haptics) },
        )

        if (gameState.floatingBursts.isNotEmpty()) {
          FloatingScoreBurstOverlay(
            bursts = gameState.floatingBursts,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
          )
        }

        // Pass-and-Play privacy screen between turns
        if (gameState.turnStatus == TurnStatus.PASS_DEVICE_INTERSTITIAL) {
          PassDeviceInterstitialCard(
            turnNumber = gameState.turnNumber,
            onReady = { viewModel.confirmPassDeviceReady() },
          )
        }

        // Match Over celebratory dialog card
        if (gameState.turnStatus == TurnStatus.MATCH_OVER) {
          MatchOverOverlayCard(
            gameState = gameState,
            onPlayAgain = {
              viewModel.startNewMatch(gameState.mode, gameState.opponent)
            },
          )
        }
      }

      // 4. Live Multiplier Score Calculation Breakdown Preview
      MultiplierScoreBreakdownBar(
        movePreview = gameState.boardState.movePreview,
        lastMoveSummary = gameState.lastMoveSummary,
        hintSuggestion = gameState.hintSuggestion,
        comboMultiplier = gameState.scoreState.comboMultiplier,
      )

      // 5. Draggable 7-Slot Player Rack with spring physics
      DraggablePlayerRack(
        rackState = gameState.rackState,
        equippedSkin = gameState.equippedSkin,
        onTileSelected = { tileId -> viewModel.selectRackTile(tileId, haptics) },
        onDragStart = { tile, startRootPos ->
          draggedTile = tile
          fingerRootPos = startRootPos
          hoveredCoord = viewportMapper.rootOffsetToBoardCoordinate(startRootPos)
          viewModel.setDraggingTile(tile.id, haptics)
        },
        onDragMove = { delta ->
          val updatedPos = fingerRootPos + delta
          fingerRootPos = updatedPos
          hoveredCoord = viewportMapper.rootOffsetToBoardCoordinate(updatedPos)
        },
        onDragEnd = {
          val droppingTile = draggedTile
          val target = hoveredCoord
          if (droppingTile != null && target != null) {
            viewModel.placeTileOnBoard(droppingTile.id, target, haptics)
          } else {
            viewModel.setDraggingTile(null)
          }
          draggedTile = null
          hoveredCoord = null
        },
      )

      // 6. Tactical Action Bar (Recall, Shuffle, Hint / Auto-Fold, Pass, FOLD WORD)
      TacticalActionBar(
        hasPendingTiles = gameState.boardState.pendingPlacements.isNotEmpty(),
        isValidMove =
          gameState.boardState.movePreview?.let { it.isValidPosition && it.allWordsValid } == true,
        pendingScore = gameState.boardState.movePreview?.totalTurnScore ?: 0,
        onRecall = { viewModel.recallAllPendingTiles(haptics) },
        onShuffle = { viewModel.shuffleRack(haptics) },
        onHint = { viewModel.requestTrieHint(haptics) },
        onAutoFold = { viewModel.autoFoldBestHintMove(haptics) },
        onPass = { viewModel.passTurn() },
        onSubmit = { viewModel.submitTurn(haptics) },
      )
    }

    // Floating dragged tile overlay tracking finger position
    FloatingDraggedTileOverlay(
      draggedTile = draggedTile,
      skin = gameState.equippedSkin,
      fingerRootOffset = fingerRootPos,
      parentRootOffset = arenaRootOffset,
    )
  }
}

@Composable
private fun ScoreAndTimerHud(
  gameState: GameState,
  onSwitchMode: () -> Unit,
) {
  val scoreState = gameState.scoreState
  val isLowTime = scoreState.turnTimeRemainingSec <= 10

  Surface(
    color = IndigoCard,
    shape = RoundedCornerShape(16.dp),
    modifier =
      Modifier.fillMaxWidth()
        .border(1.dp, IndigoElevated, RoundedCornerShape(16.dp)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      // Player 1 Score
      Column {
        Text(
          text = "YOU (P1)",
          style = MaterialTheme.typography.labelSmall,
          color = WashiMuted,
        )
        Text(
          text = scoreState.playerScore.toString(),
          style = MaterialTheme.typography.headlineMedium,
          color = CeladonJade,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("player_one_score_text"),
        )
      }

      // Center Blitz Timer + Combo Multiplier
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Turn Timer Pill
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clip(CircleShape)
                .background(
                  if (isLowTime) VermilionRed.copy(alpha = 0.25f) else IndigoElevated
                )
                .border(
                  1.dp,
                  if (isLowTime) VermilionRed else KyotoGold.copy(alpha = 0.45f),
                  CircleShape,
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.Timer,
              contentDescription = null,
              tint = if (isLowTime) VermilionRed else KyotoGold,
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "${scoreState.turnTimeRemainingSec}s",
              style = MaterialTheme.typography.labelLarge,
              color = if (isLowTime) VermilionRed else WashiCream,
            )
          }

          // Combo Streak Pill
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clip(CircleShape)
                .background(KyotoGold.copy(alpha = 0.18f))
                .border(1.dp, KyotoGold, CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Default.LocalFireDepartment,
              contentDescription = null,
              tint = KyotoGold,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "×${scoreState.comboMultiplier}",
              style = MaterialTheme.typography.labelLarge,
              color = KyotoGold,
            )
          }
        }

        Text(
          text =
            when (gameState.mode) {
              GameMode.CAMPAIGN ->
                "vs ${gameState.opponent.name} (Goal ${gameState.opponent.targetScore})"
              GameMode.PASS_AND_PLAY -> "Pass & Play Tabletop"
              GameMode.DAILY_BLITZ -> "Daily Crane Blitz"
              GameMode.ASYNC_DUEL -> "Cloud Arena"
            },
          style = MaterialTheme.typography.labelSmall,
          color = WashiMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp).clickable { onSwitchMode() },
        )
      }

      // Opponent / Player 2 Score
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text =
            if (gameState.mode == GameMode.PASS_AND_PLAY) "PLAYER 2"
            else gameState.opponent.name.uppercase().take(10),
          style = MaterialTheme.typography.labelSmall,
          color = WashiMuted,
        )
        Text(
          text = scoreState.opponentScore.toString(),
          style = MaterialTheme.typography.headlineMedium,
          color = SakuraPink,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.testTag("opponent_score_text"),
        )
      }
    }
  }
}

@Composable
private fun ActiveRelicsRow(
  activeRelics: List<RogueRelic>,
  paperCoins: Int,
  bagRemaining: Int,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    LazyRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.weight(1f),
    ) {
      items(activeRelics) { relic ->
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(IndigoSurface)
              .border(1.dp, KyotoGold.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = KyotoGold,
            modifier = Modifier.size(12.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "${relic.title} (${relic.badge})",
            style = MaterialTheme.typography.labelSmall,
            color = WashiCream,
          )
        }
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    Text(
      text = "Bag: $bagRemaining • $paperCoins Coins",
      style = MaterialTheme.typography.labelSmall,
      color = KyotoGold,
    )
  }
}

@Composable
private fun TacticalActionBar(
  hasPendingTiles: Boolean,
  isValidMove: Boolean,
  pendingScore: Int,
  onRecall: () -> Unit,
  onShuffle: () -> Unit,
  onHint: () -> Unit,
  onAutoFold: () -> Unit,
  onPass: () -> Unit,
  onSubmit: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Recall or Shuffle button
    FilledTonalButton(
      onClick = if (hasPendingTiles) onRecall else onShuffle,
      shape = RoundedCornerShape(12.dp),
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
      colors =
        ButtonDefaults.filledTonalButtonColors(
          containerColor = IndigoElevated,
          contentColor = WashiCream,
        ),
      modifier = Modifier.height(48.dp).testTag("recall_or_shuffle_button"),
    ) {
      Icon(
        imageVector =
          if (hasPendingTiles) Icons.AutoMirrored.Filled.Undo else Icons.Default.Shuffle,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text =
          if (hasPendingTiles) stringResource(R.string.action_recall)
          else stringResource(R.string.action_shuffle),
        style = MaterialTheme.typography.labelLarge,
      )
    }

    // Trie Hint button
    FilledTonalButton(
      onClick = onHint,
      shape = RoundedCornerShape(12.dp),
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
      colors =
        ButtonDefaults.filledTonalButtonColors(
          containerColor = IndigoElevated,
          contentColor = KyotoGold,
        ),
      modifier = Modifier.height(48.dp).testTag("trie_hint_button"),
    ) {
      Icon(
        imageVector = Icons.Default.Lightbulb,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(3.dp))
      Text(
        text = stringResource(R.string.action_hint),
        style = MaterialTheme.typography.labelLarge,
      )
    }

    // Auto-Fold button (places a valid Trie word on the board automatically!)
    FilledTonalButton(
      onClick = onAutoFold,
      shape = RoundedCornerShape(12.dp),
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
      colors =
        ButtonDefaults.filledTonalButtonColors(
          containerColor = IndigoElevated,
          contentColor = CeladonJade,
        ),
      modifier = Modifier.height(48.dp).testTag("auto_fold_button"),
    ) {
      Icon(
        imageVector = Icons.Default.Bolt,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(3.dp))
      Text(
        text = stringResource(R.string.action_auto_fold),
        style = MaterialTheme.typography.labelLarge,
      )
    }

    // Pass button
    IconButton(
      onClick = onPass,
      modifier =
        Modifier.size(48.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(IndigoElevated)
          .testTag("pass_turn_button"),
    ) {
      Icon(
        imageVector = Icons.Default.SkipNext,
        contentDescription = stringResource(R.string.action_pass),
        tint = WashiMuted,
      )
    }

    // Primary Submit Word button
    Button(
      onClick = onSubmit,
      enabled = hasPendingTiles,
      shape = RoundedCornerShape(14.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = if (isValidMove) VermilionRed else IndigoElevated,
          contentColor = Color.White,
        ),
      contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      modifier = Modifier.weight(1f).height(48.dp).testTag("submit_word_button"),
    ) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = if (isValidMove && pendingScore > 0) "FOLD +$pendingScore" else "FOLD",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun PassDeviceInterstitialCard(
  turnNumber: Int,
  onReady: () -> Unit,
) {
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = IndigoNight.copy(alpha = 0.96f)),
    modifier =
      Modifier.fillMaxWidth(0.88f)
        .border(2.dp, KyotoGold, RoundedCornerShape(20.dp)),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "PASS DEVICE",
        style = MaterialTheme.typography.headlineMedium,
        color = KyotoGold,
        fontWeight = FontWeight.Bold,
      )
      val nextPlayer = if (turnNumber % 2 == 0) "Player 2" else "Player 1"
      Text(
        text = "Hand the device to $nextPlayer so your letter rack remains secret.",
        style = MaterialTheme.typography.bodyLarge,
        color = WashiCream,
      )
      Button(
        onClick = onReady,
        colors = ButtonDefaults.buttonColors(containerColor = VermilionRed),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("confirm_pass_device_button"),
      ) {
        Text("I AM $nextPlayer — SHOW RACK", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun MatchOverOverlayCard(
  gameState: GameState,
  onPlayAgain: () -> Unit,
) {
  val won = gameState.scoreState.playerScore >= gameState.scoreState.opponentScore
  Card(
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = IndigoNight.copy(alpha = 0.96f)),
    modifier =
      Modifier.fillMaxWidth(0.9f)
        .border(2.dp, if (won) CeladonJade else KyotoGold, RoundedCornerShape(22.dp)),
  ) {
    Column(
      modifier = Modifier.padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = if (won) "ORIGAMI VICTORY!" else "MATCH COMPLETE",
        style = MaterialTheme.typography.headlineLarge,
        color = if (won) CeladonJade else KyotoGold,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = gameState.lastMoveSummary,
        style = MaterialTheme.typography.bodyLarge,
        color = WashiCream,
      )
      Button(
        onClick = onPlayAgain,
        colors = ButtonDefaults.buttonColors(containerColor = VermilionRed),
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("play_again_button"),
      ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text("START NEW MATCH", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun CampaignAndModesScreen(
  gameState: GameState,
  onStartMode: (GameMode, AiOpponent) -> Unit,
  onToggleRelic: (RogueRelic) -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize().widthIn(max = 600.dp),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
          Image(
            painter = painterResource(id = R.drawable.img_origami_hero_1794379928974),
            contentDescription = stringResource(R.string.cd_hero_banner),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(Color.Transparent, IndigoNight.copy(alpha = 0.9f))
                  )
                )
          )
          Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
              text = "ORIGAMI GRAND CAMPAIGN",
              style = MaterialTheme.typography.headlineMedium,
              color = KyotoGold,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Select game mode, rogue-lite run relics, and AI Scribe opponent",
              style = MaterialTheme.typography.bodyMedium,
              color = WashiCream,
            )
          }
        }
      }
    }

    // Quick Mode Cards (Pass & Play and Daily Crane Blitz)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = IndigoCard),
          shape = RoundedCornerShape(16.dp),
          modifier =
            Modifier.weight(1f)
              .border(1.dp, CeladonJade.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
              .clickable { onStartMode(GameMode.DAILY_BLITZ, gameState.opponent) }
              .testTag("start_daily_blitz_card"),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "DAILY CRANE BLITZ",
              style = MaterialTheme.typography.labelLarge,
              color = CeladonJade,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Pre-seeded 'ORIGAMI' board • 35s rapid turn timer",
              style = MaterialTheme.typography.bodyMedium,
              color = WashiCream,
            )
          }
        }

        Card(
          colors = CardDefaults.cardColors(containerColor = IndigoCard),
          shape = RoundedCornerShape(16.dp),
          modifier =
            Modifier.weight(1f)
              .border(1.dp, SakuraPink.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
              .clickable { onStartMode(GameMode.PASS_AND_PLAY, gameState.opponent) }
              .testTag("start_pass_and_play_card"),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "PASS & PLAY 2P",
              style = MaterialTheme.typography.labelLarge,
              color = SakuraPink,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Local 2-player tabletop duel with secret rack handoff",
              style = MaterialTheme.typography.bodyMedium,
              color = WashiCream,
            )
          }
        }
      }
    }

    // Rogue-Lite Relics Loadout
    item {
      Text(
        text = "ACTIVE ROGUE-LITE RELICS (EQUIP UP TO 3)",
        style = MaterialTheme.typography.labelLarge,
        color = KyotoGold,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (relic in RogueRelic.entries) {
          val isEquipped = relic in gameState.activeRelics
          FilterChip(
            selected = isEquipped,
            onClick = { onToggleRelic(relic) },
            label = {
              Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                  text = "${relic.title} (${relic.badge})",
                  style = MaterialTheme.typography.titleMedium,
                  color = if (isEquipped) KyotoGold else WashiCream,
                )
                Text(
                  text = relic.subtitle,
                  style = MaterialTheme.typography.bodyMedium,
                  color = WashiMuted,
                )
              }
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = IndigoElevated,
                containerColor = IndigoCard,
              ),
            modifier = Modifier.fillMaxWidth().testTag("relic_chip_${relic.id}"),
          )
        }
      }
    }

    // Campaign AI Opponents
    item {
      Text(
        text = "THEMED AI SCRIBE OPPONENTS",
        style = MaterialTheme.typography.labelLarge,
        color = KyotoGold,
      )
    }

    items(GameCatalog.CAMPAIGN_OPPONENTS) { opponent ->
      val isSelected =
        gameState.mode == GameMode.CAMPAIGN && gameState.opponent.stage == opponent.stage
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        modifier =
          Modifier.fillMaxWidth()
            .border(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) VermilionRed else IndigoElevated,
              shape = RoundedCornerShape(18.dp),
            ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "STAGE ${opponent.stage} • ${opponent.title.uppercase()}",
              style = MaterialTheme.typography.labelSmall,
              color = KyotoGold,
            )
            Text(
              text = opponent.name,
              style = MaterialTheme.typography.titleLarge,
              color = WashiCream,
            )
            Text(
              text = "\"${opponent.quote}\"",
              style = MaterialTheme.typography.bodyMedium,
              color = WashiMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Target: ${opponent.targetScore} pts • Reward: +${opponent.rewardCoins} Paper Coins",
              style = MaterialTheme.typography.labelSmall,
              color = CeladonJade,
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Button(
            onClick = { onStartMode(GameMode.CAMPAIGN, opponent) },
            colors = ButtonDefaults.buttonColors(containerColor = VermilionRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("challenge_ai_stage_${opponent.stage}"),
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("DUEL")
          }
        }
      }
    }
  }
}

@Composable
private fun CosmeticTileVaultScreen(
  gameState: GameState,
  onSelectOrUnlockSkin: (CosmeticTileSkin, androidx.compose.ui.hapticfeedback.HapticFeedback) -> Unit,
) {
  val haptics = LocalHapticFeedback.current
  LazyColumn(
    modifier = Modifier.fillMaxSize().widthIn(max = 600.dp),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "ORIGAMI TILE VAULT",
            style = MaterialTheme.typography.headlineMedium,
            color = KyotoGold,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Unlock mythic washi materials & animated letter borders",
            style = MaterialTheme.typography.bodyMedium,
            color = WashiMuted,
          )
        }
        Surface(
          color = IndigoCard,
          shape = CircleShape,
          modifier = Modifier.border(1.dp, KyotoGold, CircleShape),
        ) {
          Text(
            text = "${gameState.paperCoins} Coins",
            style = MaterialTheme.typography.labelLarge,
            color = KyotoGold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          )
        }
      }
    }

    items(GameCatalog.COSMETIC_SKINS) { skin ->
      val isUnlocked = skin.id in gameState.unlockedSkinIds
      val isEquipped = gameState.equippedSkin.id == skin.id
      val canAfford = gameState.paperCoins >= skin.costCoins

      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        modifier =
          Modifier.fillMaxWidth()
            .border(
              width = if (isEquipped) 2.dp else 1.dp,
              color = if (isEquipped) CeladonJade else Color(skin.borderColorHex),
              shape = RoundedCornerShape(18.dp),
            ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Sample Cosmetic Tile Preview
          Box(
            modifier =
              Modifier.size(width = 58.dp, height = 66.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(skin.baseColorHex))
                .border(2.5.dp, Color(skin.borderColorHex), RoundedCornerShape(12.dp)),
          ) {
            Text(
              text = "W",
              style = MaterialTheme.typography.headlineLarge,
              color = Color(skin.textColorHex),
              fontWeight = FontWeight.Bold,
              modifier = Modifier.align(Alignment.Center),
            )
            Text(
              text = "4",
              style = MaterialTheme.typography.labelSmall,
              color = Color(skin.textColorHex),
              modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
          }

          Spacer(modifier = Modifier.width(16.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = skin.rarity,
              style = MaterialTheme.typography.labelSmall,
              color = Color(skin.accentGlowHex),
            )
            Text(
              text = skin.name,
              style = MaterialTheme.typography.titleLarge,
              color = WashiCream,
            )
            Text(
              text = skin.description,
              style = MaterialTheme.typography.bodyMedium,
              color = WashiMuted,
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          Button(
            onClick = { onSelectOrUnlockSkin(skin, haptics) },
            enabled = isUnlocked || canAfford,
            colors =
              ButtonDefaults.buttonColors(
                containerColor =
                  when {
                    isEquipped -> CeladonJade
                    isUnlocked -> IndigoElevated
                    else -> VermilionRed
                  }
              ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag("skin_action_${skin.id}"),
          ) {
            if (!isUnlocked) {
              Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
              text =
                when {
                  isEquipped -> "EQUIPPED"
                  isUnlocked -> "EQUIP"
                  else -> "${skin.costCoins} C"
                },
              color = if (isEquipped) IndigoNight else Color.White,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LexiconAndCloudScreen(
  gameState: GameState,
  cloudMatchesState: CloudUiState<List<SavedMatchDocument>>,
  viewModel: GameViewModel,
  onSignOut: () -> Unit,
) {
  var queryWord by remember { mutableStateOf("ORIGAMI") }

  val isValidWord = remember(queryWord) { viewModel.trieEngine.isValidWord(queryWord.trim()) }
  val anagrams =
    remember(queryWord) {
      viewModel.trieEngine.findAnagrams(
        queryWord.trim().toList(),
        minLength = 2,
        maxResults = 16,
      )
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().widthIn(max = 600.dp),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "TRIE ENGINE & CLOUD SYNC",
            style = MaterialTheme.typography.headlineMedium,
            color = KyotoGold,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "${viewModel.trieEngine.wordCount} Tournament Words • Best Word: ${gameState.bestWordPlayed.ifBlank { "—" }} (${gameState.highestSingleTurnScore} pts)",
            style = MaterialTheme.typography.bodyMedium,
            color = WashiMuted,
          )
        }
        OutlinedButton(
          onClick = onSignOut,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.testTag("sign_out_button"),
        ) {
          Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = null,
            tint = WashiCream,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(stringResource(R.string.sign_out), color = WashiCream)
        }
      }
    }

    // Interactive Trie Validator & Anagram Solver Card
    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = IndigoCard),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "IN-MEMORY TRIE WORD VALIDATOR (<2ms)",
            style = MaterialTheme.typography.labelLarge,
            color = CeladonJade,
          )
          OutlinedTextField(
            value = queryWord,
            onValueChange = { queryWord = it.uppercase().take(12) },
            label = { Text("Enter Letters or Word to Validate & Solve") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("trie_search_input"),
          )
          Text(
            text =
              if (isValidWord) "✓ '${queryWord.trim()}' is a VALID Origami Lexicon word!"
              else "✗ '${queryWord.trim()}' is not a valid standalone word.",
            style = MaterialTheme.typography.titleMedium,
            color = if (isValidWord) CeladonJade else VermilionRed,
          )
          if (anagrams.isNotEmpty()) {
            Text(
              text = "Sub-Anagrams (${anagrams.size}): ${anagrams.joinToString(", ")}",
              style = MaterialTheme.typography.bodyMedium,
              color = WashiCream,
            )
          }
        }
      }
    }

    // Cloud Firestore Match History
    item {
      Text(
        text = "CLOUD FIRESTORE MATCH HISTORY",
        style = MaterialTheme.typography.labelLarge,
        color = KyotoGold,
      )
    }

    when (cloudMatchesState) {
      is CloudUiState.Loading -> {
        item {
          Text(
            text = "Syncing matches from Cloud Firestore...",
            style = MaterialTheme.typography.bodyMedium,
            color = WashiMuted,
          )
        }
      }
      is CloudUiState.Error -> {
        item {
          Text(
            text = cloudMatchesState.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      is CloudUiState.Success -> {
        val matches = cloudMatchesState.data
        if (matches.isEmpty()) {
          item {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = IndigoCard),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "No cloud matches recorded yet — fold your first word on the Blitz Board!",
                style = MaterialTheme.typography.bodyMedium,
                color = WashiMuted,
                modifier = Modifier.padding(16.dp),
              )
            }
          }
        } else {
          items(matches, key = { it.id }) { match ->
            Card(
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = IndigoCard),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text(
                    text = "${match.mode} vs ${match.opponentName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = WashiCream,
                  )
                  Text(
                    text =
                      "Turn ${match.turnNumber} • Best Word: ${match.bestPlayedWord.ifBlank { "—" }} • Status: ${match.status}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WashiMuted,
                  )
                }
                Text(
                  text = "${match.playerScore} - ${match.opponentScore}",
                  style = MaterialTheme.typography.titleLarge,
                  color = CeladonJade,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }
    }
  }
}
