package com.example.livora.ui.usage

import com.example.livora.data.usage.AppUsage
import com.example.livora.data.usage.UsageMath
import com.example.livora.data.usage.UsagePeriod
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

enum class UsageRange { Day, Week, Month }

class UsageChart(
    val values: List<Float>,
    val titles: List<String>,
    val valueTexts: List<String>,
    val axis: List<String?>,
    val initial: Int?,
    val description: String
)

class UsageView(
    val range: UsageRange,
    val from: LocalDate,
    val to: LocalDate,
    val period: String,
    val headlineLabel: String,
    val headline: String,
    val context: String?,
    val chart: UsageChart,
    val hint: String,
    val apps: List<AppUsage>,
    val total: Long,
    val perDay: Long,
    val days: Int,
    val canPrevious: Boolean,
    val canNext: Boolean
) {
    val hasData: Boolean get() = total > 0
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val dayFull = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0 min"
    val minutes = Math.round(millis / 60_000.0).toInt()
    if (minutes < 1) return "Under 1 min"
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}

object UsageViews {

    fun rangeOf(range: UsageRange, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        UsageRange.Day -> anchor to anchor
        UsageRange.Week -> UsageMath.weekStart(anchor).let { it to it.plusDays(6) }
        UsageRange.Month -> YearMonth.from(anchor).let { it.atDay(1) to it.atEndOfMonth() }
    }

    fun shift(range: UsageRange, anchor: LocalDate, direction: Int): LocalDate = when (range) {
        UsageRange.Day -> anchor.plusDays(direction.toLong())
        UsageRange.Week -> anchor.plusWeeks(direction.toLong())
        UsageRange.Month -> YearMonth.from(anchor).plusMonths(direction.toLong()).atDay(1)
    }

    private fun daysBetween(from: LocalDate, to: LocalDate): Int =
        if (to < from) 0 else (ChronoUnit.DAYS.between(from, to) + 1).toInt()

    fun build(
        range: UsageRange,
        anchor: LocalDate,
        today: LocalDate,
        current: UsagePeriod,
        previous: UsagePeriod,
        firstRecorded: LocalDate?,
        zone: ZoneId
    ): UsageView {
        val from = current.from
        val to = current.to
        val total = current.total

        val recordedFrom = if (firstRecorded != null && firstRecorded > from) firstRecorded else from
        val days = maxOf(1, daysBetween(recordedFrom, minOf(to, today)))
        val previousRecordedFrom = if (firstRecorded != null && firstRecorded > previous.from) firstRecorded else previous.from
        val previousDays = daysBetween(previousRecordedFrom, previous.to)
        val perDay = if (range == UsageRange.Day) total else total / days

        val chart = when (range) {
            UsageRange.Day -> {
                val millis = (0 until 24).map { hour -> current.hourMillis[UsageMath.hourKey(anchor, hour, zone)] ?: 0L }
                val peak = millis.indices.maxByOrNull { millis[it] } ?: 0
                val nowHour = java.time.LocalTime.now(zone).hour
                UsageChart(
                    values = millis.map { it / 60_000f },
                    titles = (0 until 24).map { "%02d:00 to %02d:00".format(it, (it + 1) % 24) },
                    valueTexts = millis.map { formatDuration(it) },
                    axis = (0 until 24).map { if (it in listOf(0, 6, 12, 18, 23)) "$it" else null },
                    initial = if (anchor == today) nowHour else null,
                    description = "Minutes on screen for each hour of ${dayFull.format(anchor)}." +
                        if (millis[peak] > 0) " Busiest at ${"%02d".format(peak)}:00 with ${formatDuration(millis[peak])}." else ""
                )
            }
            UsageRange.Week -> {
                val millis = (0 until 7).map { current.dayMillis[from.plusDays(it.toLong()).toEpochDay()] ?: 0L }
                UsageChart(
                    values = millis.map { it / 60_000f },
                    titles = (0 until 7).map { dayFull.format(from.plusDays(it.toLong())) },
                    valueTexts = millis.map { formatDuration(it) },
                    axis = (0 until 7).map { dayFormat.format(from.plusDays(it.toLong())).take(3) },
                    initial = if (today in from..to) (today.toEpochDay() - from.toEpochDay()).toInt() else null,
                    description = "Screen time for each day from ${shortDate.format(from)} to ${shortDate.format(to)}."
                )
            }
            UsageRange.Month -> {
                val length = daysBetween(from, to)
                val millis = (0 until length).map { current.dayMillis[from.plusDays(it.toLong()).toEpochDay()] ?: 0L }
                UsageChart(
                    values = millis.map { it / 60_000f },
                    titles = (0 until length).map { shortDate.format(from.plusDays(it.toLong())) },
                    valueTexts = millis.map { formatDuration(it) },
                    axis = (0 until length).map { if (it % 7 == 0) "${it + 1}" else null },
                    initial = if (today in from..to) today.dayOfMonth - 1 else null,
                    description = "Screen time for each day of ${monthTitle.format(from)}."
                )
            }
        }

        val period = when (range) {
            UsageRange.Day -> when (anchor) {
                today -> "Today, ${shortDate.format(anchor)}"
                today.minusDays(1) -> "Yesterday, ${shortDate.format(anchor)}"
                else -> dayFormat.format(anchor)
            }
            UsageRange.Week -> (if (today in from..to) "This week, " else "") + "${shortDate.format(from)} to ${shortDate.format(to)}"
            UsageRange.Month -> monthTitle.format(from)
        }

        val context = when (range) {
            UsageRange.Day -> if (previous.total > 0) {
                (if (anchor == today) "Yesterday" else "The day before") + " was ${formatDuration(previous.total)}"
            } else null
            else -> if (previous.total > 0 && previousDays > 0) {
                val before = previous.total / previousDays
                val diff = perDay - before
                val name = if (range == UsageRange.Week) "last week" else "last month"
                if (abs(diff) < 60_000) "About the same as $name"
                else "${formatDuration(abs(diff))} ${if (diff < 0) "less" else "more"} per day than $name"
            } else null
        }

        val hint = when (range) {
            UsageRange.Day -> "Touch or drag along the bars to see each hour."
            UsageRange.Week -> "Touch or drag along the bars to see each day."
            UsageRange.Month -> "Touch or drag along the bars to see each day."
        }

        return UsageView(
            range = range,
            from = from,
            to = to,
            period = period,
            headlineLabel = if (range == UsageRange.Day) "Screen time" else "Daily average",
            headline = formatDuration(perDay),
            context = context,
            chart = chart,
            hint = hint,
            apps = current.apps,
            total = total,
            perDay = perDay,
            days = days,
            canPrevious = firstRecorded != null && firstRecorded < from,
            canNext = to < today
        )
    }
}
