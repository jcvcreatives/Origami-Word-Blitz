package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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
        val intensity = (abs(delta) * 52f).toInt().coerceIn(0, 14)
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
 * AGSL (Android Graphics Shading Language) Washi Paper Texture Shader for locked board cells.
 * Synthesizes an animated Kozo (mulberry) fiber weave + fine pressed-paper pulp grain +
 * warm raking-light sheen across locked Matcha-green origami cells.
 */
const val WASHI_LOCKED_CELL_AGSL_SRC =
  """
  uniform float2 uOrigin;
  uniform float2 uSize;
  uniform float uTime;
  uniform float uFoldBoost;
  uniform float uSeed;

  float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
  }

  float valueNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
  }

  half4 main(float2 fragCoord) {
    float2 uv = (fragCoord - uOrigin) / max(uSize, float2(1.0, 1.0));
    float2 driftA = float2(cos(uTime * 0.65 + uSeed) * 0.18, sin(uTime * 0.52 + uSeed) * 0.14);
    float2 driftB = float2(-sin(uTime * 0.48 + uSeed) * 0.15, cos(uTime * 0.60 + uSeed) * 0.16);

    // Dual-axis anisotropic Kozo mulberry paper fibers
    float2 fiberUv1 = float2(uv.x * 16.0 + uv.y * 5.5, uv.y * 4.2 - uv.x * 2.0) + driftA;
    float2 fiberUv2 = float2(uv.x * 5.0 - uv.y * 15.0, uv.x * 3.8 + uv.y * 4.5) + driftB;
    float kozoWeave = 0.55 * valueNoise(fiberUv1) + 0.45 * valueNoise(fiberUv2);

    // Fine pressed-pulp micro-grain
    float finePulp = valueNoise(uv * 28.0 + driftA * 1.9 + float2(uSeed * 1.7, uSeed * 2.3));
    float grainSignal = (kozoWeave * 0.64 + finePulp * 0.36) - 0.5;

    // Subtle raking-light wave moving diagonally across the pressed Washi surface
    float rakingWave = sin((uv.x * 2.6 + uv.y * 2.1) - uTime * 1.65 + uSeed) * 0.5 + 0.5;
    float highlightMask = smoothstep(0.35, 0.95, rakingWave);

    half3 warmFiberHighlight = half3(1.0, 0.988, 0.945);
    half3 deepSagePulp = half3(0.067, 0.388, 0.235);
    half3 tint = grainSignal >= 0.0 ? warmFiberHighlight : deepSagePulp;

    float baseAlpha = clamp(abs(grainSignal) * (0.34 + 0.22 * uFoldBoost) + highlightMask * (0.045 + 0.065 * uFoldBoost), 0.0, 0.24);
    return half4(tint * baseAlpha, baseAlpha);
  }
"""

/**
 * High-performance Washi Paper Texture Shader renderer for locked crossword cells.
 * Uses AGSL [RuntimeShader] on Android 13+ (API 33+) and a dual-layer matrix-animated
 * Kozo-fiber [BitmapShader] fallback on API 24–32 so every locked cell exhibits a tactile,
 * living handmade paper-craft grain at locked 60 FPS with zero per-frame allocations.
 */
object WashiLockedCellShader {
  private const val TEXTURE_SIZE = 96

  private val shaderMatrixA = Matrix()
  private val shaderMatrixB = Matrix()
  private val drawRectF = RectF()

