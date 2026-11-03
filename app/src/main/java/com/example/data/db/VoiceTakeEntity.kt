package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "voice_takes")
data class VoiceTakeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val script: String,
    val stylePrompt: String,
    val isMultiSpeaker: Boolean,
    val speaker1Voice: String,
    val speaker2Voice: String? = null,
    val speed: Float,
    val pitch: Float,
    val durationMs: Long,
    val filePath: String,
    val amplitudesJson: String, // Comma-separated normalized floats e.g. "0.2,0.8,0.5..."
    val timestamp: Long = System.currentTimeMillis(),
    val isGeminiCloud: Boolean = true
)
