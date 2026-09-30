package com.example.livora.ui.expenses

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.livora.ui.components.chartColor
import com.example.livora.ui.components.chartNeutral
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
