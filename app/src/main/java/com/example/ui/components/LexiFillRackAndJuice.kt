package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.domain.model.CosmeticTileSkin
import com.example.domain.model.DropPreviewEvaluation
import com.example.domain.model.LevelClearSummary
import com.example.domain.model.PuzzleState
import com.example.domain.model.Tile
import com.example.domain.model.TileBonusEffect
import com.example.ui.theme.CeladonJade
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoElevated
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.IndigoSurface
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import com.example.ui.theme.WashiMuted
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Live Dual-Axis Intersection & Clue Status Banner.
 * Shows real-time horizontal + vertical word validation while hovering a dragged tile over a blank slot,
 * or shows the active unsolved crossword clue skeleton when idle.
 */
@Composable
fun LiveIntersectionPreviewBanner(
  puzzleState: PuzzleState,
  modifier: Modifier = Modifier,
) {
  val preview: DropPreviewEvaluation? = puzzleState.hoverPreview
  val borderColor =
    when {
      preview != null && preview.isDualAxisValid -> CeladonJade
      preview != null && !preview.isDualAxisValid -> VermilionRed
      puzzleState.isStatusError -> VermilionRed
      else -> IndigoElevated
    }

  val bgColor =
    when {
      preview != null && preview.isDualAxisValid -> Color(0xFF0A2E24)
      preview != null && !preview.isDualAxisValid -> Color(0xFF33141E)
      puzzleState.isStatusError -> Color(0xFF2D141C)
      else -> IndigoSurface
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
        .testTag("intersection_preview_banner"),
    shape = RoundedCornerShape(14.dp),
    color = bgColor,
  ) {
    if (preview != null && preview.isValidSlot) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector =
            if (preview.isDualAxisValid) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
          contentDescription = "Dual axis validation indicator",
          tint = if (preview.isDualAxisValid) CeladonJade else VermilionRed,
          modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          val axesText =
            buildList {
                preview.horizontalPatternAfter?.let { add("ACROSS: $it") }
                preview.verticalPatternAfter?.let { add("DOWN: $it") }
              }
              .joinToString("  •  ")
          Text(
            text = axesText.ifEmpty { "Target Slot (${preview.targetCoord.x + 1}, ${preview.targetCoord.y + 1})" },
            style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = WashiCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text =
              if (preview.isDualAxisValid) {
                if (preview.wouldCompleteWords.size >= 2) {
                  "DUAL INTERSECTION CLEAR! Completes ${preview.wouldCompleteWords.joinToString(" & ")}"
                } else if (preview.wouldCompleteWords.size == 1) {
                  "Trie Validated ✓ Completes \"${preview.wouldCompleteWords.first()}\""
                } else {
                  "Fits intersecting skeleton constraints"
                }
              } else {
                preview.invalidReason ?: "Conflicts with intersecting word"
              },
            style = MaterialTheme.typography.bodySmall,
            color = if (preview.isDualAxisValid) CeladonJade else Color(0xFFFF9EA8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (preview.isDualAxisValid && preview.estimatedScoreGain > 0) {
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(KyotoGold.copy(alpha = 0.2f))
                .border(1.dp, KyotoGold, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
          ) {
            Text(
              text = "+${preview.estimatedScoreGain}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.ExtraBold,
              color = KyotoGold,
            )
          }
        }
      }
    } else {
      // Idle state: show next unsolved TargetWord skeleton & status
      val nextTarget = puzzleState.targetWords.firstOrNull { !it.isCompleted }
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector =
            if (puzzleState.isStatusError) Icons.Filled.WarningAmber else Icons.Filled.AutoAwesome,
          contentDescription = "Puzzle status",
          tint = if (puzzleState.isStatusError) VermilionRed else SkyCyan,
          modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = puzzleState.statusMessage,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (puzzleState.isStatusError) Color(0xFFFFB4BC) else WashiCream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (nextTarget != null) {
            Text(
              text =
                "${nextTarget.direction.label.uppercase()}: ${nextTarget.formattedSkeleton(puzzleState.cells)} — ${nextTarget.clue}",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = WashiMuted,
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
 * Spring-physics Draggable Letter Rack for LexiFill: Crossword Skeleton.
 * Supports smooth 60/120 FPS drag-and-drop onto blank slots with magnetic ghost preview
 * and bouncy return-to-rack spring animation (`Animatable`) when released outside a valid slot.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LexiFillDraggableRack(
  tiles: List<Tile>,
  selectedTileId: String?,
  equippedSkin: CosmeticTileSkin,
  hintsRemaining: Int,
  hasUnlockedPlacedTiles: Boolean,
  onTileTapped: (Tile) -> Unit,
  onTileDragStart: (Tile) -> Unit,
  onTileDragMove: (Tile, Offset) -> Unit,
  onTileDragDrop: (Tile, Offset) -> Boolean,
  onTileDragCancel: () -> Unit,
  onShuffleRack: () -> Unit,
  onTriggerHint: () -> Unit,
  onRecallUnlocked: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.fillMaxWidth().testTag("lexifill_rack_container"),
    shape = RoundedCornerShape(18.dp),
    color = IndigoSurface,
    tonalElevation = 6.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "LETTER INVENTORY (${tiles.size} REMAINING)",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = WashiMuted,
        )
        Text(
          text = "Drag to blank slot or Tap tile → Tap slot",
          style = MaterialTheme.typography.labelSmall,
          color = SkyCyan,
        )
      }

      if (tiles.isEmpty()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(58.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(IndigoNight.copy(alpha = 0.6f)),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "All skeleton blanks filled! Level Complete!",
            style = MaterialTheme.typography.labelLarge,
            color = CeladonJade,
            fontWeight = FontWeight.Bold,
          )
        }
      } else {
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          for (tile in tiles) {
            SpringDraggableRackTile(
              tile = tile,
              isSelected = tile.id == selectedTileId,
              skin = equippedSkin,
              onTap = { onTileTapped(tile) },
              onDragStart = { onTileDragStart(tile) },
              onDragMove = { rootPoint -> onTileDragMove(tile, rootPoint) },
              onDragDrop = { rootPoint -> onTileDragDrop(tile, rootPoint) },
              onDragCancel = onTileDragCancel,
            )
          }
        }
      }

      // Utility Action Bar: Shuffle Rack, Smart Hint, Recall Unlocked
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onShuffleRack,
          modifier = Modifier.weight(1f).height(48.dp).testTag("btn_shuffle_rack"),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Shuffle,
            contentDescription = "Shuffle rack tiles",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Shuffle", style = MaterialTheme.typography.labelLarge)
        }

        FilledTonalButton(
          onClick = onTriggerHint,
          enabled = hintsRemaining > 0 && tiles.isNotEmpty(),
          modifier = Modifier.weight(1.15f).height(48.dp).testTag("btn_smart_hint"),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = KyotoGold.copy(alpha = 0.18f),
              contentColor = KyotoGold,
            ),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Lightbulb,
            contentDescription = "Smart intersection hint",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Hint ($hintsRemaining)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }

        if (hasUnlockedPlacedTiles) {
          OutlinedButton(
            onClick = onRecallUnlocked,
            modifier = Modifier.weight(1f).height(48.dp).testTag("btn_recall_unlocked"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Undo,
              contentDescription = "Recall unlocked tiles",
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Recall", style = MaterialTheme.typography.labelLarge)
          }
        }
      }
    }
  }
}

