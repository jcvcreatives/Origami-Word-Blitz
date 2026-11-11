package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.domain.model.Coordinate
import com.example.domain.model.CosmeticTileSkin
import com.example.domain.model.LexiBoardState
import com.example.domain.model.ParticleBurst
import com.example.domain.model.SquareMultiplier
import com.example.domain.model.Tile
import com.example.domain.model.TileBonusEffect
import com.example.ui.theme.CeladonJade
import com.example.ui.theme.IndigoCard
import com.example.ui.theme.IndigoNight
import com.example.ui.theme.KyotoGold
import com.example.ui.theme.SquareDlBg
import com.example.ui.theme.SquareDwBg
import com.example.ui.theme.SquareNoneBg
import com.example.ui.theme.SquareStarBg
import com.example.ui.theme.SquareTlBg
import com.example.ui.theme.SquareTwBg
import com.example.ui.theme.VermilionRed
import com.example.ui.theme.WashiCream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Maps a root screen coordinate (e.g., from a dragged rack tile) into a 15x15 [Coordinate]
 * accounting for the board's current pinch-zoom scale and pan offset.
 */
class BoardViewportMapper {
  var boundsInRoot: Rect = Rect.Zero
  var scale: Float = 1f
  var panOffset: Offset = Offset.Zero

  fun rootOffsetToBoardCoordinate(rootPoint: Offset): Coordinate? {
    if (boundsInRoot.isEmpty || !boundsInRoot.contains(rootPoint)) return null
    val localX = rootPoint.x - boundsInRoot.left
    val localY = rootPoint.y - boundsInRoot.top
    val centerX = boundsInRoot.width / 2f
    val centerY = boundsInRoot.height / 2f

    // Reverse transform around center + panOffset
    val unscaledX = (localX - centerX - panOffset.x) / scale + centerX
    val unscaledY = (localY - centerY - panOffset.y) / scale + centerY

    val cellSize = boundsInRoot.width / Coordinate.BOARD_SIZE.toFloat()
    if (cellSize <= 0f) return null
    val gridX = floor(unscaledX / cellSize).toInt()
    val gridY = floor(unscaledY / cellSize).toInt()
    val coord = Coordinate(gridX, gridY)
    return if (coord.isValid) coord else null
  }
}

