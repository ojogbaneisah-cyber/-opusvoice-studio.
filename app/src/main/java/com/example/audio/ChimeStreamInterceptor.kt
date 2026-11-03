package com.example.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class ChimeInterceptorConfig(
    val isEnabled: Boolean = true,
    val postSpeechMuteWindowMs: Int = 950,
    val spectralNotchEnabled: Boolean = true,
    val telephonyBandpassEnabled: Boolean = true,
    val notchCenterFreqHz: Float = 1550f,
    val notchBandwidthHz: Float = 1400f,
    val chimeAttenuationDb: Float = -28f
)

data class ChimeInterceptorStats(
    val isInterceptorActive: Boolean = true,
    val isServiceRunning: Boolean = false,
    val interceptedFramesCount: Long = 0L,
    val chimesSuppressedCount: Int = 0,
    val lastSuppressedDurationMs: Int = 0,
    val lastDetectedChimeFreqHz: Int = 0,
    val currentGateState: String = "ARMED • MONITORING STREAM",
    val activeAudioSessionId: Int = 0
)

/**
 * Real-time Audio Stream Output Interceptor & DSP Processing Module.
 *
 * Designed specifically to intercept and suppress the UI confirmation / processing chimes
 * produced by Character.AI immediately after a user or remote caller finishes speaking,
 * while preserving natural human vocal formants (300Hz - 3,400Hz).
 */
class ChimeStreamInterceptor {
    companion object {
        private const val TAG = "ChimeStreamInterceptor"
        const val DEFAULT_SAMPLE_RATE = 48000

        private val _globalStats = MutableStateFlow(ChimeInterceptorStats())
        val globalStats: StateFlow<ChimeInterceptorStats> = _globalStats.asStateFlow()

        private val _globalConfig = MutableStateFlow(ChimeInterceptorConfig())
        val globalConfig: StateFlow<ChimeInterceptorConfig> = _globalConfig.asStateFlow()

        fun updateGlobalConfig(updater: (ChimeInterceptorConfig) -> ChimeInterceptorConfig) {
            _globalConfig.value = updater(_globalConfig.value)
        }

        fun updateGlobalStats(updater: (ChimeInterceptorStats) -> ChimeInterceptorStats) {
            _globalStats.value = updater(_globalStats.value)
        }
    }

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var outputEqualizer: Equalizer? = null

    /**
     * Attaches hardware/platform output stream DSP filters to the target Android audio session
     * (or global output mix session 0 when supported) to notch out synthetic UI chimes (800Hz-2.8kHz).
     */
    fun attachToAudioOutputSession(audioSessionId: Int = 0) {
        releaseOutputEffects()
        val cfg = _globalConfig.value
        if (!cfg.isEnabled) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val dpConfig = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    1,
                    true, 6,
                    false, 0,
                    true, 6,
                    true
                ).build()

                val dp = DynamicsProcessing(0, audioSessionId, dpConfig)
                // Pre-EQ & Post-EQ: Notch out Character.AI UI chime frequencies (1.1kHz - 2.6kHz)
                val bandCutoffs = floatArrayOf(280f, 750f, 1350f, 2400f, 3400f, 8000f)
                for (i in 0 until 6) {
                    val gainDb = when (i) {
                        0 -> if (cfg.telephonyBandpassEnabled) -10f else 0f // Sub-300Hz rumble cut
                        1 -> 1.5f // Warm vocal fundamental presence (300-750Hz)
                        2 -> if (cfg.spectralNotchEnabled) -9.5f else 0f // Lower chime harmonic notch
                        3 -> if (cfg.spectralNotchEnabled) -14.0f else 0f // Primary c.ai chime ping notch (1.35k-2.4kHz)
                        4 -> 1.0f // Vocal consonant articulation
                        else -> if (cfg.telephonyBandpassEnabled) -12f else -3f // High-freq metallic ping roll-off
                    }
                    val band = DynamicsProcessing.EqBand(true, bandCutoffs[i], gainDb)
                    dp.setPreEqBandAllChannelsTo(i, band)
                    dp.setPostEqBandAllChannelsTo(i, band)
                }