@Composable
private fun SpringDraggableRackTile(
  tile: Tile,
  isSelected: Boolean,
  skin: CosmeticTileSkin,
  onTap: () -> Unit,
  onDragStart: () -> Unit,
  onDragMove: (Offset) -> Unit,
  onDragDrop: (Offset) -> Boolean,
  onDragCancel: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val dragOffset = remember(tile.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
  var isDragging by remember(tile.id) { mutableStateOf(false) }
  var tileRootCenter by remember(tile.id) { mutableStateOf(Offset.Zero) }

  val scale by
    animateFloatAsState(
      targetValue =
        when {
          isDragging -> 1.24f
          isSelected -> 1.12f
          else -> 1.0f
        },
      animationSpec =
        spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMedium,
        ),
      label = "rack_tile_scale",
    )

  val baseColor = Color(skin.baseColorHex)
  val borderColor =
    when {
      isDragging -> SkyCyan
      isSelected -> KyotoGold
      else -> Color(skin.borderColorHex)
    }
  val textColor = Color(skin.textColorHex)

  Box(
    modifier =
      Modifier.zIndex(if (isDragging) 50f else if (isSelected) 10f else 1f)
        .offset {
          IntOffset(
            dragOffset.value.x.roundToInt(),
            dragOffset.value.y.roundToInt(),
          )
        }
        .scale(scale)
        .size(50.dp)
        .shadow(
          elevation = if (isDragging) 14.dp else 4.dp,
          shape = RoundedCornerShape(12.dp),
        )
        .clip(RoundedCornerShape(12.dp))
        .background(baseColor)
        .border(
          width = if (isDragging || isSelected) 2.5.dp else 1.5.dp,
          color = borderColor,
          shape = RoundedCornerShape(12.dp),
        )
        .onGloballyPositioned { coords ->
          val pos = coords.positionInRoot()
          tileRootCenter =
            Offset(
              pos.x + coords.size.width / 2f,
              pos.y + coords.size.height / 2f,
            )
        }
        .clickable { onTap() }
        .pointerInput(tile.id) {
          detectDragGestures(
            onDragStart = {
              isDragging = true
              onDragStart()
            },
            onDrag = { change, dragAmount ->
              change.consume()
              val nextOffset = dragOffset.value + dragAmount
              scope.launch { dragOffset.snapTo(nextOffset) }
              // Calculate finger center in root coordinates for magnetic board hit-testing
              val currentRootPoint = tileRootCenter + nextOffset
              onDragMove(currentRootPoint)
            },
            onDragEnd = {
              val dropRootPoint = tileRootCenter + dragOffset.value
              isDragging = false
              val accepted = onDragDrop(dropRootPoint)
              if (!accepted) {
                // Spring back to rack slot with bouncy physics!
                scope.launch {
                  dragOffset.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec =
                      spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                      ),
                  )
                }
              }
            },
            onDragCancel = {
              isDragging = false
              onDragCancel()
              scope.launch {
                dragOffset.animateTo(
                  targetValue = Offset.Zero,
                  animationSpec =
                    spring(
                      dampingRatio = Spring.DampingRatioMediumBouncy,
                      stiffness = Spring.StiffnessMediumLow,
                    ),
                )
              }
            },
          )
        }
        .testTag("rack_tile_${tile.char}_${tile.id}"),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = tile.char.toString(),
      fontSize = 23.sp,
      fontWeight = FontWeight.ExtraBold,
      color = textColor,
    )
    Text(
      text = tile.baseScore.toString(),
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = textColor.copy(alpha = 0.72f),
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
    )
    if (tile.bonusEffect != TileBonusEffect.NONE) {
      Box(
        modifier =
          Modifier.align(Alignment.TopStart)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(KyotoGold)
            .padding(horizontal = 3.dp, vertical = 1.dp)
      ) {
        Text(
          text = tile.bonusEffect.badge,
          fontSize = 7.sp,
          fontWeight = FontWeight.Black,
          color = IndigoNight,
        )
      }
    }
  }
}

