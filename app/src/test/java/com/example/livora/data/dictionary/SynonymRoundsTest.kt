package com.example.livora.data.dictionary

import com.example.livora.data.model.DictionaryEntry
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SynonymRoundsTest {

    private fun entry(word: String, vararg synonyms: String) = DictionaryEntry(
        id = word,
        word = word,
        translation = "t-$word",
        synonyms = synonyms.toList()
    )

    private val therefore = entry("therefore", "thus", "hence", "consequently")
    private val pool = listOf(
        therefore,
        entry("abolish", "scrap", "end", "eradicate"),
        entry("abundant", "plentiful", "ample", "copious"),
        entry("acute", "severe", "intense", "serious"),
        entry("adapt", "adjust", "cope", "change")
    )

    @Test
    fun roundHasThreeCorrectAndSixOptions() {
        val round = SynonymRounds.build(therefore, pool, Random(1))!!
        assertEquals(setOf("thus", "hence", "consequently"), round.correct)
        assertEquals(6, round.options.size)
        assertTrue(round.options.containsAll(round.correct))
        assertEquals(round.options.size, round.options.distinct().size)
    }

    @Test
    fun distractorsNeverIncludeTheWordItselfOrItsSynonyms() {
        repeat(40) { seed ->
            val round = SynonymRounds.build(therefore, pool, Random(seed))!!
            val distractors = round.options - round.correct
            assertEquals(3, distractors.size)
            assertFalse("therefore" in distractors)
        }
    }

    @Test
    fun synonymsOfRelatedEntriesAreNotUsedAsDistractors() {
        val consequently = entry("consequently", "therefore", "as a result", "thus")
        val entries = pool + consequently
        repeat(40) { seed ->
            val round = SynonymRounds.build(therefore, entries, Random(seed))!!
            val distractors = round.options - round.correct
            assertFalse("as a result" in distractors)
            assertFalse("consequently" in distractors)
        }
    }

    @Test
    fun optionsAreShuffledAcrossSeeds() {
        val orders = (0 until 30).map { SynonymRounds.build(therefore, pool, Random(it))!!.options }.toSet()
        assertTrue(orders.size > 5)
    }

    @Test
    fun entryWithFewSynonymsStillGetsAFullRound() {
        val about = entry("about", "approximately", "roughly")
        val round = SynonymRounds.build(about, pool + about, Random(3))!!
        assertEquals(setOf("approximately", "roughly"), round.correct)
        assertEquals(6, round.options.size)
    }

    @Test
    fun entryWithMoreThanThreeSynonymsAsksForThree() {
        val many = entry("big", "large", "huge", "vast", "immense", "great")
        val round = SynonymRounds.build(many, pool + many, Random(5))!!
        assertEquals(3, round.correct.size)
        assertTrue(many.synonyms.containsAll(round.correct))
        assertTrue((round.options - round.correct).none { it in many.synonyms })
    }

    @Test
    fun noSynonymsMeansNoRound() {
        assertNull(SynonymRounds.build(entry("lonely"), pool, Random(1)))
    }

    @Test
    fun tooFewDistractorsMeansNoRound() {
        val a = entry("a", "x", "y", "z")
        assertNull(SynonymRounds.build(a, listOf(a), Random(1)))
    }

    @Test
    fun duplicatesAndBlanksAreIgnored() {
        val messy = entry("messy", "Thus", "thus", " ", "hence")
        val round = SynonymRounds.build(messy, pool + messy, Random(2))
        assertNotNull(round)
        assertEquals(2, round!!.correct.size)
    }

    @Test
    fun bandRankingPrefersWordsFromTheIeltsDictionary() {
        val lexicon = IeltsSynonyms.lexicon(pool)
        val picked = IeltsSynonyms.select(listOf("so", "thus", "hence", "therefore", "hereby"), lexicon)
        assertEquals(listOf("thus", "hence", "therefore"), picked)
    }

    @Test
    fun bandRankingFallsBackToTheFirstCandidatesAndCapsAtThree() {
        val picked = IeltsSynonyms.select(listOf("a", "b", "c", "d", "e"), emptySet())
        assertEquals(listOf("a", "b", "c"), picked)
    }

    @Test
    fun bandRankingDropsDuplicatesAndBlanks() {
        val picked = IeltsSynonyms.select(listOf("Thus", "thus", " ", "hence"), setOf("hence"))
        assertEquals(listOf("hence", "Thus"), picked)
    }
}
