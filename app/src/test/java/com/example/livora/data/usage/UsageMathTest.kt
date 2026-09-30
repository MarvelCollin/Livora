package com.example.livora.data.usage

import com.example.livora.ui.usage.formatDuration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageMathTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 9, 30)
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun at(hours: Int, minutes: Int = 0) = start + hours * hour + minutes * minute

    private fun resumed(pkg: String, time: Long, cls: String = "Main") = RawEvent(pkg, cls, UsageMath.RESUMED, time)
    private fun paused(pkg: String, time: Long, cls: String = "Main") = RawEvent(pkg, cls, UsageMath.PAUSED, time)

    private fun dayTotal(slices: UsageSlices, pkg: String, date: LocalDate = day): Long? = slices.days[date.toEpochDay()]?.get(pkg)

    @Test
    fun aSimpleSessionCountsFromResumeToPause() {
        val slices = UsageMath.compute(listOf(resumed("a", at(9)), paused("a", at(9, 30))), at(12), zone, emptySet())
        assertEquals(30 * minute, dayTotal(slices, "a"))
    }

    @Test
    fun sessionsAreSplitAcrossHours() {
        val slices = UsageMath.compute(listOf(resumed("a", at(9, 45)), paused("a", at(10, 15))), at(12), zone, emptySet())
        assertEquals(15 * minute, slices.hours[at(9) / hour])
        assertEquals(15 * minute, slices.hours[at(10) / hour])
    }

    @Test
    fun sessionsAreSplitAcrossMidnight() {
        val before = day.minusDays(1)
        val slices = UsageMath.compute(
            listOf(resumed("a", at(0) - 20 * minute), paused("a", at(0) + 40 * minute)),
            at(3), zone, emptySet()
        )
        assertEquals(20 * minute, dayTotal(slices, "a", before))
        assertEquals(40 * minute, dayTotal(slices, "a"))
    }

    @Test
    fun screenOffClosesAnOpenSession() {
        val slices = UsageMath.compute(
            listOf(resumed("a", at(9)), RawEvent("", "", UsageMath.SCREEN_OFF, at(9, 10))),
            at(12), zone, emptySet()
        )
        assertEquals(10 * minute, dayTotal(slices, "a"))
    }

    @Test
    fun anAppStillOpenCountsUntilNow() {
        val slices = UsageMath.compute(listOf(resumed("a", at(11))), at(11, 20), zone, emptySet())
        assertEquals(20 * minute, dayTotal(slices, "a"))
    }

    @Test
    fun switchingBetweenAppsKeepsThemSeparate() {
        val slices = UsageMath.compute(
            listOf(
                resumed("a", at(9)), paused("a", at(9, 10)),
                resumed("b", at(9, 10)), paused("b", at(9, 25)),
                resumed("a", at(9, 25)), paused("a", at(9, 30))
            ),
            at(12), zone, emptySet()
        )
        assertEquals(15 * minute, dayTotal(slices, "a"))
        assertEquals(15 * minute, dayTotal(slices, "b"))
    }

    @Test
    fun theHomeLauncherIsIgnored() {
        val slices = UsageMath.compute(
            listOf(resumed("home", at(9)), paused("home", at(9, 30))),
            at(12), zone, setOf("home")
        )
        assertNull(dayTotal(slices, "home"))
    }

    @Test
    fun aDuplicateResumeDoesNotDoubleCount() {
        val slices = UsageMath.compute(
            listOf(resumed("a", at(9)), resumed("a", at(9, 5)), paused("a", at(9, 20))),
            at(12), zone, emptySet()
        )
        assertEquals(20 * minute, dayTotal(slices, "a"))
    }

    @Test
    fun unorderedEventsAreSortedFirst() {
        val slices = UsageMath.compute(listOf(paused("a", at(9, 30)), resumed("a", at(9))), at(12), zone, emptySet())
        assertEquals(30 * minute, dayTotal(slices, "a"))
    }

    @Test
    fun mergeNeverLowersAStoredValue() {
        val merged = UsageMath.merge(mapOf("a" to 500L, "b" to 100L), mapOf("a" to 300L, "b" to 200L, "c" to 50L))
        assertEquals(500L, merged["a"])
        assertEquals(200L, merged["b"])
        assertEquals(50L, merged["c"])
    }

    @Test
    fun weeksStartOnMonday() {
        assertEquals(LocalDate.of(2026, 9, 28), UsageMath.weekStart(LocalDate.of(2026, 9, 30)))
        assertEquals(LocalDate.of(2026, 9, 28), UsageMath.weekStart(LocalDate.of(2026, 9, 28)))
        assertEquals(LocalDate.of(2026, 9, 28), UsageMath.weekStart(LocalDate.of(2026, 10, 4)))
    }

    @Test
    fun durationsReadNaturally() {
        assertEquals("0 min", formatDuration(0))
        assertEquals("Under 1 min", formatDuration(20_000))
        assertEquals("12 min", formatDuration(12 * minute))
        assertEquals("1 h", formatDuration(hour))
        assertEquals("3 h 12 min", formatDuration(3 * hour + 12 * minute))
    }
}
