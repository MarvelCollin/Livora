package com.example.livora.data.dictionary

import com.example.livora.data.model.LookupResult
import java.net.URLEncoder

class DictionaryLookupRepository {

    private val api: LookupApi = LookupClient.api

    suspend fun lookup(word: String, languageCode: String): LookupResult {
        val trimmed = word.trim()
        val translation = translate(trimmed, languageCode)
        val description = define(trimmed, languageCode)
        return LookupResult(
            translation = translation,
            description = description.orEmpty(),
            definitionFound = description != null
        )
    }

    private suspend fun translate(word: String, languageCode: String): String {
        return try {
            val encoded = URLEncoder.encode(word, "UTF-8")
            val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=$languageCode|id"
            val result = api.translate(url).responseData?.translatedText?.trim().orEmpty()
            if (result.equals(word, ignoreCase = true)) "" else result
        } catch (t: Throwable) {
            ""
        }
    }

    private suspend fun define(word: String, languageCode: String): String? {
        return try {
            val entries = api.define(languageCode, word)
            val meanings = entries.firstOrNull()?.meanings.orEmpty()
            val parts = meanings.mapNotNull { meaning ->
                val definition = meaning.definitions?.firstOrNull { !it.definition.isNullOrBlank() }?.definition?.trim()
                if (definition.isNullOrBlank()) {
                    null
                } else {
                    val pos = meaning.partOfSpeech?.takeIf { it.isNotBlank() }
                    if (pos != null) "($pos) $definition" else definition
                }
            }
            parts.take(2).joinToString("\n").ifBlank { null }
        } catch (t: Throwable) {
            null
        }
    }
}
