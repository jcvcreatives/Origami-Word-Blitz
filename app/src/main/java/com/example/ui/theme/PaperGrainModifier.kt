package com.example.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Applies a clean, smooth Washi paper illumination sheen and subtle warm edge depth
 * onto any background or Card/Surface component in Jetpack Compose — with zero
 * scratch lines or speckle dots so the UI stays crisp and visually appealing.
 */
fun Modifier.paperGrainOverlay(
  alpha: Float = 0.045f,
  fiberCount: Int = 0,
  speckleCount: Int = 0,
  seed: Int = 1337,
): Modifier =
  this.drawWithCache {
    val w = size.width
    val h = size.height

    val sheenBrush =
      Brush.linearGradient(
        colors =
          listOf(
            Color.White.copy(alpha = (alpha * 1.4f).coerceAtMost(0.10f)),
            Color.Transparent,
            Color(0xFF8C775B).copy(alpha = (alpha * 0.55f).coerceAtMost(0.04f)),
          ),
        start = Offset.Zero,
        end = Offset(w.coerceAtLeast(1f), h.coerceAtLeast(1f)),
      )

    onDrawWithContent {
      drawContent()
      if (w > 4f && h > 4f) {
        drawRect(brush = sheenBrush)
      }
    }
  }