@Composable
fun OrigamiBoardCanvas(
  boardState: LexiBoardState,
  equippedSkin: CosmeticTileSkin,
  particleBursts: List<ParticleBurst>,
  shakeIntensity: Float,
  hoveredCoord: Coordinate?,
  viewportMapper: BoardViewportMapper,
  onSquareTapped: (Coordinate) -> Unit,
  modifier: Modifier = Modifier,
) {
  var zoomScale by remember { mutableFloatStateOf(1f) }
  var panOffset by remember { mutableStateOf(Offset.Zero) }

  // Screen micro-shake keyframe animation on 3x Word or invalid plays
  val shakeOffsetX = remember { Animatable(0f) }
  LaunchedEffect(shakeIntensity) {
    if (shakeIntensity > 0f) {
      shakeOffsetX.animateTo(
        targetValue = 0f,
        animationSpec =
          keyframes {
            durationMillis = 360
            (-shakeIntensity) at 45
            shakeIntensity at 95
            (-shakeIntensity * 0.75f) at 150
            (shakeIntensity * 0.65f) at 210
            (-shakeIntensity * 0.35f) at 280
            0f at 360
          },
      )
    } else {
      shakeOffsetX.snapTo(0f)
    }
  }

  // Particle burst progress animation
  val particleProgress = remember { Animatable(0f) }
  LaunchedEffect(particleBursts) {
    if (particleBursts.isNotEmpty()) {
      particleProgress.snapTo(0f)
      particleProgress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
      )
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "board_glow")
  val pulseAlpha by
    infiniteTransition.animateFloat(
      initialValue = 0.35f,
      targetValue = 0.90f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 900, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse_alpha",
    )

  val letterPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }
  val scorePaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.RIGHT
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
  }
  val multPaint = remember {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .offset { IntOffset(shakeOffsetX.value.roundToInt(), 0) }
        .clip(RoundedCornerShape(16.dp))
        .background(IndigoNight)
        .border(1.5.dp, KyotoGold.copy(alpha = 0.38f), RoundedCornerShape(16.dp))
        .onGloballyPositioned { coords ->
          viewportMapper.boundsInRoot = coords.boundsInRoot()
          viewportMapper.scale = zoomScale
          viewportMapper.panOffset = panOffset
        }
  ) {
    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .testTag("origami_15x15_board_canvas")
          .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
              val newScale = (zoomScale * zoom).coerceIn(1f, 2.35f)
              val maxPanX = (size.width * (newScale - 1f)) / 2f
              val maxPanY = (size.height * (newScale - 1f)) / 2f
              val newPan =
                if (newScale <= 1.01f) {
                  Offset.Zero
                } else {
                  Offset(
                    x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                    y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY),
                  )
                }
              zoomScale = newScale
              panOffset = newPan
              viewportMapper.scale = newScale
              viewportMapper.panOffset = newPan
            }
          }
          .pointerInput(zoomScale, panOffset) {
            detectTapGestures { tapOffset ->
              val centerX = size.width / 2f
              val centerY = size.height / 2f
              val unscaledX = (tapOffset.x - centerX - panOffset.x) / zoomScale + centerX
              val unscaledY = (tapOffset.y - centerY - panOffset.y) / zoomScale + centerY
              val cellSize = size.width / Coordinate.BOARD_SIZE.toFloat()
              val gx = floor(unscaledX / cellSize).toInt()
              val gy = floor(unscaledY / cellSize).toInt()
              val coord = Coordinate(gx, gy)
              if (coord.isValid) {
                onSquareTapped(coord)
              }
            }
          }
    ) {
      val boardPx = size.width
      val cellPx = boardPx / Coordinate.BOARD_SIZE.toFloat()
      val centerX = size.width / 2f
      val centerY = size.height / 2f

      drawContext.canvas.save()
      drawContext.canvas.translate(centerX + panOffset.x, centerY + panOffset.y)
      drawContext.canvas.scale(zoomScale, zoomScale)
      drawContext.canvas.translate(-centerX, -centerY)

      // 1. Render 15x15 squares & multipliers
      for (y in 0 until Coordinate.BOARD_SIZE) {
        for (x in 0 until Coordinate.BOARD_SIZE) {
          val coord = Coordinate(x, y)
          val square = boardState.squares[y][x]
          val pendingTile = boardState.pendingPlacements[coord]
          val effectiveTile = pendingTile ?: square.occupyingTile
          val left = x * cellPx
          val top = y * cellPx
          val pad = cellPx * 0.05f
          val innerSize = Size(cellPx - pad * 2f, cellPx - pad * 2f)
          val innerTopLeft = Offset(left + pad, top + pad)

          val sqColor =
            when (square.multiplier) {
              SquareMultiplier.TW -> SquareTwBg
              SquareMultiplier.DW -> SquareDwBg
              SquareMultiplier.TL -> SquareTlBg
              SquareMultiplier.DL -> SquareDlBg
              SquareMultiplier.CENTER_STAR -> SquareStarBg
              SquareMultiplier.NONE -> SquareNoneBg
            }

          drawRoundRect(
            color = sqColor,
            topLeft = innerTopLeft,
            size = innerSize,
            cornerRadius = CornerRadius(cellPx * 0.14f, cellPx * 0.14f),
          )

          // Origami subtle diagonal paper fold crease on special squares
          if (square.multiplier != SquareMultiplier.NONE && effectiveTile == null) {
            drawLine(
              color = Color.White.copy(alpha = 0.08f),
              start = innerTopLeft,
              end = Offset(innerTopLeft.x + innerSize.width, innerTopLeft.y + innerSize.height),
              strokeWidth = 1f,
            )
            multPaint.color = android.graphics.Color.argb(225, 255, 248, 236)
            multPaint.textSize = cellPx * 0.36f
            val textY = top + cellPx * 0.63f
            drawContext.canvas.nativeCanvas.drawText(
              square.multiplier.label,
              left + cellPx * 0.5f,
              textY,
              multPaint,
            )
          }

          // Hint square pulse highlight
          if (coord in boardState.hintCoords && effectiveTile == null) {
            drawRoundRect(
              color = KyotoGold.copy(alpha = pulseAlpha * 0.45f),
              topLeft = innerTopLeft,
              size = innerSize,
              cornerRadius = CornerRadius(cellPx * 0.14f, cellPx * 0.14f),
            )
            drawRoundRect(
              color = KyotoGold,
              topLeft = innerTopLeft,
              size = innerSize,
              cornerRadius = CornerRadius(cellPx * 0.14f, cellPx * 0.14f),
              style = Stroke(width = 2.2f),
            )
          }

          // Hover drop target highlight when dragging a tile
          if (hoveredCoord == coord && square.occupyingTile == null) {
            drawRoundRect(
              color = CeladonJade.copy(alpha = 0.42f),
              topLeft = innerTopLeft,
              size = innerSize,
              cornerRadius = CornerRadius(cellPx * 0.16f, cellPx * 0.16f),
            )
            drawRoundRect(
              color = CeladonJade,
              topLeft = innerTopLeft,
              size = innerSize,
              cornerRadius = CornerRadius(cellPx * 0.16f, cellPx * 0.16f),
              style = Stroke(width = 3f),
            )
          }

          // Render occupying or pending Tile
          if (effectiveTile != null) {
            drawTileOnCanvas(
              tile = effectiveTile,
              skin = equippedSkin,
              isPending = pendingTile != null,
              isLastPlayed = coord in boardState.lastPlayedCoords,
              pulseAlpha = pulseAlpha,
              left = left,
              top = top,
              cellPx = cellPx,
              letterPaint = letterPaint,
              scorePaint = scorePaint,
            )
          }
        }
      }

      // 2. Render live floating score preview pill directly above pending tiles on the board
      val preview = boardState.movePreview
      if (preview != null && boardState.pendingPlacements.isNotEmpty()) {
        val minX = boardState.pendingPlacements.keys.minOf { it.x }
        val minY = boardState.pendingPlacements.keys.minOf { it.y }
        val pillX = (minX * cellPx).coerceIn(8f, boardPx - cellPx * 4.5f)
        val pillY = if (minY > 0) (minY - 0.85f) * cellPx else (minY + 1.15f) * cellPx
        val pillColor =
          if (preview.isValidPosition && preview.allWordsValid) CeladonJade else VermilionRed
        val pillWidth = cellPx * 4.3f
        val pillHeight = cellPx * 0.78f

        drawRoundRect(
          color = IndigoNight.copy(alpha = 0.92f),
          topLeft = Offset(pillX, pillY),
          size = Size(pillWidth, pillHeight),
          cornerRadius = CornerRadius(14f, 14f),
        )
        drawRoundRect(
          color = pillColor,
          topLeft = Offset(pillX, pillY),
          size = Size(pillWidth, pillHeight),
          cornerRadius = CornerRadius(14f, 14f),
          style = Stroke(width = 2.2f),
        )

        multPaint.color =
          if (preview.isValidPosition && preview.allWordsValid) {
            android.graphics.Color.rgb(0, 214, 163)
          } else {
            android.graphics.Color.rgb(255, 107, 107)
          }
        multPaint.textSize = cellPx * 0.34f
        val badgeLabel =
          if (preview.isValidPosition && preview.allWordsValid) {
            val primary = preview.formedWords.firstOrNull()?.word ?: ""
            "$primary +${preview.totalTurnScore} pts"
          } else {
            "Invalid Move"
          }
        drawContext.canvas.nativeCanvas.drawText(
          badgeLabel,
          pillX + pillWidth / 2f,
          pillY + pillHeight * 0.68f,
          multPaint,
        )
      }

      // 3. Render explosive particle bursts on scored words
      if (particleBursts.isNotEmpty() && particleProgress.value < 0.99f) {
        val p = particleProgress.value
        for (burst in particleBursts) {
          val originX = (burst.boardCoord.x + 0.5f) * cellPx
          val originY = (burst.boardCoord.y + 0.5f) * cellPx
          val baseColor = Color(burst.colorHex)
          for (i in 0 until burst.particleCount) {
            val angle = (2.0 * PI * i) / burst.particleCount
            val radius = cellPx * (0.3f + 1.85f * p) * (if (i % 2 == 0) 1.15f else 0.75f)
            val px = originX + (cos(angle) * radius).toFloat()
            val py = originY + (sin(angle) * radius).toFloat()
            val particleSize = (cellPx * 0.16f * (1f - p)).coerceAtLeast(1.5f)
            drawCircle(
              color = baseColor.copy(alpha = (1f - p).coerceIn(0f, 1f)),
              radius = particleSize,
              center = Offset(px, py),
            )
          }
        }
      }

      drawContext.canvas.restore()
    }

    // Zoom toggle pill in top-right corner of the board
    Row(
      modifier =
        Modifier.align(Alignment.TopEnd)
          .padding(6.dp)
          .clip(CircleShape)
          .background(IndigoCard.copy(alpha = 0.88f))
          .border(1.dp, KyotoGold.copy(alpha = 0.35f), CircleShape)
          .padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      IconButton(
        onClick = {
          if (zoomScale > 1.05f) {
            zoomScale = 1f
            panOffset = Offset.Zero
          } else {
            zoomScale = 1.65f
            panOffset = Offset.Zero
          }
          viewportMapper.scale = zoomScale
          viewportMapper.panOffset = panOffset
        },
        modifier = Modifier.size(32.dp).testTag("board_zoom_toggle_button"),
      ) {
        Icon(
          imageVector =
            if (zoomScale > 1.05f) Icons.Default.CenterFocusStrong else Icons.Default.ZoomIn,
          contentDescription = "Toggle board zoom",
          tint = WashiCream,
          modifier = Modifier.size(18.dp),
        )
      }
      if (zoomScale > 1.05f) {
        Text(
          text = "${(zoomScale * 100).roundToInt()}%",
          style = MaterialTheme.typography.labelSmall,
          color = KyotoGold,
          modifier = Modifier.padding(end = 6.dp),
        )
      }
    }
  }
}

