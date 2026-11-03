package com.example.data.model

import com.example.data.db.VoiceTakeEntity
import java.util.UUID

enum class BatchSegmentStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class BatchSegmentItem(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val title: String,
    val rawText: String,
    val processedText: String = rawText,
    val status: BatchSegmentStatus = BatchSegmentStatus.QUEUED,
    val progress: Float = 0f, // 0.0f to 1.0f
    val statusDetail: String = "Waiting in queue",
    val generatedTake: VoiceTakeEntity? = null,
    val errorMessage: String? = null
)

object BatchParser {
    /**
     * Parses a multi-segment text file or raw string into structured BatchSegmentItems.
     * Supports "---" block dividers, blank line paragraphs, or single lines.
     */
    fun parseSegmentsFromText(content: String, baseFileName: String = "Batch"): List<BatchSegmentItem> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        val rawBlocks = when {
            trimmed.contains("\n---") -> {
                trimmed.split(Regex("\\n---+[\\r\\n]*"))
            }
            trimmed.contains("\n\n") -> {
                trimmed.split(Regex("\\n\\s*\\n+"))
            }
            else -> {
                trimmed.lines()
            }
        }

        return rawBlocks
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { idx, segmentText ->
                val cleanPreview = segmentText
                    .replace(Regex("\\[.*?]"), "")
                    .trim()
                    .take(28)
                val segNum = idx + 1
                BatchSegmentItem(
                    index = segNum,
                    title = "$baseFileName #$segNum: $cleanPreview...",
                    rawText = segmentText
                )
            }
    }

    val SAMPLE_BATCH_SCRIPT = """
Welcome to chapter one of the AI Audio Production Guide. [excited] In this module, we explore automated batch voice synthesis for creators and studios.

---

Segment two demonstrates how the Pronunciation Dictionary automatically converts technical acronyms like SQL, GUI, and API into natural spoken words.

---

[whispers] Segment three adds intimate vocal tags and atmospheric pacing. [pause] You can export every segment at 48 kilohertz, 24-bit broadcast resolution!

---

Finally, segment four wraps up our batch queue. Tap Play or Export on any completed card below to share your mastered voice notes.
    """.trimIndent()
}
