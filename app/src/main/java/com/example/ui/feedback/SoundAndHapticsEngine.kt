package com.example.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Snappy SoundPool audio synthesizer + progressive tactile haptics engine.
 * Synthesizes crisp wooden/paper percussion and chime WAV samples at startup
 * and loads them into Android's native SoundPool for <5ms playback latency.
 */
class SoundAndHapticsEngine(context: Context) {

  private val appContext = context.applicationContext
  private var soundPool: SoundPool? = null
  private var soundTilePickId: Int = 0
  private var soundTileSnapId: Int = 0
  private var soundWordScoreId: Int = 0
  private var soundTripleWordId: Int = 0
  private var soundInvalidId: Int = 0

  var isSoundEnabled: Boolean = true
  var isHapticsEnabled: Boolean = true

  private val vibrator: Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      vm?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

  init {
    try {
      val attrs =
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_GAME)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
      soundPool = pool

      soundTilePickId = pool.load(createToneWav("sfx_pick.wav", listOf(540.0), 45), 1)
      soundTileSnapId = pool.load(createToneWav("sfx_snap.wav", listOf(720.0, 960.0), 65), 1)
      soundWordScoreId =
        pool.load(createToneWav("sfx_score.wav", listOf(523.25, 659.25, 783.99), 180), 1)
      soundTripleWordId =
        pool.load(
          createToneWav("sfx_triple.wav", listOf(523.25, 659.25, 783.99, 1046.50), 280),
          1,
        )
      soundInvalidId = pool.load(createToneWav("sfx_invalid.wav", listOf(190.0, 155.0), 140), 1)
    } catch (_: Throwable) {
      // Safe fallback in headless unit test environments
    }
  }

  fun onTilePickup(haptics: HapticFeedback?) {
    if (isSoundEnabled && soundTilePickId != 0) {
      soundPool?.play(soundTilePickId, 0.55f, 0.55f, 1, 0, 1.05f)
    }
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
  }

  fun onTileSnapToGrid(haptics: HapticFeedback?, pitchMultiplier: Float = 1.0f) {
    if (isSoundEnabled && soundTileSnapId != 0) {
      soundPool?.play(
        soundTileSnapId,
        0.75f,
        0.75f,
        1,
        0,
        pitchMultiplier.coerceIn(0.85f, 1.35f),
      )
    }
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
      vibratePulse(durationMs = 14L, amplitude = 110)
    }
  }

  fun onWordScored(score: Int, isTripleWord: Boolean, comboStreak: Int, haptics: HapticFeedback?) {
    if (isSoundEnabled) {
      val rate = (1.0f + (comboStreak - 1) * 0.06f).coerceIn(1.0f, 1.4f)
      val id = if (isTripleWord || score >= 40) soundTripleWordId else soundWordScoreId
      if (id != 0) {
        soundPool?.play(id, 0.9f, 0.9f, 2, 0, rate)
      }
    }
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
      when {
        isTripleWord || score >= 50 ->
          vibrateWaveform(longArrayOf(0, 28, 35, 42, 35, 65), intArrayOf(0, 180, 0, 220, 0, 255))
        score >= 25 ->
          vibrateWaveform(longArrayOf(0, 22, 30, 35), intArrayOf(0, 150, 0, 210))
        else -> vibratePulse(durationMs = 24L, amplitude = 140)
      }
    }
  }

  fun onInvalidWord(haptics: HapticFeedback?) {
    if (isSoundEnabled && soundInvalidId != 0) {
      soundPool?.play(soundInvalidId, 0.7f, 0.7f, 1, 0, 1.0f)
    }
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
      vibrateWaveform(longArrayOf(0, 35, 40, 35), intArrayOf(0, 190, 0, 140))
    }
  }

  private fun vibratePulse(durationMs: Long, amplitude: Int) {
    try {
      val vib = vibrator ?: return
      if (!vib.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vib.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
      } else {
        @Suppress("DEPRECATION")
        vib.vibrate(durationMs)
      }
    } catch (_: Throwable) {}
  }

  private fun vibrateWaveform(timings: LongArray, amplitudes: IntArray) {
    try {
      val vib = vibrator ?: return
      if (!vib.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
      } else {
        @Suppress("DEPRECATION")
        vib.vibrate(45L)
      }
    } catch (_: Throwable) {}
  }

  private fun createToneWav(fileName: String, frequencies: List<Double>, durationMs: Int): String {
    val file = File(appContext.cacheDir, fileName)
    if (file.exists() && file.length() > 44) return file.absolutePath

    val sampleRate = 22050
    val numSamples = (sampleRate * durationMs) / 1000
    val pcmData = ByteArray(numSamples * 2)
    val step = maxOf(1, numSamples / frequencies.size)

    for (i in 0 until numSamples) {
      val freqIdx = (i / step).coerceAtMost(frequencies.lastIndex)
      val freq = frequencies[freqIdx]
      val t = i.toDouble() / sampleRate
      val localProgress = (i % step).toDouble() / step
      val envelope = exp(-3.8 * localProgress)
      val sampleVal = (sin(2.0 * PI * freq * t) * envelope * 24000.0).toInt().coerceIn(-32767, 32767)
      pcmData[i * 2] = (sampleVal and 0xFF).toByte()
      pcmData[i * 2 + 1] = ((sampleVal shr 8) and 0xFF).toByte()
    }

    FileOutputStream(file).use { out ->
      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      header.put("RIFF".toByteArray(Charsets.US_ASCII))
      header.putInt(36 + pcmData.size)
      header.put("WAVE".toByteArray(Charsets.US_ASCII))
      header.put("fmt ".toByteArray(Charsets.US_ASCII))
      header.putInt(16) // PCM chunk size
      header.putShort(1) // AudioFormat = 1 (PCM)
      header.putShort(1) // NumChannels = 1 (Mono)
      header.putInt(sampleRate)
      header.putInt(sampleRate * 2) // ByteRate
      header.putShort(2) // BlockAlign
      header.putShort(16) // BitsPerSample
      header.put("data".toByteArray(Charsets.US_ASCII))
      header.putInt(pcmData.size)
      out.write(header.array())
      out.write(pcmData)
    }
    return file.absolutePath
  }

  fun release() {
    try {
      soundPool?.release()
      soundPool = null
    } catch (_: Throwable) {}
  }
}