/**
 * Celebratory Level Clear Fanfare Overlay with particle burst, star rating,
 * score breakdown, and automatic countdown transition to the next Crossword Skeleton puzzle.
 */
@Composable
fun LevelClearFanfareOverlay(
  visible: Boolean,
  summary: LevelClearSummary?,
  countdownSec: Int?,
  onNextLevelNow: () -> Unit,
  onReplayLevel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible && summary != null,
    enter = fadeIn(tween(240)) + scaleIn(tween(300, easing = FastOutSlowInEasing)),
    exit = fadeOut(tween(180)),
    modifier = modifier,
  ) {
    if (summary == null) return@AnimatedVisibility
    val infinite = rememberInfiniteTransition(label = "fanfare_particles")
    val orbitPhase by
      infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Restart),
        label = "fanfare_orbit",
      )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(IndigoNight.copy(alpha = 0.86f))
          .padding(20.dp)
          .testTag("level_clear_fanfare_modal"),
      contentAlignment = Alignment.Center,
    ) {
      // Celebratory radial particle halo
      Canvas(modifier = Modifier.size(320.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.42f
        for (i in 0 until 24) {
          val angle = (2.0 * PI * i) / 24.0 + orbitPhase * 0.5
          val r = baseRadius * (0.78f + 0.22f * sin(orbitPhase + i).toFloat())
          val px = center.x + (cos(angle) * r).toFloat()
          val py = center.y + (sin(angle) * r).toFloat()
          val col =
            when (i % 3) {
              0 -> KyotoGold
              1 -> CeladonJade
              else -> SkyCyan
            }
          drawCircle(
            color = col.copy(alpha = 0.75f),
            radius = if (i % 2 == 0) 6.dp.toPx() else 4.dp.toPx(),
            center = Offset(px, py),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(24.dp),
        color = IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .border(
              2.dp,
              Brush.linearGradient(listOf(KyotoGold, CeladonJade, SkyCyan)),
              RoundedCornerShape(24.dp),
            ),
      ) {
        Column(
          modifier = Modifier.padding(22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier =
              Modifier.size(60.dp)
                .clip(CircleShape)
                .background(KyotoGold.copy(alpha = 0.18f))
                .border(2.dp, KyotoGold, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Filled.EmojiEvents,
              contentDescription = "Level cleared trophy",
              tint = KyotoGold,
              modifier = Modifier.size(34.dp),
            )
          }

          Text(
            text = "SKELETON SOLVED!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = KyotoGold,
          )
          Text(
            text = "Level ${summary.levelNumber} · ${summary.levelTitle}",
            style = MaterialTheme.typography.titleMedium,
            color = WashiCream,
          )

          // Star Rating Row
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in 1..3) {
              val earned = s <= summary.stars
              Text(
                text = "★",
                fontSize = 30.sp,
                color = if (earned) KyotoGold else IndigoElevated,
              )
            }
          }

          // Breakdown Card
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = IndigoCard,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              StatRow("Intersecting Words Solved", "${summary.wordsCompleted}")
              StatRow("Max Combo Streak", "×${summary.maxCombo}")
              StatRow("Zero-Error Accuracy Bonus", "+${summary.accuracyBonus} PTS")
              StatRow("LexiCoins Awarded", "+${summary.coinsEarned} 🪙")
              StatRow("Total Level Score", "${summary.levelScore} PTS", highlight = true)
            }
          }

          if (countdownSec != null && countdownSec > 0) {
            Text(
              text = "Auto-transitioning to Level ${summary.levelNumber + 1} in ${countdownSec}s...",
              style = MaterialTheme.typography.labelMedium,
              color = SkyCyan,
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onReplayLevel,
              modifier = Modifier.weight(1f).height(48.dp).testTag("btn_replay_level"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(Icons.Filled.Replay, contentDescription = "Replay puzzle", modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Replay")
            }
            Button(
              onClick = onNextLevelNow,
              modifier = Modifier.weight(1.3f).height(48.dp).testTag("btn_next_puzzle_now"),
              shape = RoundedCornerShape(12.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = CeladonJade,
                  contentColor = IndigoNight,
                ),
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = "Next puzzle", modifier = Modifier.size(20.dp))
              Spacer(Modifier.width(6.dp))
              Text("Next Puzzle", fontWeight = FontWeight.ExtraBold)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StatRow(label: String, value: String, highlight: Boolean = false) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = if (highlight) WashiCream else WashiMuted,
      fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.labelLarge,
      color = if (highlight) KyotoGold else CeladonJade,
      fontWeight = FontWeight.ExtraBold,
    )
  }
}
