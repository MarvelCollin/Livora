package com.example.livora.ui.expenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.components.chartNeutral
import com.example.livora.data.expenses.Bucket
import com.example.livora.data.expenses.Granularity
import com.example.livora.data.expenses.Period
import com.example.livora.data.expenses.PeriodKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val monthName = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val monthShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private val dayShort = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val dayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

fun monthTitle(month: YearMonth): String = month.format(monthFormat)

fun monthOnly(month: YearMonth): String = month.format(monthName)

fun monthAbbreviation(month: YearMonth): String = month.format(monthShort)

fun dayTitle(day: LocalDate): String = day.format(dayShort)

fun dayFull(day: LocalDate): String = day.format(dayLong)

fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> dayTitle(day)
}

@Composable
fun categoryColor(slot: Int): Color = if (slot < 0) chartNeutral() else chartColor(slot)

fun percentText(part: Long, whole: Long): String {
    if (whole <= 0) return "0%"
    val value = part * 100.0 / whole
    return if (value > 0 && value < 1) "<1%" else "${Math.round(value)}%"
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun rangeText(from: LocalDate, to: LocalDate, withYear: Boolean = false): String = when {
    from == to -> if (withYear) dayMonthYear.format(from) else dayMonth.format(from)
    withYear -> "${dayMonthYear.format(from)} to ${dayMonthYear.format(to)}"
    from.month == to.month && from.year == to.year -> "${from.dayOfMonth} to ${dayMonth.format(to)}"
    else -> "${dayMonth.format(from)} to ${dayMonth.format(to)}"
}

fun periodTitle(period: Period, today: LocalDate): String = when (period.kind) {
    PeriodKind.Week -> (if (today in period) "This week, " else "") + rangeText(period.from, period.to)
    PeriodKind.Month -> monthTitle(YearMonth.from(period.from))
    PeriodKind.Year -> period.from.year.toString()
    PeriodKind.Custom -> rangeText(period.from, period.to, withYear = true)
}

fun spentLabel(period: Period, today: LocalDate): String = when (period.kind) {
    PeriodKind.Week -> if (today in period) "Spent this week" else "Spent ${rangeText(period.from, period.to)}"
    PeriodKind.Month -> "Spent in ${monthOnly(YearMonth.from(period.from))}"
    PeriodKind.Year -> if (today in period) "Spent this year" else "Spent in ${period.from.year}"
    PeriodKind.Custom -> "Spent ${rangeText(period.from, period.to)}"
}

fun previousName(period: Period, today: LocalDate): String = when (period.kind) {
    PeriodKind.Week -> if (today in period) "last week" else "the week before"
    PeriodKind.Month -> monthOnly(YearMonth.from(period.from).minusMonths(1))
    PeriodKind.Year -> (period.from.year - 1).toString()
    PeriodKind.Custom -> "the ${period.days} days before"
}

fun bucketTitle(bucket: Bucket, granularity: Granularity): String = when (granularity) {
    Granularity.Day -> dayTitle(bucket.from)
    Granularity.Week -> rangeText(bucket.from, bucket.to)
    Granularity.Month -> monthTitle(YearMonth.from(bucket.from))
}

fun historyAxis(bucket: Bucket, granularity: Granularity): String = when (granularity) {
    Granularity.Month -> monthAbbreviation(YearMonth.from(bucket.from))
    else -> dayMonth.format(bucket.from)
}

fun axisLabels(period: Period, buckets: List<Bucket>): List<String?> {
    val count = buckets.size
    return when (period.granularity) {
        Granularity.Day -> when {
            count <= 7 -> buckets.map { dayShort.format(it.from).take(3) }
            else -> buckets.mapIndexed { index, bucket ->
                if (index == 0 || bucket.from.dayOfMonth % 5 == 0 || index == count - 1) bucket.from.dayOfMonth.toString() else null
            }
        }
        Granularity.Week -> {
            val step = maxOf(1, Math.ceil(count / 5.0).toInt())
            buckets.mapIndexed { index, bucket -> if (index % step == 0) dayMonth.format(bucket.from) else null }
        }
        Granularity.Month -> {
            val step = maxOf(1, Math.ceil(count / 12.0).toInt())
            buckets.mapIndexed { index, bucket ->
                if (index % step == 0) monthAbbreviation(YearMonth.from(bucket.from)) else null
            }
        }
    }
}