private fun DrawScope.drawTileOnCanvas(
  tile: Tile,
  skin: CosmeticTileSkin,
  isPending: Boolean,
  isLastPlayed: Boolean,
  pulseAlpha: Float,
  left: Float,
  top: Float,
  cellPx: Float,
  letterPaint: Paint,
  scorePaint: Paint,
) {
  val pad = cellPx * 0.04f
  val tileTopLeft = Offset(left + pad, top + pad)
  val tileSize = Size(cellPx - pad * 2f, cellPx - pad * 2f)
  val corner = CornerRadius(cellPx * 0.16f, cellPx * 0.16f)

  val baseColor = Color(skin.baseColorHex)
  val borderColor =
    when {
      isPending -> CeladonJade
      isLastPlayed -> KyotoGold
      else -> Color(skin.borderColorHex)
    }

  // Tile drop shadow / 3D bevel
  drawRoundRect(
    color = Color.Black.copy(alpha = 0.35f),
    topLeft = Offset(tileTopLeft.x, tileTopLeft.y + cellPx * 0.05f),
    size = tileSize,
    cornerRadius = corner,
  )

  // Main washi tile body
  drawRoundRect(
    color = baseColor,
    topLeft = tileTopLeft,
    size = tileSize,
    cornerRadius = corner,
  )

  // Origami folded corner ear on bonus tiles
  if (tile.bonusEffect != TileBonusEffect.NONE) {
    val foldPath =
      Path().apply {
        moveTo(tileTopLeft.x + tileSize.width - cellPx * 0.24f, tileTopLeft.y)
        lineTo(tileTopLeft.x + tileSize.width, tileTopLeft.y)
        lineTo(tileTopLeft.x + tileSize.width, tileTopLeft.y + cellPx * 0.24f)
        close()
      }
    drawPath(
      path = foldPath,
      color =
        when (tile.bonusEffect) {
          TileBonusEffect.GOLD_FOIL -> KyotoGold
          TileBonusEffect.CRANE_BLESSING -> VermilionRed
          TileBonusEffect.SAKURA_SPARK -> Color(0xFFFF7597)
          TileBonusEffect.NONE -> Color.Transparent
        },
    )
  }

  // Animated border for pending or mythic skins
  drawRoundRect(
    color = if (isPending) borderColor.copy(alpha = pulseAlpha) else borderColor,
    topLeft = tileTopLeft,
    size = tileSize,
    cornerRadius = corner,
    style = Stroke(width = if (isPending || isLastPlayed) 2.6f else 1.5f),
  )

  // Letter glyph
  val textArgb =
    android.graphics.Color.argb(
      255,
      ((skin.textColorHex shr 16) and 0xFF).toInt(),
      ((skin.textColorHex shr 8) and 0xFF).toInt(),
      (skin.textColorHex and 0xFF).toInt(),
    )
  letterPaint.color = textArgb
  letterPaint.textSize = cellPx * 0.54f
  drawContext.canvas.nativeCanvas.drawText(
    tile.char.toString(),
    left + cellPx * 0.46f,
    top + cellPx * 0.67f,
    letterPaint,
  )

  // Subscript point value
  scorePaint.color = textArgb
  scorePaint.textSize = cellPx * 0.24f
  drawContext.canvas.nativeCanvas.drawText(
    tile.baseScore.toString(),
    left + cellPx * 0.89f,
    top + cellPx * 0.88f,
    scorePaint,
  )
}
