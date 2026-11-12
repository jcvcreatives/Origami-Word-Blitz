package com.example.ui.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Coordinates the [AudioPlayer] ('click', 'snap', 'thud', 'fanfare') with progressive
 * tactile [HapticFeedback] and [Vibrator] waveforms for "LexiFill: Crossword Skeleton".
 */
class SoundAndHapticsEngine(
  context: Context? = null,
  val audioPlayer: AudioPlayer = AudioPlayer(context),
) {

  private val appContext = context?.applicationContext

  var isSoundEnabled: Boolean
    get() = !audioPlayer.isMuted
    set(value) {
      audioPlayer.isMuted = !value
    }

  var isHapticsEnabled: Boolean = true

  private val vibrator: Vibrator? =
    if (appContext != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
    } else {
      null
    }

  /**
   * Triggers the 'click' sound effect and light selection haptic on tile pickup or selection.
   */
  fun onTilePickup(haptics: HapticFeedback?, pitchMultiplier: Float = 1.0f) {
    audioPlayer.playClick(pitchMultiplier = pitchMultiplier)
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
  }

  /**
   * Triggers the 'snap' sound effect and crisp lock-in haptic pulse on valid tile placement.
   */
  fun onTileSnapToGrid(haptics: HapticFeedback?, pitchMultiplier: Float = 1.0f) {
    audioPlayer.playSnap(pitchMultiplier = pitchMultiplier)
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
      vibratePulse(durationMs = 16L, amplitude = 125)
    }
  }

  /**
   * Triggers a pitched 'snap' sound effect and multi-stage haptic burst when a word is completed.
   */
  fun onWordScored(score: Int, isTripleWord: Boolean, comboStreak: Int, haptics: HapticFeedback?) {
    val rate = (1.05f + (comboStreak - 1) * 0.08f).coerceIn(1.0f, 1.42f)
    audioPlayer.playSnap(pitchMultiplier = rate)
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

  /**
   * Triggers the 'thud' sound effect and double-pulse rejection vibration on invalid placement.
   */
  fun onInvalidWord(haptics: HapticFeedback?) {
    audioPlayer.playThud()
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
      vibrateWaveform(longArrayOf(0, 35, 40, 35), intArrayOf(0, 190, 0, 140))
    }
  }

  /**
   * Triggers the celebratory 'fanfare' sound effect and triumphant haptic cadence on level completion.
   */
  fun onLevelClearedFanfare(haptics: HapticFeedback?) {
    audioPlayer.playFanfare()
    if (isHapticsEnabled) {
      haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
      vibrateWaveform(
        longArrayOf(0, 30, 40, 35, 40, 50, 45, 90),
        intArrayOf(0, 160, 0, 190, 0, 220, 0, 255),
      )
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

  fun release() {
    audioPlayer.release()
  }
}
