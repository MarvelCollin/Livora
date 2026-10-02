package com.example.livora.data.dictionary

import com.example.livora.data.model.DictionaryEntry
import com.example.livora.data.model.EntryCategory
import com.example.livora.data.model.QuizMode
import com.example.livora.data.model.QuizQuestion
import com.example.livora.data.model.SynonymInput
import kotlin.random.Random

object QuizBuilder {

    fun vocabularyPool(entries: List<DictionaryEntry>): List<DictionaryEntry> =
        entries.filter { it.category == EntryCategory.Vocabulary && it.translation.isNotBlank() }

    fun writingPool(entries: List<DictionaryEntry>): List<DictionaryEntry> =
        entries.filter { it.category == EntryCategory.Writing && it.synonyms.isNotEmpty() }

    fun build(
        entries: List<DictionaryEntry>,
        mode: QuizMode,
        input: SynonymInput,
        random: Random = Random.Default
    ): List<QuizQuestion> = when (mode) {
        QuizMode.Writing -> writingQuestions(entries, input, random)
        QuizMode.All, QuizMode.Hardest -> vocabularyQuestions(entries, mode, input, random)
        QuizMode.Sentence, QuizMode.Paragraph -> emptyList()
    }

    private fun writingQuestions(
        entries: List<DictionaryEntry>,
        input: SynonymInput,
        random: Random
    ): List<QuizQuestion> {
        val pool = writingPool(entries)
        return pool.shuffled(random).mapNotNull { entry ->
            val round = roundFor(entry, pool, input, random) ?: return@mapNotNull null
            QuizQuestion(
                entry = entry,
                options = emptyList(),
                correctIndex = -1,
                synonymRound = round,
                translationStep = false
            )
        }
    }

    private fun vocabularyQuestions(
        entries: List<DictionaryEntry>,
        mode: QuizMode,
        input: SynonymInput,
        random: Random
    ): List<QuizQuestion> {
        val pool = vocabularyPool(entries)
        val selected = when (mode) {
            QuizMode.Hardest -> {
                val withMistakes = pool.filter { it.wrongCount > 0 }
                val ordered = if (withMistakes.isNotEmpty()) withMistakes else pool
                ordered.sortedWith(
                    compareByDescending<DictionaryEntry> { it.wrongCount }
                        .thenByDescending { it.attempts }
                )
            }
            else -> pool.shuffled(random)
        }
        return selected.mapNotNull { entry ->
            val distractors = pool
                .filter { it.id != entry.id && !it.translation.equals(entry.translation, ignoreCase = true) }
                .map { it.translation }
                .distinct()
                .shuffled(random)
                .take(3)
            val options = (distractors + entry.translation).distinct().shuffled(random)
            if (options.size < 2) {
                null
            } else {
                QuizQuestion(
                    entry = entry,
                    options = options,
                    correctIndex = options.indexOf(entry.translation),
                    synonymRound = roundFor(entry, pool, input, random)
                )
            }
        }
    }

    private fun roundFor(
        entry: DictionaryEntry,
        pool: List<DictionaryEntry>,
        input: SynonymInput,
        random: Random
    ) = when (input) {
        SynonymInput.Write -> SynonymRounds.buildWrite(entry)
        SynonymInput.Click -> SynonymRounds.build(entry, pool, random)
    }
}
