package com.example.livora.data.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanerRulesTest {

    private fun file(
        id: Long,
        name: String = "IMG_$id.jpg",
        path: String = "DCIM/Camera/",
        size: Long = 1_000_000,
        video: Boolean = false,
        date: Long = id * 1000
    ) = CleanerFile(id, video, name, size, date, 100, 100, path, "", 0)

    @Test
    fun screenshotsAreFoundByFolderOrName() {
        assertTrue(CleanerRules.isScreenshot(file(1, path = "Pictures/Screenshots/")))
        assertTrue(CleanerRules.isScreenshot(file(2, name = "Screenshot_20260930.png")))
        assertTrue(CleanerRules.isScreenshot(file(3, name = "Screen Recording 2026.mp4", video = true)))
        assertFalse(CleanerRules.isScreenshot(file(4)))
    }

    @Test
    fun messagingMediaIsFoundBySegmentNotBySubstring() {
        assertTrue(CleanerRules.isMessaging(file(1, path = "Pictures/WhatsApp Images/")))
        assertTrue(CleanerRules.isMessaging(file(2, path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video/")))
        assertTrue(CleanerRules.isMessaging(file(3, path = "Pictures/Telegram/")))
        assertTrue(CleanerRules.isMessaging(file(4, path = "Pictures/LINE/")))
        assertFalse(CleanerRules.isMessaging(file(5, path = "Pictures/Timeline/")))
        assertFalse(CleanerRules.isMessaging(file(6, path = "DCIM/Camera/")))
    }

    @Test
    fun largeMeansFiftyMegabytesOrMore() {
        assertTrue(CleanerRules.isLarge(file(1, size = CleanerRules.LARGE_BYTES)))
        assertFalse(CleanerRules.isLarge(file(2, size = CleanerRules.LARGE_BYTES - 1)))
    }

    @Test
    fun candidatesShareASizeAndKind() {
        val a = file(1, size = 9000)
        val b = file(2, size = 9000)
        val video = file(3, size = 9000, video = true)
        val other = file(4, size = 8000)
        val tiny = file(5, size = 100)
        val tiny2 = file(6, size = 100)
        val groups = CleanerRules.duplicateCandidates(listOf(a, b, video, other, tiny, tiny2))
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().map { it.id }.toSet())
    }

    @Test
    fun identicalHashesBecomeOneOriginalAndItsCopies() {
        val old = file(1, date = 100, size = 9000)
        val newer = file(2, date = 200, size = 9000)
        val newest = file(3, date = 300, size = 9000)
        val different = file(4, date = 50, size = 9000)
        val hashes = mapOf(1L to "a", 2L to "a", 3L to "a", 4L to "b")
        val result = CleanerRules.groupDuplicates(listOf(listOf(old, newer, newest, different))) { hashes[it.id] }
        assertEquals(1, result.groups.size)
        assertSame(old, result.groups.single().original)
        assertEquals(listOf(2L, 3L), result.copies.map { it.id })
        assertEquals(18000L, result.bytes)
    }

    @Test
    fun filesThatCannotBeReadAreNeverCalledDuplicates() {
        val a = file(1, size = 9000)
        val b = file(2, size = 9000)
        val result = CleanerRules.groupDuplicates(listOf(listOf(a, b))) { null }
        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun theBiggestSavingsComeFirst() {
        val small = listOf(file(1, size = 5000), file(2, size = 5000))
        val big = listOf(file(3, size = 90000), file(4, size = 90000))
        val result = CleanerRules.groupDuplicates(listOf(small, big)) { "h" + it.size }
        assertEquals(90000L, result.groups.first().copies.first().size)
    }

    @Test
    fun sourcesFilterAsNamed() {
        val shot = file(1, name = "Screenshot_1.png")
        val chat = file(2, path = "Pictures/WhatsApp Images/")
        assertTrue(CleanerRules.matches(CleanerSource.Screenshots, shot))
        assertFalse(CleanerRules.matches(CleanerSource.Screenshots, chat))
        assertTrue(CleanerRules.matches(CleanerSource.Messaging, chat))
        assertTrue(CleanerRules.matches(CleanerSource.All, shot))
        assertEquals(CleanerSource.Large, CleanerSource.of("large"))
        assertEquals(CleanerSource.All, CleanerSource.of("nonsense"))
    }

    @Test
    fun keysSeparateImagesFromVideosWithTheSameId() {
        assertEquals("i:7", file(7).key)
        assertEquals("v:7", file(7, video = true).key)
    }

    @Test
    fun theFolderNameComesFromTheLastPathSegment() {
        assertEquals("Camera", file(1, path = "DCIM/Camera/").folder)
    }
}
