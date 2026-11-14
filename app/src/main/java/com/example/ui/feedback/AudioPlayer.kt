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
 * Physical-modeling acoustic foley and Japanese instrument engine for "Crossword Origami".
 *
 * Replaces generic sine/triangle oscillator beeps with multi-layered physical-modeling PCM buffers:
 * - [SoundEffect.CLICK] ('click'): Washi paper fiber brush + modal Hinoki cypress woodblock tap.
 * - [SoundEffect.SNAP] ('snap'): Crisp origami paper-fold crease transient + Hirajoshi bamboo/Koto fifth.
 * - [SoundEffect.THUD] ('thud'): Double-damped hollow cedar tray bump + soft paper slide-back swish.
 * - [SoundEffect.FANFARE] ('fanfare'): Polyphonic Kyoto Koto harp arpeggio (overlapping plucked strings + Furin wind-bell shimmer).
 */
class AudioPlayer(context: Context? = null) {

  enum class SoundEffect(val id: String, val label: String, val subtitle: String) {
    CLICK("click", "Washi Tap", "Hinoki Wood & Paper Brush"),
    SNAP("snap", "Origami Fold", "Crease Snap & Bamboo Chime"),
    THUD("thud", "Tatami Settle", "Muted Cedar Tray Bump"),
    FANFARE("fanfare", "Koto Ensemble", "Kyoto Harp & Furin Bell"),
  }

  enum class AcousticKit(
    val id: String,
    val title: String,
    val subtitle: String,
    val woodBaseHz: Double,
    val chimeScaleRatio: Double,
    val paperPresence: Double,
  ) {
    HINOKI_WASHI(
      id = "hinoki_washi",
      title = "Hinoki & Washi",
      subtitle = "Warm Cypress Woodblock, Paper Crease & Koto Harp",
      woodBaseHz = 385.0,
      chimeScaleRatio = 1.0,
      paperPresence = 1.0,
    ),
    KYOTO_BAMBOO(
      id = "kyoto_bamboo",
      title = "Kyoto Bamboo",
      subtitle = "Hollow Take-Marimba, Shishi-odoshi Clack & Wind Bell",
      woodBaseHz = 468.0,
      chimeScaleRatio = 1.12246, // +2 semitones (E-based pentatonic)
      paperPresence = 0.85,
    ),
    SUMI_CERAMIC(
      id = "sumi_ceramic",
      title = "Sumi Porcelain",
      subtitle = "Go-Stone Slate Click, Celadon Bowl & Temple Chime",
      woodBaseHz = 560.0,
      chimeScaleRatio = 0.8909, // -2 semitones (C-based warm resonance)
      paperPresence = 0.72,
    ),
  }

  private val appContext: Context? = context?.applicationContext
  private var soundPool: SoundPool? = null
  private val soundIds = ConcurrentHashMap<String, Int>()
  private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()
  private val rawPcmCache = ConcurrentHashMap<String, ShortArray>()
  private val playCounts = ConcurrentHashMap<SoundEffect, Int>()
  private val audioExecutor = Executors.newSingleThreadExecutor()

  @Volatile var isMuted: Boolean = false
  @Volatile var activeKit: AcousticKit = AcousticKit.HINOKI_WASHI
    private set
  @Volatile var lastPlayedEffect: SoundEffect? = null
    private set

