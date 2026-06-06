package com.example.livora.data.dictionary

import com.example.livora.data.model.LookupResult
import java.net.URLEncoder

class DictionaryLookupRepository {

    private val api: LookupApi = LookupClient.api

    suspend fun checkSpelling(word: String, languageCode: String): String? {
        val ltLang = ltLanguage(languageCode) ?: return null
        val trimmed = word.trim()
        if (trimmed.isBlank() || trimmed.contains(" ")) return null
        return try {
            val response = api.spellCheck(
                url = "https://api.languagetool.org/v2/check",
                text = trimmed,
                language = ltLang
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

    private fun ltLanguage(languageCode: String): String? = when (languageCode) {
        "en" -> "en-US"
        "es" -> "es"
        "fr" -> "fr"
        "de" -> "de-DE"
        "it" -> "it"
        else -> null
    }

    suspend fun lookup(word: String, languageCode: String): LookupResult {
        val trimmed = word.trim()
        val translation = translate(trimmed, languageCode)
        val definition = define(trimmed, languageCode)
        val descriptionId = if (definition != null && definition.primary.isNotBlank()) {
            translateText(definition.primary, languageCode)
        } else {
            ""
        }
        val example = fetchExample(trimmed, languageCode).ifBlank { definition?.example.orEmpty() }
        return LookupResult(
            translation = translation,
            description = definition?.description.orEmpty(),
            descriptionId = descriptionId,
            example = example,
            synonyms = definition?.synonyms ?: emptyList(),
            definitionFound = definition != null
        )
    }

    private data class Definition(
        val description: String,
        val primary: String,
        val example: String,
        val synonyms: List<String>
    )

    private suspend fun fetchExample(word: String, languageCode: String): String {
        return try {
            val from = iso3(languageCode)
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "https://tatoeba.org/en/api_v0/search?from=$from&query=$encoded&sort=relevance"
            api.examples(url).results.orEmpty()
                .firstOrNull { !it.text.isNullOrBlank() }
                ?.text?.trim()
                .orEmpty()
        } catch (t: Throwable) {
            ""
        }
    }

    private fun iso3(languageCode: String): String = when (languageCode) {
        "en" -> "eng"
        "es" -> "spa"
        "fr" -> "fra"
        "de" -> "deu"
        "it" -> "ita"
        "ja" -> "jpn"
        "ko" -> "kor"
        else -> "eng"
    }

    private suspend fun translate(word: String, languageCode: String): String {
        return try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$languageCode|id"
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

    private suspend fun translateText(text: String, languageCode: String): String {
        return try {
            val capped = if (text.length > 480) text.substring(0, 480) else text
            val encoded = URLEncoder.encode(capped, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$languageCode|id"
            api.translate(url).responseData?.translatedText?.trim().orEmpty()
        } catch (t: Throwable) {
            ""
        }
    }

    private suspend fun define(word: String, languageCode: String): Definition? {
        return try {
            val meanings = api.define(languageCode, word).firstOrNull()?.meanings.orEmpty()
            val primary = meanings
                .firstNotNullOfOrNull { meaning ->
                    meaning.definitions?.firstOrNull { !it.definition.isNullOrBlank() }?.definition?.trim()
                }
                .orEmpty()
            val parts = meanings.mapNotNull { meaning ->
                val definition = meaning.definitions?.firstOrNull { !it.definition.isNullOrBlank() }?.definition?.trim()
                if (definition.isNullOrBlank()) {
                    null
                } else {
                    val pos = meaning.partOfSpeech?.takeIf { it.isNotBlank() }
                    if (pos != null) "($pos) $definition" else definition
                }
            }
            val description = parts.take(2).joinToString("\n")
            if (description.isBlank()) {
                null
            } else {
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
                    .take(8)
                Definition(description = description, primary = primary, example = example, synonyms = synonyms)
            }
        } catch (t: Throwable) {
            null
        }
    }
}