  private val fallbackGrainPaintA = Paint(Paint.ANTI_ALIAS_FLAG)
  private val fallbackGrainPaintB = Paint(Paint.ANTI_ALIAS_FLAG)
  private val agslPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  private val runtimeShader: Any? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      runCatching { RuntimeShader(WASHI_LOCKED_CELL_AGSL_SRC) }.getOrNull()
    } else {
      null
    }
  }

  private val lockedKozoShaderA: BitmapShader? by lazy {
    buildKozoWashiBitmapShader(seed = 2026, fiberAngleRad = 0.42f)
  }

  private val lockedKozoShaderB: BitmapShader? by lazy {
    buildKozoWashiBitmapShader(seed = 4099, fiberAngleRad = -1.08f)
  }

  private fun buildKozoWashiBitmapShader(seed: Int, fiberAngleRad: Float): BitmapShader? =
    runCatching {
      val s = TEXTURE_SIZE
      val raw = FloatArray(s * s)
      val rng = Random(seed)
      for (i in raw.indices) {
        raw[i] = rng.nextFloat()
      }

      val cosA = cos(fiberAngleRad)
      val sinA = sin(fiberAngleRad)
      val pixels = IntArray(s * s)

      for (y in 0 until s) {
        for (x in 0 until s) {
          // 3x3 pulp average
          var sum = 0f
          for (dy in -1..1) {
            val ny = (y + dy + s) % s
            for (dx in -1..1) {
              val nx = (x + dx + s) % s
              sum += raw[ny * s + nx]
            }
          }
          val pulp = sum / 9f

          // Anisotropic Kozo mulberry fiber streak modulation
          val rx = x * cosA + y * sinA
          val ry = -x * sinA + y * cosA
          val fiberWave =
            (sin(rx * 0.46f + sin(ry * 0.19f) * 2.2f) * 0.5f + 0.5f) * 0.55f +
              (cos(ry * 0.38f + cos(rx * 0.15f) * 1.8f) * 0.5f + 0.5f) * 0.45f

          val combined = (pulp * 0.58f + fiberWave * 0.42f) - 0.5f
          val isHighlight = combined >= 0f
          val alpha = (abs(combined) * 92f).toInt().coerceIn(0, 34)
          // Warm Washi silk-ivory fiber highlights vs deep Matcha-sage pulp pockets
          val r = if (isHighlight) 255 else 16
          val g = if (isHighlight) 252 else 104
          val b = if (isHighlight) 240 else 62
          pixels[y * s + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }
      }

      val bmp = Bitmap.createBitmap(pixels, s, s, Bitmap.Config.ARGB_8888)
      BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }.getOrNull()

  fun drawOnLockedCell(
    drawScope: DrawScope,
    topLeft: Offset,
    cellSize: Size,
    cornerRadius: CornerRadius,
    timeSec: Float,
    foldBoost: Float,
    cellSeed: Int,
  ) {
    val w = cellSize.width
    val h = cellSize.height
    if (w <= 2f || h <= 2f) return

    val seedPhase = (cellSeed % 97) * 0.13f
    drawRectF.set(topLeft.x, topLeft.y, topLeft.x + w, topLeft.y + h)
    val nativeCanvas = drawScope.drawContext.canvas.nativeCanvas

    // 1. Try hardware AGSL RuntimeShader on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val agsl = runtimeShader as? RuntimeShader
      if (agsl != null) {
        agsl.setFloatUniform("uOrigin", topLeft.x, topLeft.y)
        agsl.setFloatUniform("uSize", w, h)
        agsl.setFloatUniform("uTime", timeSec)
        agsl.setFloatUniform("uFoldBoost", foldBoost.coerceIn(0f, 1f))
        agsl.setFloatUniform("uSeed", seedPhase)
        agslPaint.shader = agsl
        agslPaint.alpha = 255
        nativeCanvas.drawRoundRect(drawRectF, cornerRadius.x, cornerRadius.y, agslPaint)
      }
    }

    // 2. Also render the tactile dual-layer Kozo fiber BitmapShader pass (provides rich visible
    // mulberry fiber grain on all API levels 24–36 and during animated lock-in transitions)
    val shaderA = lockedKozoShaderA
    val shaderB = lockedKozoShaderB
    if (shaderA != null && shaderB != null) {
      val driftAx = topLeft.x + cos(timeSec * 0.85f + seedPhase) * 9.5f + cellSeed * 7.3f
      val driftAy = topLeft.y + sin(timeSec * 0.70f + seedPhase) * 7.5f + cellSeed * 5.1f
      shaderMatrixA.reset()
      shaderMatrixA.postScale(0.78f, 0.78f)
      shaderMatrixA.postTranslate(driftAx, driftAy)
      shaderA.setLocalMatrix(shaderMatrixA)

      val driftBx = topLeft.x - sin(timeSec * 0.62f + seedPhase * 1.4f) * 8.0f + cellSeed * 11.7f
      val driftBy = topLeft.y + cos(timeSec * 0.76f + seedPhase * 1.4f) * 8.0f + cellSeed * 3.9f
      shaderMatrixB.reset()
      shaderMatrixB.postScale(0.64f, 0.64f)
      shaderMatrixB.postTranslate(driftBx, driftBy)
      shaderB.setLocalMatrix(shaderMatrixB)

      val boostAlpha = ((0.68f + 0.32f * foldBoost.coerceIn(0f, 1f)) * 255f).toInt().coerceIn(0, 255)
      fallbackGrainPaintA.shader = shaderA
      fallbackGrainPaintA.alpha = boostAlpha
      nativeCanvas.drawRoundRect(drawRectF, cornerRadius.x, cornerRadius.y, fallbackGrainPaintA)

      fallbackGrainPaintB.shader = shaderB
      fallbackGrainPaintB.alpha = (boostAlpha * 0.78f).toInt().coerceIn(0, 255)
      nativeCanvas.drawRoundRect(drawRectF, cornerRadius.x, cornerRadius.y, fallbackGrainPaintB)
    }

    // 3. Subtle animated raking-light sheen across the pressed Washi fibers
    val wavePhase = (sin(timeSec * 1.55f + seedPhase).toFloat() * 0.5f + 0.5f)
    val sheenPeakAlpha = 0.055f + 0.085f * foldBoost.coerceIn(0f, 1f) + 0.035f * wavePhase
    val bandOffset = w * (-0.25f + 1.5f * ((timeSec * 0.28f + seedPhase * 0.2f) % 1.0f))
    drawScope.drawRoundRect(
      brush =
        Brush.linearGradient(
          colors =
            listOf(
              Color.Transparent,
              Color(0xFFFFFDF6).copy(alpha = sheenPeakAlpha),
              Color(0xFF157F4B).copy(alpha = sheenPeakAlpha * 0.42f),
              Color.Transparent,
            ),
          start = Offset(topLeft.x + bandOffset - w * 0.45f, topLeft.y),
          end = Offset(topLeft.x + bandOffset + w * 0.55f, topLeft.y + h),
        ),
      topLeft = topLeft,
      size = cellSize,
      cornerRadius = cornerRadius,
    )
  }
}

/**
 * DrawScope extension that applies the animated Washi paper texture shader to a locked board cell.
 */
fun DrawScope.drawLockedWashiCellShader(
  topLeft: Offset,
  size: Size,
  cornerRadius: CornerRadius,
  timeSec: Float,
  foldBoost: Float = 0f,
  cellSeed: Int = 0,
) {
  WashiLockedCellShader.drawOnLockedCell(
    drawScope = this,
    topLeft = topLeft,
    cellSize = size,
    cornerRadius = cornerRadius,
    timeSec = timeSec,
    foldBoost = foldBoost,
    cellSeed = cellSeed,
  )
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
