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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.domain.model.WordDirection
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
 * Live Dual-Axis Intersection & Active Clue Spotlight Banner.
 * Shows real-time horizontal + vertical word validation while hovering a dragged tile over a blank slot,
 * or displays a crisp interactive Active Clue Spotlight with boxed letter slots when idle.
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
      else -> SkyCyan.copy(alpha = 0.45f)
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
        .border(1.2.dp, borderColor, RoundedCornerShape(16.dp))
        .testTag("intersection_preview_banner"),
    shape = RoundedCornerShape(16.dp),
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
      // Idle state: Active Clue Spotlight Card with boxed letter slots
      Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                      if (focusedWord.isCompleted) CeladonJade.copy(alpha = 0.2f)
                      else SkyCyan.copy(alpha = 0.18f)
                    )
                    .border(
                      1.dp,
                      if (focusedWord.isCompleted) CeladonJade else SkyCyan.copy(alpha = 0.6f),
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
                color = if (puzzleState.isStatusError) Color(0xFFFFB4BC) else WashiCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          // Visual boxed letter slots for the focused word
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
                          focusedWord.isCompleted -> CeladonJade.copy(alpha = 0.22f)
                          ch != null && isBlank -> KyotoGold.copy(alpha = 0.24f)
                          ch != null -> IndigoElevated
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
                            else -> SkyCyan.copy(alpha = 0.65f)
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
          color = if (puzzleState.isStatusError) Color(0xFFFF9EA8) else WashiMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/**
 * Spring-physics Draggable Letter Rack for LexiFill: Crossword Skeleton.
 * Features a Multiplier & Intersection Legend strip, a dark recessed tile tray well,
 * 3D beveled physical tiles with velocity tilt & comet trail, and quick utility actions.
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
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.dp, IndigoElevated, RoundedCornerShape(20.dp))
        .testTag("lexifill_rack_container"),
    shape = RoundedCornerShape(20.dp),
    color = IndigoSurface,
    tonalElevation = 8.dp,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Top header + Board Multiplier Legend pills
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
            text = "TILE RACK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = WashiCream,
          )
          Box(
            modifier =
              Modifier.clip(RoundedCornerShape(6.dp))
                .background(SkyCyan.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 1.dp)
          ) {
            Text(
              text = "${tiles.size} left",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = SkyCyan,
            )
          }
        }

        // Compact Multiplier & Nexus Legend
        Row(
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LegendMiniPill(label = "◆ Nexus", color = KyotoGold)
          LegendMiniPill(label = "2L/3L", color = SkyCyan)
          LegendMiniPill(label = "2W/3W", color = VermilionRed)
        }
      }

      // Recessed Dark Tile Tray Well
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(IndigoNight.copy(alpha = 0.72f))
            .border(1.dp, IndigoCard, RoundedCornerShape(14.dp))
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
              text = "All skeleton blanks filled! Level Complete!",
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
          Text("Shuffle", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }

        FilledTonalButton(
          onClick = onTriggerHint,
          enabled = hintsRemaining > 0 && tiles.isNotEmpty(),
          modifier = Modifier.weight(1.2f).height(48.dp).testTag("btn_smart_hint"),
          shape = RoundedCornerShape(12.dp),
          colors =
            ButtonDefaults.filledTonalButtonColors(
              containerColor = KyotoGold.copy(alpha = 0.2f),
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
private fun LegendMiniPill(label: String, color: Color) {
  Box(
    modifier =
      Modifier.clip(RoundedCornerShape(5.dp))
        .background(color.copy(alpha = 0.15f))
        .border(0.8.dp, color.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
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
  val tiltZ = remember(tile.id) { Animatable(0f) }
  var isDragging by remember(tile.id) { mutableStateOf(false) }
  var tileRootCenter by remember(tile.id) { mutableStateOf(Offset.Zero) }
  var dragVelocity by remember(tile.id) { mutableStateOf(Offset.Zero) }
  var trailOffsets by remember(tile.id) { mutableStateOf<List<Offset>>(emptyList()) }

  val scale by
    animateFloatAsState(
      targetValue =
        when {
          isDragging -> 1.28f
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

  val infinite = rememberInfiniteTransition(label = "drag_halo_${tile.id}")
  val haloPhase by
    infinite.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
      label = "halo_phase",
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
        .size(52.dp),
    contentAlignment = Alignment.Center,
  ) {
    // Render Comet Spark Trail & Magnetic Energy Halo while dragging!
    if (isDragging && trailOffsets.isNotEmpty()) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val currentPos = center + dragOffset.value

        // Comet trail behind the dragged tile
        trailOffsets.forEachIndexed { idx, pastOffset ->
          val progress = (idx + 1).toFloat() / trailOffsets.size.toFloat()
          val sparkCenter = center + pastOffset
          val sparkColor = if (idx % 2 == 0) SkyCyan else KyotoGold
          drawCircle(
            color = sparkColor.copy(alpha = progress * 0.65f),
            radius = (3.5f + 7.5f * progress).dp.toPx(),
            center = sparkCenter,
          )
        }

        // Pulsing magnetic energy halo & 4 orbiting sparks around the lifted tile
        drawCircle(
          color = SkyCyan.copy(alpha = 0.28f),
          radius = 34.dp.toPx(),
          center = currentPos,
          style = Stroke(width = 2.dp.toPx()),
        )
        for (i in 0 until 4) {
          val angle = haloPhase + (i * PI / 2.0).toFloat()
          val orbitR = 32.dp.toPx()
          drawCircle(
            color = if (i % 2 == 0) KyotoGold else SkyCyan,
            radius = 3.dp.toPx(),
            center =
              Offset(
                currentPos.x + cos(angle) * orbitR,
                currentPos.y + sin(angle) * orbitR,
              ),
          )
        }
      }
    }

    Box(
      modifier =
        Modifier.offset {
            IntOffset(
              dragOffset.value.x.roundToInt(),
              dragOffset.value.y.roundToInt(),
            )
          }
          .scale(scale)
          .graphicsLayer {
            rotationZ = tiltZ.value
            rotationX = (-dragVelocity.y * 0.45f).coerceIn(-14f, 14f)
            rotationY = (dragVelocity.x * 0.45f).coerceIn(-14f, 14f)
          }
          .size(50.dp)
          .shadow(
            elevation = if (isDragging) 18.dp else 5.dp,
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
                trailOffsets = (trailOffsets + nextOffset).takeLast(9)
                val targetTilt = (dragVelocity.x * 0.65f).coerceIn(-16f, 16f)
                scope.launch {
                  dragOffset.snapTo(nextOffset)
                  tiltZ.snapTo(targetTilt)
                }
                val currentRootPoint = tileRootCenter + nextOffset
                onDragMove(currentRootPoint)
              },
              onDragEnd = {
                val dropRootPoint = tileRootCenter + dragOffset.value
                isDragging = false
                dragVelocity = Offset.Zero
                trailOffsets = emptyList()
                val accepted = onDragDrop(dropRootPoint)
                if (!accepted) {
                  // Elastic rejection wiggle + bouncy spring return to rack slot!
                  scope.launch {
                    launch {
                      tiltZ.animateTo(
                        targetValue = 0f,
                        animationSpec =
                          keyframes {
                            durationMillis = 480
                            (-16f) at 80
                            16f at 170
                            (-10f) at 270
                            6f at 370
                            0f at 480
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
                  scope.launch { tiltZ.snapTo(0f) }
                }
              },
              onDragCancel = {
                isDragging = false
                dragVelocity = Offset.Zero
                trailOffsets = emptyList()
                onDragCancel()
                scope.launch {
                  launch { tiltZ.animateTo(0f, tween(220)) }
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
      // Top specular highlight bar for tactile 3D tile polish
      Box(
        modifier =
          Modifier.align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.42f))
      )
      // Bottom 3D lip bevel shadow
      Box(
        modifier =
          Modifier.align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.Black.copy(alpha = 0.18f))
      )

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
            color = IndigoNight,
          )
        }
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
