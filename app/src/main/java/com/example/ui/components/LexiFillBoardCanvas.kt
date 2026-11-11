package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.domain.model.Coordinate
import com.example.domain.model.PuzzleState
import com.example.domain.model.SquareMultiplier
import com.example.ui.theme.BlankSlotSocketBg
import com.example.ui.theme.BlankSlotSocketBorder
import com.example.ui.theme.CeladonJade
import com.example.ui.theme.CompletedWordBg
import com.example.ui.theme.CompletedWordBorder
import com.example.ui.theme.FixedClueCellBg
import com.example.ui.theme.FixedClueCellBorder
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.MagneticSnapGlow
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SquareDlBg
import com.example.ui.theme.SquareDwBg
import com.example.ui.theme.SquareTlBg
import com.example.ui.theme.SquareTwBg
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reports the root window coordinates and cell metrics of the Crossword Skeleton canvas
 * so dragged rack tiles can perform <1ms magnetic hit-testing against unfilled blank slots.
 */
data class SkeletonBoardGeometry(
  val rootTopLeft: Offset = Offset.Zero,
  val gridOffsetInCanvas: Offset = Offset.Zero,
  val cellSizePx: Float = 1f,
  val boardWidth: Int = 8,
  val boardHeight: Int = 8,
) {
  /**
   * Finds the best unfilled blank target coordinate within magnetic snap range of [rootPoint].
   */
  fun resolveMagneticSlot(rootPoint: Offset, state: PuzzleState): Coordinate? {
    if (cellSizePx <= 1f) return null
    val localX = rootPoint.x - rootTopLeft.x - gridOffsetInCanvas.x
    val localY = rootPoint.y - rootTopLeft.y - gridOffsetInCanvas.y

    val exactCol = (localX / cellSizePx).toInt()
    val exactRow = (localY / cellSizePx).toInt()

    // Direct hit on an open or unlocked blank slot
    val directCell = state.cellAt(exactCol, exactRow)
    if (directCell != null && directCell.isBlankTarget && !directCell.isLockedCompleted) {
      return Coordinate(exactCol, exactRow)
    }

    // Magnetic snap radius search (up to 1.35x cell width) for frictionless mobile drag-and-drop
    val maxSnapDist = cellSizePx * 1.35f
    var bestCoord: Coordinate? = null
    var bestDist = Float.MAX_VALUE

    for (y in 0 until state.boardHeight) {
      for (x in 0 until state.boardWidth) {
        val cell = state.cellAt(x, y) ?: continue
        if (!cell.isBlankTarget || cell.isLockedCompleted) continue
        val centerX = (x + 0.5f) * cellSizePx
        val centerY = (y + 0.5f) * cellSizePx
        val dist = hypot(localX - centerX, localY - centerY)
        if (dist <= maxSnapDist && dist < bestDist) {
          bestDist = dist
          bestCoord = Coordinate(x, y)
        }
      }
    }
    return bestCoord
  }

  fun cellCenterInCanvas(coord: Coordinate): Offset {
    return Offset(
      x = gridOffsetInCanvas.x + (coord.x + 0.5f) * cellSizePx,
      y = gridOffsetInCanvas.y + (coord.y + 0.5f) * cellSizePx,
    )
  }
}

