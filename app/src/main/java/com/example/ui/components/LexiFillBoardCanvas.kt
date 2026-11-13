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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.example.ui.theme.IndigoElevated
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.IndigoSurface
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SquareDlBg
import com.example.ui.theme.SquareDwBg
import com.example.ui.theme.SquareTlBg
import com.example.ui.theme.SquareTwBg
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import com.example.ui.theme.WashiMuted
import com.example.ui.theme.paperGrainOverlay
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reports the root window coordinates and cell metrics of the Crossword Origami Washi-paper board
 * so dragged rack tiles can perform <1ms magnetic hit-testing and spring-physics snap attraction.
 */
data class SkeletonBoardGeometry(
  val rootTopLeft: Offset = Offset.Zero,
  val gridOffsetInCanvas: Offset = Offset.Zero,
  val cellSizePx: Float = 1f,
  val boardWidth: Int = 8,
  val boardHeight: Int = 8,
) {
  /**
   * Returns the root-window screen center [Offset] of the given board [coord].
   */
  fun slotCenterInRoot(coord: Coordinate): Offset {
    val cx = rootTopLeft.x + gridOffsetInCanvas.x + (coord.x + 0.5f) * cellSizePx
    val cy = rootTopLeft.y + gridOffsetInCanvas.y + (coord.y + 0.5f) * cellSizePx
    return Offset(cx, cy)
  }

  /**
   * Finds the best unfilled blank target coordinate within magnetic snap range of [rootPoint].
   */
  fun resolveMagneticSlot(rootPoint: Offset, state: PuzzleState): Coordinate? {
    return resolveMagneticPull(rootPoint, state).first
  }

  /**
   * Resolves both the nearest valid open blank slot [Coordinate] AND a physical magnetic pull
   * vector [Offset] that tugs the dragged tile toward the center of that slot when nearby.
   */
  fun resolveMagneticPull(rootPoint: Offset, state: PuzzleState): Pair<Coordinate?, Offset> {
    if (cellSizePx <= 1f) return null to Offset.Zero
    val localX = rootPoint.x - rootTopLeft.x - gridOffsetInCanvas.x
    val localY = rootPoint.y - rootTopLeft.y - gridOffsetInCanvas.y

    val maxSnapDist = cellSizePx * 1.48f
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

    val target = bestCoord ?: return null to Offset.Zero
    val slotRoot = slotCenterInRoot(target)
    val deltaX = slotRoot.x - rootPoint.x
    val deltaY = slotRoot.y - rootPoint.y

    // Smoothly ramp magnetic attraction strength as the tile enters the slot's gravity well
    val normalizedProximity = (1f - (bestDist / maxSnapDist)).coerceIn(0f, 1f)
    val pullStrength = 0.42f + 0.40f * normalizedProximity
    val pullVector = Offset(deltaX * pullStrength, deltaY * pullStrength)
    return target to pullVector
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
  val infiniteTransition = rememberInfiniteTransition(label = "origami_paper_pulse")
  val pulseAlpha by
    infiniteTransition.animateFloat(
      initialValue = 0.35f,
      targetValue = 0.90f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(900, easing = FastOutSlowInEasing),
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
          animation = tween(700, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "reticle_phase",
    )

  // Frame clock tick for smooth tile-snap squash/stretch, subtle paper shimmer & popups
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
      while (System.currentTimeMillis() - start < 1450L) {
        withFrameMillis {
          frameNowMs = System.currentTimeMillis()
        }
      }
    }
  }

  // Gentle paper micro-nudge on invalid intersection or dual-axis clear
  val shakeOffset = remember { Animatable(0f) }
  LaunchedEffect(puzzleState.boardShakeIntensity) {
    if (puzzleState.boardShakeIntensity > 0f) {
      val mag = puzzleState.boardShakeIntensity * 7f
      val keyframes = listOf(-mag, mag, -mag * 0.6f, mag * 0.6f, 0f)
      for (target in keyframes) {
        shakeOffset.animateTo(target, tween(32, easing = LinearEasing))
      }
      onShakeConsumed()
    }
  }

  val letterPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
  }
  val subPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
  }

  var currentGeometry by remember { mutableStateOf(SkeletonBoardGeometry()) }
  val dogEarPath = remember { Path() }
  val petalPath = remember { Path() }

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

  val focusedWordCoords =
    remember(puzzleState.focusedWordId, puzzleState.targetWords) {
      val fid = puzzleState.focusedWordId
      puzzleState.targetWords.find { it.id == fid }?.coordinates()?.toSet().orEmpty()
    }

  val aspectRatio =
    (puzzleState.boardWidth.toFloat() / puzzleState.boardHeight.toFloat()).coerceIn(0.85f, 1.35f)

  // Handcrafted Washi Paper Board Sheet Container
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(aspectRatio)
        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
        .shadow(elevation = 4.dp, shape = RoundedCornerShape(18.dp))
        .clip(RoundedCornerShape(18.dp))
        .background(
          Brush.verticalGradient(
            listOf(IndigoSurface, Color(0xFFF6F0E2))
          )
        )
        .paperGrainOverlay(alpha = 0.055f, fiberCount = 72, speckleCount = 110, seed = 404)
        .border(
          1.5.dp,
          IndigoElevated,
          RoundedCornerShape(18.dp),
        )
        .padding(10.dp)
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
      val cornerRadius = CornerRadius(cellSize * 0.14f, cellSize * 0.14f)

      val skin = puzzleState.equippedSkin
      val skinBaseColor = Color(skin.baseColorHex)
      val skinBorderColor = Color(skin.borderColorHex)
      val skinTextColorArgb = Color(skin.textColorHex).toArgb()

      for (y in 0 until puzzleState.boardHeight) {
        for (x in 0 until puzzleState.boardWidth) {
          val cell = puzzleState.cells[y][x]
          if (!cell.isPlayable) continue

          val coord = Coordinate(x, y)
          val topLeft = Offset(originX + x * cellSize + pad, originY + y * cellSize + pad)
          val cellCenter = Offset(topLeft.x + innerSize / 2f, topLeft.y + innerSize / 2f)

          val isHoveredTarget = puzzleState.hoverCoord == coord
          val isInHoveredCorridor = coord in hoveredCorridorCoords
          val isInFocusedWord = coord in focusedWordCoords
          val isHintHighlighted = puzzleState.highlightedHintCoord == coord
          val flashStart = puzzleState.cellFlashTimestamps[coord]
          val foldElapsed = if (flashStart != null) (frameNowMs - flashStart) else 9999L
          val foldAnim = computePaperFoldTransition(foldElapsed, cell.isLockedCompleted)

          // Placement squash-and-stretch timing (when putting a tile into a slot)
          val placeStart = puzzleState.tilePlacementTimestamps[coord]
          val placeElapsed = if (placeStart != null) (frameNowMs - placeStart) else 9999L
          val (snapScaleX, snapScaleY) = computeSnapSquashStretch(placeElapsed)

          // Focused clue corridor or drag corridor highlight on the Washi paper
          if ((isInHoveredCorridor || isInFocusedWord) && !isHoveredTarget) {
            val corridorColor =
              if (isInHoveredCorridor) SkyCyan.copy(alpha = 0.14f + 0.08f * reticlePhase)
              else SkyCyan.copy(alpha = 0.09f)
            drawRoundRect(
              color = corridorColor,
              topLeft = Offset(topLeft.x - pad * 0.65f, topLeft.y - pad * 0.65f),
              size = Size(innerSize + pad * 1.3f, innerSize + pad * 1.3f),
              cornerRadius = cornerRadius,
            )
          }

          when {
            // 1. Permanent Fixed Clue Letter Cell (Folded Kraft Origami Paper Tile)
            cell.fixedLetter != null -> {
              val showLockedGreen = foldAnim.showLockedGreen
              val bgColor =
                if (showLockedGreen) CompletedWordBg else FixedClueCellBg
              val borderColor =
                if (showLockedGreen) CompletedWordBorder
                else if (isInFocusedWord) SkyCyan.copy(alpha = 0.65f)
                else FixedClueCellBorder

              withTransform({
                if (foldAnim.isFolding) {
                  scale(
                    scaleX = foldAnim.scaleX,
                    scaleY = foldAnim.scaleY,
                    pivot = cellCenter,
                  )
                  rotate(degrees = foldAnim.tiltDeg, pivot = cellCenter)
                }
              }) {
                // Tactile 3D bottom bevel & soft paper elevation
                drawRoundRect(
                  color = borderColor.copy(alpha = 0.55f),
                  topLeft = Offset(topLeft.x, topLeft.y + cellSize * 0.045f),
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                )
                drawRoundRect(
                  color = bgColor,
                  topLeft = topLeft,
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                )

                // Paper-fold crease shadow/highlight during active fold transition
                if (foldAnim.creaseAlpha > 0f) {
                  drawRoundRect(
                    color = Color(0xFF157F4B).copy(alpha = foldAnim.creaseAlpha * 0.28f),
                    topLeft = topLeft,
                    size = Size(innerSize, innerSize),
                    cornerRadius = cornerRadius,
                  )
                  drawLine(
                    color = Color.White.copy(alpha = foldAnim.creaseAlpha * 0.85f),
                    start = Offset(topLeft.x + innerSize * 0.08f, cellCenter.y),
                    end = Offset(topLeft.x + innerSize * 0.92f, cellCenter.y),
                    strokeWidth = cellSize * 0.045f,
                    cap = StrokeCap.Round,
                  )
                }

                // Clean subtle origami corner fold accent in top-right corner
                val foldSize = innerSize * 0.16f
                dogEarPath.reset()
                dogEarPath.moveTo(topLeft.x + innerSize - foldSize, topLeft.y)
                dogEarPath.lineTo(topLeft.x + innerSize, topLeft.y + foldSize)
                dogEarPath.lineTo(topLeft.x + innerSize - foldSize, topLeft.y + foldSize)
                dogEarPath.close()
                drawPath(
                  path = dogEarPath,
                  color = borderColor.copy(alpha = 0.28f),
                )

                drawRoundRect(
                  color = borderColor,
                  topLeft = topLeft,
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                  style = Stroke(width = cellSize * 0.032f),
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

                subPaint.color = WashiMuted.toArgb()
                subPaint.textSize = innerSize * 0.19f
                drawContext.canvas.nativeCanvas.drawText(
                  com.example.domain.model.GameCatalog.letterBaseScore(cell.fixedLetter).toString(),
                  topLeft.x + innerSize * 0.80f,
                  topLeft.y + innerSize * 0.88f,
                  subPaint,
                )
              }
            }

            // 2. Player Placed Origami Tile inside a Blank Target Slot (with Squash-and-Stretch & Paper-Fold!)
            cell.placedTile != null -> {
              val tile = cell.placedTile
              val showLockedGreen = foldAnim.showLockedGreen
              withTransform({
                scale(
                  scaleX = snapScaleX * foldAnim.scaleX,
                  scaleY = snapScaleY * foldAnim.scaleY,
                  pivot = cellCenter,
                )
                if (foldAnim.isFolding) {
                  rotate(degrees = foldAnim.tiltDeg, pivot = cellCenter)
                }
              }) {
                // Paper shadow
                drawRoundRect(
                  color = Color(0xFF7D6B4D).copy(alpha = 0.28f),
                  topLeft = Offset(topLeft.x + cellSize * 0.02f, topLeft.y + cellSize * 0.045f),
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                )
                drawRoundRect(
                  color = if (showLockedGreen) CompletedWordBg else skinBaseColor,
                  topLeft = topLeft,
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                )

                if (foldAnim.creaseAlpha > 0f) {
                  drawRoundRect(
                    color = Color(0xFF157F4B).copy(alpha = foldAnim.creaseAlpha * 0.28f),
                    topLeft = topLeft,
                    size = Size(innerSize, innerSize),
                    cornerRadius = cornerRadius,
                  )
                  drawLine(
                    color = Color.White.copy(alpha = foldAnim.creaseAlpha * 0.85f),
                    start = Offset(topLeft.x + innerSize * 0.08f, cellCenter.y),
                    end = Offset(topLeft.x + innerSize * 0.92f, cellCenter.y),
                    strokeWidth = cellSize * 0.045f,
                    cap = StrokeCap.Round,
                  )
                }

                // Origami dog-ear corner fold on placed tile
                val foldSize = innerSize * 0.19f
                dogEarPath.reset()
                dogEarPath.moveTo(topLeft.x + innerSize - foldSize, topLeft.y)
                dogEarPath.lineTo(topLeft.x + innerSize, topLeft.y + foldSize)
                dogEarPath.lineTo(topLeft.x + innerSize - foldSize, topLeft.y + foldSize)
                dogEarPath.close()
                drawPath(
                  path = dogEarPath,
                  color = (if (showLockedGreen) CeladonJade else skinBorderColor).copy(alpha = 0.35f),
                )

                val borderCol =
                  if (showLockedGreen) CeladonJade else skinBorderColor
                drawRoundRect(
                  color = borderCol,
                  topLeft = topLeft,
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                  style = Stroke(width = cellSize * 0.048f),
                )

                val charColorArgb =
                  if (showLockedGreen) WashiCream.toArgb() else skinTextColorArgb
                letterPaint.color = charColorArgb
                letterPaint.textSize = innerSize * 0.55f
                val textY = cellCenter.y - (letterPaint.descent() + letterPaint.ascent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                  tile.char.toString(),
                  cellCenter.x,
                  textY,
                  letterPaint,
                )

                subPaint.color = charColorArgb
                subPaint.textSize = innerSize * 0.20f
                drawContext.canvas.nativeCanvas.drawText(
                  tile.baseScore.toString(),
                  topLeft.x + innerSize * 0.80f,
                  topLeft.y + innerSize * 0.88f,
                  subPaint,
                )
              }
            }

            // 3. Open Blank Target Paper Cutout Socket (Needs a Letter!)
            else -> {
              val multBg =
                when (cell.multiplier) {
                  SquareMultiplier.TW -> SquareTwBg
                  SquareMultiplier.DW -> SquareDwBg
                  SquareMultiplier.TL -> SquareTlBg
                  SquareMultiplier.DL -> SquareDlBg
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
                  else -> BlankSlotSocketBorder
                }

              val strokeWidth =
                if (isHoveredTarget || isHintHighlighted) cellSize * 0.064f else cellSize * 0.034f

              // Clean solid rounded border for open sockets (no busy dashed lines)
              drawRoundRect(
                color = strokeColor,
                topLeft = topLeft,
                size = Size(innerSize, innerSize),
                cornerRadius = cornerRadius,
                style = Stroke(width = strokeWidth),
              )

              // Subtle inner recessed well rim
              drawRoundRect(
                color = Color.White.copy(alpha = 0.55f),
                topLeft = Offset(topLeft.x + cellSize * 0.04f, topLeft.y + cellSize * 0.04f),
                size = Size(innerSize - cellSize * 0.08f, innerSize - cellSize * 0.08f),
                cornerRadius = CornerRadius(cellSize * 0.11f, cellSize * 0.11f),
                style = Stroke(width = cellSize * 0.018f),
              )

              // Magnetic Lock-On Ghost Letter & Target Reticle Brackets while Dragging!
              if (isHoveredTarget && preview != null) {
                val activeColor = if (preview.isDualAxisValid) CeladonJade else VermilionRed
                drawRoundRect(
                  color = activeColor.copy(alpha = 0.18f + 0.10f * reticlePhase),
                  topLeft = topLeft,
                  size = Size(innerSize, innerSize),
                  cornerRadius = cornerRadius,
                )

                // Draw 4 animated magnetic corner lock-on brackets around the paper socket
                val bracketInset = -cellSize * (0.03f + 0.035f * reticlePhase)
                val bracketLen = innerSize * 0.26f
                val bx0 = topLeft.x + bracketInset
                val by0 = topLeft.y + bracketInset
                val bx1 = topLeft.x + innerSize - bracketInset
                val by1 = topLeft.y + innerSize - bracketInset
                val bStroke = cellSize * 0.05f

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
                letterPaint.textSize = innerSize * (0.52f + 0.05f * reticlePhase)
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
              } else if (cell.multiplier != SquareMultiplier.NONE) {
                subPaint.color = WashiCream.copy(alpha = 0.78f).toArgb()
                subPaint.textSize = innerSize * 0.27f
                val by = cellCenter.y - (subPaint.descent() + subPaint.ascent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(
                  cell.multiplier.label,
                  cellCenter.x,
                  by,
                  subPaint,
                )
              }
            }
          }

          // Subtle Paper Ripple Ring when a tile snaps into place
          if (placeElapsed in 0..420) {
            val t = placeElapsed / 420f
            val ringRadius = cellSize * (0.42f + 0.48f * t)
            val ringAlpha = (1f - t).coerceIn(0f, 1f)
            drawCircle(
              color = SkyCyan.copy(alpha = ringAlpha * 0.45f),
              radius = ringRadius,
              center = cellCenter,
              style = Stroke(width = cellSize * 0.035f * (1f - t * 0.5f)),
            )
          }

          // Soft Vermilion Ripple when dropping an invalid tile onto a slot
          val rejectStart = puzzleState.rejectedSlotTimestamps[coord]
          val rejectElapsed = if (rejectStart != null) (frameNowMs - rejectStart) else 9999L
          if (rejectElapsed in 0..480) {
            val rt = rejectElapsed / 480f
            val rAlpha = (1f - rt).coerceIn(0f, 1f)
            drawCircle(
              color = VermilionRed.copy(alpha = rAlpha * 0.55f),
              radius = cellSize * (0.42f + 0.65f * rt),
              center = cellCenter,
              style = Stroke(width = cellSize * 0.045f * rAlpha),
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
          (originY + hc.y * cellSize - cellSize * 0.55f).coerceAtLeast(cellSize * 0.40f)
        val pillW = cellSize * 2.1f
        val pillH = cellSize * 0.50f
        val pillColor = if (hp.isDualAxisValid) CeladonJade else VermilionRed

        drawRoundRect(
          color = IndigoSurface.copy(alpha = 0.96f),
          topLeft = Offset(centerX - pillW / 2f, pillCenterY - pillH / 2f),
          size = Size(pillW, pillH),
          cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
        )
        drawRoundRect(
          color = pillColor,
          topLeft = Offset(centerX - pillW / 2f, pillCenterY - pillH / 2f),
          size = Size(pillW, pillH),
          cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
          style = Stroke(width = cellSize * 0.04f),
        )

        val calloutLabel =
          if (hp.isDualAxisValid) "${hp.tileChar} • +${hp.estimatedScoreGain}"
          else "${hp.tileChar} ✕"
        subPaint.color = pillColor.toArgb()
        subPaint.textSize = cellSize * 0.24f
        val ty = pillCenterY - (subPaint.descent() + subPaint.ascent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(calloutLabel, centerX, ty, subPaint)
      }

      // Gentle Origami Paper Petals Wafting on Word Completion (Non-Distracting & Localized!)
      val origamiPetalPalette =
        listOf(
          CeladonJade,
          KyotoGold,
          SkyCyan,
        )

      for (confetti in puzzleState.confettiExplosions) {
        val elapsed = (frameNowMs - confetti.createdAtMs).coerceAtLeast(0L)
        if (elapsed in 0..720) {
          val t = elapsed / 720f
          val fadeAlpha = (sin(t * PI).toFloat() * 0.42f).coerceIn(0f, 0.42f)
          val gridCenter = Offset(originX + gridW / 2f, originY + gridH / 2f)
          val count = confetti.fragmentCount.coerceAtMost(12)

          for (i in 0 until count) {
            val angle = (2.0 * PI * i) / count + t * 0.35
            val dist = cellSize * (0.35f + 1.15f * t)
            val px = gridCenter.x + (cos(angle) * dist).toFloat()
            val py = gridCenter.y + (sin(angle) * dist * 0.65f).toFloat() - cellSize * 0.45f * t
            val petalSize = cellSize * 0.11f * (1f - t * 0.35f)
            val col = origamiPetalPalette[i % origamiPetalPalette.size].copy(alpha = fadeAlpha)

            withTransform({
              translate(left = px, top = py)
              rotate(degrees = (i * 45f + t * 90f) % 360f, pivot = Offset.Zero)
            }) {
              // Tiny folded origami triangle petal
              petalPath.reset()
              petalPath.moveTo(0f, -petalSize)
              petalPath.lineTo(petalSize * 0.85f, petalSize * 0.7f)
              petalPath.lineTo(-petalSize * 0.85f, petalSize * 0.7f)
              petalPath.close()
              drawPath(path = petalPath, color = col)
            }
          }
        }
      }

      // Render Compact Floating Score Popups (+50 PTS · COMBO x2!)
      for (popup in puzzleState.scorePopups) {
        val elapsed = (frameNowMs - popup.createdAtMs).coerceAtLeast(0L)
        if (elapsed in 0..1050) {
          val t = elapsed / 1050f
          val alpha = (1f - (t - 0.35f).coerceAtLeast(0f) / 0.65f).coerceIn(0f, 1f)
          val anchorX =
            (originX + (popup.coord.x + 0.5f) * cellSize).coerceIn(
              cellSize * 1.8f,
              size.width - cellSize * 1.8f,
            )
          val anchorY =
            (originY + popup.coord.y * cellSize - cellSize * 0.65f * t).coerceAtLeast(cellSize * 0.55f)

          letterPaint.color =
            (if (popup.isDualAxisClear) KyotoGold else CeladonJade)
              .copy(alpha = alpha)
              .toArgb()
          letterPaint.textSize = cellSize * 0.30f
          drawContext.canvas.nativeCanvas.drawText(
            popup.headline,
            anchorX,
            anchorY,
            letterPaint,
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
  if (elapsedMs !in 0..380) return 1f to 1f
  val t = elapsedMs / 380f
  return when {
    t < 0.28f -> {
      val p = t / 0.28f
      val sx = 1.24f + (1.12f - 1.24f) * p
      val sy = 1.24f + (0.89f - 1.24f) * p
      sx to sy
    }
    t < 0.62f -> {
      val p = (t - 0.28f) / 0.34f
      val sx = 1.12f + (0.96f - 1.12f) * p
      val sy = 0.89f + (1.05f - 0.89f) * p
      sx to sy
    }
    else -> {
      val p = (t - 0.62f) / 0.38f
      val sx = 0.96f + (1.0f - 0.96f) * p
      val sy = 1.05f + (1.0f - 1.05f) * p
      sx to sy
    }
  }
}

private data class PaperFoldAnimState(
  val isFolding: Boolean,
  val showLockedGreen: Boolean,
  val scaleX: Float,
  val scaleY: Float,
  val tiltDeg: Float,
  val creaseAlpha: Float,
)

/**
 * Computes the subtle 3D origami 'paper-fold' state when a board cell transitions
 * from its warm Washi/Kraft state into the locked Matcha Jade green state.
 */
private fun computePaperFoldTransition(
  foldElapsedMs: Long,
  isCellLocked: Boolean,
): PaperFoldAnimState {
  if (!isCellLocked) {
    return PaperFoldAnimState(
      isFolding = false,
      showLockedGreen = false,
      scaleX = 1f,
      scaleY = 1f,
      tiltDeg = 0f,
      creaseAlpha = 0f,
    )
  }
  // Waiting for staggered letter turn along the completed word
  if (foldElapsedMs < 0L) {
    return PaperFoldAnimState(
      isFolding = false,
      showLockedGreen = false,
      scaleX = 1f,
      scaleY = 1f,
      tiltDeg = 0f,
      creaseAlpha = 0f,
    )
  }
  val duration = 520f
  if (foldElapsedMs > duration.toLong()) {
    return PaperFoldAnimState(
      isFolding = false,
      showLockedGreen = true,
      scaleX = 1f,
      scaleY = 1f,
      tiltDeg = 0f,
      creaseAlpha = 0f,
    )
  }

  val t = (foldElapsedMs / duration).coerceIn(0f, 1f)
  return when {
    // Phase 1 (0..42%): Fold inward along horizontal origami crease before flipping to green
    t < 0.42f -> {
      val p = t / 0.42f
      PaperFoldAnimState(
        isFolding = true,
        showLockedGreen = false,
        scaleX = 1f - 0.08f * p,
        scaleY = 1f - 0.82f * p,
        tiltDeg = -3.2f * p,
        creaseAlpha = p,
      )
    }
    // Phase 2 (42..76%): Unfold into locked Matcha green state with slight spring overshoot
    t < 0.76f -> {
      val p = (t - 0.42f) / 0.34f
      PaperFoldAnimState(
        isFolding = true,
        showLockedGreen = true,
        scaleX = 0.92f + (1.05f - 0.92f) * p,
        scaleY = 0.18f + (1.06f - 0.18f) * p,
        tiltDeg = -3.2f * (1f - p) + 1.4f * p,
        creaseAlpha = (1f - p * 0.7f).coerceIn(0f, 1f),
      )
    }
    // Phase 3 (76..100%): Settle flat onto the Washi board
    else -> {
      val p = (t - 0.76f) / 0.24f
      PaperFoldAnimState(
        isFolding = true,
        showLockedGreen = true,
        scaleX = 1.05f + (1.0f - 1.05f) * p,
        scaleY = 1.06f + (1.0f - 1.06f) * p,
        tiltDeg = 1.4f * (1f - p),
        creaseAlpha = (0.3f * (1f - p)).coerceIn(0f, 1f),
      )
    }
  }
}

