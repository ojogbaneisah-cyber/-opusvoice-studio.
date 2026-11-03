package com.example.audio

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

data class BackgroundTrack(
    val id: String,
    val title: String,
    val genre: String,
    val description: String,
    val emoji: String,
    val durationSec: Int = 30,
    val isCustom: Boolean = false,
    val customUri: Uri? = null
)

val ROYALTY_FREE_TRACKS = listOf(
    BackgroundTrack(
        id = "lofi_midnight",
        title = "Lo-Fi Midnight Chill",
        genre = "Lo-Fi Beats",
        description = "Mellow electric keys with gentle warm vinyl ambience",
        emoji = "☕",
        durationSec = 30
    ),
    BackgroundTrack(
        id = "cinematic_horizon",
        title = "Cinematic Ambient Horizon",
        genre = "Ambient Drone",
        description = "Atmospheric swell with gentle cinematic chords",
        emoji = "🌌",
        durationSec = 30
    ),
    BackgroundTrack(
        id = "acoustic_warmth",
        title = "Acoustic Storytelling",
        genre = "Acoustic Folk",
        description = "Warm acoustic arpeggios ideal for storytelling & podcasts",
        emoji = "🎸",
        durationSec = 30
    ),
    BackgroundTrack(
        id = "cyberpunk_synth",
        title = "Neon Synthwave Glow",
        genre = "Electronic",
        description = "Futuristic analog synth pads for sci-fi and tech reviews",
        emoji = "⚡",
        durationSec = 30
    ),
    BackgroundTrack(
        id = "meditation_calm",
        title = "Deep Focus Meditation",
        genre = "Zen & Chill",
        description = "Harmonic singing tone with slow soothing breathing pad",
        emoji = "🧘",
        durationSec = 30
    )
)

data class MusicMixSettings(
    val isEnabled: Boolean = false,
    val selectedTrack: BackgroundTrack = ROYALTY_FREE_TRACKS[0],
    val musicVolume: Float = 0.25f, // 0.0 to 1.0 (25% default so speech stands out)
    val speechVolume: Float = 1.0f,
    val fadeIn: Boolean = true,
    val fadeInDurationMs: Int = 1500,
    val fadeOut: Boolean = true,
    val fadeOutDurationMs: Int = 2000
)

class BackgroundMusicMixer(private val context: Context) {
    private val TAG = "BackgroundMusicMixer"

    /**
     * Generates or fetches the PCM audio for a royalty-free track.
     * Uses clean algorithmic harmonic synthesis at 24000Hz 16-bit mono.
     */
    suspend fun getTrackPcmData(track: BackgroundTrack, sampleRate: Int = 24000): ByteArray = withContext(Dispatchers.IO) {
        if (track.isCustom && track.customUri != null) {
            try {
                context.contentResolver.openInputStream(track.customUri)?.use { input ->
                    val bytes = input.readBytes()
                    // If WAV, skip 44-byte header
                    return@withContext if (bytes.size > 44) bytes.copyOfRange(44, bytes.size) else bytes
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading custom music track", e)
            }
        }

        // Generate melodic ambient loop for the chosen preset
        val durationSec = 30
        val totalSamples = sampleRate * durationSec
        val pcm = ByteArray(totalSamples * 2)
        val buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        val baseFreqs = when (track.id) {
            "lofi_midnight" -> listOf(261.63, 329.63, 392.00, 493.88) // C maj7
            "cinematic_horizon" -> listOf(220.00, 261.63, 329.63, 440.00) // A min
            "acoustic_warmth" -> listOf(196.00, 246.94, 293.66, 392.00) // G maj
            "cyberpunk_synth" -> listOf(146.83, 220.00, 293.66, 370.00) // D min/F
            else -> listOf(174.61, 220.00, 261.63, 349.23) // F maj
        }

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sampleVal = 0.0

            // Combine gentle harmonics
            for ((index, freq) in baseFreqs.withIndex()) {
                val lfo = 0.5 + 0.5 * sin(2 * PI * (0.2 + index * 0.1) * t)
                val fundamental = sin(2 * PI * freq * t)
                val overtone = 0.3 * sin(2 * PI * freq * 2.0 * t)
                sampleVal += (fundamental + overtone) * lfo
            }

            // Normalize and smooth amplitude
            sampleVal /= baseFreqs.size
            val maxAmp = 10000.0 // Headroom for mixing
            val finalSample = (sampleVal * maxAmp).toInt().coerceIn(-32767, 32767).toShort()
            buffer.putShort(finalSample)
        }

        pcm
    }

    /**
     * Blends speech WAV file with selected background music using independent volume & fade curves.
     */
    suspend fun mixAudio(
        speechWavFile: File,
        settings: MusicMixSettings,
        sampleRate: Int = 24000
    ): File = withContext(Dispatchers.IO) {
        if (!settings.isEnabled) return@withContext speechWavFile

        try {
            val speechBytes = speechWavFile.readBytes()
            if (speechBytes.size <= 44) return@withContext speechWavFile

            val speechPcm = speechBytes.copyOfRange(44, speechBytes.size)
            val speechSampleCount = speechPcm.size / 2

            val musicPcm = getTrackPcmData(settings.selectedTrack, sampleRate)
            val musicSampleCount = musicPcm.size / 2

            if (musicSampleCount == 0) return@withContext speechWavFile

            val speechShorts = ShortArray(speechSampleCount)
            ByteBuffer.wrap(speechPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(speechShorts)

            val musicShorts = ShortArray(musicSampleCount)
            ByteBuffer.wrap(musicPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(musicShorts)

            val mixedShorts = ShortArray(speechSampleCount)
            val fadeInSamples = (settings.fadeInDurationMs.toDouble() / 1000.0 * sampleRate).toInt().coerceAtMost(speechSampleCount)
            val fadeOutSamples = (settings.fadeOutDurationMs.toDouble() / 1000.0 * sampleRate).toInt().coerceAtMost(speechSampleCount)
            val fadeOutStartSample = speechSampleCount - fadeOutSamples

            for (i in 0 until speechSampleCount) {
                val speechSample = speechShorts[i] * settings.speechVolume
                val musicRaw = musicShorts[i % musicSampleCount]

                // Apply fade in & fade out multiplier to background music
                var musicFade = 1.0f
                if (settings.fadeIn && i < fadeInSamples && fadeInSamples > 0) {
                    musicFade *= (i.toFloat() / fadeInSamples.toFloat())
                }
                if (settings.fadeOut && i >= fadeOutStartSample && fadeOutSamples > 0) {
                    musicFade *= ((speechSampleCount - i).toFloat() / fadeOutSamples.toFloat())
                }

                val musicSample = musicRaw * settings.musicVolume * musicFade
                val mixed = (speechSample + musicSample).toInt().coerceIn(-32768, 32767).toShort()
                mixedShorts[i] = mixed
            }

            val mixedPcm = ByteArray(speechSampleCount * 2)
            ByteBuffer.wrap(mixedPcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(mixedShorts)

            val mixedFile = File(context.filesDir, "audio/take_mixed_${System.currentTimeMillis()}.wav")
            PcmWavHelper.createWavFile(mixedPcm, mixedFile, sampleRate = sampleRate)
            return@withContext mixedFile
        } catch (e: Exception) {
            Log.e(TAG, "Error mixing speech with background music", e)
            return@withContext speechWavFile
        }
    }
}