@Composable
fun LexiFillBoardCanvas(
  puzzleState: PuzzleState,
  onGeometryUpdated: (SkeletonBoardGeometry) -> Unit,
  onBlankSlotTapped: (Int, Int) -> Unit,
  onShakeConsumed: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "skeleton_pulse")
  val pulseAlpha by
    infiniteTransition.animateFloat(
      initialValue = 0.35f,
      targetValue = 0.95f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(850, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse_alpha",
    )

  // Frame clock tick for smooth word-completion cell flashes, floating score bursts & particles
  var frameNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(puzzleState.cellFlashTimestamps, puzzleState.scorePopups, puzzleState.particleBursts) {
    val hasActiveAnimations =
      puzzleState.cellFlashTimestamps.isNotEmpty() ||
        puzzleState.scorePopups.isNotEmpty() ||
        puzzleState.particleBursts.isNotEmpty()
    if (hasActiveAnimations) {
      val start = System.currentTimeMillis()
      while (System.currentTimeMillis() - start < 1900L) {
        frameNowMs = System.currentTimeMillis()
        delay(16L)
      }
    }
  }

  // Screen micro-shake on invalid intersection or dual-axis clear
  val shakeOffset = remember { Animatable(0f) }
  LaunchedEffect(puzzleState.boardShakeIntensity) {
    if (puzzleState.boardShakeIntensity > 0f) {
      val mag = puzzleState.boardShakeIntensity * 10f
      val keyframes = listOf(-mag, mag, -mag * 0.7f, mag * 0.7f, -mag * 0.35f, 0f)
      for (target in keyframes) {
        shakeOffset.animateTo(target, tween(36, easing = LinearEasing))
      }
      onShakeConsumed()
    }
  }

  val letterPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }
  val subPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
  }

  var currentGeometry by remember { androidx.compose.runtime.mutableStateOf(SkeletonBoardGeometry()) }

  // Identify all coordinates in the intersecting Horizontal & Vertical words when hovering a slot
  val hoveredCorridorCoords =
    remember(puzzleState.hoverCoord, puzzleState.targetWords) {
      val hc = puzzleState.hoverCoord
      if (hc == null) emptySet()
      else {
        puzzleState.targetWords
          .filter { it.containsCoordinate(hc.x, hc.y) }
          .flatMap { it.coordinates() }
          .toSet()
      }
    }

  val aspectRatio =
    (puzzleState.boardWidth.toFloat() / puzzleState.boardHeight.toFloat()).coerceIn(0.85f, 1.35f)

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio)
        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
        .clip(RoundedCornerShape(18.dp))
        .background(IndigoNight)
        .border(1.5.dp, IndigoCard, RoundedCornerShape(18.dp))
        .padding(8.dp)
        .testTag("lexifill_board_canvas"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .onGloballyPositioned { coords ->
            val rootPos = coords.positionInRoot()
            val w = coords.size.width.toFloat()
            val h = coords.size.height.toFloat()
            val cellSize = min(w / puzzleState.boardWidth, h / puzzleState.boardHeight)
            val gridW = cellSize * puzzleState.boardWidth
            val gridH = cellSize * puzzleState.boardHeight
            val origin = Offset((w - gridW) / 2f, (h - gridH) / 2f)
            val geom =
              SkeletonBoardGeometry(
                rootTopLeft = rootPos,
                gridOffsetInCanvas = origin,
                cellSizePx = cellSize,
                boardWidth = puzzleState.boardWidth,
                boardHeight = puzzleState.boardHeight,
              )
            currentGeometry = geom
            onGeometryUpdated(geom)
          }
          .pointerInput(puzzleState.boardWidth, puzzleState.boardHeight, currentGeometry) {
            detectTapGestures { tapOffset ->
              val cellSize = currentGeometry.cellSizePx
              if (cellSize > 1f) {
                val gx = ((tapOffset.x - currentGeometry.gridOffsetInCanvas.x) / cellSize).toInt()
                val gy = ((tapOffset.y - currentGeometry.gridOffsetInCanvas.y) / cellSize).toInt()
                if (gx in 0 until puzzleState.boardWidth && gy in 0 until puzzleState.boardHeight) {
                  onBlankSlotTapped(gx, gy)
                }
              }
            }
          }
    ) {
      val cellSize = min(size.width / puzzleState.boardWidth, size.height / puzzleState.boardHeight)
      val gridW = cellSize * puzzleState.boardWidth
      val gridH = cellSize * puzzleState.boardHeight
      val originX = (size.width - gridW) / 2f
      val originY = (size.height - gridH) / 2f
      val pad = cellSize * 0.06f
      val innerSize = cellSize - pad * 2f
      val cornerRadius = CornerRadius(cellSize * 0.18f, cellSize * 0.18f)

      val skin = puzzleState.equippedSkin
      val skinBaseColor = Color(skin.baseColorHex)
      val skinBorderColor = Color(skin.borderColorHex)
      val skinTextColorArgb = Color(skin.textColorHex).toArgb()

      for (y in 0 until puzzleState.boardHeight) {
        for (x in 0 until puzzleState.boardWidth) {
          val cell = puzzleState.cells[y][x]
          val coord = Coordinate(x, y)
          val topLeft = Offset(originX + x * cellSize + pad, originY + y * cellSize + pad)
          val cellCenter = Offset(topLeft.x + innerSize / 2f, topLeft.y + innerSize / 2f)

          if (!cell.isPlayable) {
            // Subtle architectural blueprint dot for non-playable skeleton space
            drawCircle(
              color = IndigoCard.copy(alpha = 0.55f),
              radius = cellSize * 0.045f,
              center = cellCenter,
            )
            continue
          }

          val isHoveredTarget = puzzleState.hoverCoord == coord
          val isInHoveredCorridor = coord in hoveredCorridorCoords
          val isHintHighlighted = puzzleState.highlightedHintCoord == coord
          val flashStart = puzzleState.cellFlashTimestamps[coord]
          val flashElapsed = if (flashStart != null) (frameNowMs - flashStart) else 9999L
          val flashProgress =
            if (flashElapsed in 0..850) 1f - (flashElapsed / 850f) else 0f

          // Corridor highlight under intersecting horizontal/vertical words during drag
          if (isInHoveredCorridor && !isHoveredTarget) {
            drawRoundRect(
              color = SkyCyan.copy(alpha = 0.16f),
              topLeft = Offset(topLeft.x - pad * 0.5f, topLeft.y - pad * 0.5f),
              size = Size(innerSize + pad, innerSize + pad),
              cornerRadius = cornerRadius,
            )
          }

          when {
            // 1. Permanent Fixed Clue Letter Cell
            cell.fixedLetter != null -> {
              val bgColor =
                if (cell.isLockedCompleted) CompletedWordBg else FixedClueCellBg
              val borderColor =
                if (cell.isLockedCompleted) CompletedWordBorder else FixedClueCellBorder

              drawRoundRect(
                color = bgColor,
                topLeft = topLeft,
                size = Size(innerSize, innerSize),
                cornerRadius = cornerRadius,
              )
              drawRoundRect(
                color = borderColor,
                topLeft = topLeft,
                size = Size(innerSize, innerSize),
                cornerRadius = cornerRadius,
                style = Stroke(width = cellSize * 0.04f),
              )

              // Draw permanent clue letter
              letterPaint.color = WashiCream.toArgb()
              letterPaint.textSize = innerSize * 0.54f
              val textY = cellCenter.y - (letterPaint.descent() + letterPaint.ascent()) / 2f
              drawContext.canvas.nativeCanvas.drawText(
                cell.fixedLetter.toString(),
                cellCenter.x,
                textY,
                letterPaint,
              )

              // Subtle point value subscript
              subPaint.color = WashiCream.copy(alpha = 0.55f).toArgb()
              subPaint.textSize = innerSize * 0.19f
              drawContext.canvas.nativeCanvas.drawText(
                com.example.domain.model.GameCatalog.letterBaseScore(cell.fixedLetter).toString(),
                topLeft.x + innerSize * 0.80f,
                topLeft.y + innerSize * 0.88f,
                subPaint,
              )
            }

            // 2. Player Placed Tile inside a Blank Target Slot
            cell.placedTile != null -> {
              val tile = cell.placedTile
              drawRoundRect(
                color = skinBaseColor,
                topLeft = topLeft,
                size = Size(innerSize, innerSize),
                cornerRadius = cornerRadius,
              )
              val borderCol =
                if (cell.isLockedCompleted) CeladonJade else skinBorderColor
              drawRoundRect(
                color = borderCol,
                topLeft = topLeft,
                size = Size(innerSize, innerSize),
                cornerRadius = cornerRadius,
                style = Stroke(width = cellSize * 0.055f),
              )

              letterPaint.color = skinTextColorArgb
              letterPaint.textSize = innerSize * 0.55f
              val textY = cellCenter.y - (letterPaint.descent() + letterPaint.ascent()) / 2f
              drawContext.canvas.nativeCanvas.drawText(
                tile.char.toString(),
                cellCenter.x,
                textY,
                letterPaint,
              )

              subPaint.color = skinTextColorArgb
              subPaint.textSize = innerSize * 0.20f
              drawContext.canvas.nativeCanvas.drawText(
                tile.baseScore.toString(),
                topLeft.x + innerSize * 0.80f,
                topLeft.y + innerSize * 0.88f,
                subPaint,
              )
            }

            // 3. Open Blank Target Socket (Needs a Letter!)
            else -> {
              val multBg =
                when (cell.multiplier) {
                  SquareMultiplier.TW -> SquareTwBg.copy(alpha = 0.55f)
                  SquareMultiplier.DW -> SquareDwBg.copy(alpha = 0.55f)
                  SquareMultiplier.TL -> SquareTlBg.copy(alpha = 0.55f)
                  SquareMultiplier.DL -> SquareDlBg.copy(alpha = 0.55f)
                  else -> BlankSlotSocketBg
                }

              drawRoundRect(
                color = multBg,
                topLeft = topLeft,
                size = Size(innerSize, innerSize),
                cornerRadius = cornerRadius,
              )

              val preview = puzzleState.hoverPreview
              val strokeColor =
                when {
                  isHoveredTarget && preview != null && preview.isDualAxisValid -> CeladonJade
                  isHoveredTarget && preview != null && !preview.isDualAxisValid -> VermilionRed
                  isHintHighlighted -> KyotoGold
                  cell.isIntersection -> KyotoGold.copy(alpha = 0.65f + pulseAlpha * 0.25f)
                  else -> BlankSlotSocketBorder.copy(alpha = 0.85f)
                }

              val strokeWidth =
                if (isHoveredTarget || isHintHighlighted) cellSize * 0.075f else cellSize * 0.042f

              drawRoundRect(
                color = strokeColor,
                topLeft = topLeft,
                size = Size(innerSize, innerSize),
                cornerRadius = cornerRadius,
                style =
                  Stroke(
                    width = strokeWidth,
                    pathEffect =
                      if (isHoveredTarget) null
                      else PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f),
                  ),
              )

              // If dragging a tile over this blank slot, draw magnetic Ghost Letter Preview!
              if (isHoveredTarget && preview != null) {
                val ghostColor =
                  if (preview.isDualAxisValid) CeladonJade.copy(alpha = 0.28f)
                  else VermilionRed.copy(alpha = 0.28f)
                drawRoundRect(
                  color = ghostColor,
                  topLeft = topLeft,
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                )
                letterPaint.color =
                  (if (preview.isDualAxisValid) CeladonJade else VermilionRed).toArgb()
                letterPaint.textSize = innerSize * 0.54f
                val textY = cellCenter.y - (letterPaint.descent() + letterPaint.ascent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                  preview.tileChar.toString(),
                  cellCenter.x,
                  textY,
                  letterPaint,
                )
              } else if (isHintHighlighted && cell.expectedLetter != null) {
                // Smart Hint ghost letter
                letterPaint.color = KyotoGold.copy(alpha = pulseAlpha).toArgb()
                letterPaint.textSize = innerSize * 0.52f
                val textY = cellCenter.y - (letterPaint.descent() + letterPaint.ascent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                  cell.expectedLetter.toString(),
                  cellCenter.x,
                  textY,
                  letterPaint,
                )
              } else {
                // Draw Multiplier Label (2L, 3L, 2W, 3W) or Intersection Nexus Diamond (◆)
                val badge =
                  if (cell.multiplier != SquareMultiplier.NONE) cell.multiplier.label
                  else if (cell.isIntersection) "◆"
                  else "•"
                subPaint.color =
                  (if (cell.isIntersection) KyotoGold else WashiCream.copy(alpha = 0.65f)).toArgb()
                subPaint.textSize =
                  if (cell.multiplier != SquareMultiplier.NONE) innerSize * 0.28f
                  else innerSize * 0.30f
                val by = cellCenter.y - (subPaint.descent() + subPaint.ascent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(badge, cellCenter.x, by, subPaint)
              }
            }
          }

          // Word Completion Flash Animation Overlay on newly completed cells!
          if (flashProgress > 0f) {
            drawRoundRect(
              color = KyotoGold.copy(alpha = flashProgress * 0.58f),
              topLeft = topLeft,
              size = Size(innerSize, innerSize),
              cornerRadius = cornerRadius,
            )
            drawRoundRect(
              color = CeladonJade.copy(alpha = flashProgress * 0.95f),
              topLeft = Offset(topLeft.x - 2f, topLeft.y - 2f),
              size = Size(innerSize + 4f, innerSize + 4f),
              cornerRadius = cornerRadius,
              style = Stroke(width = cellSize * 0.08f * flashProgress),
            )
          }
        }
      }

      // Render Particle Bursts over completed intersections
      for (burst in puzzleState.particleBursts) {
        val elapsed = (frameNowMs - burst.createdAtMs).coerceAtLeast(0L)
        if (elapsed in 0..1100) {
          val t = elapsed / 1100f
          val center =
            Offset(
              originX + (burst.boardCoord.x + 0.5f) * cellSize,
              originY + (burst.boardCoord.y + 0.5f) * cellSize,
            )
          val maxRadius = cellSize * 2.1f
          val baseColor = Color(burst.colorHex)
          for (i in 0 until burst.particleCount) {
            val angle = (2.0 * PI * i) / burst.particleCount + (i % 3) * 0.2f
            val speedFactor = 0.55f + (i % 4) * 0.18f
            val dist = maxRadius * t * speedFactor
            val px = center.x + (cos(angle) * dist).toFloat()
            val py = center.y + (sin(angle) * dist).toFloat()
            val pColor = if (i % 2 == 0) baseColor else KyotoGold
            drawCircle(
              color = pColor.copy(alpha = (1f - t).coerceIn(0f, 1f)),
              radius = cellSize * 0.09f * (1f - t * 0.6f),
              center = Offset(px, py),
            )
          }
        }
      }

      // Render Floating Score Popups (+50 PTS · COMBO x2!)
      for (popup in puzzleState.scorePopups) {
        val elapsed = (frameNowMs - popup.createdAtMs).coerceAtLeast(0L)
        if (elapsed in 0..1500) {
          val t = elapsed / 1500f
          val alpha = (1f - (t - 0.35f).coerceAtLeast(0f) / 0.65f).coerceIn(0f, 1f)
          val anchorX =
            (originX + (popup.coord.x + 0.5f) * cellSize).coerceIn(
              cellSize * 1.8f,
              size.width - cellSize * 1.8f,
            )
          val anchorY =
            (originY + popup.coord.y * cellSize - cellSize * 0.9f * t).coerceAtLeast(cellSize * 0.65f)

          letterPaint.color =
            (if (popup.isDualAxisClear) KyotoGold else CeladonJade)
              .copy(alpha = alpha)
              .toArgb()
          letterPaint.textSize = cellSize * (0.34f + 0.08f * sin(t * PI).toFloat())
          drawContext.canvas.nativeCanvas.drawText(
            popup.headline,
            anchorX,
            anchorY,
            letterPaint,
          )

          subPaint.color = WashiCream.copy(alpha = alpha).toArgb()
          subPaint.textSize = cellSize * 0.23f
          drawContext.canvas.nativeCanvas.drawText(
            popup.subline,
            anchorX,
            anchorY + cellSize * 0.28f,
            subPaint,
          )
        }
      }
    }
  }
}
