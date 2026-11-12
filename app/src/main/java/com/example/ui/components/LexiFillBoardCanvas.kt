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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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

    val directCell = state.cellAt(exactCol, exactRow)
    if (directCell != null && directCell.isBlankTarget && !directCell.isLockedCompleted) {
      return Coordinate(exactCol, exactRow)
    }

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
          animation = tween(750, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse_alpha",
    )

  val reticlePhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(650, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "reticle_phase",
    )

  // Frame clock tick for smooth tile-snap squash/stretch, shockwaves, cell flashes, popups, particles & center confetti
  var frameNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(
    puzzleState.cellFlashTimestamps,
    puzzleState.tilePlacementTimestamps,
    puzzleState.rejectedSlotTimestamps,
    puzzleState.scorePopups,
    puzzleState.particleBursts,
    puzzleState.confettiExplosions,
  ) {
    val hasActiveAnimations =
      puzzleState.cellFlashTimestamps.isNotEmpty() ||
        puzzleState.tilePlacementTimestamps.isNotEmpty() ||
        puzzleState.rejectedSlotTimestamps.isNotEmpty() ||
        puzzleState.scorePopups.isNotEmpty() ||
        puzzleState.particleBursts.isNotEmpty() ||
        puzzleState.confettiExplosions.isNotEmpty()
    if (hasActiveAnimations) {
      val start = System.currentTimeMillis()
      while (System.currentTimeMillis() - start < 2100L) {
        frameNowMs = System.currentTimeMillis()
        delay(16L)
      }
    }
  }

  // Screen micro-shake on invalid intersection or tile snap / dual-axis clear
  val shakeOffset = remember { Animatable(0f) }
  LaunchedEffect(puzzleState.boardShakeIntensity) {
    if (puzzleState.boardShakeIntensity > 0f) {
      val mag = puzzleState.boardShakeIntensity * 10f
      val keyframes = listOf(-mag, mag, -mag * 0.7f, mag * 0.7f, -mag * 0.35f, 0f)
      for (target in keyframes) {
        shakeOffset.animateTo(target, tween(34, easing = LinearEasing))
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

  var currentGeometry by remember { mutableStateOf(SkeletonBoardGeometry()) }

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

          // Placement squash-and-stretch timing (when putting a tile into a slot)
          val placeStart = puzzleState.tilePlacementTimestamps[coord]
          val placeElapsed = if (placeStart != null) (frameNowMs - placeStart) else 9999L
          val (snapScaleX, snapScaleY) = computeSnapSquashStretch(placeElapsed)

          // Corridor highlight under intersecting horizontal/vertical words during drag
          if (isInHoveredCorridor && !isHoveredTarget) {
            val corridorAlpha = 0.14f + 0.10f * reticlePhase
            drawRoundRect(
              color = SkyCyan.copy(alpha = corridorAlpha),
              topLeft = Offset(topLeft.x - pad * 0.6f, topLeft.y - pad * 0.6f),
              size = Size(innerSize + pad * 1.2f, innerSize + pad * 1.2f),
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

              letterPaint.color = WashiCream.toArgb()
              letterPaint.textSize = innerSize * 0.54f
              val textY = cellCenter.y - (letterPaint.descent() + letterPaint.ascent()) / 2f
              drawContext.canvas.nativeCanvas.drawText(
                cell.fixedLetter.toString(),
                cellCenter.x,
                textY,
                letterPaint,
              )

              subPaint.color = WashiCream.copy(alpha = 0.55f).toArgb()
              subPaint.textSize = innerSize * 0.19f
              drawContext.canvas.nativeCanvas.drawText(
                com.example.domain.model.GameCatalog.letterBaseScore(cell.fixedLetter).toString(),
                topLeft.x + innerSize * 0.80f,
                topLeft.y + innerSize * 0.88f,
                subPaint,
              )
            }

            // 2. Player Placed Tile inside a Blank Target Slot (with Squash-and-Stretch Impact Physics!)
            cell.placedTile != null -> {
              val tile = cell.placedTile
              withTransform({
                scale(scaleX = snapScaleX, scaleY = snapScaleY, pivot = cellCenter)
              }) {
                // Impact under-shadow when landing
                if (placeElapsed in 0..380) {
                  val shadowExpand = (1f - placeElapsed / 380f) * cellSize * 0.12f
                  drawRoundRect(
                    color = SkyCyan.copy(alpha = (1f - placeElapsed / 380f) * 0.55f),
                    topLeft = Offset(topLeft.x - shadowExpand, topLeft.y - shadowExpand),
                    size = Size(innerSize + shadowExpand * 2f, innerSize + shadowExpand * 2f),
                    cornerRadius = cornerRadius,
                  )
                }

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

              // Magnetic Lock-On Ghost Letter & Target Reticle Brackets while Dragging!
              if (isHoveredTarget && preview != null) {
                val activeColor = if (preview.isDualAxisValid) CeladonJade else VermilionRed
                drawRoundRect(
                  color = activeColor.copy(alpha = 0.26f + 0.12f * reticlePhase),
                  topLeft = topLeft,
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                )

                // Draw 4 animated magnetic corner lock-on brackets around the socket
                val bracketInset = -cellSize * (0.04f + 0.04f * reticlePhase)
                val bracketLen = innerSize * 0.28f
                val bx0 = topLeft.x + bracketInset
                val by0 = topLeft.y + bracketInset
                val bx1 = topLeft.x + innerSize - bracketInset
                val by1 = topLeft.y + innerSize - bracketInset
                val bStroke = cellSize * 0.055f

                // Top-left bracket
                drawLine(activeColor, Offset(bx0, by0), Offset(bx0 + bracketLen, by0), bStroke, StrokeCap.Round)
                drawLine(activeColor, Offset(bx0, by0), Offset(bx0, by0 + bracketLen), bStroke, StrokeCap.Round)
                // Top-right bracket
                drawLine(activeColor, Offset(bx1, by0), Offset(bx1 - bracketLen, by0), bStroke, StrokeCap.Round)
                drawLine(activeColor, Offset(bx1, by0), Offset(bx1, by0 + bracketLen), bStroke, StrokeCap.Round)
                // Bottom-left bracket
                drawLine(activeColor, Offset(bx0, by1), Offset(bx0 + bracketLen, by1), bStroke, StrokeCap.Round)
                drawLine(activeColor, Offset(bx0, by1), Offset(bx0, by1 - bracketLen), bStroke, StrokeCap.Round)
                // Bottom-right bracket
                drawLine(activeColor, Offset(bx1, by1), Offset(bx1 - bracketLen, by1), bStroke, StrokeCap.Round)
                drawLine(activeColor, Offset(bx1, by1), Offset(bx1, by1 - bracketLen), bStroke, StrokeCap.Round)

                letterPaint.color = activeColor.toArgb()
                letterPaint.textSize = innerSize * (0.52f + 0.06f * reticlePhase)
                val textY = cellCenter.y - (letterPaint.descent() + letterPaint.ascent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                  preview.tileChar.toString(),
                  cellCenter.x,
                  textY,
                  letterPaint,
                )
              } else if (isHintHighlighted && cell.expectedLetter != null) {
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

          // Concentric Tile Placement Shockwave Rings (when putting a tile!)
          if (placeElapsed in 0..620) {
            val t = placeElapsed / 620f
            val ring1Radius = cellSize * (0.45f + 1.25f * t)
            val ring2Radius = cellSize * (0.30f + 0.90f * t)
            val ringAlpha = (1f - t).coerceIn(0f, 1f)
            drawCircle(
              color = SkyCyan.copy(alpha = ringAlpha * 0.85f),
              radius = ring1Radius,
              center = cellCenter,
              style = Stroke(width = cellSize * 0.06f * (1f - t * 0.5f)),
            )
            drawCircle(
              color = KyotoGold.copy(alpha = ringAlpha * 0.75f),
              radius = ring2Radius,
              center = cellCenter,
              style = Stroke(width = cellSize * 0.04f * (1f - t * 0.5f)),
            )
          }

          // Crimson Rejection Ripple when dropping an invalid tile onto a slot
          val rejectStart = puzzleState.rejectedSlotTimestamps[coord]
          val rejectElapsed = if (rejectStart != null) (frameNowMs - rejectStart) else 9999L
          if (rejectElapsed in 0..550) {
            val rt = rejectElapsed / 550f
            val rAlpha = (1f - rt).coerceIn(0f, 1f)
            drawCircle(
              color = VermilionRed.copy(alpha = rAlpha * 0.85f),
              radius = cellSize * (0.45f + 1.15f * rt),
              center = cellCenter,
              style = Stroke(width = cellSize * 0.065f * rAlpha),
            )
          }
        }
      }

      // Floating Magnifier Callout Pill above the Hovered Blank Slot during Drag
      val hc = puzzleState.hoverCoord
      val hp = puzzleState.hoverPreview
      if (hc != null && hp != null && hp.isValidSlot) {
        val centerX =
          (originX + (hc.x + 0.5f) * cellSize).coerceIn(
            cellSize * 1.3f,
            size.width - cellSize * 1.3f,
          )
        val pillCenterY =
          (originY + hc.y * cellSize - cellSize * 0.58f).coerceAtLeast(cellSize * 0.42f)
        val pillW = cellSize * 2.15f
        val pillH = cellSize * 0.52f
        val pillColor = if (hp.isDualAxisValid) CeladonJade else VermilionRed

        drawRoundRect(
          color = IndigoNight.copy(alpha = 0.94f),
          topLeft = Offset(centerX - pillW / 2f, pillCenterY - pillH / 2f),
          size = Size(pillW, pillH),
          cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
        )
        drawRoundRect(
          color = pillColor,
          topLeft = Offset(centerX - pillW / 2f, pillCenterY - pillH / 2f),
          size = Size(pillW, pillH),
          cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
          style = Stroke(width = cellSize * 0.045f),
        )

        val calloutLabel =
          if (hp.isDualAxisValid) "${hp.tileChar} • +${hp.estimatedScoreGain}"
          else "${hp.tileChar} ✕"
        subPaint.color = pillColor.toArgb()
        subPaint.textSize = cellSize * 0.25f
        val ty = pillCenterY - (subPaint.descent() + subPaint.ascent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(calloutLabel, centerX, ty, subPaint)
      }

      // Render Particle Bursts over placed tiles and completed intersections
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

      // Render Center-of-Grid Canvas Confetti Particle Explosion when a word is completed!
      val gridCenter = Offset(originX + gridW / 2f, originY + gridH / 2f)
      val confettiPalette =
        listOf(
          KyotoGold,
          CeladonJade,
          SkyCyan,
          VermilionRed,
          Color(0xFFFF7597),
          WashiCream,
        )

      for (confetti in puzzleState.confettiExplosions) {
        val elapsed = (frameNowMs - confetti.createdAtMs).coerceAtLeast(0L)
        if (elapsed in 0..1850) {
          val t = elapsed / 1850f
          val fadeAlpha = (1f - ((t - 0.58f).coerceAtLeast(0f) / 0.42f)).coerceIn(0f, 1f)

          // Center-of-grid ignition shockwave ring at explosion start
          if (t < 0.32f) {
            val ringT = t / 0.32f
            val ringColor = if (confetti.isDualIntersection) KyotoGold else CeladonJade
            drawCircle(
              color = ringColor.copy(alpha = (1f - ringT) * 0.85f),
              radius = cellSize * (0.4f + 2.6f * ringT),
              center = gridCenter,
              style = Stroke(width = cellSize * 0.08f * (1f - ringT * 0.6f)),
            )
          }

          // Ballistic 3D tumbling confetti fragments spawned from the center of the grid
          for (i in 0 until confetti.fragmentCount) {
            val hash = ((i * 73856093) xor (confetti.seed * 19349663)) and 0x7FFFFFFF
            val angleSpread =
              (2.0 * PI * i) / confetti.fragmentCount + ((hash % 100) / 100.0 - 0.5) * 0.25
            val speedFactor = 0.28f + ((hash % 1000) / 1000f) * 0.56f
            val radialSpeed = min(gridW, gridH) * speedFactor

            // Air drag deceleration on initial blast velocity + upward fountain impulse + gravity
            val dragProgress = 1f - (1f - t) * (1f - t) * (1f - t)
            val vx = cos(angleSpread).toFloat() * radialSpeed
            val vy = sin(angleSpread).toFloat() * radialSpeed - gridH * 0.22f
            val gravity = gridH * 0.82f

            // Sinusoidal paper flutter as fragments drift downward
            val flutterPhase = (hash % 360) * (PI / 180.0).toFloat()
            val flutterFreq = 9f + (i % 5) * 2.5f
            val flutterAmp = cellSize * (0.08f + (i % 3) * 0.05f) * t
            val flutterX = sin(t * flutterFreq + flutterPhase) * flutterAmp

            val px = gridCenter.x + vx * dragProgress + flutterX
            val py = gridCenter.y + vy * dragProgress + 0.5f * gravity * t * t

            // 3D spin & flip simulation
            val spinDeg = ((hash % 360) + t * (340f + (i % 6) * 120f)) % 360f
            val tumbleFlipX = cos(t * (11f + (i % 4) * 3f) + flutterPhase).coerceIn(-1f, 1f)
            val fragColor = confettiPalette[i % confettiPalette.size].copy(alpha = fadeAlpha)

            val fragW =
              when (i % 3) {
                0 -> cellSize * 0.24f // Ribbon rectangle
                1 -> cellSize * 0.17f // Square/diamond shard
                else -> cellSize * 0.30f // Long streamer strip
              }
            val fragH =
              when (i % 3) {
                0 -> cellSize * 0.12f
                1 -> cellSize * 0.17f
                else -> cellSize * 0.08f
              }

            withTransform({
              translate(left = px, top = py)
              rotate(degrees = spinDeg, pivot = Offset.Zero)
              scale(
                scaleX = if (kotlin.math.abs(tumbleFlipX) < 0.18f) 0.18f else tumbleFlipX,
                scaleY = 1f,
                pivot = Offset.Zero,
              )
            }) {
              drawRoundRect(
                color = fragColor,
                topLeft = Offset(-fragW / 2f, -fragH / 2f),
                size = Size(fragW, fragH),
                cornerRadius = CornerRadius(fragH * 0.25f, fragH * 0.25f),
              )
            }
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

/**
 * Computes physical squash-and-stretch scale factors when a tile snaps into a board socket.
 */
private fun computeSnapSquashStretch(elapsedMs: Long): Pair<Float, Float> {
  if (elapsedMs !in 0..420) return 1f to 1f
  val t = elapsedMs / 420f
  return when {
    // Phase 1 (0..0.28): Tile drops in from lifted scale (1.35x) to impact squash (1.16x wide, 0.85x tall)
    t < 0.28f -> {
      val p = t / 0.28f
      val sx = 1.35f + (1.16f - 1.35f) * p
      val sy = 1.35f + (0.85f - 1.35f) * p
      sx to sy
    }
    // Phase 2 (0.28..0.62): Rebound stretch (0.93x wide, 1.09x tall)
    t < 0.62f -> {
      val p = (t - 0.28f) / 0.34f
      val sx = 1.16f + (0.93f - 1.16f) * p
      val sy = 0.85f + (1.09f - 0.85f) * p
      sx to sy
    }
    // Phase 3 (0.62..1.0): Settle into socket (1.0x, 1.0x)
    else -> {
      val p = (t - 0.62f) / 0.38f
      val sx = 0.93f + (1.0f - 0.93f) * p
      val sy = 1.09f + (1.0f - 1.09f) * p
      sx to sy
    }
  }
}
