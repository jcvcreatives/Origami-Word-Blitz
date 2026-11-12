package com.example.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Low-latency game audio utility for "LexiFill: Crossword Skeleton".
 *
 * Synthesizes and plays four distinct, tactile acoustic effects using Android's native [SoundPool]
 * (with an instant [AudioTrack] fallback while SoundPool samples finish loading):
 * - [SoundEffect.CLICK] ('click'): Crisp woody transient for tile selection & pickup.
 * - [SoundEffect.SNAP] ('snap'): Magnetic dual-harmonic lock-in pop for successful tile placement.
 * - [SoundEffect.THUD] ('thud'): Low-frequency muffled pitch-drop thump for invalid placement.
 * - [SoundEffect.FANFARE] ('fanfare'): Celebratory 5-note major arpeggio chord for level completion.
 */
class AudioPlayer(context: Context? = null) {

  enum class SoundEffect(val id: String, val label: String) {
    CLICK("click", "Tile Selection Click"),
    SNAP("snap", "Successful Placement Snap"),
    THUD("thud", "Invalid Placement Thud"),
    FANFARE("fanfare", "Level Completion Fanfare"),
  }

  private val appContext: Context? = context?.applicationContext
  private var soundPool: SoundPool? = null
  private val soundIds = ConcurrentHashMap<SoundEffect, Int>()
  private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()
  private val rawPcmCache = ConcurrentHashMap<SoundEffect, ShortArray>()
  private val playCounts = ConcurrentHashMap<SoundEffect, Int>()
  private val audioExecutor = Executors.newSingleThreadExecutor()

  @Volatile var isMuted: Boolean = false
  @Volatile var lastPlayedEffect: SoundEffect? = null
    private set

  init {
    // Pre-synthesize the 4 waveform profiles in memory so they are immediately available
    rawPcmCache[SoundEffect.CLICK] = synthesizeClickPcm()
    rawPcmCache[SoundEffect.SNAP] = synthesizeSnapPcm()
    rawPcmCache[SoundEffect.THUD] = synthesizeThudPcm()
    rawPcmCache[SoundEffect.FANFARE] = synthesizeFanfarePcm()

    if (appContext != null) {
      try {
        val attrs =
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val pool =
          SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        pool.setOnLoadCompleteListener { _, sampleId, status ->
          if (status == 0) {
            loadedSoundIds.add(sampleId)
          }
        }
        soundPool = pool

        for (effect in SoundEffect.entries) {
          val pcm = rawPcmCache[effect] ?: continue
          val wavPath = writePcmToWavFile(appContext, "lexifill_${effect.id}.wav", pcm)
          val id = pool.load(wavPath, 1)
          soundIds[effect] = id
        }
      } catch (_: Throwable) {
        // Safe fallback in headless JVM / Robolectric unit test environments
      }
    }
  }

  /**
   * Triggers the specified [SoundEffect] with optional [pitchMultiplier] and [volume].
   */
  fun play(
    effect: SoundEffect,
    pitchMultiplier: Float = 1.0f,
    volume: Float = 0.85f,
  ) {
    lastPlayedEffect = effect
    playCounts[effect] = (playCounts[effect] ?: 0) + 1

    if (isMuted) return

    val safePitch = pitchMultiplier.coerceIn(0.75f, 1.45f)
    val safeVol = volume.coerceIn(0.1f, 1.0f)

    try {
      val pool = soundPool
      val sampleId = soundIds[effect] ?: 0
      if (pool != null && sampleId != 0 && loadedSoundIds.contains(sampleId)) {
        val priority = if (effect == SoundEffect.FANFARE) 2 else 1
        pool.play(sampleId, safeVol, safeVol, priority, 0, safePitch)
        return
      }

      // Instant fallback via AudioTrack if SoundPool is still decoding on first interaction
      val pcm = rawPcmCache[effect]
      if (pcm != null && appContext != null) {
        audioExecutor.execute {
          playPcmOneShot(pcm, safeVol)
        }
      }
    } catch (_: Throwable) {
      // Ignore in headless test environments
    }
  }

