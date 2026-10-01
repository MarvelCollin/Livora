package com.example.livora.data.dictionary

import com.example.livora.data.model.DictionaryEntry
import com.example.livora.data.model.SynonymRound
import kotlin.random.Random

sealed interface WriteResult {
    data class Accepted(val word: String) : WriteResult
    data object Duplicate : WriteResult
    data object Wrong : WriteResult
    data object Blank : WriteResult
}

object SynonymRounds {

    const val MAX_CORRECT = 3
    const val OPTION_COUNT = 6
    const val MAX_MISSES = 3
    private const val MIN_DISTRACTORS = 2

    fun build(
        entry: DictionaryEntry,
        entries: List<DictionaryEntry>,
        random: Random = Random.Default
    ): SynonymRound? {
        val own = clean(entry.synonyms)
        if (own.isEmpty()) return null

        val blocked = relatedTerms(entry, own, entries)
        val distractorPool = clean(entries.flatMap { it.synonyms })
            .filter { it.lowercase() !in blocked }

        val correct = own.shuffled(random).take(MAX_CORRECT)
        val distractors = distractorPool.shuffled(random).take(OPTION_COUNT - correct.size)
        if (distractors.size < MIN_DISTRACTORS) return null

        return SynonymRound(
            options = (correct + distractors).shuffled(random),
            correct = correct.toSet()
        )
    }

    fun buildWrite(entry: DictionaryEntry): SynonymRound? {
        val own = clean(entry.synonyms)
        if (own.isEmpty()) return null
        return SynonymRound(
            options = emptyList(),
            correct = own.toSet(),
            needed = minOf(own.size, MAX_CORRECT),
            typed = true
        )
    }

    fun check(input: String, round: SynonymRound, found: List<String>): WriteResult {
        val typed = normalize(input)
        if (typed.isEmpty()) return WriteResult.Blank
        val match = round.correct.firstOrNull { normalize(it) == typed } ?: return WriteResult.Wrong
        if (found.any { normalize(it) == typed }) return WriteResult.Duplicate
        return WriteResult.Accepted(match)
    }

    private fun relatedTerms(
        entry: DictionaryEntry,
        own: List<String>,
        entries: List<DictionaryEntry>
    ): Set<String> {
        val word = entry.word.trim().lowercase()
        val ownLower = own.map { it.lowercase() }.toSet()
        val related = HashSet<String>()
        related += word
        related += ownLower
        entries.forEach { other ->
            val otherWord = other.word.trim().lowercase()
            val otherSynonyms = other.synonyms.map { it.trim().lowercase() }
            if (otherWord in ownLower || word in otherSynonyms) {
                related += otherWord
                related += otherSynonyms
            }
        }
        return related
    }

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(WHITESPACE, " ")

    private fun clean(values: List<String>): List<String> =
        values.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    private val WHITESPACE = Regex("\\s+")
}
