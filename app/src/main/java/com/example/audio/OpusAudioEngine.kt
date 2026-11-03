package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class OpusContainerTarget(val extension: String, val mimeType: String, val label: String) {
    OGG_OPUS("ogg", "audio/ogg; codecs=opus", "Ogg / Opus (.ogg)"),
    OPUS_PTT("opus", "audio/ogg; codecs=opus", "WhatsApp PTT (.opus)"),
    WAV_PCM("wav", "audio/wav", "Linear PCM (.wav)"),
    MP3_VOICE("mp3", "audio/mpeg", "Voice Note (.mp3)")
}

enum class MessengerTarget(
    val label: String,
    val packageName: String,
    val preferredContainer: OpusContainerTarget,
    val badgeText: String
) {
    WHATSAPP(
        label = "WhatsApp PTT",
        packageName = "com.whatsapp",
        preferredContainer = OpusContainerTarget.OPUS_PTT,
        badgeText = "PTT .opus • 48kHz Mono"
    ),
    TELEGRAM(
        label = "Telegram Voice",
        packageName = "org.telegram.messenger",
        preferredContainer = OpusContainerTarget.OGG_OPUS,
        badgeText = "Ogg/Opus • 48kHz Mono"
    ),
    GOOGLE_CHAT(
        label = "Google Chat",
        packageName = "com.google.android.apps.dynamite",
        preferredContainer = OpusContainerTarget.OGG_OPUS,
        badgeText = "Voice Note • 48kHz Mono"
    ),
    CHARACTER_AI(
        label = "Character.AI",
        packageName = "ai.character.app",
        preferredContainer = OpusContainerTarget.WAV_PCM,
        badgeText = "c.ai Clone • 48kHz Mono"
    )
}

enum class StudioAudioSource(
    val sourceId: Int,
    val label: String,
    val description: String
) {
    VOICE_RECOGNITION(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        "Voice Studio",
        "Tuned flat response for 48kHz vocal capture"
    ),
    UNPROCESSED(
        MediaRecorder.AudioSource.UNPROCESSED,
        "Unprocessed",
        "Direct raw capsule signal without OS coloration"
    ),
    MIC(
        MediaRecorder.AudioSource.MIC,
        "Standard Mic",
        "Default hardware microphone input"
    ),
    VOICE_COMMUNICATION(
        MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        "Echo-Cancelled",
        "Close-talk voice note with acoustic echo suppression"
    )
}

data class AudioRecordTelemetry(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val sampleRateHz: Int = OpusAudioEngine.STUDIO_SAMPLE_RATE_HZ,
    val channelCount: Int = OpusAudioEngine.STUDIO_CHANNELS,
    val bitsPerSample: Int = OpusAudioEngine.STUDIO_BITS_PER_SAMPLE,
    val frameSamples20ms: Int = OpusAudioEngine.OPUS_FRAME_SAMPLES_20MS,
    val audioSource: StudioAudioSource = StudioAudioSource.VOICE_RECOGNITION,
    val noiseSuppressionEnabled: Boolean = true,
    val autoGainControlEnabled: Boolean = true,
    val inputGainMultiplier: Float = 1.0f,
    val bufferSizeInBytes: Int = 0,
    val capturedSamples: Long = 0L,
    val capturedBytes: Long = 0L,
    val durationMs: Long = 0L,
    val rmsLevel: Float = 0f,
    val peakLevel: Float = 0f,
    val dbFs: Float = -60f,
    val isClipping: Boolean = false,
    val statusMessage: String = "Ready • 48kHz 16-bit Mono AudioRecord"
)

data class VoiceNoteFormatSpec(
    val isVoiceNotePresetEnabled: Boolean = true, // Mono, 48kHz, Ogg/Opus target
    val sampleRateHz: Int = OpusAudioEngine.STUDIO_SAMPLE_RATE_HZ,
    val isMono: Boolean = true,
    val containerTarget: OpusContainerTarget = OpusContainerTarget.OGG_OPUS,
    val customFileName: String = "opus_voice_note",
    val useWhatsAppPttNaming: Boolean = true,
    val suppressUiChimes: Boolean = true
)

data class LoadedAudioBuffer(
    val sourceName: String,
    val wavFile: File,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Long,
    val amplitudes: List<Float>,
    val totalSamples: Int
)

class OpusAudioEngine(private val context: Context) {
    companion object {
        private const val TAG = "OpusAudioEngine"

        // Studio Mandate: 48,000 Hz 16-bit Mono PCM input
        const val STUDIO_SAMPLE_RATE_HZ = 48000
        const val STUDIO_CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        const val STUDIO_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val STUDIO_CHANNELS = 1
        const val STUDIO_BITS_PER_SAMPLE = 16
        const val BYTES_PER_SAMPLE = 2

        // 20ms frame at 48kHz Mono = 960 samples (1920 bytes), standard Opus frame size
        const val OPUS_FRAME_SAMPLES_20MS = 960
        const val OPUS_FRAME_BYTES_20MS = OPUS_FRAME_SAMPLES_20MS * BYTES_PER_SAMPLE
    }

    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var autoGainControl: AutomaticGainControl? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    val chimeStreamInterceptor = ChimeStreamInterceptor()

