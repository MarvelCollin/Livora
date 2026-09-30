package com.example.livora.data.model

data class DictionaryEntry(
    val id: String,
    val word: String,
    val language: String,
    val translation: String,
    val description: String,
    val descriptionId: String = "",
    val example: String = "",
    val synonyms: List<String> = emptyList(),
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val attempts: Int get() = correctCount + wrongCount
    val accuracy: Float get() = if (attempts == 0) 0f else correctCount.toFloat() / attempts.toFloat()
}

enum class QuizMode { All, Hardest }

enum class DictionaryLanguage(val code: String, val label: String) {
    English("en", "English"),
    Spanish("es", "Spanish"),
    French("fr", "French"),
    German("de", "German"),
    Italian("it", "Italian"),
    Japanese("ja", "Japanese"),
    Korean("ko", "Korean");

    companion object {
        fun fromCode(code: String): DictionaryLanguage =
            entries.firstOrNull { it.code == code } ?: English
    }
}

data class LookupResult(
    val translation: String,
    val description: String,
    val descriptionId: String,
    val example: String,
    val synonyms: List<String>,
    val definitionFound: Boolean
)

data class QuizQuestion(
    val entry: DictionaryEntry,
    val options: List<String>,
    val correctIndex: Int
)
