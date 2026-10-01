package com.example.livora.data.dictionary

import com.example.livora.data.model.LookupResult
import java.net.URLEncoder

class DictionaryLookupRepository {

    private val api: LookupApi = LookupClient.api

    suspend fun checkSpelling(word: String): String? {
        val trimmed = word.trim()
        if (trimmed.isBlank() || trimmed.contains(" ")) return null
        return try {
            val response = api.spellCheck(
                url = "https://api.languagetool.org/v2/check",
                text = trimmed,
                language = "en-US"
            )
            val match = response.matches?.firstOrNull {
                it.rule?.issueType == "misspelling" && !it.replacements.isNullOrEmpty()
            }
            match?.replacements?.firstOrNull()?.value?.trim()
                ?.takeIf { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun lookup(word: String): LookupResult {
        val trimmed = word.trim()
        val translation = translate(trimmed)
        val definition = define(trimmed)
        val example = fetchExample(trimmed).ifBlank { definition.example }
        return LookupResult(
            translation = translation,
            synonyms = definition.synonyms,
            example = example
        )
    }

    private data class Definition(
        val example: String,
        val synonyms: List<String>
    )

    private suspend fun fetchExample(word: String): String {
        return try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "https://tatoeba.org/en/api_v0/search?from=eng&query=$encoded&sort=relevance"
            val sentences = api.examples(url).results.orEmpty()
                .mapNotNull { it.text?.trim()?.takeIf { text -> text.isNotBlank() } }
            sentences.firstOrNull { it.length in MIN_EXAMPLE_LENGTH..MAX_EXAMPLE_LENGTH }
                ?: sentences.firstOrNull().orEmpty()
        } catch (t: Throwable) {
            ""
        }
    }

    private suspend fun translate(word: String): String {
        return try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=en|id"
            val response = api.translate(url)
            val primary = response.responseData?.translatedText?.trim().orEmpty()
            if (primary.isNotBlank() && !primary.equals(word, ignoreCase = true)) {
                return primary
            }
            response.matches
                ?.mapNotNull { it.translation?.trim() }
                ?.firstOrNull { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                .orEmpty()
        } catch (t: Throwable) {
            ""
        }
    }

    private suspend fun define(word: String): Definition {
        return try {
            val meanings = api.define("en", word).firstOrNull()?.meanings.orEmpty()
            val example = meanings
                .flatMap { it.definitions.orEmpty() }
                .firstNotNullOfOrNull { it.example?.trim()?.takeIf { e -> e.isNotBlank() } }
                .orEmpty()
            val synonyms = meanings
                .flatMap { meaning ->
                    meaning.synonyms.orEmpty() + meaning.definitions.orEmpty().flatMap { it.synonyms.orEmpty() }
                }
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.equals(word, ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .take(MAX_SYNONYMS)
            Definition(example = example, synonyms = synonyms)
        } catch (t: Throwable) {
            Definition(example = "", synonyms = emptyList())
        }
    }

    private companion object {
        const val MIN_EXAMPLE_LENGTH = 15
        const val MAX_EXAMPLE_LENGTH = 90
        const val MAX_SYNONYMS = 5
    }
}
