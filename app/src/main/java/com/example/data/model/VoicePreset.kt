package com.example.data.model

data class VoicePreset(
    val id: String,
    val name: String,
    val gender: VoiceGender,
    val description: String,
    val iconEmoji: String,
    val sampleTone: String
)

enum class VoiceGender {
    MALE, FEMALE
}

val AVAILABLE_VOICES = listOf(
    VoicePreset(
        id = "Puck",
        name = "Puck",
        gender = VoiceGender.MALE,
        description = "Upbeat, energetic & engaging",
        iconEmoji = "⚡",
        sampleTone = "Great for advertisements, intros, and energetic storytelling"
    ),
    VoicePreset(
        id = "Charon",
        name = "Charon",
        gender = VoiceGender.MALE,
        description = "Calm, deep & informative",
        iconEmoji = "🎙️",
        sampleTone = "Ideal for documentaries, tech podcasts, and news"
    ),
    VoicePreset(
        id = "Fenrir",
        name = "Fenrir",
        gender = VoiceGender.MALE,
        description = "Bold, passionate & dynamic",
        iconEmoji = "🔥",
        sampleTone = "Suited for cinematic trailers, drama, and intense speeches"
    ),
    VoicePreset(
        id = "Orpheus",
        name = "Orpheus",
        gender = VoiceGender.MALE,
        description = "Warm, literary & narrative",
        iconEmoji = "📖",
        sampleTone = "Perfect for audiobooks, poetry, and reflective audio"
    ),
    VoicePreset(
        id = "Kore",
        name = "Kore",
        gender = VoiceGender.FEMALE,
        description = "Firm, confident & authoritative",
        iconEmoji = "✨",
        sampleTone = "Excellent for presentations, executive summaries, and guides"
    ),
    VoicePreset(
        id = "Aoede",
        name = "Aoede",
        gender = VoiceGender.FEMALE,
        description = "Breezy, natural & conversational",
        iconEmoji = "🎧",
        sampleTone = "Perfect for voice notes, friendly chat, and casual podcasts"
    ),
    VoicePreset(
        id = "Leda",
        name = "Leda",
        gender = VoiceGender.FEMALE,
        description = "Warm, soothing & expressive",
        iconEmoji = "🌙",
        sampleTone = "Wonderful for bedtime stories, meditation, and wellness"
    )
)

data class VocalTag(
    val tag: String,
    val label: String,
    val description: String,
    val icon: String
)

val VOCAL_DELIVERY_TAGS = listOf(
    VocalTag("[whispers]", "Whisper", "Soft, intimate whispering tone", "🤫"),
    VocalTag("[excited]", "Excited", "High-energy, joyous inflection", "🎉"),
    VocalTag("[sighs]", "Sigh", "Exhaled release or wistful delivery", "💨"),
    VocalTag("[laughs]", "Laugh", "Chuckling or amused inflection", "😄"),
    VocalTag("[pause]", "Pause", "Natural 1-second pause", "⏸️"),
    VocalTag("[dramatic pause]", "Dramatic Pause", "2-second suspenseful silence", "⏳"),
    VocalTag("[softly]", "Softly", "Gentle, tender vocal volume", "🕊️"),
    VocalTag("[clears throat]", "Clears Throat", "Expressive conversational filler", "🗣️")
)

val STYLE_PROMPT_PRESETS = listOf(
    "Speak in a calm, natural conversational tone",
    "Warm, expressive storytelling voice with gentle pauses",
    "Professional radio host delivering a late night broadcast",
    "Intimate, late-night voice note whispered to a friend",
    "Upbeat tech podcast co-host excited about a breakthrough",
    "Soothing meditation guide with slow pacing and rhythmic breath"
)

data class DialogueTurn(
    val id: String = java.util.UUID.randomUUID().toString(),
    val speaker: Int = 1, // 1 or 2
    val text: String = "",
    val styleInstruction: String = ""
)
