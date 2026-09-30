package com.example.livora.data.expenses

import java.time.LocalDate
import java.time.YearMonth

class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val slot: Int,
    val iconKey: String,
    val amount: Long,
    val count: Int
)

enum class PeriodKind { Week, Month, Year, Custom }

enum class Granularity { Day, Week, Month }

class Period(val kind: PeriodKind, val from: LocalDate, val to: LocalDate) {

    val days: Int get() = (to.toEpochDay() - from.toEpochDay() + 1).toInt()

    operator fun contains(day: LocalDate): Boolean = day >= from && day <= to

    fun previous(): Period = when (kind) {
        PeriodKind.Week -> of(kind, from.minusWeeks(1))
        PeriodKind.Month -> of(kind, from.minusMonths(1))
        PeriodKind.Year -> of(kind, from.minusYears(1))
        PeriodKind.Custom -> Period(kind, from.minusDays(days.toLong()), from.minusDays(1))
    }

    fun next(): Period = when (kind) {
        PeriodKind.Week -> of(kind, from.plusWeeks(1))
        PeriodKind.Month -> of(kind, from.plusMonths(1))
        PeriodKind.Year -> of(kind, from.plusYears(1))
        PeriodKind.Custom -> Period(kind, to.plusDays(1), to.plusDays(days.toLong()))
    }

    val granularity: Granularity
        get() = when (kind) {
            PeriodKind.Week, PeriodKind.Month -> Granularity.Day
            PeriodKind.Year -> Granularity.Month
            PeriodKind.Custom -> when {
                days <= 31 -> Granularity.Day
                days <= 140 -> Granularity.Week
                else -> Granularity.Month
            }
        }

    companion object {
        fun of(kind: PeriodKind, anchor: LocalDate): Period = when (kind) {
            PeriodKind.Week -> anchor.minusDays((anchor.dayOfWeek.value - 1).toLong()).let { Period(kind, it, it.plusDays(6)) }
            PeriodKind.Month -> YearMonth.from(anchor).let { Period(kind, it.atDay(1), it.atEndOfMonth()) }
            PeriodKind.Year -> Period(kind, LocalDate.of(anchor.year, 1, 1), LocalDate.of(anchor.year, 12, 31))
            PeriodKind.Custom -> Period(kind, anchor, anchor)
        }

        fun custom(from: LocalDate, to: LocalDate): Period =
            if (to < from) Period(PeriodKind.Custom, to, from) else Period(PeriodKind.Custom, from, to)
    }
}

class Bucket(val from: LocalDate, val to: LocalDate, val spent: Long, val received: Long)

class PeriodSummary(
    val period: Period,
    val spent: Long,
    val received: Long,
    val buckets: List<Bucket>,
    val categories: List<CategoryTotal>,
    val previousSpent: Long,
    val previousToDate: Long,
    val elapsedDays: Int,
    val averagePerDay: Long,
    val projected: Long?,
    val biggest: ExpenseEntity?,
    val history: List<Bucket>?,
    val historyGranularity: Granularity,
    val count: Int
) {
    val net: Long get() = received - spent
    val change: Long get() = spent - previousToDate
}

object ExpenseMath {

    const val WEEK_HISTORY = 8
    const val MONTH_HISTORY = 6

    fun queryFrom(period: Period): LocalDate {
        val history = when (period.kind) {
            PeriodKind.Week -> period.from.minusWeeks((WEEK_HISTORY - 1).toLong())
            PeriodKind.Month -> period.from.minusMonths((MONTH_HISTORY - 1).toLong())
            else -> period.from
        }
        val previous = period.previous().from
        return if (history < previous) history else previous
    }

    private fun sum(rows: List<ExpenseEntity>, from: LocalDate, to: LocalDate, spent: Boolean): Long {
        val a = from.toEpochDay()
        val b = to.toEpochDay()
        return if (spent) -rows.filter { it.day in a..b && it.amount < 0 }.sumOf { it.amount }
        else rows.filter { it.day in a..b && it.amount > 0 }.sumOf { it.amount }
    }

    private fun bucket(rows: List<ExpenseEntity>, from: LocalDate, to: LocalDate) =
        Bucket(from, to, sum(rows, from, to, true), sum(rows, from, to, false))

