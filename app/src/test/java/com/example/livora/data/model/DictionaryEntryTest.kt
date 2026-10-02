package com.example.livora.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryEntryTest {

    private val entry = DictionaryEntry(
        id = "1",
        word = "therefore",
        translation = "oleh karena itu",
        synonyms = listOf("thus", "hence", "consequently"),
        synonymTranslations = mapOf("thus" to "dengan demikian", "hence" to "maka dari itu", "blank" to " ")
    )

    @Test
    fun translationOfIgnoresCaseAndSpacing() {
        assertEquals("dengan demikian", entry.translationOf("  Thus "))
    }

    @Test
    fun translationOfIsNullWhenMissingOrBlank() {
        assertNull(entry.translationOf("consequently"))
        assertNull(entry.translationOf("blank"))
    }

    @Test
    fun entriesWithoutTranslationsStillWork() {
        val plain = DictionaryEntry(id = "2", word = "abolish", translation = "menghapuskan")
        assertNull(plain.translationOf("scrap"))
        assertEquals(EntryCategory.Vocabulary, plain.category)
    }

    @Test
    fun categoryParsingFallsBackToVocabulary() {
        assertEquals(EntryCategory.Writing, EntryCategory.fromDb(" Writing "))
        assertEquals(EntryCategory.Vocabulary, EntryCategory.fromDb(null))
        assertEquals(EntryCategory.Vocabulary, EntryCategory.fromDb("unknown"))
    }
}
