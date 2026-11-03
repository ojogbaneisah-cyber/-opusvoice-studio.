package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pronunciation_rules")
data class PronunciationRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalWord: String,
    val phoneticReplacement: String,
    val isCaseSensitive: Boolean = false,
    val isWholeWordOnly: Boolean = true,
    val isEnabled: Boolean = true,
    val category: String = "Custom",
    val timestamp: Long = System.currentTimeMillis()
)