    fun buckets(rows: List<ExpenseEntity>, period: Period): List<Bucket> {
        val byDay = HashMap<Long, LongArray>()
        rows.forEach { row ->
            if (row.day < period.from.toEpochDay() || row.day > period.to.toEpochDay()) return@forEach
            val pair = byDay.getOrPut(row.day) { LongArray(2) }
            if (row.amount < 0) pair[0] += -row.amount else pair[1] += row.amount
        }
        fun cut(from: LocalDate, to: LocalDate): Bucket {
            var spent = 0L
            var received = 0L
            var d = from.toEpochDay()
            val end = to.toEpochDay()
            while (d <= end) {
                byDay[d]?.let {
                    spent += it[0]
                    received += it[1]
                }
                d++
            }
            return Bucket(from, to, spent, received)
        }
        return when (period.granularity) {
            Granularity.Day -> generateSequence(period.from) { it.plusDays(1) }
                .takeWhile { it <= period.to }
                .map { cut(it, it) }
                .toList()
            Granularity.Week -> generateSequence(period.from) { it.plusDays(7) }
                .takeWhile { it <= period.to }
                .map { start -> cut(start, minOf(start.plusDays(6), period.to)) }
                .toList()
            Granularity.Month -> generateSequence(YearMonth.from(period.from)) { it.plusMonths(1) }
                .takeWhile { it <= YearMonth.from(period.to) }
                .map { month -> cut(maxOf(month.atDay(1), period.from), minOf(month.atEndOfMonth(), period.to)) }
                .toList()
        }
    }

    fun history(rows: List<ExpenseEntity>, period: Period): List<Bucket>? = when (period.kind) {
        PeriodKind.Week -> (WEEK_HISTORY - 1 downTo 0).map {
            val start = period.from.minusWeeks(it.toLong())
            bucket(rows, start, start.plusDays(6))
        }
        PeriodKind.Month -> (MONTH_HISTORY - 1 downTo 0).map {
            val month = YearMonth.from(period.from).minusMonths(it.toLong())
            bucket(rows, month.atDay(1), month.atEndOfMonth())
        }
        else -> null
    }

    fun summarize(
        rows: List<ExpenseEntity>,
        categories: Map<Long, ExpenseCategoryEntity>,
        period: Period,
        today: LocalDate
    ): PeriodSummary {
        val first = period.from.toEpochDay()
        val last = period.to.toEpochDay()
        val inPeriod = rows.filter { it.day in first..last }
        val spentRows = inPeriod.filter { it.amount < 0 }
        val spent = -spentRows.sumOf { it.amount }
        val received = inPeriod.filter { it.amount > 0 }.sumOf { it.amount }

        val byCategory = spentRows
            .groupBy { it.categoryId }
            .map { (id, list) ->
                val category = categories[id]
                CategoryTotal(
                    categoryId = id,
                    name = category?.name ?: "Uncategorized",
                    slot = category?.slot ?: -1,
                    iconKey = category?.iconKey ?: "other",
                    amount = -list.sumOf { it.amount },
                    count = list.size
                )
            }
            .sortedByDescending { it.amount }

        val current = today in period
        val elapsed = if (current) (today.toEpochDay() - period.from.toEpochDay() + 1).toInt() else period.days
        val previous = period.previous()
        val previousSpent = sum(rows, previous.from, previous.to, true)
        val previousCut = previous.from.plusDays((minOf(elapsed, previous.days) - 1).toLong())
        val previousToDate = sum(rows, previous.from, previousCut, true)

        val projected = if (current && elapsed < period.days && spent > 0) spent * period.days / elapsed else null

        return PeriodSummary(
            period = period,
            spent = spent,
            received = received,
            buckets = buckets(rows, period),
            categories = byCategory,
            previousSpent = previousSpent,
            previousToDate = previousToDate,
            elapsedDays = elapsed,
            averagePerDay = if (elapsed > 0) spent / elapsed else 0,
            projected = projected,
            biggest = spentRows.minByOrNull { it.amount },
            history = history(rows, period),
            historyGranularity = if (period.kind == PeriodKind.Week) Granularity.Week else Granularity.Month,
            count = inPeriod.size
        )
    }

    fun nextFreeSlot(used: Collection<Int>): Int = (0..7).firstOrNull { it !in used } ?: -1
}
