package com.example.livora.data.usage

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class RawEvent(val packageName: String, val className: String, val type: Int, val time: Long)

class UsageSlices(
    val days: Map<Long, Map<String, Long>>,
    val hours: Map<Long, Long>
)

object UsageMath {

    const val RESUMED = 1
    const val PAUSED = 2
    const val SCREEN_OFF = 16
    const val KEYGUARD_SHOWN = 17
    const val STOPPED = 23
    const val SHUTDOWN = 26

    private const val HOUR = 3_600_000L

    fun compute(events: List<RawEvent>, until: Long, zone: ZoneId, ignored: Set<String>): UsageSlices {
        val days = HashMap<Long, HashMap<String, Long>>()
        val hours = HashMap<Long, Long>()
        val open = HashMap<String, Pair<String, Long>>()

        fun record(pkg: String, start: Long, end: Long) {
            if (end <= start || pkg in ignored) return
            var cursor = start
            while (cursor < end) {
                val day = java.time.Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val stop = minOf(end, dayEnd)
                val perApp = days.getOrPut(day.toEpochDay()) { HashMap() }
                perApp[pkg] = (perApp[pkg] ?: 0L) + (stop - cursor)
                cursor = stop
            }
            var hour = start / HOUR
            while (hour * HOUR < end) {
                val from = maxOf(start, hour * HOUR)
                val to = minOf(end, (hour + 1) * HOUR)
                if (to > from) hours[hour] = (hours[hour] ?: 0L) + (to - from)
                hour++
            }
        }

        fun closeAll(time: Long) {
            open.values.forEach { (pkg, start) -> record(pkg, start, time) }
            open.clear()
        }

        events.sortedBy { it.time }.forEach { event ->
            val key = event.packageName + "/" + event.className
            when (event.type) {
                RESUMED -> if (!open.containsKey(key)) open[key] = event.packageName to event.time
                PAUSED, STOPPED -> open.remove(key)?.let { (pkg, start) -> record(pkg, start, event.time) }
                SCREEN_OFF, KEYGUARD_SHOWN, SHUTDOWN -> closeAll(event.time)
            }
        }
        closeAll(until)
        return UsageSlices(days, hours)
    }

    fun merge(existing: Map<String, Long>, fresh: Map<String, Long>): Map<String, Long> {
        val out = HashMap(existing)
        fresh.forEach { (pkg, millis) -> if (millis > (out[pkg] ?: 0L)) out[pkg] = millis }
        return out
    }

    fun weekStart(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

    fun hourKey(day: LocalDate, hour: Int, zone: ZoneId): Long =
        day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli() / HOUR

    fun monthDays(month: YearMonth): Int = month.lengthOfMonth()
}
