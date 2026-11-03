package com.example

import com.example.audio.AudioExportConfig
import com.example.audio.ExportBitDepth
import com.example.audio.ExportChannels
import com.example.audio.ExportSampleRate
import com.example.audio.PcmWavHelper
import com.example.audio.PronunciationProcessor
import com.example.audio.ROYALTY_FREE_TRACKS
import com.example.data.db.PronunciationRuleEntity
import com.example.data.model.AVAILABLE_VOICES
import com.example.data.model.BatchParser
import com.example.data.model.VOCAL_DELIVERY_TAGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExampleUnitTest {

    @Test
    fun `available voices catalog has prebuilt voices`() {
        assertTrue(AVAILABLE_VOICES.isNotEmpty())
        val puck = AVAILABLE_VOICES.firstOrNull { it.id == "Puck" }
        assertNotNull(puck)
        assertEquals("Puck", puck?.name)
    }

    @Test
    fun `vocal delivery tags contain required performance tags`() {
        val tags = VOCAL_DELIVERY_TAGS.map { it.tag }
        assertTrue(tags.contains("[whispers]"))
        assertTrue(tags.contains("[excited]"))
        assertTrue(tags.contains("[sighs]"))
        assertTrue(tags.contains("[laughs]"))
        assertTrue(tags.contains("[pause]"))
    }

    @Test
    fun `royalty free background tracks catalog has presets`() {
        assertTrue(ROYALTY_FREE_TRACKS.isNotEmpty())
        val lofi = ROYALTY_FREE_TRACKS.firstOrNull { it.id == "lofi_midnight" }
        assertNotNull(lofi)
    }

    @Test
    fun `pcm to wav writer creates valid file header`() {
        val testFile = File.createTempFile("test_speech", ".wav")
        testFile.deleteOnExit()
        val dummyPcm = ByteArray(200) { 1 }

        val created = PcmWavHelper.createWavFile(dummyPcm, testFile, sampleRate = 24000)
        assertTrue(created)
        assertTrue(testFile.exists())
        assertEquals(244L, testFile.length()) // 44 bytes header + 200 bytes PCM

        val amplitudes = PcmWavHelper.extractAmplitudesFromPcm(dummyPcm, targetBarCount = 20)
        assertEquals(20, amplitudes.size)
    }

    @Test
    fun `transcode wav resamples to 48kHz 24bit stereo`() {
        val srcFile = File.createTempFile("src_24k", ".wav")
        val destFile = File.createTempFile("dest_48k", ".wav")
        srcFile.deleteOnExit()
        destFile.deleteOnExit()

        // 240 samples at 24kHz 16-bit mono = 480 bytes
        val dummyPcm = ByteArray(480) { 10 }
        PcmWavHelper.createWavFile(dummyPcm, srcFile, sampleRate = 24000, channels = 1, bitsPerSample = 16)

        val config = AudioExportConfig(
            sampleRate = ExportSampleRate.RATE_48000,
            bitDepth = ExportBitDepth.BIT_24,
            channels = ExportChannels.STEREO,
            normalizeAudio = true
        )

        val ok = PcmWavHelper.transcodeWavFile(srcFile, destFile, config)
        assertTrue(ok)
        assertTrue(destFile.exists())

        val bytes = destFile.readBytes()
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, header.getShort(22).toInt()) // Stereo channels
        assertEquals(48000, header.getInt(24)) // 48kHz sample rate
        assertEquals(24, header.getShort(34).toInt()) // 24-bit depth
    }

    @Test
    fun `pronunciation processor applies custom rules accurately`() {
        val rules = listOf(
            PronunciationRuleEntity(originalWord = "SQL", phoneticReplacement = "sequel", isCaseSensitive = true, isWholeWordOnly = true),
            PronunciationRuleEntity(originalWord = "GUI", phoneticReplacement = "gooey", isCaseSensitive = false, isWholeWordOnly = true)
        )
        val input = "Query the SQL database using the gui interface."
        val output = PronunciationProcessor.applyRules(input, rules)
        assertEquals("Query the sequel database using the gooey interface.", output)
    }

    @Test
    fun `batch parser splits text into multiple segments`() {
        val raw = "Segment one text.\n\nSegment two text.\n\nSegment three text."
        val items = BatchParser.parseSegmentsFromText(raw, "Chapter")
        assertEquals(3, items.size)
        assertEquals("Segment one text.", items[0].rawText)
        assertEquals(2, items[1].index)
    }

    @Test
    fun `opus audio engine adheres to 48kHz mono studio specification`() {
        assertEquals(48000, com.example.audio.OpusAudioEngine.STUDIO_SAMPLE_RATE_HZ)
        assertEquals(1, com.example.audio.OpusAudioEngine.STUDIO_CHANNELS)
        assertEquals(16, com.example.audio.OpusAudioEngine.STUDIO_BITS_PER_SAMPLE)
        assertEquals(960, com.example.audio.OpusAudioEngine.OPUS_FRAME_SAMPLES_20MS)
        assertEquals(1920, com.example.audio.OpusAudioEngine.OPUS_FRAME_BYTES_20MS)

        val defaultConfig = AudioExportConfig()
        assertEquals(ExportSampleRate.RATE_48000, defaultConfig.sampleRate)
        assertEquals(ExportChannels.MONO, defaultConfig.channels)

        // Verify 48kHz 16-bit Mono WAV file header creation (10 x 20ms Opus frames = 19,200 bytes PCM)
        val wav48k = File.createTempFile("mic_48k_mono", ".wav")
        wav48k.deleteOnExit()
        val pcm48k = ByteArray(com.example.audio.OpusAudioEngine.OPUS_FRAME_BYTES_20MS * 10) { 12 }
        val created = PcmWavHelper.createWavFile(
            pcmData = pcm48k,
            outputFile = wav48k,
            sampleRate = com.example.audio.OpusAudioEngine.STUDIO_SAMPLE_RATE_HZ,
            channels = com.example.audio.OpusAudioEngine.STUDIO_CHANNELS,
            bitsPerSample = com.example.audio.OpusAudioEngine.STUDIO_BITS_PER_SAMPLE
        )
        assertTrue(created)
        val header = ByteBuffer.wrap(wav48k.readBytes(), 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, header.getShort(22).toInt()) // 1 channel (Mono)
        assertEquals(48000, header.getInt(24)) // 48,000 Hz sample rate
        assertEquals(96000, header.getInt(28)) // 48,000 * 1 * 2 = 96,000 byteRate
        assertEquals(16, header.getShort(34).toInt()) // 16-bit PCM
    }

    @Test
    fun `chime stream interceptor attenuates synthetic c_ai chime bursts`() {
        val interceptor = com.example.audio.ChimeStreamInterceptor()
        val sampleRate = 48000
        val totalSamples = 960 * 5 // 5 frames of 20ms at 48kHz
        val rawSamples = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            // Generate a 1,680 Hz synthetic UI chime sine wave
            val angle = 2.0 * Math.PI * 1680.0 * i / sampleRate
            rawSamples[i] = (Math.sin(angle) * 18000).toInt().toShort()
        }
        val inputBytes = ByteArray(totalSamples * 2)
        ByteBuffer.wrap(inputBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(rawSamples)

        val filteredBytes = interceptor.interceptAndFilterPcmStream(inputBytes, sampleRate)
        assertEquals(inputBytes.size, filteredBytes.size)

        val outputSamples = ShortArray(totalSamples)
        ByteBuffer.wrap(filteredBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(outputSamples)

        var inputPeak = 0
        var outputPeak = 0
        for (i in 0 until totalSamples) {
            inputPeak = maxOf(inputPeak, kotlin.math.abs(rawSamples[i].toInt()))
            outputPeak = maxOf(outputPeak, kotlin.math.abs(outputSamples[i].toInt()))
        }
        assertTrue("Expected chime burst peak to be strongly attenuated", outputPeak < inputPeak / 3)
    }

    @Test
    fun `messenger targets map to native voice note containers`() {
        assertEquals(
            com.example.audio.OpusContainerTarget.OPUS_PTT,
            com.example.audio.MessengerTarget.WHATSAPP.preferredContainer
        )
        assertEquals(
            com.example.audio.OpusContainerTarget.OGG_OPUS,
            com.example.audio.MessengerTarget.TELEGRAM.preferredContainer
        )
        assertEquals(
            com.example.audio.OpusContainerTarget.OGG_OPUS,
            com.example.audio.MessengerTarget.GOOGLE_CHAT.preferredContainer
        )
    }
}

