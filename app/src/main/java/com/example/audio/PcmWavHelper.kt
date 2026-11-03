package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PcmWavHelper {
    private const val TAG = "PcmWavHelper"

    /**
     * Converts raw linear PCM bytes into a standard RIFF WAVE file.
     * Default sampleRate for Gemini TTS is 24000 Hz, 1 channel mono, 16-bit LE.
     */
    fun createWavFile(
        pcmData: ByteArray,
        outputFile: File,
        sampleRate: Int = 24000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): Boolean {
        try {
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                val bytesPerSample = bitsPerSample / 8
                val byteRate = sampleRate * channels * bytesPerSample
                val blockAlign = channels * bytesPerSample
                val totalDataLen = pcmData.size + 36
                val totalAudioLen = pcmData.size

                val header = ByteArray(44)
                val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

                // RIFF header
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                buffer.putInt(4, totalDataLen)
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()

                // 'fmt ' chunk
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                buffer.putInt(16, 16) // Subchunk1Size for PCM
                buffer.putShort(20, 1.toShort()) // AudioFormat 1 = PCM
                buffer.putShort(22, channels.toShort())
                buffer.putInt(24, sampleRate)
                buffer.putInt(28, byteRate)
                buffer.putShort(32, blockAlign.toShort())
                buffer.putShort(34, bitsPerSample.toShort())

                // 'data' chunk
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                buffer.putInt(40, totalAudioLen)

                fos.write(header)
                fos.write(pcmData)
                fos.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write WAV file", e)
            return false
        }
    }

    /**
     * Reads a source 16-bit WAV file and transcodes/resamples it according to AudioExportConfig
     * (supporting 24kHz, 44.1kHz, 48kHz, 8-bit, 16-bit, 24-bit, Mono/Stereo, and Peak Normalization).
     */
    fun transcodeWavFile(
        sourceWavFile: File,
        destWavFile: File,
        config: AudioExportConfig
    ): Boolean {
        try {
            val rawBytes = sourceWavFile.readBytes()
            if (rawBytes.size <= 44) return false

            // Read source sample rate from WAV header (bytes 24..27)
            val headerBuf = ByteBuffer.wrap(rawBytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
            val sourceChannels = headerBuf.getShort(22).toInt().coerceAtLeast(1)
            val sourceSampleRate = headerBuf.getInt(24).let { if (it in 8000..96000) it else 24000 }

            val pcmBytes = rawBytes.copyOfRange(44, rawBytes.size)
            val totalShortCount = pcmBytes.size / 2
            if (totalShortCount == 0) return false

            val rawShorts = ShortArray(totalShortCount)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(rawShorts)

            // Mix down to mono array first if source had multiple channels
            val monoCount = totalShortCount / sourceChannels
            val monoFloats = FloatArray(monoCount)
            var peakAmplitude = 0.001f

            for (i in 0 until monoCount) {
                val sample = rawShorts[i * sourceChannels] / 32768.0f
                monoFloats[i] = sample
                val absVal = abs(sample)
                if (absVal > peakAmplitude) peakAmplitude = absVal
            }

            // Normalize to -0.5 dBFS (~0.94f) if requested
            if (config.normalizeAudio && peakAmplitude > 0.01f) {
                val gain = (0.94f / peakAmplitude).coerceIn(0.5f, 4.0f)
                for (i in 0 until monoCount) {
                    monoFloats[i] = (monoFloats[i] * gain).coerceIn(-1.0f, 1.0f)
                }
            }

            // Resample from sourceSampleRate to config.sampleRate.hz using linear interpolation
            val targetRate = config.sampleRate.hz
            val resampledMono = if (targetRate == sourceSampleRate) {
                monoFloats
            } else {
                val ratio = sourceSampleRate.toDouble() / targetRate.toDouble()
                val outLength = max(1, (monoCount / ratio).toInt())
                val outArr = FloatArray(outLength)
                for (i in 0 until outLength) {
                    val srcPos = i * ratio
                    val idx0 = srcPos.toInt().coerceIn(0, monoCount - 1)
                    val idx1 = (idx0 + 1).coerceIn(0, monoCount - 1)
                    val frac = (srcPos - idx0).toFloat()
                    outArr[i] = monoFloats[idx0] * (1f - frac) + monoFloats[idx1] * frac
                }
                outArr
            }

            // Encode to target bit depth and channel count
            val targetChannels = config.channels.count
            val targetBits = config.bitDepth.bits
            val bytesPerSample = targetBits / 8
            val outPcmStream = ByteArrayOutputStream(resampledMono.size * targetChannels * bytesPerSample)

            for (sampleFloat in resampledMono) {
                val clamped = sampleFloat.coerceIn(-1.0f, 1.0f)
                repeat(targetChannels) {
                    when (targetBits) {
                        8 -> {
                            // 8-bit WAV PCM is unsigned (0..255, center 128)
                            val u8 = ((clamped * 127.0f) + 128.0f).roundToInt().coerceIn(0, 255)
                            outPcmStream.write(u8)
                        }
                        24 -> {
                            // 24-bit signed little-endian (-8388608..8388607)
                            val s24 = (clamped * 8388607.0f).roundToInt().coerceIn(-8388608, 8388607)
                            outPcmStream.write(s24 and 0xFF)
                            outPcmStream.write((s24 shr 8) and 0xFF)
                            outPcmStream.write((s24 shr 16) and 0xFF)
                        }
                        else -> {
                            // 16-bit signed little-endian (-32768..32767)
                            val s16 = (clamped * 32767.0f).roundToInt().coerceIn(-32768, 32767)
                            outPcmStream.write(s16 and 0xFF)
                            outPcmStream.write((s16 shr 8) and 0xFF)
                        }
                    }
                }
            }

            return createWavFile(
                pcmData = outPcmStream.toByteArray(),
                outputFile = destWavFile,
                sampleRate = targetRate,
                channels = targetChannels,
                bitsPerSample = targetBits
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error transcoding WAV file with custom export config", e)
            return false
        }
    }

    /**
     * Extracts a list of normalized amplitude points (0.05f to 1.0f) for visual waveform rendering.
     */
    fun extractAmplitudesFromPcm(pcmData: ByteArray, targetBarCount: Int = 50): List<Float> {
        if (pcmData.size < 4) {
            return List(targetBarCount) { 0.2f }
        }

        val shortCount = pcmData.size / 2
        val shorts = ShortArray(shortCount)
        ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        val samplesPerBar = max(1, shortCount / targetBarCount)
        val amplitudes = ArrayList<Float>(targetBarCount)

        var globalMax = 1.0f
        val rawPeaks = ArrayList<Float>(targetBarCount)

        for (i in 0 until targetBarCount) {
            val start = i * samplesPerBar
            val end = (start + samplesPerBar).coerceAtMost(shortCount)
            if (start >= shortCount) {
                rawPeaks.add(0.1f)
                continue
            }

            var sumSquares = 0.0
            var count = 0
            for (j in start until end) {
                val sample = shorts[j].toDouble()
                sumSquares += sample * sample
                count++
            }
            val rms = if (count > 0) sqrt(sumSquares / count).toFloat() else 0.0f
            rawPeaks.add(rms)
            if (rms > globalMax) globalMax = rms
        }

        for (peak in rawPeaks) {
            val normalized = (peak / globalMax).coerceIn(0.12f, 1.0f)
            amplitudes.add(normalized)
        }

        return amplitudes
    }

    fun parseAmplitudes(serialized: String): List<Float> {
        if (serialized.isBlank()) return emptyList()
        return try {
            serialized.split(",").mapNotNull { it.trim().toFloatOrNull() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeAmplitudes(amplitudes: List<Float>): String {
        return amplitudes.joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) }
    }

    /**
     * Exports a WAV file into the public Downloads/Music directory with the user's selected
     * AudioExportConfig (Sample Rate, Bit Depth, Channels, Normalization).
     */
    fun exportToPublicStorage(
        context: Context,
        sourceFile: File,
        title: String,
        config: AudioExportConfig = AudioExportConfig()
    ): Uri? {
        val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
        val rateSuffix = "${config.sampleRate.hz / 1000}kHz_${config.bitDepth.bits}bit"
        val fileName = "${cleanTitle}_${rateSuffix}_${System.currentTimeMillis()}.wav"
        val mimeType = "audio/wav"

        // Prepare transcoded file in cache first
        val tempExportFile = File(context.cacheDir, "audio/export_$fileName")
        tempExportFile.parentFile?.mkdirs()
        val transcodeSuccess = transcodeWavFile(sourceFile, tempExportFile, config)
        val fileToExport = if (transcodeSuccess && tempExportFile.exists()) tempExportFile else sourceFile

        val resultUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VoxStudio")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    fileToExport.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "VoxStudio")
            dir.mkdirs()
            val destFile = File(dir, fileName)
            fileToExport.copyTo(destFile, overwrite = true)
            Uri.fromFile(destFile)
        }

        if (tempExportFile.exists()) {
            tempExportFile.delete()
        }
        return resultUri
    }

    /**
     * Shares audio file via Intent.ACTION_SEND with optional transcoding to ExportConfig.
     */
    fun shareAudioFile(
        context: Context,
        audioFile: File,
        title: String,
        config: AudioExportConfig? = null
    ) {
        try {
            val fileToShare = if (config != null) {
                val cleanTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(28)
                val sharedTemp = File(context.cacheDir, "audio/${cleanTitle}_${config.sampleRate.hz / 1000}kHz_${config.bitDepth.bits}bit.wav")
                sharedTemp.parentFile?.mkdirs()
                if (transcodeWavFile(audioFile, sharedTemp, config)) sharedTemp else audioFile
            } else {
                audioFile
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileToShare
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Generated with VoxStudio AI Voice Studio: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Voice Note").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing audio file", e)
        }
    }
}
