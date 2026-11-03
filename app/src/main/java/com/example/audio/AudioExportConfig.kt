package com.example.audio

enum class ExportSampleRate(val hz: Int, val label: String, val description: String) {
    RATE_24000(24000, "24.0 kHz", "Native Gemini Studio Rate (Compact & Clear)"),
    RATE_44100(44100, "44.1 kHz", "CD Audio Standard (Podcasts & Music)"),
    RATE_48000(48000, "48.0 kHz", "Pro Broadcast & Video Production Grade")
}

enum class ExportBitDepth(val bits: Int, val label: String, val description: String) {
    BIT_16(16, "16-bit PCM", "Standard Studio Dynamic Range (Universal)"),
    BIT_24(24, "24-bit PCM", "High-Resolution Mastering Headroom"),
    BIT_8(8, "8-bit Retro", "Compact Voice Note / Lo-Fi Bandwidth")
}

enum class ExportChannels(val count: Int, val label: String) {
    MONO(1, "Mono (1ch)"),
    STEREO(2, "Stereo (2ch)")
}

data class AudioExportConfig(
    val sampleRate: ExportSampleRate = ExportSampleRate.RATE_48000,
    val bitDepth: ExportBitDepth = ExportBitDepth.BIT_16,
    val channels: ExportChannels = ExportChannels.MONO,
    val normalizeAudio: Boolean = true
)
