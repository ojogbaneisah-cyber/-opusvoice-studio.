package com.example.audio

import com.example.data.db.PronunciationRuleEntity

object PronunciationProcessor {

    /**
     * Applies user-defined pronunciation dictionary mappings to the input script.
     * Supports case-sensitivity and whole-word boundary matching.
     */
    fun applyRules(script: String, rules: List<PronunciationRuleEntity>): String {
        if (script.isBlank() || rules.isEmpty()) return script

        var result = script
        val activeRules = rules
            .filter { it.isEnabled && it.originalWord.isNotBlank() }
            // Sort by longest originalWord first to prevent partial word collisions
            .sortedByDescending { it.originalWord.length }

        for (rule in activeRules) {
            val escapedWord = Regex.escape(rule.originalWord.trim())
            val pattern = if (rule.isWholeWordOnly) {
                "(?<!\\w)$escapedWord(?!\\w)"
            } else {
                escapedWord
            }

            val options = if (rule.isCaseSensitive) {
                emptySet()
            } else {
                setOf(RegexOption.IGNORE_CASE)
            }

            val regex = Regex(pattern, options)
            result = result.replace(regex) {
                Regex.escapeReplacement(rule.phoneticReplacement)
            }
        }

        return result
    }

    val DEFAULT_STARTER_RULES = listOf(
        PronunciationRuleEntity(
            originalWord = "AI",
            phoneticReplacement = "A. I.",
            isCaseSensitive = true,
            isWholeWordOnly = true,
            isEnabled = true,
            category = "Acronym"
        ),
        PronunciationRuleEntity(
            originalWord = "SQL",
            phoneticReplacement = "sequel",
            isCaseSensitive = true,
            isWholeWordOnly = true,
            isEnabled = true,
            category = "Tech"
        ),
        PronunciationRuleEntity(
            originalWord = "GUI",
            phoneticReplacement = "gooey",
            isCaseSensitive = true,
            isWholeWordOnly = true,
            isEnabled = true,
            category = "Tech"
        ),
        PronunciationRuleEntity(
            originalWord = "API",
            phoneticReplacement = "A. P. I.",
            isCaseSensitive = true,
            isWholeWordOnly = true,
            isEnabled = true,
            category = "Acronym"
        ),
        PronunciationRuleEntity(
            originalWord = "TTS",
            phoneticReplacement = "text to speech",
            isCaseSensitive = true,
            isWholeWordOnly = true,
            isEnabled = true,
            category = "Acronym"
        ),
        PronunciationRuleEntity(
            originalWord = "IEEE",
            phoneticReplacement = "I triple E",
            isCaseSensitive = true,
            isWholeWordOnly = true,
            isEnabled = true,
            category = "Acronym"
        )
    )
}
