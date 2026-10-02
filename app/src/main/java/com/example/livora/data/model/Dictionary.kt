package com.example.livora.data.model

enum class EntryCategory(val dbValue: String) {
    Vocabulary("vocabulary"),
    Writing("writing");

    companion object {
        fun fromDb(value: String?): EntryCategory =
            entries.firstOrNull { it.dbValue.equals(value?.trim(), ignoreCase = true) } ?: Vocabulary
    }
}

data class DictionaryEntry(
    val id: String,
    val word: String,
    val translation: String,
    val synonyms: List<String> = emptyList(),
    val example: String = "",
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val category: EntryCategory = EntryCategory.Vocabulary,
    val synonymTranslations: Map<String, String> = emptyMap()
) {
    val attempts: Int get() = correctCount + wrongCount
    val accuracy: Float get() = if (attempts == 0) 0f else correctCount.toFloat() / attempts.toFloat()

    fun translationOf(synonym: String): String? =
        synonymTranslations[synonym.trim().lowercase()]?.takeIf { it.isNotBlank() }
}

enum class QuizMode { All, Hardest, Writing }

enum class SynonymInput { Click, Write }

data class LookupResult(
    val translation: String,
    val synonyms: List<String>,
    val example: String
) {
    val isEmpty: Boolean
        get() = translation.isBlank() && synonyms.isEmpty() && example.isBlank()
}

data class SynonymRound(
    val options: List<String>,
    val correct: Set<String>,
    val needed: Int = correct.size,
    val typed: Boolean = false
)

data class QuizQuestion(
    val entry: DictionaryEntry,
    val options: List<String>,
    val correctIndex: Int,
    val synonymRound: SynonymRound? = null,
    val translationStep: Boolean = true
)
