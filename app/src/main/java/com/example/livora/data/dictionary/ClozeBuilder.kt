package com.example.livora.data.dictionary

import com.example.livora.data.model.ClozeLevel
import com.example.livora.data.model.ClozeQuestion
import com.example.livora.data.model.ClozeToken
import com.example.livora.data.model.DictionaryEntry
import kotlin.random.Random

object ClozeBuilder {

    const val SENTENCES_PER_QUIZ = 10
    const val PARAGRAPHS_PER_QUIZ = 3
    const val DISTRACTORS_PER_QUESTION = 3

    private val BLANK = Regex("""\[\[(.+?)]]""")
    private val WHITESPACE = Regex("\\s+")

    fun count(level: ClozeLevel, items: List<ClozeItem> = ClozeBank.items): Int =
        items.count { it.level == level }

    fun parse(text: String): List<ClozeToken> {
        val tokens = mutableListOf<ClozeToken>()
        var cursor = 0
        var blank = 0

        fun addWords(chunk: String) {
            var rest = chunk
            val last = tokens.lastOrNull()
            if (last is ClozeToken.Slot && rest.isNotEmpty() && !rest[0].isWhitespace()) {
                val run = rest.takeWhile { !it.isWhitespace() }
                tokens[tokens.lastIndex] = last.copy(trailing = run)
                rest = rest.drop(run.length)
            }
            rest.split(WHITESPACE).filter { it.isNotEmpty() }.forEach { tokens += ClozeToken.Word(it) }
        }

        BLANK.findAll(text).forEach { match ->
            addWords(text.substring(cursor, match.range.first))
            val options = match.groupValues[1].split("|").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            tokens += ClozeToken.Slot(
                index = blank++,
                answer = options.first(),
                accepted = options.toSet(),
                capitalize = startsSentence(tokens)
            )
            cursor = match.range.last + 1
        }
        addWords(text.substring(cursor))
        return tokens
    }

    fun build(
        level: ClozeLevel,
        entries: List<DictionaryEntry> = emptyList(),
        random: Random = Random.Default,
        items: List<ClozeItem> = ClozeBank.items
    ): List<ClozeQuestion> {
        val limit = if (level == ClozeLevel.Sentence) SENTENCES_PER_QUIZ else PARAGRAPHS_PER_QUIZ
        return items.filter { it.level == level }
            .shuffled(random)
            .take(limit)
            .map { item -> question(item, entries, random) }
    }

    fun fill(question: ClozeQuestion): String =
        question.tokens.joinToString(" ") { token ->
            when (token) {
                is ClozeToken.Word -> token.value
                is ClozeToken.Slot -> display(token.answer, token.capitalize) + token.trailing
            }
        }

    fun display(word: String, capitalize: Boolean): String =
        if (capitalize) word.replaceFirstChar { it.uppercase() } else word

    private fun question(item: ClozeItem, entries: List<DictionaryEntry>, random: Random): ClozeQuestion {
        val tokens = parse(item.text)
        val slots = tokens.filterIsInstance<ClozeToken.Slot>()
        val answers = slots.map { it.answer }
        val blocked = slots.flatMap { it.accepted }.toSet()
        val distractors = item.distractors
            .map { it.trim().lowercase() }
            .filter { it !in blocked }
            .distinct()
            .shuffled(random)
            .take(DISTRACTORS_PER_QUESTION)
        val meanings = answers.distinct().mapNotNull { answer ->
            val meaning = entries.firstOrNull { it.word.equals(answer, ignoreCase = true) }
                ?.translation?.takeIf { it.isNotBlank() }
            meaning?.let { answer to it }
        }.toMap()
        return ClozeQuestion(
            level = item.level,
            tokens = tokens,
            bank = (answers + distractors).shuffled(random),
            meanings = meanings
        )
    }

    private fun startsSentence(tokens: List<ClozeToken>): Boolean {
        val last = tokens.lastOrNull() ?: return true
        val text = when (last) {
            is ClozeToken.Word -> last.value
            is ClozeToken.Slot -> last.trailing
        }
        return text.isNotEmpty() && text.last() in SENTENCE_END
    }

    private val SENTENCE_END = charArrayOf('.', '!', '?')
}