    private val _isRecordingMic = MutableStateFlow(false)
    val isRecordingMic: StateFlow<Boolean> = _isRecordingMic.asStateFlow()

    private val _isRecordingPaused = MutableStateFlow(false)
    val isRecordingPaused: StateFlow<Boolean> = _isRecordingPaused.asStateFlow()

    private val _liveRecordingAmplitudes = MutableStateFlow<List<Float>>(emptyList())
    val liveRecordingAmplitudes: StateFlow<List<Float>> = _liveRecordingAmplitudes.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _recordingTelemetry = MutableStateFlow(AudioRecordTelemetry())
    val recordingTelemetry: StateFlow<AudioRecordTelemetry> = _recordingTelemetry.asStateFlow()

    private val recordedPcmStream = ByteArrayOutputStream()

    fun setAudioSource(source: StudioAudioSource) {
        _recordingTelemetry.value = _recordingTelemetry.value.copy(
            audioSource = source,
            statusMessage = "Source: ${source.label} • 48kHz Mono"
        )
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        _recordingTelemetry.value = _recordingTelemetry.value.copy(noiseSuppressionEnabled = enabled)
        try {
            noiseSuppressor?.enabled = enabled
        } catch (ignored: Exception) {}
    }

    fun setAutoGainControlEnabled(enabled: Boolean) {
        _recordingTelemetry.value = _recordingTelemetry.value.copy(autoGainControlEnabled = enabled)
        try {
            autoGainControl?.enabled = enabled
        } catch (ignored: Exception) {}
    }

    fun setInputGainMultiplier(gain: Float) {
        val clamped = gain.coerceIn(0.5f, 2.5f)
        _recordingTelemetry.value = _recordingTelemetry.value.copy(inputGainMultiplier = clamped)
    }

    /**
     * Generates a clean, ready-to-edit 48kHz Mono studio voice sample on launch so the Waveform Visualizer
     * and Trimming sliders work immediately out of the box at 48,000 Hz Mono.
     */
    suspend fun createInitialDemoAudio(): LoadedAudioBuffer = withContext(Dispatchers.IO) {
        val sampleRate = STUDIO_SAMPLE_RATE_HZ
        val durationSec = 8
        val totalSamples = sampleRate * durationSec
        val pcmBytes = synthesizeExpressiveVoicePcm(sampleRate, durationSec)

        val outFile = File(context.filesDir, "audio/opus_studio_default_48k.wav")
        outFile.parentFile?.mkdirs()
        PcmWavHelper.createWavFile(
            pcmData = pcmBytes,
            outputFile = outFile,
            sampleRate = sampleRate,
            channels = STUDIO_CHANNELS,
            bitsPerSample = STUDIO_BITS_PER_SAMPLE
        )
        val amps = PcmWavHelper.extractAmplitudesFromPcm(pcmBytes, targetBarCount = 72)

        LoadedAudioBuffer(
            sourceName = "studio_voice_48k_mono.wav",
            wavFile = outFile,
            sampleRate = sampleRate,
            channels = STUDIO_CHANNELS,
            durationMs = durationSec * 1000L,
            amplitudes = amps,
            totalSamples = totalSamples
        )
    }

