package com.example.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Applies a procedural Washi 'paper-grain' fiber and micro-speckle texture overlay
 * onto any background or Card/Surface component in Jetpack Compose.
 *
 * Uses [drawWithCache] so fiber coordinates and grain points are computed only when
 * component bounds change, keeping 60/120 FPS rendering completely allocation-free.
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

    val darkFiberColor = Color(0xFF5C4B37).copy(alpha = alpha)
    val warmSpeckleColor = Color(0xFF7A654B).copy(alpha = alpha * 0.85f)
    val highlightFiberColor = Color(0xFFFFFFFF).copy(alpha = (alpha * 1.35f).coerceAtMost(0.12f))

    // Precompute deterministic organic Washi fibers & grain speckles for these bounds
    val fibers =
      if (w > 4f && h > 4f) {
        List(fiberCount) { i ->
          val h1 = ((i * 73856093) xor (seed * 19349663)) and 0x7FFFFFFF
          val h2 = (((i + 17) * 83492791) xor (seed * 297121507)) and 0x7FFFFFFF
          val startX = (h1 % 1000) / 1000f * w
          val startY = (h2 % 1000) / 1000f * h
          val angle = ((h1 xor h2) % 360) * (Math.PI / 180.0)
          val len = 6f + ((h1 % 14))
          val endX = (startX + (cos(angle) * len).toFloat()).coerceIn(0f, w)
          val endY = (startY + (sin(angle) * len).toFloat()).coerceIn(0f, h)
          Offset(startX, startY) to Offset(endX, endY)
        }
      } else {
        emptyList()
      }

    val speckles =
      if (w > 4f && h > 4f) {
        List(speckleCount) { i ->
          val h1 = (((i + 31) * 49979693) xor (seed * 86028121)) and 0x7FFFFFFF
          val h2 = (((i + 73) * 67867967) xor (seed * 982451653)) and 0x7FFFFFFF
          val px = (h1 % 1000) / 1000f * w
          val py = (h2 % 1000) / 1000f * h
          Offset(px, py)
        }
      } else {
        emptyList()
      }

    onDrawWithContent {
      drawContent()

      // 1. Subtle horizontal Washi deckle weave lines
      val stepY = 14f
      var y = 6f
      while (y < h) {
        drawLine(
          color = darkFiberColor.copy(alpha = alpha * 0.35f),
          start = Offset(0f, y),
          end = Offset(w, y),
          strokeWidth = 0.7f,
        )
        y += stepY
      }

      // 2. Organic short mulberry Washi fibers (alternating dark walnut & warm white fibers)
      fibers.forEachIndexed { idx, (start, end) ->
        drawLine(
          color = if (idx % 3 == 0) highlightFiberColor else darkFiberColor,
          start = start,
          end = end,
          strokeWidth = if (idx % 2 == 0) 1.1f else 0.8f,
        )
      }

      // 3. Fine paper-grain micro-speckles
      speckles.forEachIndexed { idx, pt ->
        drawCircle(
          color = if (idx % 4 == 0) highlightFiberColor else warmSpeckleColor,
          radius = if (idx % 3 == 0) 1.3f else 0.9f,
          center = pt,
        )
      }
    }
  }