  /** Triggers 'click' for tile selection, pickup, or clue focus. */
  fun playClick(pitchMultiplier: Float = 1.0f) {
    play(SoundEffect.CLICK, pitchMultiplier = pitchMultiplier, volume = 0.65f)
  }

  /** Triggers 'snap' for successful tile placement into a crossword blank slot. */
  fun playSnap(pitchMultiplier: Float = 1.0f) {
    play(SoundEffect.SNAP, pitchMultiplier = pitchMultiplier, volume = 0.85f)
  }

  /** Triggers 'thud' for invalid tile placement or intersection conflict. */
  fun playThud() {
    play(SoundEffect.THUD, pitchMultiplier = 1.0f, volume = 0.80f)
  }

  /** Triggers 'fanfare' for completing all intersecting words in a level. */
  fun playFanfare() {
    play(SoundEffect.FANFARE, pitchMultiplier = 1.0f, volume = 0.95f)
  }

  fun toggleMute(): Boolean {
    isMuted = !isMuted
    return isMuted
  }

  fun getPlayCount(effect: SoundEffect): Int = playCounts[effect] ?: 0

  fun resetStatsForTesting() {
    playCounts.clear()
    lastPlayedEffect = null
  }

  fun release() {
    try {
      soundPool?.release()
      soundPool = null
      audioExecutor.shutdownNow()
    } catch (_: Throwable) {}
  }

  /**
   * 'click': 38ms crisp woody percussion tap (680Hz -> 940Hz fast transient with harmonic click).
   */
  private fun synthesizeClickPcm(): ShortArray {
    val durationMs = 38
    val numSamples = (SAMPLE_RATE * durationMs) / 1000
    val samples = ShortArray(numSamples)
    for (i in 0 until numSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / numSamples
      // Rapid pitch chirp from 680Hz to 940Hz + high overtone transient
      val freq = 680.0 + 260.0 * progress
      val env = exp(-7.5 * progress)
      val wave =
        0.72 * sin(2.0 * PI * freq * t) +
          0.28 * sin(2.0 * PI * (freq * 2.4) * t) * exp(-14.0 * progress)
      samples[i] = (wave * env * 25000.0).toInt().coerceIn(-32767, 32767).toShort()
    }
    return samples
  }

  /**
   * 'snap': 82ms magnetic lock-in pop (two-stage mechanical latch + rising fifth harmonic chime).
   */
  private fun synthesizeSnapPcm(): ShortArray {
    val durationMs = 82
    val numSamples = (SAMPLE_RATE * durationMs) / 1000
    val samples = ShortArray(numSamples)
    val splitIndex = (numSamples * 0.36).toInt()
    for (i in 0 until numSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val isSecondStage = i >= splitIndex
      val localProgress =
        if (!isSecondStage) {
          i.toDouble() / maxOf(1, splitIndex)
        } else {
          (i - splitIndex).toDouble() / maxOf(1, numSamples - splitIndex)
        }
      val baseFreq = if (!isSecondStage) 660.0 else 990.0 // E5 -> B5 magnetic lock fifth
      val env = exp(-4.2 * localProgress)
      val harmonic =
        0.75 * sin(2.0 * PI * baseFreq * t) +
          0.25 * sin(2.0 * PI * (baseFreq * 2.0) * t)
      samples[i] = (harmonic * env * 27000.0).toInt().coerceIn(-32767, 32767).toShort()
    }
    return samples
  }

  /**
   * 'thud': 155ms low-frequency muffled bass impact (165Hz pitching down to 74Hz with dull damping).
   */
  private fun synthesizeThudPcm(): ShortArray {
    val durationMs = 155
    val numSamples = (SAMPLE_RATE * durationMs) / 1000
    val samples = ShortArray(numSamples)
    var phase1 = 0.0
    var phase2 = 0.0
    for (i in 0 until numSamples) {
      val progress = i.toDouble() / numSamples
      // Pitch bends downward from 165Hz to 74Hz for a heavy rejection thud
      val instFreq = 165.0 - 91.0 * progress
      phase1 += (2.0 * PI * instFreq) / SAMPLE_RATE
      phase2 += (2.0 * PI * (instFreq * 1.5)) / SAMPLE_RATE
      val env = exp(-4.8 * progress)
      val subWave = 0.80 * sin(phase1) + 0.20 * sin(phase2) * exp(-8.0 * progress)
      samples[i] = (subWave * env * 28500.0).toInt().coerceIn(-32767, 32767).toShort()
    }
    return samples
  }