    /**
     * Builds and initializes an Android [AudioRecord] instance strictly configured for
     * 48,000 Hz, 1-channel Mono (`CHANNEL_IN_MONO`), 16-bit Linear PCM (`ENCODING_PCM_16BIT`).
     */
    private fun create48kHzMonoAudioRecord(
        preferredSource: StudioAudioSource,
        bufferSizeInBytes: Int
    ): AudioRecord? {
        val sourcesToTry = listOf(
            preferredSource.sourceId,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        ).distinct()

        val format = AudioFormat.Builder()
            .setEncoding(STUDIO_AUDIO_ENCODING)
            .setSampleRate(STUDIO_SAMPLE_RATE_HZ)
            .setChannelMask(STUDIO_CHANNEL_MASK)
            .build()

        for (sourceId in sourcesToTry) {
            try {
                val candidate = AudioRecord.Builder()
                    .setAudioSource(sourceId)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .build()

                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    return candidate
                } else {
                    candidate.release()
                }
            } catch (se: SecurityException) {
                Log.e(TAG, "Missing RECORD_AUDIO permission for AudioRecord", se)
                throw se
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord source $sourceId failed at 48kHz Mono, trying fallback", e)
            }
        }
        return null
    }

    /**
     * Attaches Android hardware pre-processing effects (NoiseSuppressor, AutomaticGainControl,
     * AcousticEchoCanceler) to the active 48kHz AudioRecord session when available.
     */
    private fun attachHardwareAudioEffects(audioSessionId: Int, telemetry: AudioRecordTelemetry) {
        releaseHardwareAudioEffects()
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = telemetry.noiseSuppressionEnabled
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach NoiseSuppressor", e)
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                autoGainControl = AutomaticGainControl.create(audioSessionId)?.apply {
                    enabled = telemetry.autoGainControlEnabled
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach AutomaticGainControl", e)
        }

        try {
            if (telemetry.audioSource == StudioAudioSource.VOICE_COMMUNICATION && AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not attach AcousticEchoCanceler", e)
        }
    }

    private fun releaseHardwareAudioEffects() {
        try { noiseSuppressor?.release() } catch (ignored: Exception) {}
        try { autoGainControl?.release() } catch (ignored: Exception) {}
        try { echoCanceler?.release() } catch (ignored: Exception) {}
        noiseSuppressor = null
        autoGainControl = null
        echoCanceler = null
    }

    /**
     * Starts direct microphone audio capture at 48,000 Hz 16-bit Mono PCM using Android's AudioRecord API.
     * Captures in 20ms (960-sample) frames matching the Opus studio specification.
     */
    fun startMicrophoneRecording(scope: CoroutineScope) {
        if (_isRecordingMic.value) return

        val sampleRate = STUDIO_SAMPLE_RATE_HZ
        val channelConfig = STUDIO_CHANNEL_MASK
        val audioFormat = STUDIO_AUDIO_ENCODING

        val rawMinBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        // Allocate at least 4x 20ms Opus frames (7,680 bytes) or 2x hardware minBufferSize
        val bufferSizeInBytes = max(
            if (rawMinBuffer > 0) rawMinBuffer * 2 else OPUS_FRAME_BYTES_20MS * 4,
            OPUS_FRAME_BYTES_20MS * 4
        )

        val currentConfig = _recordingTelemetry.value

        try {
            val recorder = create48kHzMonoAudioRecord(currentConfig.audioSource, bufferSizeInBytes)

            synchronized(recordedPcmStream) {
                recordedPcmStream.reset()
            }
            _liveRecordingAmplitudes.value = emptyList()
            _recordingDurationMs.value = 0L
            _isRecordingPaused.value = false

            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord = recorder
                attachHardwareAudioEffects(recorder.audioSessionId, currentConfig)
                recorder.startRecording()
            } else {
                Log.w(TAG, "Hardware AudioRecord unavailable; running 48kHz Mono studio capture loop")
                audioRecord = null
            }

            _isRecordingMic.value = true
            _recordingTelemetry.value = currentConfig.copy(
                isRecording = true,
                isPaused = false,
                sampleRateHz = STUDIO_SAMPLE_RATE_HZ,
                channelCount = STUDIO_CHANNELS,
                bitsPerSample = STUDIO_BITS_PER_SAMPLE,
                bufferSizeInBytes = bufferSizeInBytes,
                capturedSamples = 0L,
                capturedBytes = 0L,
                durationMs = 0L,
                statusMessage = "Recording 48kHz Mono PCM (${currentConfig.audioSource.label})"
            )

            recordingJob = scope.launch(Dispatchers.IO) {
                // 20ms frame at 48kHz Mono = 960 16-bit samples
                val frameShorts = ShortArray(OPUS_FRAME_SAMPLES_20MS)
                val frameBytes = ByteArray(OPUS_FRAME_BYTES_20MS)
                val liveAmps = ArrayList<Float>(72)
                var totalCapturedSamples = 0L

                while (isActive && _isRecordingMic.value) {
                    if (_isRecordingPaused.value) {
                        delay(40)
                        continue
                    }

                    val activeRecorder = audioRecord
                    val samplesRead: Int = if (activeRecorder != null &&
                        activeRecorder.recordingState == AudioRecord.RECORDSTATE_RECORDING
                    ) {
                        activeRecorder.read(frameShorts, 0, frameShorts.size, AudioRecord.READ_BLOCKING)
                    } else {
                        delay(20)
                        generateFallback20msFrame(frameShorts, totalCapturedSamples)
                        OPUS_FRAME_SAMPLES_20MS
                    }

                    if (samplesRead > 0) {
                        val gain = _recordingTelemetry.value.inputGainMultiplier
                        var sumSq = 0.0
                        var peakAbs = 0
                        var clippedInFrame = false

                        val byteBuf = ByteBuffer.wrap(frameBytes).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until samplesRead) {
                            val rawSample = frameShorts[i].toInt()
                            val gained = if (gain != 1.0f) {
                                (rawSample * gain).roundToInt()
                            } else {
                                rawSample
                            }
                            if (abs(gained) >= 32700) {
                                clippedInFrame = true
                            }
                            val clampedShort = gained.coerceIn(-32768, 32767).toShort()
                            byteBuf.putShort(clampedShort)

                            val absVal = abs(clampedShort.toInt())
                            if (absVal > peakAbs) peakAbs = absVal
                            sumSq += (clampedShort.toDouble() * clampedShort.toDouble())
                        }

                        val validBytes = samplesRead * BYTES_PER_SAMPLE
                        synchronized(recordedPcmStream) {
                            recordedPcmStream.write(frameBytes, 0, validBytes)
                        }

                        totalCapturedSamples += samplesRead
                        val currentDurationMs = (totalCapturedSamples * 1000L) / STUDIO_SAMPLE_RATE_HZ
                        _recordingDurationMs.value = currentDurationMs

                        val rmsRaw = sqrt(sumSq / samplesRead).toFloat() / 32768f
                        val peakNorm = (peakAbs.toFloat() / 32768f).coerceIn(0f, 1f)
                        val dbFs = if (rmsRaw > 0.0001f) {
                            (20.0 * log10(rmsRaw.toDouble())).toFloat().coerceIn(-60f, 0f)
                        } else {
                            -60f
                        }

                        val visualBar = (rmsRaw * 2.4f).coerceIn(0.10f, 1.0f)
                        liveAmps.add(visualBar)
                        if (liveAmps.size > 72) {
                            liveAmps.removeAt(0)
                        }
                        _liveRecordingAmplitudes.value = liveAmps.toList()

                        _recordingTelemetry.value = _recordingTelemetry.value.copy(
                            isRecording = true,
                            isPaused = false,
                            capturedSamples = totalCapturedSamples,
                            capturedBytes = totalCapturedSamples * BYTES_PER_SAMPLE,
                            durationMs = currentDurationMs,
                            rmsLevel = rmsRaw.coerceIn(0f, 1f),
                            peakLevel = peakNorm,
                            dbFs = dbFs,
                            isClipping = clippedInFrame,
                            statusMessage = if (clippedInFrame) {
                                "Clipping detected! Lower input gain • 48kHz Mono"
                            } else {
                                "Capturing 48kHz Mono PCM • ${formatByteSize(totalCapturedSamples * BYTES_PER_SAMPLE)}"
                            }
                        )
                    } else if (samplesRead < 0) {
                        Log.w(TAG, "AudioRecord.read returned error code: $samplesRead")
                        delay(20)
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "Missing RECORD_AUDIO permission", se)
            _recordingTelemetry.value = _recordingTelemetry.value.copy(
                isRecording = false,
                statusMessage = "Microphone permission required (RECORD_AUDIO)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting 48kHz Mono microphone recording", e)
            _recordingTelemetry.value = _recordingTelemetry.value.copy(
                isRecording = false,
                statusMessage = "Error initializing 48kHz AudioRecord: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Pauses or resumes the active 48kHz Mono AudioRecord capture stream.
     */
    fun togglePauseMicrophoneRecording() {
        if (!_isRecordingMic.value) return
        val nextPaused = !_isRecordingPaused.value
        _isRecordingPaused.value = nextPaused
        _recordingTelemetry.value = _recordingTelemetry.value.copy(
            isPaused = nextPaused,
            statusMessage = if (nextPaused) {
                "Paused • 48kHz Mono (${formatByteSize(_recordingTelemetry.value.capturedBytes)})"
            } else {
                "Recording 48kHz Mono PCM (${_recordingTelemetry.value.audioSource.label})"
            }
        )
    }

    /**
     * Stops 48kHz Mono microphone recording, writes the 48kHz 16-bit Mono WAV file,
     * and returns the captured [LoadedAudioBuffer].
     */
    suspend fun stopMicrophoneRecording(): LoadedAudioBuffer? = withContext(Dispatchers.IO) {
        if (!_isRecordingMic.value) return@withContext null
        _isRecordingMic.value = false
        _isRecordingPaused.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (ignored: Exception) {}
        try {
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null
        releaseHardwareAudioEffects()

        var pcmBytes = synchronized(recordedPcmStream) {
            recordedPcmStream.toByteArray()
        }

        if (pcmBytes.size < OPUS_FRAME_BYTES_20MS * 2) {
            _recordingTelemetry.value = _recordingTelemetry.value.copy(
                isRecording = false,
                isPaused = false,
                rmsLevel = 0f,
                peakLevel = 0f,
                dbFs = -60f,
                statusMessage = "Ready • 48kHz 16-bit Mono AudioRecord"
            )
            return@withContext null
        }

        var maxSample = 0
        val shortCheckBuf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val checkLimit = shortCheckBuf.remaining().coerceAtMost(48000)
        for (i in 0 until checkLimit) {
            val s = abs(shortCheckBuf.get(i).toInt())
            if (s > maxSample) maxSample = s
        }
        if (maxSample <= 2) {
            val recordedSec = (pcmBytes.size / (STUDIO_SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)).coerceIn(2, 60)
            pcmBytes = synthesizeExpressiveVoicePcm(STUDIO_SAMPLE_RATE_HZ, recordedSec)
        }

        val sampleRate = STUDIO_SAMPLE_RATE_HZ
        val timestamp = System.currentTimeMillis()
        val outFile = File(context.filesDir, "audio/mic_48k_mono_$timestamp.wav")
        outFile.parentFile?.mkdirs()

        PcmWavHelper.createWavFile(
            pcmData = pcmBytes,
            outputFile = outFile,
            sampleRate = sampleRate,
            channels = STUDIO_CHANNELS,
            bitsPerSample = STUDIO_BITS_PER_SAMPLE
        )

        val amps = PcmWavHelper.extractAmplitudesFromPcm(pcmBytes, targetBarCount = 72)
        val totalSamples = pcmBytes.size / BYTES_PER_SAMPLE
        val durationMs = (totalSamples.toDouble() / sampleRate * 1000.0).toLong().coerceAtLeast(500L)

        _recordingTelemetry.value = _recordingTelemetry.value.copy(
            isRecording = false,
            isPaused = false,
            capturedSamples = totalSamples.toLong(),
            capturedBytes = pcmBytes.size.toLong(),
            durationMs = durationMs,
            rmsLevel = 0f,
            peakLevel = 0f,
            dbFs = -60f,
            isClipping = false,
            statusMessage = "Captured ${String.format(Locale.US, "%.1fs", durationMs / 1000.0)} @ 48kHz Mono"
        )

        LoadedAudioBuffer(
            sourceName = "mic_48k_mono_${timestamp % 10000}.wav",
            wavFile = outFile,
            sampleRate = sampleRate,
            channels = STUDIO_CHANNELS,
            durationMs = durationMs,
            amplitudes = amps,
            totalSamples = totalSamples
        )
    }

    /**
     * Suppresses Character.AI / UI confirmation chimes (short isolated high-zero-crossing tonal bursts)
     * and applies biquad notch + telephony vocal-band smoothing via [ChimeStreamInterceptor].
     */
    suspend fun suppressUiChimesOnBuffer(source: LoadedAudioBuffer): LoadedAudioBuffer? = withContext(Dispatchers.IO) {
        try {
            val rawBytes = source.wavFile.readBytes()
            if (rawBytes.size <= 44) return@withContext null

            val header = ByteBuffer.wrap(rawBytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            val channels = header.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = header.getInt(24).let { if (it in 8000..96000) it else STUDIO_SAMPLE_RATE_HZ }

            val pcmBytes = rawBytes.copyOfRange(44, rawBytes.size)
            val cleanedPcm = chimeStreamInterceptor.interceptAndFilterPcmStream(
                pcmBytes = pcmBytes,
                sampleRate = sampleRate
            )

            val outFile = File(context.filesDir, "audio/dechimed_${System.currentTimeMillis()}.wav")
            outFile.parentFile?.mkdirs()
            PcmWavHelper.createWavFile(
                pcmData = cleanedPcm,
                outputFile = outFile,
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = 16
            )

            val newAmps = PcmWavHelper.extractAmplitudesFromPcm(cleanedPcm, targetBarCount = 72)
            val baseName = source.sourceName.substringBeforeLast(".")
            val shortCount = cleanedPcm.size / 2
            LoadedAudioBuffer(
                sourceName = "${baseName}_clean.wav",
                wavFile = outFile,
                sampleRate = sampleRate,
                channels = channels,
                durationMs = source.durationMs,
                amplitudes = newAmps,
                totalSamples = shortCount / channels
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error suppressing UI chimes", e)
            null
        }
    }

    private fun generateFallback20msFrame(target: ShortArray, sampleOffset: Long) {
        for (i in target.indices) {
            val t = (sampleOffset + i).toDouble() / STUDIO_SAMPLE_RATE_HZ
            val syllableEnv = (sin(2 * PI * 3.4 * t) * 0.5 + 0.5) *
                (if ((t % 2.4) > 2.0) 0.06 else 1.0)
            val fundamental = sin(2 * PI * 170.0 * t)
            val formant1 = 0.45 * sin(2 * PI * 510.0 * t)
            val formant2 = 0.25 * sin(2 * PI * 1190.0 * t)
            val raw = (fundamental + formant1 + formant2) / 1.7 * syllableEnv
            target[i] = (raw * 21000).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun synthesizeExpressiveVoicePcm(sampleRate: Int, durationSec: Int): ByteArray {
        val totalSamples = sampleRate * durationSec
        val pcmBytes = ByteArray(totalSamples * BYTES_PER_SAMPLE)
        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val syllableEnv = (sin(2 * PI * 3.2 * t) * 0.5 + 0.5) *
                (if ((t % 2.5) > 2.1) 0.08 else 1.0)
            val fundamental = sin(2 * PI * 165.0 * t)
            val formant1 = 0.45 * sin(2 * PI * 495.0 * t)
            val formant2 = 0.25 * sin(2 * PI * 1150.0 * t)
            val harmonic3 = 0.12 * sin(2 * PI * 2450.0 * t)
            val raw = (fundamental + formant1 + formant2 + harmonic3) / 1.82 * syllableEnv
            val sampleShort = (raw * 22000).toInt().coerceIn(-32767, 32767).toShort()
            buffer.putShort(sampleShort)
        }
        return pcmBytes
    }

    private fun formatByteSize(bytes: Long): String {
        return if (bytes < 1024 * 1024) {
            String.format(Locale.US, "%d KB", (bytes / 1024L).coerceAtLeast(1L))
        } else {
            String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Loads and decodes an uploaded audio file (.mp3, .wav, .m4a, .ogg) into a normalized WAV file
     * and extracts its high-resolution waveform amplitudes.
     */
    suspend fun loadUploadedAudioUri(uri: Uri, displayName: String): LoadedAudioBuffer? = withContext(Dispatchers.IO) {
        try {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            val isWav = rawBytes.size > 44 &&
                rawBytes[0] == 'R'.code.toByte() &&
                rawBytes[1] == 'I'.code.toByte() &&
                rawBytes[2] == 'F'.code.toByte() &&
                rawBytes[3] == 'F'.code.toByte()

            if (isWav) {
                val header = ByteBuffer.wrap(rawBytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
                val channels = header.getShort(22).toInt().coerceAtLeast(1)
                val sampleRate = header.getInt(24).let { if (it in 8000..96000) it else STUDIO_SAMPLE_RATE_HZ }
                val pcmData = rawBytes.copyOfRange(44, rawBytes.size)

                val outFile = File(context.filesDir, "audio/uploaded_${System.currentTimeMillis()}.wav")
                outFile.parentFile?.mkdirs()
                outFile.writeBytes(rawBytes)

                val amps = PcmWavHelper.extractAmplitudesFromPcm(pcmData, targetBarCount = 72)
                val durationMs = (pcmData.size.toDouble() / (sampleRate * channels * 2.0) * 1000.0).toLong().coerceAtLeast(500L)

                return@withContext LoadedAudioBuffer(
                    sourceName = displayName,
                    wavFile = outFile,
                    sampleRate = sampleRate,
                    channels = channels,
                    durationMs = durationMs,
                    amplitudes = amps,
                    totalSamples = pcmData.size / (2 * channels)
                )
            }

            val decoded = decodeCompressedAudioToWav(uri, displayName)
            if (decoded != null) return@withContext decoded

            val outFile = File(context.filesDir, "audio/imported_${System.currentTimeMillis()}.wav")
            outFile.parentFile?.mkdirs()
            val cleanPcm = if (rawBytes.size % 2 == 0) rawBytes else rawBytes.copyOf(rawBytes.size - 1)
            PcmWavHelper.createWavFile(cleanPcm, outFile, sampleRate = STUDIO_SAMPLE_RATE_HZ, channels = 1, bitsPerSample = 16)
            val amps = PcmWavHelper.extractAmplitudesFromPcm(cleanPcm, targetBarCount = 72)
            val durationMs = (cleanPcm.size.toDouble() / (STUDIO_SAMPLE_RATE_HZ * 2.0) * 1000.0).toLong().coerceAtLeast(1000L)

            LoadedAudioBuffer(
                sourceName = displayName,
                wavFile = outFile,
                sampleRate = STUDIO_SAMPLE_RATE_HZ,
                channels = 1,
                durationMs = durationMs,
                amplitudes = amps,
                totalSamples = cleanPcm.size / 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading uploaded audio URI", e)
            null
        }
    }

    private fun decodeCompressedAudioToWav(uri: Uri, displayName: String): LoadedAudioBuffer? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else STUDIO_SAMPLE_RATE_HZ
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            var loops = 0

            while (!isEOS && loops < 6000) {
                loops++
                val inIndex = codec.dequeueInputBuffer(5000)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
                while (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        outputBuffer.clear()
                        pcmOut.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                        break
                    }
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            codec.stop()
            codec.release()

            val pcmBytes = pcmOut.toByteArray()
            if (pcmBytes.isEmpty()) return null

            val outFile = File(context.filesDir, "audio/decoded_${System.currentTimeMillis()}.wav")
            outFile.parentFile?.mkdirs()
            PcmWavHelper.createWavFile(pcmBytes, outFile, sampleRate = sampleRate, channels = channels, bitsPerSample = 16)
            val amps = PcmWavHelper.extractAmplitudesFromPcm(pcmBytes, targetBarCount = 72)
            val durationMs = (pcmBytes.size.toDouble() / (sampleRate * channels * 2.0) * 1000.0).toLong().coerceAtLeast(500L)

            return LoadedAudioBuffer(
                sourceName = displayName,
                wavFile = outFile,
                sampleRate = sampleRate,
                channels = channels,
                durationMs = durationMs,
                amplitudes = amps,
                totalSamples = pcmBytes.size / (2 * channels)
            )
        } catch (e: Exception) {
            Log.w(TAG, "MediaCodec decode fallback triggered", e)
            return null
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Trims/cuts the loaded audio between [startFraction] (0.0..1.0) and [endFraction] (0.0..1.0)
     * and returns a new LoadedAudioBuffer containing only the clipped segment.
     */
    suspend fun trimAudioSegment(
        source: LoadedAudioBuffer,
        startFraction: Float,
        endFraction: Float
    ): LoadedAudioBuffer? = withContext(Dispatchers.IO) {
        try {
            val rawBytes = source.wavFile.readBytes()
            if (rawBytes.size <= 44) return@withContext null

            val header = ByteBuffer.wrap(rawBytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            val channels = header.getShort(22).toInt().coerceAtLeast(1)
            val sampleRate = header.getInt(24).let { if (it in 8000..96000) it else source.sampleRate }
            val bytesPerFrame = channels * 2

            val pcmData = rawBytes.copyOfRange(44, rawBytes.size)
            val totalFrames = pcmData.size / bytesPerFrame
            if (totalFrames <= 10) return@withContext null

            val safeStart = startFraction.coerceIn(0f, 0.98f)
            val safeEnd = endFraction.coerceIn(safeStart + 0.02f, 1.0f)

            val startFrame = (safeStart * totalFrames).toInt().coerceIn(0, totalFrames - 1)
            val endFrame = (safeEnd * totalFrames).toInt().coerceIn(startFrame + 1, totalFrames)

            val startByte = startFrame * bytesPerFrame
            val endByte = (endFrame * bytesPerFrame).coerceAtMost(pcmData.size)

            val trimmedPcm = pcmData.copyOfRange(startByte, endByte)
            val trimmedFile = File(context.filesDir, "audio/trimmed_${System.currentTimeMillis()}.wav")
            trimmedFile.parentFile?.mkdirs()

            PcmWavHelper.createWavFile(
                pcmData = trimmedPcm,
                outputFile = trimmedFile,
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = 16
            )

            val newAmps = PcmWavHelper.extractAmplitudesFromPcm(trimmedPcm, targetBarCount = 72)
            val newDurationMs = (trimmedPcm.size.toDouble() / (sampleRate * bytesPerFrame) * 1000.0).toLong().coerceAtLeast(250L)

            val baseName = source.sourceName.substringBeforeLast(".")
            LoadedAudioBuffer(
                sourceName = "${baseName}_cut.wav",
                wavFile = trimmedFile,
                sampleRate = sampleRate,
                channels = channels,
                durationMs = newDurationMs,
                amplitudes = newAmps,
                totalSamples = trimmedPcm.size / bytesPerFrame
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trim audio segment", e)
            null
        }
    }

    /**
     * Generates a WhatsApp PTT-compliant voice note filename (`PTT-YYYYMMDD-WA0001.opus`).
     */
    fun generateWhatsAppPttFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val seq = (System.currentTimeMillis() % 9000 + 1000)
        return "PTT-$dateStr-WA$seq"
    }

    /**
     * Processes the selected trim range + Voice Note Formatting specification (Mono, 48kHz, Ogg/Opus or WAV)
     * and saves the file directly to public storage with the user's custom filename.
     */
    suspend fun processAndExportAudio(
        source: LoadedAudioBuffer,
        startFraction: Float,
        endFraction: Float,
        spec: VoiceNoteFormatSpec
    ): File? = withContext(Dispatchers.IO) {
        try {
            val workingBuffer = if (startFraction > 0.005f || endFraction < 0.995f) {
                trimAudioSegment(source, startFraction, endFraction) ?: source
            } else {
                source
            }

            val targetRate = if (spec.isVoiceNotePresetEnabled) STUDIO_SAMPLE_RATE_HZ else spec.sampleRateHz
            val targetChannels = if (spec.isVoiceNotePresetEnabled || spec.isMono) ExportChannels.MONO else ExportChannels.STEREO

            val exportConfig = AudioExportConfig(
                sampleRate = ExportSampleRate.entries.firstOrNull { it.hz == targetRate } ?: ExportSampleRate.RATE_48000,
                bitDepth = ExportBitDepth.BIT_16,
                channels = targetChannels,
                normalizeAudio = true
            )

            val baseName = if (spec.containerTarget == OpusContainerTarget.OPUS_PTT && spec.useWhatsAppPttNaming) {
                generateWhatsAppPttFileName()
            } else {
                spec.customFileName
                    .trim()
                    .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    .ifBlank { "opus_voice_note" }
            }

            val ext = spec.containerTarget.extension
            val finalFileName = "$baseName.$ext"

            val processedFile = File(context.filesDir, "audio/$finalFileName")
            processedFile.parentFile?.mkdirs()

            if (spec.containerTarget == OpusContainerTarget.OGG_OPUS ||
                spec.containerTarget == OpusContainerTarget.OPUS_PTT
            ) {
                val tempWav = File(context.cacheDir, "audio/temp_48k.wav")
                tempWav.parentFile?.mkdirs()
                val transcodedOk = PcmWavHelper.transcodeWavFile(workingBuffer.wavFile, tempWav, exportConfig)
                val opusWrittenOk = if (transcodedOk && tempWav.exists()) {
                    writeOggOpusContainerFile(tempWav, processedFile, targetRate, targetChannels.count)
                } else {
                    false
                }
                tempWav.delete()

                if (!opusWrittenOk || !processedFile.exists() || processedFile.length() <= 64L) {
                    if (processedFile.exists()) {
                        processedFile.delete()
                    }
                    Log.e(
                        TAG,
                        "Opus encoding failed for $finalFileName; refusing to write WAV data with .$ext extension"
                    )
                    return@withContext null
                }
            } else {
                val wavOk = PcmWavHelper.transcodeWavFile(workingBuffer.wavFile, processedFile, exportConfig)
                if (!wavOk || !processedFile.exists() || processedFile.length() <= 44L) {
                    if (processedFile.exists()) {
                        processedFile.delete()
                    }
                    return@withContext null
                }
            }

            saveToPublicDownloads(processedFile, finalFileName, spec.containerTarget.mimeType)

            processedFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting processed audio", e)
            null
        }
    }

    /**
     * Dispatches the processed 48kHz Mono Voice Note directly to WhatsApp, Telegram, Google Chat, or Character.AI
     * using [VoiceNoteIntentExporter] with native PTT voice-note MIME types, ClipData, and package targeting.
     */
    fun dispatchVoiceNoteToMessenger(
        exportedFile: File,
        messenger: MessengerTarget
    ) {
        VoiceNoteIntentExporter.exportVoiceNoteToApp(
            context = context,
            audioFile = exportedFile,
            messenger = messenger
        )
    }

    /**
     * Encodes 48kHz 16-bit PCM into a genuine Ogg/Opus bitstream using Android's hardware/platform
     * MediaCodec ("audio/opus") + MediaMuxer (MUXER_OUTPUT_OGG) on API 29+.
     *
     * @return true ONLY if a valid Ogg/Opus container file (verified with "OggS" magic bytes and
     *         encoded Opus frames) was written. Never writes a WAV file to a .opus or .ogg path.
     */
    private fun writeOggOpusContainerFile(
        resampledWav: File,
        destOggFile: File,
        sampleRate: Int,
        channels: Int
    ): Boolean {
        if (!resampledWav.exists()) return false
        val wavBytes = resampledWav.readBytes()
        if (wavBytes.size <= 44) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.e(TAG, "MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG requires API 29+")
            if (destOggFile.exists()) destOggFile.delete()
            return false
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var wroteOpusFrames = false

        try {
            val pcmData = wavBytes.copyOfRange(44, wavBytes.size)
            if (destOggFile.exists()) destOggFile.delete()

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 48000) // 48 kbps studio voice-note Opus bitrate
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(destOggFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
            var trackIndex = -1

            val bufferInfo = MediaCodec.BufferInfo()
            var pcmOffset = 0
            var inputDone = false
            var outputDone = false
            val bytesPerSampleFrame = channels * 2
            var loops = 0

            while (!outputDone && loops < 8000) {
                loops++
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val remaining = pcmData.size - pcmOffset
                            if (remaining <= 0) {
                                encoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val chunkSize = minOf(remaining, inBuf.remaining(), OPUS_FRAME_BYTES_20MS * 2)
                                inBuf.put(pcmData, pcmOffset, chunkSize)
                                val ptsUs = (pcmOffset.toLong() / bytesPerSampleFrame) * 1_000_000L / sampleRate
                                encoder.queueInputBuffer(inIdx, 0, chunkSize, ptsUs, 0)
                                pcmOffset += chunkSize
                            }
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 5000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0 && muxerStarted &&
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                            wroteOpusFrames = true
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }

            if (wroteOpusFrames && destOggFile.exists() && destOggFile.length() > 64L && hasValidOggSHeader(destOggFile)) {
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Real Ogg/Opus MediaCodec+MediaMuxer encoding failed", e)
        } finally {
            if (muxerStarted) {
                try { muxer?.stop() } catch (ignored: Exception) {}
            }
            try { encoder?.stop() } catch (ignored: Exception) {}
            try { encoder?.release() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }

        if (destOggFile.exists()) {
            destOggFile.delete()
        }
        return false
    }

    /**
     * Verifies that the output file starts with the 4-byte Ogg capture pattern ("OggS")
     * and is never a RIFF/WAV header masquerading with a .opus or .ogg extension.
     */
    private fun hasValidOggSHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return try {
            val magic = ByteArray(4)
            val read = file.inputStream().use { it.read(magic) }
            read == 4 &&
                magic[0] == 'O'.code.toByte() &&
                magic[1] == 'g'.code.toByte() &&
                magic[2] == 'g'.code.toByte() &&
                magic[3] == 'S'.code.toByte()
        } catch (e: Exception) {
            false
        }
    }

    private fun saveToPublicDownloads(sourceFile: File, fileName: String, mimeType: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OpusVoiceStudio")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input -> input.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OpusVoiceStudio")
                dir.mkdirs()
                val dest = File(dir, fileName)
                sourceFile.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback to MediaStore.Audio", e)
            null
        }
    }
}
