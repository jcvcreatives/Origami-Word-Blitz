package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.BurstColorType
import com.example.domain.model.Coordinate
import com.example.domain.model.CosmeticTileSkin
import com.example.domain.model.FloatingScoreBurst
import com.example.domain.model.MovePreview
import com.example.domain.model.RackState
import com.example.domain.model.Tile
import com.example.domain.model.TileBonusEffect
import com.example.ui.theme.CeladonJade
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoElevated
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.SakuraPink
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import com.example.ui.theme.WashiMuted
import kotlin.math.roundToInt

/**
 * Live Multiplier Score Breakdown Bar displayed between the 15x15 board and the 7-tile rack.
 * Previews every formed horizontal and vertical word, letter/word multipliers, and total score
 * before the player submits their turn.
 */
@Composable
fun MultiplierScoreBreakdownBar(
  movePreview: MovePreview?,
  lastMoveSummary: String,
  hintSuggestion: String?,
  comboMultiplier: Float,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = IndigoCard,
    shape = RoundedCornerShape(14.dp),
    modifier =
      modifier
        .fillMaxWidth()
        .border(
          width = 1.dp,
          color =
            when {
              movePreview == null -> IndigoElevated
              movePreview.isValidPosition && movePreview.allWordsValid -> CeladonJade
              else -> VermilionRed
            },
          shape = RoundedCornerShape(14.dp),
        )
        .testTag("multiplier_breakdown_bar"),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      if (movePreview == null) {
        Text(
          text = hintSuggestion ?: lastMoveSummary,
          style = MaterialTheme.typography.bodyMedium,
          color = if (hintSuggestion != null) KyotoGold else WashiCream,
          maxLines = 2,
        )
      } else if (!movePreview.isValidPosition || !movePreview.allWordsValid) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = VermilionRed,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = movePreview.placementError ?: "Invalid placement",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFF8E8E),
            fontWeight = FontWeight.SemiBold,
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = CeladonJade,
              modifier = Modifier.size(18.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(movePreview.formedWords) { wb ->
                WordBreakdownChip(
                  word = wb.word,
                  basePts = wb.basePoints,
                  wordMult = wb.wordMultiplier,
                  totalPts = wb.totalPoints,
                )
              }
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          // Total turn score pill
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              Modifier.clip(RoundedCornerShape(10.dp))
                .background(CeladonJade.copy(alpha = 0.2f))
                .border(1.dp, CeladonJade, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
          ) {
            if (comboMultiplier > 1.0f) {
              Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                tint = KyotoGold,
                modifier = Modifier.size(14.dp),
              )
              Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
              text = "+${movePreview.totalTurnScore} PTS",
              style = MaterialTheme.typography.labelLarge,
              color = CeladonJade,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WordBreakdownChip(
  word: String,
  basePts: Int,
  wordMult: Int,
  totalPts: Int,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.clip(RoundedCornerShape(8.dp))
        .background(IndigoElevated)
        .padding(horizontal = 8.dp, vertical = 3.dp),
  ) {
    Text(
      text = word,
      style = MaterialTheme.typography.labelLarge,
      color = WashiCream,
      fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.width(4.dp))
    val formula = if (wordMult > 1) "${basePts}×${wordMult}W=$totalPts" else "+$totalPts"
    Text(
      text = formula,
      style = MaterialTheme.typography.labelSmall,
      color = if (wordMult >= 3) VermilionRed else KyotoGold,
    )
  }
}

/**
 * 7-slot player letter rack supporting both:
 * 1. Direct drag-and-drop onto the 15x15 Canvas board with spring physics and live hover preview
 * 2. Instant tap-to-select & tap-board-square placement
 */
@Composable
fun DraggablePlayerRack(
  rackState: RackState,
  equippedSkin: CosmeticTileSkin,
  onTileSelected: (String) -> Unit,
  onDragStart: (Tile, Offset) -> Unit,
  onDragMove: (Offset) -> Unit,
  onDragEnd: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val tileRootPositions = remember { mutableStateMapOf<String, Offset>() }

  Surface(
    color = IndigoCard,
    shape = RoundedCornerShape(18.dp),
    shadowElevation = 6.dp,
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.dp, KyotoGold.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
        .testTag("player_tile_rack"),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (rackState.tiles.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxWidth().height(54.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = "All 7 tiles placed on the board! Tap FOLD WORD or recall tiles.",
            style = MaterialTheme.typography.bodyMedium,
            color = KyotoGold,
          )
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          for (tile in rackState.tiles) {
            val isSelected = rackState.selectedTileId == tile.id
            val isDragging = rackState.draggingTileId == tile.id

            RackTileSlot(
              tile = tile,
              skin = equippedSkin,
              isSelected = isSelected,
              isDragging = isDragging,
              onPositionedInRoot = { rootPos -> tileRootPositions[tile.id] = rootPos },
              onTap = { onTileSelected(tile.id) },
              onDragStart = { localOffset ->
                val origin = tileRootPositions[tile.id] ?: Offset.Zero
                onDragStart(tile, origin + localOffset)
              },
              onDragBy = { delta -> onDragMove(delta) },
              onDragEnd = onDragEnd,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RackTileSlot(
  tile: Tile,
  skin: CosmeticTileSkin,
  isSelected: Boolean,
  isDragging: Boolean,
  onPositionedInRoot: (Offset) -> Unit,
  onTap: () -> Unit,
  onDragStart: (Offset) -> Unit,
  onDragBy: (Offset) -> Unit,
  onDragEnd: () -> Unit,
) {
  val scale by
    animateFloatAsState(
      targetValue =
        when {
          isDragging -> 0.85f
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

  val yOffsetDp = if (isSelected) (-6).dp else 0.dp
  val baseColor = Color(skin.baseColorHex)
  val borderColor = if (isSelected) CeladonJade else Color(skin.borderColorHex)
  val textColor = Color(skin.textColorHex)

  Box(
    modifier =
      Modifier.size(width = 45.dp, height = 54.dp)
        .offset(y = yOffsetDp)
        .scale(scale)
        .onGloballyPositioned { coords -> onPositionedInRoot(coords.positionInRoot()) }
        .shadow(if (isSelected) 8.dp else 3.dp, RoundedCornerShape(10.dp))
        .clip(RoundedCornerShape(10.dp))
        .background(if (isDragging) baseColor.copy(alpha = 0.35f) else baseColor)
        .border(
          width = if (isSelected) 2.5.dp else 1.5.dp,
          color = borderColor,
          shape = RoundedCornerShape(10.dp),
        )
        .clickable { onTap() }
        .pointerInput(tile.id) {
          detectDragGestures(
            onDragStart = { startOffset -> onDragStart(startOffset) },
            onDrag = { change, dragAmount ->
              change.consume()
              onDragBy(dragAmount)
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() },
          )
        }
        .testTag("rack_tile_${tile.char}"),
  ) {
    // Bonus effect badge in top-left
    if (tile.bonusEffect != TileBonusEffect.NONE) {
      Box(
        modifier =
          Modifier.align(Alignment.TopStart)
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
              when (tile.bonusEffect) {
                TileBonusEffect.GOLD_FOIL -> KyotoGold
                TileBonusEffect.CRANE_BLESSING -> VermilionRed
                TileBonusEffect.SAKURA_SPARK -> SakuraPink
                TileBonusEffect.NONE -> Color.Transparent
              }
            )
            .padding(horizontal = 3.dp, vertical = 1.dp)
      ) {
        Text(
          text = tile.bonusEffect.badge,
          fontSize = 8.sp,
          fontWeight = FontWeight.Bold,
          color = IndigoNight,
        )
      }
    }

    // Center Letter
    Text(
      text = tile.char.toString(),
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = textColor,
      modifier = Modifier.align(Alignment.Center),
    )

    // Bottom-right base score
    Text(
      text = tile.baseScore.toString(),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = textColor.copy(alpha = 0.8f),
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 2.dp),
    )
  }
}

/**
 * Floating dragged tile that follows the player's finger across the screen onto the 15x15 board.
 */
@Composable
fun FloatingDraggedTileOverlay(
  draggedTile: Tile?,
  skin: CosmeticTileSkin,
  fingerRootOffset: Offset,
  parentRootOffset: Offset,
) {
  if (draggedTile == null) return
  val localX = fingerRootOffset.x - parentRootOffset.x - 75f
  val localY = fingerRootOffset.y - parentRootOffset.y - 110f

  Box(
    modifier =
      Modifier.offset { IntOffset(localX.roundToInt(), localY.roundToInt()) }
        .size(width = 54.dp, height = 62.dp)
        .shadow(16.dp, RoundedCornerShape(12.dp))
        .clip(RoundedCornerShape(12.dp))
        .background(Color(skin.baseColorHex))
        .border(2.5.dp, CeladonJade, RoundedCornerShape(12.dp)),
  ) {
    Text(
      text = draggedTile.char.toString(),
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = Color(skin.textColorHex),
      modifier = Modifier.align(Alignment.Center),
    )
    Text(
      text = draggedTile.baseScore.toString(),
      style = MaterialTheme.typography.labelSmall,
      color = Color(skin.textColorHex),
      modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
    )
  }
}

/**
 * Game Juice Floating Score Counter Overlay (`+42! TRIPLE WORD!`) with pop and upward float
 * keyframe transitions.
 */
@Composable
fun FloatingScoreBurstOverlay(
  bursts: List<FloatingScoreBurst>,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.TopCenter,
  ) {
    for (burst in bursts) {
      val floatY = remember(burst.id) { Animatable(0f) }
      val popScale = remember(burst.id) { Animatable(0.6f) }

      LaunchedEffect(burst.id) {
        launchPopAndFloat(popScale, floatY)
      }

      AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(150)) + scaleIn(tween(220)),
        exit = fadeOut(tween(300)),
      ) {
        val bgBrush =
          when (burst.colorType) {
            BurstColorType.TRIPLE_WORD ->
              Brush.horizontalGradient(listOf(VermilionRed, KyotoGold))
            BurstColorType.DOUBLE_WORD ->
              Brush.horizontalGradient(listOf(SakuraPink, KyotoGold))
            BurstColorType.COMBO_STREAK ->
              Brush.horizontalGradient(listOf(CeladonJade, KyotoGold))
            BurstColorType.AI_MOVE ->
              Brush.horizontalGradient(listOf(IndigoElevated, VermilionRed))
            BurstColorType.STANDARD ->
              Brush.horizontalGradient(listOf(CeladonJade, Color(0xFF0E6BA8)))
          }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier =
            Modifier.offset { IntOffset(0, floatY.value.roundToInt()) }
              .scale(popScale.value)
              .shadow(12.dp, CircleShape)
              .clip(RoundedCornerShape(20.dp))
              .background(bgBrush)
              .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
              .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
          Text(
            text = burst.headline,
            style = MaterialTheme.typography.titleLarge,
            color = IndigoNight,
            fontWeight = FontWeight.ExtraBold,
          )
          if (burst.subline.isNotBlank()) {
            Text(
              text = burst.subline,
              style = MaterialTheme.typography.labelSmall,
              color = IndigoNight.copy(alpha = 0.85f),
            )
          }
        }
      }
    }
  }
}

private suspend fun kotlinx.coroutines.CoroutineScope.launchPopAndFloat(
  popScale: Animatable<Float, *>,
  floatY: Animatable<Float, *>,
) {
  popScale.animateTo(
    targetValue = 1.08f,
    animationSpec =
      spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
      ),
  )
  floatY.animateTo(
    targetValue = -42f,
    animationSpec = tween(durationMillis = 1100),
  )
}