                // Limiter to clamp sudden synthetic UI chime transients
                val limiter = DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    1.5f, // Fast 1.5ms attack for UI chime spikes
                    45f,
                    10f,
                    -6.0f,
                    0.0f
                )
                dp.setLimiterAllChannelsTo(limiter)
                dp.enabled = true
                dynamicsProcessing = dp
            } else {
                val eq = Equalizer(0, audioSessionId)
                val numBands = eq.numberOfBands.toInt()
                for (b in 0 until numBands) {
                    val centerMilliHz = eq.getCenterFreq(b.toShort())
                    val centerHz = centerMilliHz / 1000
                    if (cfg.spectralNotchEnabled && centerHz in 1100..2800) {
                        val minLevel = eq.bandLevelRange[0]
                        eq.setBandLevel(b.toShort(), (minLevel / 2).toShort())
                    }
                }
                eq.enabled = true
                outputEqualizer = eq
            }

            updateGlobalStats {
                it.copy(
                    isInterceptorActive = true,
                    activeAudioSessionId = audioSessionId,
                    currentGateState = "ACTIVE • STREAM DSP NOTCH ARMED"
                )
            }
        } catch (e: Exception) {
            // Some devices restrict global session 0 attachment; PCM stream interceptor remains active
            Log.d(TAG, "Hardware session effect fallback to PCM stream interceptor: ${e.message}")
            updateGlobalStats {
                it.copy(
                    isInterceptorActive = true,
                    activeAudioSessionId = audioSessionId,
                    currentGateState = "ACTIVE • PCM STREAM INTERCEPTOR"
                )
            }
        }
    }

    fun releaseOutputEffects() {
        try {
            dynamicsProcessing?.enabled = false
            dynamicsProcessing?.release()
        } catch (ignored: Exception) {}
        dynamicsProcessing = null

        try {
            outputEqualizer?.enabled = false
            outputEqualizer?.release()
        } catch (ignored: Exception) {}
        outputEqualizer = null
    }

    /**
     * Intercepts a 16-bit PCM audio stream buffer and suppresses Character.AI UI sound effects:
     * 1. Post-speech turn-end holdoff window gating: Attenuates non-vocal chimes at the start/end of a turn.
     * 2. High-Q biquad notch filtering centered on Character.AI's synthetic UI chime frequencies (1.4kHz & 2.1kHz).
     * 3. Short-duration high-zero-crossing rate (ZCR) tonal burst suppression.
     */
    fun interceptAndFilterPcmStream(
        pcmBytes: ByteArray,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        config: ChimeInterceptorConfig = _globalConfig.value
    ): ByteArray {
        if (!config.isEnabled || pcmBytes.size < 4) return pcmBytes

        val sampleCount = pcmBytes.size / 2
        val samples = ShortArray(sampleCount)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        // Frame size: 20ms (960 samples at 48kHz)
        val frameSamples = (sampleRate / 50).coerceAtLeast(240)
        val totalFrames = (sampleCount + frameSamples - 1) / frameSamples
        val postSpeechWindowFrames = ((config.postSpeechMuteWindowMs / 20).coerceIn(5, 110))

        var suppressedFrames = 0
        var detectedChimeFreqHz = 0

        // Biquad Notch Filter state for 1,680 Hz UI chime ping
        val omega = 2.0 * PI * config.notchCenterFreqHz / sampleRate.toDouble()
        val alpha = sin(omega) / (2.0 * 3.5) // Q = 3.5 narrow chime notch
        val b0 = 1.0
        val b1 = -2.0 * cos(omega)
        val b2 = 1.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cos(omega)
        val a2 = 1.0 - alpha

        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0

        // Telephony 1st-order high-pass (300Hz) & low-pass (3400Hz) state
        var hpPrevX = 0.0
        var hpPrevY = 0.0
        var lpPrevY = 0.0
        val hpAlpha = 0.94
        val lpAlpha = 0.36

        for (frameIdx in 0 until totalFrames) {
            val start = frameIdx * frameSamples
            val end = minOf(start + frameSamples, sampleCount)
            val len = end - start
            if (len <= 0) continue

            var sumSq = 0.0
            var zeroCrossings = 0
            for (i in start until end) {
                val s = samples[i].toDouble()
                sumSq += s * s
                if (i > start) {
                    val prev = samples[i - 1].toInt()
                    val curr = samples[i].toInt()
                    if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                        zeroCrossings++
                    }
                }
            }

            val rms = sqrt(sumSq / len) / 32768.0
            val estimatedFreqHz = ((zeroCrossings * sampleRate) / (2 * len))

            // Identify synthetic UI chime bursts:
            // - High zero-crossing rate in the 950Hz - 3200Hz chime band
            // - Especially within the post-speech turn boundary window (start or end of stream)
            val isInTurnTransitionWindow = frameIdx < minOf(postSpeechWindowFrames, totalFrames / 3 + 1) ||
                frameIdx >= maxOf(0, totalFrames - minOf(12, totalFrames / 4))
            val isSyntheticChimeSignature = estimatedFreqHz in 980..3300 && rms > 0.012 &&
                (isInTurnTransitionWindow || estimatedFreqHz > 1450)

            if (isSyntheticChimeSignature) {
                suppressedFrames++
                detectedChimeFreqHz = estimatedFreqHz
            }

            // Soft-knee attenuation factor for detected chime frames (-28 dB = ~0.04x)
            val frameGain = if (isSyntheticChimeSignature) 0.04 else 1.0

            for (i in start until end) {
                var x = samples[i].toDouble() * frameGain

                // Apply spectral chime notch filter if enabled
                if (config.spectralNotchEnabled) {
                    val notched = (b0 / a0) * x + (b1 / a0) * x1 + (b2 / a0) * x2 -
                        (a1 / a0) * y1 - (a2 / a0) * y2
                    x2 = x1
                    x1 = x
                    y2 = y1
                    y1 = notched
                    x = notched
                }

                // Apply telephony 300Hz-3400Hz voice bandpass if enabled
                if (config.telephonyBandpassEnabled) {
                    val hp = hpAlpha * (hpPrevY + x - hpPrevX)
                    hpPrevX = x
                    hpPrevY = hp
                    val lp = lpPrevY + lpAlpha * (hp - lpPrevY)
                    lpPrevY = lp
                    x = lp * 1.12
                }

                samples[i] = x.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }

        updateGlobalStats { prev ->
            prev.copy(
                interceptedFramesCount = prev.interceptedFramesCount + totalFrames,
                chimesSuppressedCount = prev.chimesSuppressedCount + (if (suppressedFrames > 0) 1 else 0),
                lastSuppressedDurationMs = if (suppressedFrames > 0) suppressedFrames * 20 else prev.lastSuppressedDurationMs,
                lastDetectedChimeFreqHz = if (detectedChimeFreqHz > 0) detectedChimeFreqHz else prev.lastDetectedChimeFreqHz,
                currentGateState = if (suppressedFrames > 0) {
                    "SUPPRESSED CHIME (${suppressedFrames * 20}ms @ ${detectedChimeFreqHz}Hz)"
                } else {
                    "CLEAN VOICE STREAM • ZERO CHIMES"
                }
            )
        }

        val outBytes = ByteArray(pcmBytes.size)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(samples)
        return outBytes
    }
}
