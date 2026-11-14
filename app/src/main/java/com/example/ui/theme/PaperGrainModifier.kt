package com.example.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * Lightweight, cached 64x64 procedural Washi paper grain bitmap shader.
 * Synthesizes soft, organic 3x3 box-filtered warm paper pulp luminance modulation
 * once per process — with zero scratch lines or large dots — and tiles seamlessly
 * across background and card surfaces at zero per-frame allocation cost.
 */
private val cachedWashiGrainBrush: ShaderBrush? by lazy {
  runCatching {
    val size = 64
    val rawNoise = FloatArray(size * size)
    val rng = Random(1337)
    for (i in rawNoise.indices) {
      rawNoise[i] = rng.nextFloat()
    }

    val pixels = IntArray(size * size)
    for (y in 0 until size) {
      for (x in 0 until size) {
        // 3x3 wrap-around box filter for soft, tactile handmade Washi pulp texture
        var sum = 0f
        for (dy in -1..1) {
          val ny = (y + dy + size) % size
          for (dx in -1..1) {
            val nx = (x + dx + size) % size
            sum += rawNoise[ny * size + nx]
          }
        }
        val avg = sum / 9f
        val delta = avg - 0.5f
        val isWarmPeak = delta >= 0f
        val intensity = ( kotlin.math.abs(delta) * 52f ).toInt().coerceIn(0, 14)
        val r = if (isWarmPeak) 255 else 118
        val g = if (isWarmPeak) 250 else 101
        val b = if (isWarmPeak) 238 else 78
        pixels[y * size + x] = (intensity shl 24) or (r shl 16) or (g shl 8) or b
      }
    }

    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    val shader =
      ImageShader(
        image = bitmap.asImageBitmap(),
        tileModeX = TileMode.Repeated,
        tileModeY = TileMode.Repeated,
      )
    ShaderBrush(shader)
  }.getOrNull()
}

/**
 * Applies a procedural 'washi paper' grain effect and warm illumination sheen
 * to background and card surfaces using a lightweight cached canvas shader overlay
 * to enhance the handcrafted tactile aesthetic.
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
    val grainBrush = cachedWashiGrainBrush
    val grainOpacity = (alpha * 16f).coerceIn(0.45f, 0.95f)

    val sheenBrush =
      Brush.linearGradient(
        colors =
          listOf(
            Color.White.copy(alpha = (alpha * 1.35f).coerceAtMost(0.09f)),
            Color.Transparent,
            Color(0xFF8C775B).copy(alpha = (alpha * 0.50f).coerceAtMost(0.038f)),
          ),
        start = Offset.Zero,
        end = Offset(w.coerceAtLeast(1f), h.coerceAtLeast(1f)),
      )

    onDrawWithContent {
      drawContent()
      if (w > 4f && h > 4f) {
        if (grainBrush != null) {
          drawRect(brush = grainBrush, alpha = grainOpacity)
        }
        drawRect(brush = sheenBrush)
      }
    }
  }
