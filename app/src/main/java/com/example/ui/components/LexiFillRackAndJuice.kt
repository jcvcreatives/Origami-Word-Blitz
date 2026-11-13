package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
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
import com.example.domain.model.WordDirection
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
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Live Dual-Axis Intersection & Active Clue Washi-Paper Banner.
 * Shows real-time horizontal + vertical word validation while hovering a dragged tile over a blank slot,
 * or displays a crisp interactive Active Clue Spotlight with paper letter slots when idle.
 */
@Composable
fun LiveIntersectionPreviewBanner(
  puzzleState: PuzzleState,
  modifier: Modifier = Modifier,
) {
  val preview: DropPreviewEvaluation? = puzzleState.hoverPreview
  val focusedWord =
    remember(puzzleState.focusedWordId, puzzleState.targetWords) {
      puzzleState.targetWords.find { it.id == puzzleState.focusedWordId }
        ?: puzzleState.targetWords.firstOrNull { !it.isCompleted }
        ?: puzzleState.targetWords.firstOrNull()
    }

  val borderColor =
    when {
      preview != null && preview.isDualAxisValid -> CeladonJade
      preview != null && !preview.isDualAxisValid -> VermilionRed
      puzzleState.isStatusError -> VermilionRed
      else -> IndigoElevated
    }

  val bgColor =
    when {
      preview != null && preview.isDualAxisValid -> CompletedWordBg
      preview != null && !preview.isDualAxisValid -> Color(0xFFFCE8E6)
      puzzleState.isStatusError -> Color(0xFFFCE8E6)
      else -> IndigoSurface
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp))
        .paperGrainOverlay(alpha = 0.045f, fiberCount = 32, speckleCount = 48, seed = 505)
        .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
        .testTag("intersection_preview_banner"),
    shape = RoundedCornerShape(14.dp),
    color = bgColor,
  ) {
    if (preview != null && preview.isValidSlot) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                  "Magnetic Lock ✓ Completes \"${preview.wouldCompleteWords.first()}\""
                } else {
                  "Magnetic Lock ✓ Fits intersecting crossword fold"
                }
              } else {
                preview.invalidReason ?: "Conflicts with intersecting word"
              },
            style = MaterialTheme.typography.bodySmall,
            color = if (preview.isDualAxisValid) CeladonJade else VermilionRed,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (preview.isDualAxisValid && preview.estimatedScoreGain > 0) {
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(KyotoGold.copy(alpha = 0.16f))
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
      // Idle state: Active Clue Washi-Paper Spotlight Card with boxed letter slots
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            if (focusedWord != null) {
              val isAcross = focusedWord.direction == WordDirection.HORIZONTAL
              Box(
                modifier =
                  Modifier.clip(RoundedCornerShape(6.dp))
                    .background(
                      if (focusedWord.isCompleted) CompletedWordBg
                      else SkyCyan.copy(alpha = 0.12f)
                    )
                    .border(
                      1.dp,
                      if (focusedWord.isCompleted) CeladonJade else SkyCyan.copy(alpha = 0.5f),
                      RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
              ) {
                Text(
                  text = if (isAcross) "ACROSS →" else "DOWN ↓",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = if (focusedWord.isCompleted) CeladonJade else SkyCyan,
                )
              }
              Text(
                text = focusedWord.clue,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = WashiCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            } else {
              Icon(
                imageVector =
                  if (puzzleState.isStatusError) Icons.Filled.WarningAmber
                  else Icons.Filled.AutoAwesome,
                contentDescription = "Puzzle status",
                tint = if (puzzleState.isStatusError) VermilionRed else SkyCyan,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = puzzleState.statusMessage,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (puzzleState.isStatusError) VermilionRed else WashiCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          // Visual paper letter slots for the focused word
          if (focusedWord != null) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(3.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              focusedWord.coordinates().forEachIndexed { idx, coord ->
                val cell = puzzleState.cellAt(coord.x, coord.y)
                val ch = cell?.activeLetter
                val isBlank = idx in focusedWord.missingBlankIndices
                Box(
                  modifier =
                    Modifier.size(20.dp)
                      .clip(RoundedCornerShape(4.dp))
                      .background(
                        when {
                          focusedWord.isCompleted -> CompletedWordBg
                          ch != null && isBlank -> KyotoGold.copy(alpha = 0.18f)
                          ch != null -> IndigoCard
                          else -> IndigoNight
                        }
                      )
                      .border(
                        width = 1.dp,
                        color =
                          when {
                            focusedWord.isCompleted -> CeladonJade
                            ch != null && isBlank -> KyotoGold
                            ch != null -> IndigoElevated
                            else -> SkyCyan.copy(alpha = 0.55f)
                          },
                        shape = RoundedCornerShape(4.dp),
                      ),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = ch?.toString() ?: "_",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    color =
                      when {
                        focusedWord.isCompleted -> CeladonJade
                        ch != null && isBlank -> KyotoGold
                        ch != null -> WashiCream
                        else -> SkyCyan
                      },
                  )
                }
              }
            }
          }
        }

        // Bottom status line
        Text(
          text = puzzleState.statusMessage,
          style = MaterialTheme.typography.bodySmall,
          color = if (puzzleState.isStatusError) VermilionRed else WashiMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Origami Washi-Paper Letter Rack with:
 * 1. Subtle continuous 'float' animation for idle tiles sitting in the rack.
 * 2. Spring-driven magnetic-snap physics effect (`magneticPullOffset`) when dragged near a valid board slot.
 * 3. Bouncy return-to-rack spring animation when released outside a valid slot.
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
  onTileDragMove: (Tile, Offset) -> Offset,
  onTileDragDrop: (Tile, Offset) -> Boolean,
  onTileDragCancel: () -> Unit,
  onShuffleRack: () -> Unit,
  onTriggerHint: () -> Unit,
  onRecallUnlocked: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var shuffleGeneration by remember { mutableIntStateOf(0) }
  val shuffleIconRotation by
    animateFloatAsState(
      targetValue = shuffleGeneration * 360f,
      animationSpec =
        spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow,
        ),
      label = "shuffle_btn_spin",
    )

  // Shared single InfiniteTransition for all idle floating rack tiles (eliminates N per-tile coroutines)
  val sharedRackInfinite = rememberInfiniteTransition(label = "shared_rack_tile_float")
  val sharedFloatPhase by
    sharedRackInfinite.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2500, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "shared_float_phase",
    )

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .shadow(elevation = 3.dp, shape = RoundedCornerShape(18.dp))
        .paperGrainOverlay(alpha = 0.05f, fiberCount = 48, speckleCount = 72, seed = 606)
        .border(1.2.dp, IndigoElevated, RoundedCornerShape(18.dp))
        .testTag("lexifill_rack_container"),
    shape = RoundedCornerShape(18.dp),
    color = IndigoSurface,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Top header + Origami Board Legend pills
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "ORIGAMI PAPER RACK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = WashiCream,
          )
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(6.dp))
                .background(SkyCyan.copy(alpha = 0.14f))
                .padding(horizontal = 6.dp, vertical = 1.dp)
          ) {
            Text(
              text = "${tiles.size} tiles",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = SkyCyan,
            )
          }
        }

        // Compact Multiplier & Nexus Paper Stamp Legend
        Row(
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LegendMiniPill(label = "◆ Crossing", color = KyotoGold)
          LegendMiniPill(label = "2L/3L", color = SkyCyan)
          LegendMiniPill(label = "2W/3W", color = VermilionRed)
        }
      }

      // Recessed Kraft Paper Tile Tray Well
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IndigoCard.copy(alpha = 0.75f))
            .border(1.dp, IndigoElevated, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (tiles.isEmpty()) {
          Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = "All blanks solved",
              tint = CeladonJade,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "All origami folds complete! Level Solved!",
              style = MaterialTheme.typography.labelLarge,
              color = CeladonJade,
              fontWeight = FontWeight.ExtraBold,
            )
          }
        } else {
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            tiles.forEachIndexed { index, tile ->
              key(tile.id) {
                SpringDraggableRackTile(
                  tile = tile,
                  tileIndex = index,
                  sharedFloatPhase = sharedFloatPhase,
                  shuffleGeneration = shuffleGeneration,
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
        }
      }

      // Utility Action Bar: Shuffle Rack, Smart Hint, Recall Unlocked
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = {
            shuffleGeneration++
            onShuffleRack()
          },
          modifier = Modifier.weight(1f).height(48.dp).testTag("btn_shuffle_rack"),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Shuffle,
            contentDescription = "Shuffle rack tiles",
            modifier =
              Modifier.size(18.dp).graphicsLayer {
                rotationZ = shuffleIconRotation
              },
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Shuffle", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }

        FilledTonalButton(
          onClick = onTriggerHint,
          enabled = hintsRemaining > 0 && tiles.isNotEmpty(),
          modifier = Modifier.weight(1.2f).height(48.dp).testTag("btn_smart_hint"),
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
          Text(
            "Smart Hint ($hintsRemaining)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
          )
        }

        if (hasUnlockedPlacedTiles) {
          OutlinedButton(
            onClick = onRecallUnlocked,
            modifier = Modifier.weight(0.95f).height(48.dp).testTag("btn_recall_unlocked"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Undo,
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
private fun LegendMiniPill(label: String, color: Color) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(5.dp))
        .background(color.copy(alpha = 0.12f))
        .border(0.8.dp, color.copy(alpha = 0.48f), RoundedCornerShape(5.dp))
        .padding(horizontal = 5.dp, vertical = 1.5.dp)
  ) {
    Text(
      text = label,
      fontSize = 8.5.sp,
      fontWeight = FontWeight.Bold,
      color = color,
    )
  }
}

@Composable
private fun SpringDraggableRackTile(
  tile: Tile,
  tileIndex: Int,
  sharedFloatPhase: Float,
  shuffleGeneration: Int,
  isSelected: Boolean,
  skin: CosmeticTileSkin,
  onTap: () -> Unit,
  onDragStart: () -> Unit,
  onDragMove: (Offset) -> Offset,
  onDragDrop: (Offset) -> Boolean,
  onDragCancel: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val dragOffset = remember(tile.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
  // Spring-driven magnetic-snap pull offset when dragged near a valid board slot!
  val magneticPullOffset = remember(tile.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
  val tiltZ = remember(tile.id) { Animatable(0f) }

  // 3D Origami Paper Flip & Swirl Shuffle Animatables
  val shuffleOffset = remember(tile.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
  val shuffleFlipY = remember(tile.id) { Animatable(0f) }
  val shuffleTiltZ = remember(tile.id) { Animatable(0f) }
  val shufflePopScale = remember(tile.id) { Animatable(1f) }

  LaunchedEffect(shuffleGeneration) {
    if (shuffleGeneration > 0) {
      // Stagger each tile slightly for a cascading paper-fan shuffle wave
      delay((tileIndex % 8) * 22L)
      val directionSign = if (tileIndex % 2 == 0) 1f else -1f
      val arcX = directionSign * (14f + (tileIndex % 3) * 8f)
      val arcY = -18f - (tileIndex % 4) * 5f

      launch {
        shufflePopScale.snapTo(1.04f)
        shufflePopScale.animateTo(
          targetValue = 1.18f,
          animationSpec = tween(durationMillis = 130, easing = FastOutSlowInEasing),
        )
        shufflePopScale.animateTo(
          targetValue = 1f,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMedium,
            ),
        )
      }
      launch {
        shuffleFlipY.snapTo(0f)
        shuffleFlipY.animateTo(
          targetValue = 360f,
          animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        )
        shuffleFlipY.snapTo(0f)
      }
      launch {
        shuffleTiltZ.snapTo(0f)
        shuffleTiltZ.animateTo(
          targetValue = 0f,
          animationSpec =
            keyframes {
              durationMillis = 380
              (directionSign * 16f) at 110 using FastOutSlowInEasing
              (-directionSign * 9f) at 240 using FastOutSlowInEasing
              0f at 380 using FastOutSlowInEasing
            },
        )
      }
      launch {
        shuffleOffset.animateTo(
          targetValue = Offset(arcX, arcY),
          animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        )
        shuffleOffset.animateTo(
          targetValue = Offset.Zero,
          animationSpec =
            spring(
              dampingRatio = Spring.DampingRatioMediumBouncy,
              stiffness = Spring.StiffnessMediumLow,
            ),
        )
      }
    }
  }

  var isDragging by remember(tile.id) { mutableStateOf(false) }
  var isMagneticallySnapped by remember(tile.id) { mutableStateOf(false) }
  var tileRootCenter by remember(tile.id) { mutableStateOf(Offset.Zero) }
  var dragVelocity by remember(tile.id) { mutableStateOf(Offset.Zero) }
  var trailOffsets by remember(tile.id) { mutableStateOf<List<Offset>>(emptyList()) }

  // 1. Subtle continuous 'float' animation driven by the shared rack phase clock
  val phaseAngle = sharedFloatPhase + (tileIndex * 0.85f)
  val floatAmplitudePx = with(density) { 2.8.dp.toPx() }
  val idleFloatY = if (!isDragging) sin(phaseAngle) * floatAmplitudePx else 0f
  val idleFloatTilt = if (!isDragging) cos(phaseAngle) * 1.4f else 0f

  val scale by
    animateFloatAsState(
      targetValue =
        when {
          isDragging && isMagneticallySnapped -> 1.16f
          isDragging -> 1.24f
          isSelected -> 1.10f
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
      isDragging && isMagneticallySnapped -> CeladonJade
      isDragging -> SkyCyan
      isSelected -> KyotoGold
      else -> Color(skin.borderColorHex)
    }
  val textColor = Color(skin.textColorHex)
  val dogEarFoldPath = remember { Path() }

  // Combined visual offset = raw finger drag + magnetic spring snap pull + shuffle arc + idle rack float
  val totalVisualOffset =
    Offset(
      x = dragOffset.value.x + magneticPullOffset.value.x + shuffleOffset.value.x,
      y = dragOffset.value.y + magneticPullOffset.value.y + shuffleOffset.value.y + idleFloatY,
    )

  Box(
    modifier =
      Modifier.zIndex(if (isDragging) 50f else if (isSelected) 10f else 1f)
        .size(52.dp),
    contentAlignment = Alignment.Center,
  ) {
    // Subtle paper trail & magnetic lock halo while dragging
    if (isDragging) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val currentPos = center + totalVisualOffset

        // Soft paper-breeze trail dots behind dragged tile
        trailOffsets.forEachIndexed { idx, pastOffset ->
          val progress = (idx + 1).toFloat() / trailOffsets.size.toFloat()
          val sparkCenter = center + pastOffset
          val sparkColor = if (isMagneticallySnapped) CeladonJade else SkyCyan
          drawCircle(
            color = sparkColor.copy(alpha = progress * 0.35f),
            radius = (2.5f + 4.5f * progress).dp.toPx(),
            center = sparkCenter,
          )
        }

        // Magnetic lock-on halo around the tile when snapped near a valid board slot
        if (isMagneticallySnapped) {
          drawCircle(
            color = CeladonJade.copy(alpha = 0.35f),
            radius = 32.dp.toPx(),
            center = currentPos,
            style = Stroke(width = 2.2.dp.toPx()),
          )
        }
      }
    }

    Box(
      modifier =
        Modifier.offset {
            IntOffset(
              totalVisualOffset.x.roundToInt(),
              totalVisualOffset.y.roundToInt(),
            )
          }
          .scale(scale * shufflePopScale.value)
          .graphicsLayer {
            rotationZ =
              if (isDragging) tiltZ.value else (idleFloatTilt + shuffleTiltZ.value)
            rotationX = (-dragVelocity.y * 0.35f).coerceIn(-12f, 12f)
            rotationY =
              (dragVelocity.x * 0.35f).coerceIn(-12f, 12f) + shuffleFlipY.value
          }
          .size(50.dp)
          .shadow(
            elevation = if (isDragging) 14.dp else 4.dp,
            shape = RoundedCornerShape(10.dp),
          )
          .clip(RoundedCornerShape(10.dp))
          .background(baseColor)
          .paperGrainOverlay(alpha = 0.05f, fiberCount = 10, speckleCount = 16, seed = 707 + tileIndex)
          .border(
            width = if (isDragging || isSelected) 2.2.dp else 1.4.dp,
            color = borderColor,
            shape = RoundedCornerShape(10.dp),
          )
          .onGloballyPositioned { coords ->
            if (!isDragging) {
              val pos = coords.positionInRoot()
              tileRootCenter =
                Offset(
                  pos.x + coords.size.width / 2f,
                  pos.y + coords.size.height / 2f,
                )
            }
          }
          .clickable { onTap() }
          .pointerInput(tile.id) {
            detectDragGestures(
              onDragStart = {
                isDragging = true
                isMagneticallySnapped = false
                dragVelocity = Offset.Zero
                trailOffsets = listOf(dragOffset.value)
                onDragStart()
              },
              onDrag = { change, dragAmount ->
                change.consume()
                val nextOffset = dragOffset.value + dragAmount
                dragVelocity =
                  Offset(
                    x = dragVelocity.x * 0.55f + dragAmount.x * 0.45f,
                    y = dragVelocity.y * 0.55f + dragAmount.y * 0.45f,
                  )
                trailOffsets = (trailOffsets + nextOffset).takeLast(7)

                val currentFingerRootPoint = tileRootCenter + nextOffset
                val magneticPull = onDragMove(currentFingerRootPoint)
                val snappedNow = hypot(magneticPull.x, magneticPull.y) > 1.5f
                isMagneticallySnapped = snappedNow

                // Dampen tilt when magnetically locked onto a slot so it aligns cleanly
                val targetTilt =
                  if (snappedNow) (dragVelocity.x * 0.18f).coerceIn(-4f, 4f)
                  else (dragVelocity.x * 0.55f).coerceIn(-14f, 14f)

                scope.launch {
                  dragOffset.snapTo(nextOffset)
                  tiltZ.snapTo(targetTilt)
                  // Spring-animate the magnetic pull offset for tactile magnetic-snap physics!
                  magneticPullOffset.animateTo(
                    targetValue = magneticPull,
                    animationSpec =
                      spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                      ),
                  )
                }
              },
              onDragEnd = {
                val dropRootPoint = tileRootCenter + dragOffset.value + magneticPullOffset.value
                isDragging = false
                isMagneticallySnapped = false
                dragVelocity = Offset.Zero
                trailOffsets = emptyList()
                val accepted = onDragDrop(dropRootPoint)
                if (!accepted) {
                  // Elastic rejection wiggle + bouncy spring return to rack slot!
                  scope.launch {
                    launch {
                      magneticPullOffset.animateTo(
                        targetValue = Offset.Zero,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                      )
                    }
                    launch {
                      tiltZ.animateTo(
                        targetValue = 0f,
                        animationSpec =
                          keyframes {
                            durationMillis = 440
                            (-14f) at 80
                            14f at 170
                            (-8f) at 270
                            4f at 360
                            0f at 440
                          },
                      )
                    }
                    dragOffset.animateTo(
                      targetValue = Offset.Zero,
                      animationSpec =
                        spring(
                          dampingRatio = Spring.DampingRatioMediumBouncy,
                          stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                  }
                } else {
                  scope.launch {
                    tiltZ.snapTo(0f)
                    magneticPullOffset.snapTo(Offset.Zero)
                  }
                }
              },
              onDragCancel = {
                isDragging = false
                isMagneticallySnapped = false
                dragVelocity = Offset.Zero
                trailOffsets = emptyList()
                onDragCancel()
                scope.launch {
                  launch { tiltZ.animateTo(0f, tween(200)) }
                  launch { magneticPullOffset.animateTo(Offset.Zero, tween(200)) }
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
      // Subtle top-right origami corner fold on the paper tile (no slash line across letter)
      Canvas(modifier = Modifier.fillMaxSize()) {
        val fold = size.width * 0.18f
        dogEarFoldPath.reset()
        dogEarFoldPath.moveTo(size.width - fold, 0f)
        dogEarFoldPath.lineTo(size.width, fold)
        dogEarFoldPath.lineTo(size.width - fold, fold)
        dogEarFoldPath.close()
        drawPath(
          path = dogEarFoldPath,
          color = borderColor.copy(alpha = 0.28f),
        )
      }

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
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 3.dp),
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
            color = Color.White,
          )
        }
      }
    }
  }
}

/**
 * Celebratory Level Clear Washi-Paper Fanfare Overlay with gentle origami petals,
 * star rating, score breakdown, and automatic countdown transition to the next puzzle.
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
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Restart),
        label = "fanfare_orbit",
      )

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color(0xFF231E18).copy(alpha = 0.62f))
          .padding(20.dp)
          .testTag("level_clear_fanfare_modal"),
      contentAlignment = Alignment.Center,
    ) {
      // Gentle radial paper halo
      Canvas(modifier = Modifier.size(300.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.42f
        for (i in 0 until 16) {
          val angle = (2.0 * PI * i) / 16.0 + orbitPhase * 0.35
          val r = baseRadius * (0.82f + 0.15f * sin(orbitPhase + i).toFloat())
          val px = center.x + (cos(angle) * r).toFloat()
          val py = center.y + (sin(angle) * r).toFloat()
          val col =
            when (i % 3) {
              0 -> KyotoGold
              1 -> CeladonJade
              else -> SkyCyan
            }
          drawCircle(
            color = col.copy(alpha = 0.45f),
            radius = if (i % 2 == 0) 5.dp.toPx() else 3.5.dp.toPx(),
            center = Offset(px, py),
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(22.dp),
        color = IndigoSurface,
        modifier =
          Modifier.fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp))
            .paperGrainOverlay(alpha = 0.048f, fiberCount = 44, speckleCount = 68, seed = 808)
            .border(
              2.dp,
              Brush.linearGradient(listOf(KyotoGold, CeladonJade, SkyCyan)),
              RoundedCornerShape(22.dp),
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
                .background(KyotoGold.copy(alpha = 0.16f))
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
            text = "ORIGAMI SOLVED!",
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
              StatRow("Origami Coins Awarded", "+${summary.coinsEarned} 🪙")
              StatRow("Total Level Score", "${summary.levelScore} PTS", highlight = true)
            }
          }

          if (countdownSec != null && countdownSec > 0) {
            Text(
              text = "Unfolding Level ${summary.levelNumber + 1} in ${countdownSec}s...",
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
                  contentColor = Color.White,
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
