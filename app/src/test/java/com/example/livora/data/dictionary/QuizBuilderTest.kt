package com.example.livora.data.dictionary

import com.example.livora.data.model.DictionaryEntry
import com.example.livora.data.model.EntryCategory
import com.example.livora.data.model.QuizMode
import com.example.livora.data.model.SynonymInput
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizBuilderTest {

    private fun entry(
        word: String,
        translation: String,
        category: EntryCategory,
        vararg synonyms: String,
        wrong: Int = 0
    ) = DictionaryEntry(
        id = word,
        word = word,
        translation = translation,
        synonyms = synonyms.toList(),
        category = category,
        wrongCount = wrong
    )

    private val vocab = listOf(
        entry("abolish", "menghapuskan", EntryCategory.Vocabulary, "scrap", "end", "eradicate"),
        entry("abundant", "melimpah", EntryCategory.Vocabulary, "plentiful", "ample", "copious", wrong = 2),
        entry("acute", "akut", EntryCategory.Vocabulary, "severe", "intense", "serious"),
        entry("adapt", "beradaptasi", EntryCategory.Vocabulary, "adjust", "cope", "change", wrong = 1)
    )

    private val writing = listOf(
        entry("therefore", "oleh karena itu", EntryCategory.Writing, "thus", "hence", "consequently"),
        entry("about", "sekitar", EntryCategory.Writing, "approximately", "roughly", "in the region of"),
        entry("however", "namun", EntryCategory.Writing, "nevertheless", "nonetheless", "even so"),
        entry("big", "besar", EntryCategory.Writing, "substantial", "considerable", "significant")
    )

    private val all = vocab + writing

    @Test
    fun writingModeOnlyAsksWritingWordsWithoutATranslationStep() {
        val quiz = QuizBuilder.build(all, QuizMode.Writing, SynonymInput.Click, Random(1))
        assertEquals(writing.map { it.word }.toSet(), quiz.map { it.entry.word }.toSet())
        assertTrue(quiz.all { !it.translationStep && it.options.isEmpty() && it.synonymRound != null })
    }

    @Test
    fun vocabularyModesNeverIncludeWritingWords() {
        listOf(QuizMode.All, QuizMode.Hardest).forEach { mode ->
            val quiz = QuizBuilder.build(all, mode, SynonymInput.Click, Random(2))
            assertTrue(quiz.all { it.entry.category == EntryCategory.Vocabulary })
            assertTrue(quiz.all { it.translationStep })
        }
    }

    @Test
    fun translationOptionsComeFromTheVocabularyPoolOnly() {
        val writingTranslations = writing.map { it.translation }.toSet()
        val quiz = QuizBuilder.build(all, QuizMode.All, SynonymInput.Click, Random(3))
        assertTrue(quiz.flatMap { it.options }.none { it in writingTranslations })
    }

    @Test
    fun hardestPutsTheMostMissedFirst() {
        val quiz = QuizBuilder.build(all, QuizMode.Hardest, SynonymInput.Click, Random(4))
        assertEquals(listOf("abundant", "adapt"), quiz.map { it.entry.word })
    }

    @Test
    fun clickInputBuildsOptionRoundsAndWriteInputBuildsTypedRounds() {
        val click = QuizBuilder.build(all, QuizMode.Writing, SynonymInput.Click, Random(5))
        assertTrue(click.all { it.synonymRound!!.options.isNotEmpty() && !it.synonymRound!!.typed })

        val write = QuizBuilder.build(all, QuizMode.Writing, SynonymInput.Write, Random(5))
        assertTrue(write.all { it.synonymRound!!.options.isEmpty() && it.synonymRound!!.typed })
    }

    @Test
    fun writingQuestionsStayInsideTheWritingPoolForDistractors() {
        val writingSynonyms = writing.flatMap { it.synonyms }.toSet()
        repeat(20) { seed ->
            val quiz = QuizBuilder.build(all, QuizMode.Writing, SynonymInput.Click, Random(seed))
            quiz.forEach { question ->
                assertTrue(question.synonymRound!!.options.all { it in writingSynonyms })
            }
        }
    }

    @Test
    fun poolsAreDividedByCategory() {
        assertEquals(4, QuizBuilder.vocabularyPool(all).size)
        assertEquals(4, QuizBuilder.writingPool(all).size)
    }

    @Test
    fun typedRoundAsksForUpToThreeAndAcceptsEveryListedSynonym() {
        val five = entry("big", "besar", EntryCategory.Writing, "large", "huge", "vast", "immense", "great")
        val round = SynonymRounds.buildWrite(five)!!
        assertEquals(3, round.needed)
        assertEquals(5, round.correct.size)
        assertTrue(round.typed)
    }

    @Test
    fun typedRoundNeedsAtLeastOneSynonym() {
        assertNull(SynonymRounds.buildWrite(entry("lonely", "sepi", EntryCategory.Writing)))
    }

    @Test
    fun checkAcceptsTypedSynonymsIgnoringCaseAndSpacing() {
        val round = SynonymRounds.buildWrite(writing[1])!!
        val accepted = SynonymRounds.check("  In The   Region of ", round, emptyList())
        assertEquals(WriteResult.Accepted("in the region of"), accepted)
    }

    @Test
    fun checkRejectsUnknownBlankAndRepeatedAnswers() {
        val round = SynonymRounds.buildWrite(writing[0])!!
        assertEquals(WriteResult.Wrong, SynonymRounds.check("so", round, emptyList()))
        assertEquals(WriteResult.Blank, SynonymRounds.check("   ", round, emptyList()))
        assertEquals(WriteResult.Duplicate, SynonymRounds.check("THUS", round, listOf("thus")))
        assertFalse(SynonymRounds.check("hence", round, listOf("thus")) is WriteResult.Duplicate)
    }
}
