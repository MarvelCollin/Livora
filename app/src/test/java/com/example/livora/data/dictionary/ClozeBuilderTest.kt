package com.example.livora.data.dictionary

import com.example.livora.data.model.ClozeLevel
import com.example.livora.data.model.ClozeToken
import com.example.livora.data.model.DictionaryEntry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClozeBuilderTest {

    @Test
    fun parseSplitsWordsAndBlanks() {
        val tokens = ClozeBuilder.parse("Each student sleeps, [[therefore]] they fail.")
        assertEquals(
            listOf("Each", "student", "sleeps,"),
            tokens.takeWhile { it is ClozeToken.Word }.map { (it as ClozeToken.Word).value }
        )
        val slot = tokens.filterIsInstance<ClozeToken.Slot>().single()
        assertEquals("therefore", slot.answer)
        assertEquals(setOf("therefore"), slot.accepted)
        assertFalse(slot.capitalize)
        assertEquals(listOf("they", "fail."), tokens.dropWhile { it !is ClozeToken.Slot }.drop(1).map { (it as ClozeToken.Word).value })
    }

    @Test
    fun punctuationAfterABlankStaysGluedToIt() {
        val slot = ClozeBuilder.parse("Cars are fast. [[however]], they pollute.")
            .filterIsInstance<ClozeToken.Slot>().single()
        assertEquals(",", slot.trailing)
        assertTrue(slot.capitalize)
    }

    @Test
    fun aBlankAtTheStartIsCapitalised() {
        val slot = ClozeBuilder.parse("[[although]] fuel is costly, people drive.")
            .filterIsInstance<ClozeToken.Slot>().single()
        assertTrue(slot.capitalize)
        assertEquals("Although", ClozeBuilder.display(slot.answer, slot.capitalize))
    }

    @Test
    fun alternativesAreAcceptedButTheFirstIsTheAnswer() {
        val slot = ClozeBuilder.parse("Intro. [[firstly|for example]], tuition helps.")
            .filterIsInstance<ClozeToken.Slot>().single()
        assertEquals("firstly", slot.answer)
        assertEquals(setOf("firstly", "for example"), slot.accepted)
    }

    @Test
    fun blanksAreNumberedInOrder() {
        val slots = ClozeBuilder.parse("A [[x]] b [[y]] c [[z]].").filterIsInstance<ClozeToken.Slot>()
        assertEquals(listOf(0, 1, 2), slots.map { it.index })
    }

    @Test
    fun everyBankItemIsWellFormed() {
        ClozeBank.items.forEach { item ->
            val slots = ClozeBuilder.parse(item.text).filterIsInstance<ClozeToken.Slot>()
            assertTrue("no blank in: ${item.text}", slots.isNotEmpty())
            assertFalse("unparsed bracket in: ${item.text}", ClozeBuilder.fill(
                ClozeBuilder.build(item.level, items = listOf(item), random = Random(1)).first()
            ).contains("["))
            val accepted = slots.flatMap { it.accepted }.toSet()
            assertTrue("distractor clashes in: ${item.text}", item.distractors.none { it.lowercase() in accepted })
            assertEquals("duplicate distractors in: ${item.text}", item.distractors.size, item.distractors.distinct().size)
            assertTrue("too few distractors in: ${item.text}", item.distractors.size >= ClozeBuilder.DISTRACTORS_PER_QUESTION)
            assertFalse("semicolon or dash in: ${item.text}", item.text.contains(';') || item.text.contains('—'))
        }
    }

    @Test
    fun sentencesHaveAtLeastOneBlankAndParagraphsHaveSeveral() {
        ClozeBank.items.forEach { item ->
            val blanks = ClozeBuilder.parse(item.text).count { it is ClozeToken.Slot }
            if (item.level == ClozeLevel.Paragraph) assertTrue(item.text, blanks >= 3) else assertTrue(item.text, blanks >= 1)
        }
    }

    @Test
    fun theUserExampleSentenceIsInTheBank() {
        val item = ClozeBank.items.first { it.text.contains("sleeping during the class") }
        val slot = ClozeBuilder.parse(item.text).filterIsInstance<ClozeToken.Slot>().single()
        assertEquals("therefore", slot.answer)
    }

    @Test
    fun builtQuestionsHoldEveryAnswerPlusDistractors() {
        val questions = ClozeBuilder.build(ClozeLevel.Paragraph, random = Random(7))
        assertEquals(ClozeBuilder.PARAGRAPHS_PER_QUIZ, questions.size)
        questions.forEach { question ->
            val answers = question.slots.map { it.answer }
            assertEquals(answers.size + ClozeBuilder.DISTRACTORS_PER_QUESTION, question.bank.size)
            assertTrue(question.bank.containsAll(answers))
        }
    }

    @Test
    fun sentenceQuizzesAreCappedAndShuffled() {
        val first = ClozeBuilder.build(ClozeLevel.Sentence, random = Random(1))
        val second = ClozeBuilder.build(ClozeLevel.Sentence, random = Random(2))
        assertEquals(ClozeBuilder.SENTENCES_PER_QUIZ, first.size)
        assertTrue(first.map { ClozeBuilder.fill(it) } != second.map { ClozeBuilder.fill(it) })
    }

    @Test
    fun meaningsComeFromTheDictionaryByHeadword() {
        val entries = listOf(
            DictionaryEntry(id = "1", word = "therefore", translation = "oleh karena itu"),
            DictionaryEntry(id = "2", word = "however", translation = "namun")
        )
        val item = ClozeItem(ClozeLevel.Sentence, "Cold, [[therefore]] stay in.", listOf("whereas", "despite", "because"))
        val question = ClozeBuilder.build(ClozeLevel.Sentence, entries, Random(1), listOf(item)).single()
        assertEquals(mapOf("therefore" to "oleh karena itu"), question.meanings)
    }

    @Test
    fun fillRebuildsTheCompletedText() {
        val item = ClozeItem(ClozeLevel.Sentence, "Cars are fast. [[however]], they pollute.", listOf("a", "b", "c"))
        val question = ClozeBuilder.build(ClozeLevel.Sentence, items = listOf(item), random = Random(1)).single()
        assertEquals("Cars are fast. However, they pollute.", ClozeBuilder.fill(question))
    }
}
