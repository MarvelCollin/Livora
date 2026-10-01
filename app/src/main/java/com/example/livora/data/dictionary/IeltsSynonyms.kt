package com.example.livora.data.dictionary

import com.example.livora.data.model.DictionaryEntry

object IeltsSynonyms {

    const val LIMIT = 3

    fun lexicon(entries: List<DictionaryEntry>): Set<String> =
        entries.flatMap { listOf(it.word) + it.synonyms }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

    fun select(candidates: List<String>, lexicon: Set<String>, limit: Int = LIMIT): List<String> {
        val cleaned = candidates.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val (known, other) = cleaned.partition { it.lowercase() in lexicon }
        return (known + other).take(limit)
    }
}