  /**
   * 'fanfare': 540ms triumphant 5-note major-7th/octave arpeggio (C5 -> E5 -> G5 -> B5 -> C6)
   * with warm overtone sustain.
   */
  private fun synthesizeFanfarePcm(): ShortArray {
    val notes = doubleArrayOf(523.25, 659.25, 783.99, 987.77, 1046.50) // C5, E5, G5, B5, C6
    val noteDurationsMs = intArrayOf(80, 80, 80, 90, 210)
    val totalMs = noteDurationsMs.sum()
    val totalSamples = (SAMPLE_RATE * totalMs) / 1000
    val samples = ShortArray(totalSamples)

    var cursor = 0
    for (nIdx in notes.indices) {
      val freq = notes[nIdx]
      val noteSamples = (SAMPLE_RATE * noteDurationsMs[nIdx]) / 1000
      val decayRate = if (nIdx == notes.lastIndex) 2.4 else 3.8
      for (i in 0 until noteSamples) {
        if (cursor >= totalSamples) break
        val t = i.toDouble() / SAMPLE_RATE
        val localProgress = i.toDouble() / maxOf(1, noteSamples)
        val attack = (i.toDouble() / (SAMPLE_RATE * 0.006)).coerceAtMost(1.0)
        val env = attack * exp(-decayRate * localProgress)
        // Fundamental + octave + fifth shimmer
        val wave =
          0.65 * sin(2.0 * PI * freq * t) +
            0.23 * sin(2.0 * PI * (freq * 2.0) * t) +
            0.12 * sin(2.0 * PI * (freq * 1.5) * t)
        samples[cursor] = (wave * env * 26500.0).toInt().coerceIn(-32767, 32767).toShort()
        cursor++
      }
    }
    return samples
  }

  private fun writePcmToWavFile(context: Context, fileName: String, pcmSamples: ShortArray): String {
    val file = File(context.cacheDir, fileName)
    val pcmBytes = ByteArray(pcmSamples.size * 2)
    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmSamples)

    FileOutputStream(file).use { out ->
      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      header.put("RIFF".toByteArray(Charsets.US_ASCII))
      header.putInt(36 + pcmBytes.size)
      header.put("WAVE".toByteArray(Charsets.US_ASCII))
      header.put("fmt ".toByteArray(Charsets.US_ASCII))
      header.putInt(16) // PCM chunk size
      header.putShort(1) // AudioFormat = 1 (PCM)
      header.putShort(1) // NumChannels = 1 (Mono)
      header.putInt(SAMPLE_RATE)
      header.putInt(SAMPLE_RATE * 2) // ByteRate
      header.putShort(2) // BlockAlign
      header.putShort(16) // BitsPerSample
      header.put("data".toByteArray(Charsets.US_ASCII))
      header.putInt(pcmBytes.size)
      out.write(header.array())
      out.write(pcmBytes)
    }
    return file.absolutePath
  }

  private fun playPcmOneShot(pcm: ShortArray, volume: Float) {
    var track: AudioTrack? = null
    try {
      val byteCount = pcm.size * 2
      track =
        AudioTrack(
          AudioManager.STREAM_MUSIC,
          SAMPLE_RATE,
          AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT,
          maxOf(byteCount, AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)),
          AudioTrack.MODE_STATIC,
        )
      track.write(pcm, 0, pcm.size)
      track.setVolume(volume)
      track.play()
      val sleepMs = ((pcm.size * 1000L) / SAMPLE_RATE) + 30L
      Thread.sleep(sleepMs)
    } catch (_: Throwable) {
    } finally {
      try {
        track?.stop()
        track?.release()
      } catch (_: Throwable) {}
    }
  }

  private companion object {
    const val SAMPLE_RATE = 22050
  }
}
