package com.example.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies a procedural Washi 'paper-grain' fiber and micro-speckle texture overlay
 * onto any background or Card/Surface component in Jetpack Compose.
 *
 * Optimized for 120 FPS APK performance:
 * - Pre-builds batched [Path] geometry for deckle weave lines and mulberry fibers inside [drawWithCache].
 * - Batches all micro-speckles into two [PointMode.Points] GPU draw calls instead of ratusan individual
 *   circle/line JNI calls, keeping per-frame rendering completely allocation-free.
 */
fun Modifier.paperGrainOverlay(
  alpha: Float = 0.045f,
  fiberCount: Int = 64,
  speckleCount: Int = 96,
  seed: Int = 1337,
): Modifier =
  this.drawWithCache {
    val w = size.width
    val h = size.height

    val deckleColor = Color(0xFF5C4B37).copy(alpha = alpha * 0.35f)
    val darkFiberColor = Color(0xFF5C4B37).copy(alpha = alpha)
    val warmSpeckleColor = Color(0xFF7A654B).copy(alpha = alpha * 0.85f)
    val highlightFiberColor = Color(0xFFFFFFFF).copy(alpha = (alpha * 1.35f).coerceAtMost(0.12f))

    val decklePath = Path()
    val darkFiberPath = Path()
    val highlightFiberPath = Path()
    val warmSpeckles = ArrayList<Offset>(speckleCount)
    val highlightSpeckles = ArrayList<Offset>(speckleCount / 3 + 1)

    if (w > 4f && h > 4f) {
      // 1. Pre-build horizontal Washi deckle weave path
      val stepY = 14f
      var y = 6f
      while (y < h) {
        decklePath.moveTo(0f, y)
        decklePath.lineTo(w, y)
        y += stepY
      }

      // 2. Pre-build batched organic mulberry Washi fiber paths
      for (i in 0 until fiberCount) {
        val h1 = ((i * 73856093) xor (seed * 19349663)) and 0x7FFFFFFF
        val h2 = (((i + 17) * 83492791) xor (seed * 297121507)) and 0x7FFFFFFF
        val startX = (h1 % 1000) / 1000f * w
        val startY = (h2 % 1000) / 1000f * h
        val angle = ((h1 xor h2) % 360) * (Math.PI / 180.0)
        val len = 6f + (h1 % 14)
        val endX = (startX + (cos(angle) * len).toFloat()).coerceIn(0f, w)
        val endY = (startY + (sin(angle) * len).toFloat()).coerceIn(0f, h)

        val targetPath = if (i % 3 == 0) highlightFiberPath else darkFiberPath
        targetPath.moveTo(startX, startY)
        targetPath.lineTo(endX, endY)
      }

      // 3. Pre-build batched paper-grain micro-speckle point lists
      for (i in 0 until speckleCount) {
        val h1 = (((i + 31) * 49979693) xor (seed * 86028121)) and 0x7FFFFFFF
        val h2 = (((i + 73) * 67867967) xor (seed * 982451653)) and 0x7FFFFFFF
        val px = (h1 % 1000) / 1000f * w
        val py = (h2 % 1000) / 1000f * h
        val pt = Offset(px, py)
        if (i % 4 == 0) {
          highlightSpeckles.add(pt)
        } else {
          warmSpeckles.add(pt)
        }
      }
    }

    val deckleStroke = Stroke(width = 0.7f)
    val fiberStroke = Stroke(width = 0.95f, cap = StrokeCap.Round)

    onDrawWithContent {
      drawContent()
      if (w > 4f && h > 4f) {
        drawPath(path = decklePath, color = deckleColor, style = deckleStroke)
        drawPath(path = darkFiberPath, color = darkFiberColor, style = fiberStroke)
        drawPath(path = highlightFiberPath, color = highlightFiberColor, style = fiberStroke)
        if (warmSpeckles.isNotEmpty()) {
          drawPoints(
            points = warmSpeckles,
            pointMode = PointMode.Points,
            color = warmSpeckleColor,
            strokeWidth = 2.0f,
            cap = StrokeCap.Round,
          )
        }
        if (highlightSpeckles.isNotEmpty()) {
          drawPoints(
            points = highlightSpeckles,
            pointMode = PointMode.Points,
            color = highlightFiberColor,
            strokeWidth = 2.3f,
            cap = StrokeCap.Round,
          )
        }
      }
    }
  }