  init {
    // Pre-synthesize waveforms for the default kit immediately, and warm all kits
    for (kit in AcousticKit.entries) {
      rawPcmCache[cacheKey(kit, SoundEffect.CLICK)] = synthesizeClickPcm(kit)
      rawPcmCache[cacheKey(kit, SoundEffect.SNAP)] = synthesizeSnapPcm(kit)
      rawPcmCache[cacheKey(kit, SoundEffect.THUD)] = synthesizeThudPcm(kit)
      rawPcmCache[cacheKey(kit, SoundEffect.FANFARE)] = synthesizeFanfarePcm(kit)
    }

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

        for (kit in AcousticKit.entries) {
          for (effect in SoundEffect.entries) {
            val key = cacheKey(kit, effect)
            val pcm = rawPcmCache[key] ?: continue
            val wavPath = writePcmToWavFile(appContext, "origami_${key}.wav", pcm)
            val id = pool.load(wavPath, 1)
            soundIds[key] = id
          }
        }
      } catch (_: Throwable) {
        // Safe fallback in headless JVM / Robolectric unit test environments
      }
    }
  }

  private fun cacheKey(kit: AcousticKit, effect: SoundEffect): String = "${kit.id}_${effect.id}"

  fun selectAcousticKit(kit: AcousticKit) {
    activeKit = kit
  }

  /**
   * Triggers the specified [SoundEffect] with optional [pitchMultiplier] and [volume].
   */
  fun play(
    effect: SoundEffect,
    pitchMultiplier: Float = 1.0f,
    volume: Float = 0.88f,
  ) {
    lastPlayedEffect = effect
    playCounts[effect] = (playCounts[effect] ?: 0) + 1

    if (isMuted) return

    val safePitch = pitchMultiplier.coerceIn(0.82f, 1.38f)
    val safeVol = volume.coerceIn(0.1f, 1.0f)
    val key = cacheKey(activeKit, effect)

    try {
      val pool = soundPool
      val sampleId = soundIds[key] ?: 0
      if (pool != null && sampleId != 0 && loadedSoundIds.contains(sampleId)) {
        val priority = if (effect == SoundEffect.FANFARE) 2 else 1
        pool.play(sampleId, safeVol, safeVol, priority, 0, safePitch)
        return
      }

      // Instant fallback via AudioTrack if SoundPool is still decoding on first interaction
      val pcm = rawPcmCache[key]
      if (pcm != null && appContext != null) {
        audioExecutor.execute {
          playPcmOneShot(pcm, safeVol)
        }
      }
    } catch (_: Throwable) {
      // Ignore in headless test environments
    }
  }

  /** Triggers 'click' (Washi paper brush + Hinoki wood tap) for tile pickup or focus. */
  fun playClick(pitchMultiplier: Float = 1.0f) {
    play(SoundEffect.CLICK, pitchMultiplier = pitchMultiplier, volume = 0.72f)
  }

  /** Triggers 'snap' (Origami crease + Bamboo/Koto lock) for valid tile placement. */
  fun playSnap(pitchMultiplier: Float = 1.0f) {
    play(SoundEffect.SNAP, pitchMultiplier = pitchMultiplier, volume = 0.90f)
  }

  /** Triggers 'thud' (Double-damped cedar tray bump + paper slide) for invalid placement. */
  fun playThud() {
    play(SoundEffect.THUD, pitchMultiplier = 1.0f, volume = 0.84f)
  }

  /** Triggers 'fanfare' (Polyphonic Kyoto Koto & Furin wind-bell ensemble) on level clear. */
  fun playFanfare() {
    play(SoundEffect.FANFARE, pitchMultiplier = 1.0f, volume = 0.96f)
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
   * Deterministic pink/velvet paper-fiber noise generator so Washi paper rustles and
   * origami creases sound warm and organic rather than harsh white static.
   */
  private fun warmPaperNoise(index: Int, seed: Int): Double {
    var n = (index * 1664525 + seed * 1013904223) xor (index shl 13)
    n = (n * (n * n * 15731 + 789221) + 1376312589)
    return ((n and 0x7fffffff).toDouble() / 1073741824.0) - 1.0
  }

  /**
   * Soft acoustic saturation (prevents digital harshness and adds warm wood/paper body).
   */
  private fun warmSaturate(x: Double): Double {
    val clamped = x.coerceIn(-2.5, 2.5)
    return clamped * (27.0 + clamped * clamped) / (27.0 + 9.0 * clamped * clamped)
  }

  /**
   * 'click': 56ms Washi Paper Fiber Brush + Modal Hinoki Woodblock Tap.
   * Uses inharmonic wooden plate modes (1.0 : 1.73 : 2.61 : 3.78) + band-filtered paper brush.
   */
  private fun synthesizeClickPcm(kit: AcousticKit): ShortArray {
    val durationMs = 56
    val numSamples = (SAMPLE_RATE * durationMs) / 1000
    val samples = ShortArray(numSamples)
    val f0 = kit.woodBaseHz
    var lpState = 0.0
    var bpState = 0.0

    for (i in 0 until numSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / numSamples

      // 1. Washi paper fiber brush (0..22ms tactile card-lift texture)
      val rawNoise = warmPaperNoise(i, 41)
      lpState += 0.28 * (rawNoise - lpState)
      val highPassed = rawNoise - lpState
      bpState += 0.42 * (highPassed - bpState)
      val paperEnv = sin(PI * (progress / 0.42).coerceAtMost(1.0)) * exp(-5.2 * progress)
      val paperBrush = bpState * paperEnv * 0.42 * kit.paperPresence

      // 2. Modal Hinoki woodblock tap (inharmonic resonant modes with rapid high-mode damping)
      val impactBend = 1.0 + 0.14 * exp(-38.0 * progress)
      val m1 = sin(2.0 * PI * (f0 * impactBend) * t) * exp(-6.8 * progress)
      val m2 = sin(2.0 * PI * (f0 * 1.73 * impactBend) * t) * exp(-13.5 * progress) * 0.48
      val m3 = sin(2.0 * PI * (f0 * 2.61) * t) * exp(-22.0 * progress) * 0.28
      val m4 = sin(2.0 * PI * (f0 * 3.78) * t) * exp(-34.0 * progress) * 0.16

      val attack = (i.toDouble() / (SAMPLE_RATE * 0.0018)).coerceAtMost(1.0)
      val woodWave = (m1 + m2 + m3 + m4) * attack * 0.78

      val mixed = warmSaturate((woodWave + paperBrush) * 1.35)
      samples[i] = (mixed * 26500.0).toInt().coerceIn(-32767, 32767).toShort()
    }
    return samples
  }

  /**
   * 'snap': 150ms Origami Paper Crease + Japanese Bamboo/Koto Fifth Lock.
   * Stage 1 (0..36ms): Crisp origami fold crease (swept paper-fiber friction + thumb thump).
   * Stage 2 (24..150ms): Plucked Hirajoshi fifth (D5 587.33Hz + A5 880.0Hz) with warm harmonics.
   */
  private fun synthesizeSnapPcm(kit: AcousticKit): ShortArray {
    val durationMs = 150
    val numSamples = (SAMPLE_RATE * durationMs) / 1000
    val samples = ShortArray(numSamples)
    val note1Hz = 587.33 * kit.chimeScaleRatio // D5
    val note2Hz = 880.00 * kit.chimeScaleRatio // A5
    var filterA = 0.0
    var filterB = 0.0

    for (i in 0 until numSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / numSamples

      // Stage 1: Crisp Origami Paper Fold Crease + tactile thumb press (0..38ms)
      val creaseProgress = (progress / 0.26).coerceAtMost(1.0)
      val rawNoise = warmPaperNoise(i, 108)
      val cutoff = 0.22 + 0.34 * sin(PI * creaseProgress)
      filterA += cutoff * (rawNoise - filterA)
      filterB += cutoff * (filterA - filterB)
      val bandNoise = filterA - filterB
      val creaseEnv =
        if (progress < 0.28) {
          sin(PI * creaseProgress) * exp(-3.2 * creaseProgress)
        } else 0.0
      val thumbPress = sin(2.0 * PI * (178.0 - 48.0 * creaseProgress) * t) * exp(-18.0 * progress) * 0.45
      val paperCrease = (bandNoise * creaseEnv * 0.75 * kit.paperPresence) + thumbPress

      // Stage 2: Plucked Bamboo / Koto Resonator Double-Stop (starts at 16ms, second note blooms at 34ms)
      var chime = 0.0
      if (t >= 0.014) {
        val t1 = t - 0.014
        val p1 = (t1 / 0.136).coerceAtMost(1.0)
        val env1 = (t1 / 0.003).coerceAtMost(1.0) * exp(-4.8 * p1)
        chime +=
          (0.62 * sin(2.0 * PI * note1Hz * t1) +
            0.24 * sin(2.0 * PI * (note1Hz * 2.0) * t1) * exp(-3.5 * p1) +
            0.14 * sin(2.0 * PI * (note1Hz * 3.0) * t1) * exp(-7.0 * p1)) * env1 * 0.58
      }
      if (t >= 0.034) {
        val t2 = t - 0.034
        val p2 = (t2 / 0.116).coerceAtMost(1.0)
        val env2 = (t2 / 0.0025).coerceAtMost(1.0) * exp(-4.2 * p2)
        // Fundamental + warm octave + delicate Furin wind-bell partial (2.756x)
        chime +=
          (0.64 * sin(2.0 * PI * note2Hz * t2) +
            0.22 * sin(2.0 * PI * (note2Hz * 2.0) * t2) * exp(-4.0 * p2) +
            0.14 * sin(2.0 * PI * (note2Hz * 2.756) * t2) * exp(-8.5 * p2)) * env2 * 0.68
      }

      val mixed = warmSaturate((paperCrease + chime) * 1.28)
      samples[i] = (mixed * 27200.0).toInt().coerceIn(-32767, 32767).toShort()
    }
    return samples
  }

  /**
   * 'thud': 168ms Double-Damped Hollow Cedar Tray Settle + Soft Washi Slide-Back.
   * Replaces synthetic pitch-drop lasers with a warm, dry wooden double-knock and paper slide.
   */
  private fun synthesizeThudPcm(kit: AcousticKit): ShortArray {
    val durationMs = 168
    val numSamples = (SAMPLE_RATE * durationMs) / 1000
    val samples = ShortArray(numSamples)
    val baseBoxHz = (kit.woodBaseHz * 0.34).coerceIn(110.0, 185.0)
    var slideFilter = 0.0

    for (i in 0 until numSamples) {
      val t = i.toDouble() / SAMPLE_RATE
      val progress = i.toDouble() / numSamples

      // Hit 1 (0..62ms): Muted wooden tray edge bump
      val env1 = (t / 0.003).coerceAtMost(1.0) * exp(-26.0 * t)
      val knock1 =
        (0.68 * sin(2.0 * PI * baseBoxHz * t) +
          0.32 * sin(2.0 * PI * (baseBoxHz * 1.58) * t) * exp(-42.0 * t) +
          0.16 * sin(2.0 * PI * (baseBoxHz * 2.35) * t) * exp(-65.0 * t)) * env1

      // Hit 2 (54..168ms): Lower wooden rack settle
      var knock2 = 0.0
      if (t >= 0.054) {
        val t2 = t - 0.054
        val settleHz = baseBoxHz * 0.78
        val env2 = (t2 / 0.0035).coerceAtMost(1.0) * exp(-22.0 * t2)
        knock2 =
          (0.74 * sin(2.0 * PI * settleHz * t2) +
            0.26 * sin(2.0 * PI * (settleHz * 1.62) * t2) * exp(-38.0 * t2)) * env2 * 0.82
      }

      // Soft Washi paper slide-back friction swish (20..135ms)
      val rawNoise = warmPaperNoise(i, 77)
      slideFilter += 0.16 * (rawNoise - slideFilter)
      val slideEnv = sin(PI * progress) * exp(-3.4 * progress)
      val paperSlide = (rawNoise - slideFilter) * slideEnv * 0.22 * kit.paperPresence

      val mixed = warmSaturate((knock1 + knock2 + paperSlide) * 1.35)
      samples[i] = (mixed * 27500.0).toInt().coerceIn(-32767, 32767).toShort()
    }
    return samples
  }

  /**
   * 'fanfare': 780ms Polyphonic Kyoto Koto Harp & Furin Wind-Bell Ensemble.
   * Uses overlapping plucked Koto strings tuned to the Japanese Yo / Hirajoshi pentatonic scale
   * (D4 - E4 - A4 - B4 - D5 - [F#5 + A5 + D6 chord]) with natural oshide vibrato and ivory pick attack.
   */
  private fun synthesizeFanfarePcm(kit: AcousticKit): ShortArray {
    val totalMs = 780
    val totalSamples = (SAMPLE_RATE * totalMs) / 1000
    val accumulator = DoubleArray(totalSamples)
    val scale = kit.chimeScaleRatio

    // Overlapping polyphonic Koto notes: (startTimeSec, durationSec, freqHz, amplitude)
    data class KotoVoice(val startSec: Double, val durSec: Double, val freqHz: Double, val amp: Double)

    val voices =
      listOf(
        KotoVoice(0.000, 0.42, 293.66 * scale, 0.34), // D4 warm bass root
        KotoVoice(0.075, 0.40, 329.63 * scale, 0.32), // E4
        KotoVoice(0.150, 0.42, 440.00 * scale, 0.35), // A4
        KotoVoice(0.225, 0.42, 493.88 * scale, 0.35), // B4
        KotoVoice(0.300, 0.46, 587.33 * scale, 0.40), // D5
        // Final blooming Kyoto Koto & Furin Wind-Bell harmony at 385ms:
        KotoVoice(0.385, 0.395, 440.00 * scale, 0.26), // A4 warm body
        KotoVoice(0.385, 0.395, 587.33 * scale, 0.32), // D5
        KotoVoice(0.385, 0.395, 739.99 * scale, 0.30), // F#5 luminous tierce
        KotoVoice(0.385, 0.395, 880.00 * scale, 0.42), // A5 lead koto string
        KotoVoice(0.395, 0.385, 1174.66 * scale, 0.22), // D6 Furin wind-bell shimmer
      )

    for ((vIdx, v) in voices.withIndex()) {
      val startSample = (v.startSec * SAMPLE_RATE).toInt()
      val noteSamples = (v.durSec * SAMPLE_RATE).toInt()
      var phase = 0.0
      var pickFilter = 0.0

      for (i in 0 until noteSamples) {
        val globalIdx = startSample + i
        if (globalIdx >= totalSamples) break
        val localT = i.toDouble() / SAMPLE_RATE
        val localProg = i.toDouble() / maxOf(1, noteSamples)

        // Subtle 'oshide' Koto string vibrato after 90ms + tiny pluck pitch-bend on attack
        val pluckBend = 1.0 + 0.018 * exp(-45.0 * localT)
        val vibrato = if (localT > 0.09) 1.0 + 0.0032 * sin(2.0 * PI * 5.4 * (localT - 0.09)) else 1.0
        val instFreq = v.freqHz * pluckBend * vibrato
        phase += (2.0 * PI * instFreq) / SAMPLE_RATE

        // Plucked string envelope (2.2ms ivory tsume pick attack + natural resonant decay)
        val attack = (localT / 0.0022).coerceAtMost(1.0)
        val env = attack * exp(-3.6 * localProg)

        // Rich plucked Koto harmonic series (higher partials decay faster than fundamental)
        val h1 = sin(phase) * 0.56
        val h2 = sin(phase * 2.0) * exp(-2.8 * localProg) * 0.25
        val h3 = sin(phase * 3.0) * exp(-5.2 * localProg) * 0.12
        val h4 = sin(phase * 4.0) * exp(-8.5 * localProg) * 0.05
        val bell = sin(phase * 2.756) * exp(-9.5 * localProg) * 0.04

        // Crisp ivory plectrum (tsume) scrape on the first 9ms of each pluck
        val rawNoise = warmPaperNoise(i, 300 + vIdx * 19)
        pickFilter += 0.35 * (rawNoise - pickFilter)
        val pickScrape =
          if (localT < 0.011) {
            (rawNoise - pickFilter) * exp(-180.0 * localT) * 0.28 * kit.paperPresence
          } else 0.0

        accumulator[globalIdx] += ((h1 + h2 + h3 + h4 + bell) * env + pickScrape) * v.amp
      }
    }

    val samples = ShortArray(totalSamples)
    for (i in 0 until totalSamples) {
      val fadeOut = if (i > totalSamples - 600) (totalSamples - i).toDouble() / 600.0 else 1.0
      val mixed = warmSaturate(accumulator[i] * fadeOut * 1.15)
      samples[i] = (mixed * 27800.0).toInt().coerceIn(-32767, 32767).toShort()
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
          maxOf(
            byteCount,
            AudioTrack.getMinBufferSize(
              SAMPLE_RATE,
              AudioFormat.CHANNEL_OUT_MONO,
              AudioFormat.ENCODING_PCM_16BIT,
            ),
          ),
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
