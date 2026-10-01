package com.example.livora.data.model

data class DictionaryEntry(
    val id: String,
    val word: String,
    val translation: String,
    val synonyms: List<String> = emptyList(),
    val example: String = "",
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val attempts: Int get() = correctCount + wrongCount
    val accuracy: Float get() = if (attempts == 0) 0f else correctCount.toFloat() / attempts.toFloat()
}

enum class QuizMode { All, Hardest }

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
    val correct: Set<String>
)

data class QuizQuestion(
    val entry: DictionaryEntry,
    val options: List<String>,
    val correctIndex: Int,
    val synonymRound: SynonymRound? = null
)
