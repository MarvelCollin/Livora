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

class MonthTotal(
    val month: YearMonth,
    val spent: Long,
    val received: Long
)

class MonthSummary(
    val month: YearMonth,
    val spent: Long,
    val received: Long,
    val daily: List<Long>,
    val categories: List<CategoryTotal>,
    val previousSpent: Long,
    val previousToDate: Long,
    val elapsedDays: Int,
    val averagePerDay: Long,
    val projected: Long?,
    val biggest: ExpenseEntity?,
    val trend: List<MonthTotal>,
    val count: Int
) {
    val net: Long get() = received - spent
    val change: Long get() = spent - previousToDate
}

object ExpenseMath {

    const val TREND_MONTHS = 6

    fun firstDayOfTrend(month: YearMonth): Long =
        month.minusMonths((TREND_MONTHS - 1).toLong()).atDay(1).toEpochDay()

    fun summarize(
        rows: List<ExpenseEntity>,
        categories: Map<Long, ExpenseCategoryEntity>,
        month: YearMonth,
        today: LocalDate
    ): MonthSummary {
        val first = month.atDay(1).toEpochDay()
        val length = month.lengthOfMonth()
        val last = month.atEndOfMonth().toEpochDay()

        val inMonth = rows.filter { it.day in first..last }
        val spentRows = inMonth.filter { it.amount < 0 }
        val spent = -spentRows.sumOf { it.amount }
        val received = inMonth.filter { it.amount > 0 }.sumOf { it.amount }

        val daily = LongArray(length)
        spentRows.forEach { daily[(it.day - first).toInt()] += -it.amount }

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

        val current = YearMonth.from(today) == month
        val elapsed = if (current) today.dayOfMonth else length
        val previous = month.minusMonths(1)
        val previousFirst = previous.atDay(1).toEpochDay()
        val previousLength = previous.lengthOfMonth()
        val previousLast = previous.atEndOfMonth().toEpochDay()
        val previousRows = rows.filter { it.day in previousFirst..previousLast && it.amount < 0 }
        val previousSpent = -previousRows.sumOf { it.amount }
        val previousCut = previousFirst + minOf(elapsed, previousLength) - 1
        val previousToDate = -previousRows.filter { it.day <= previousCut }.sumOf { it.amount }

        val projected = if (current && elapsed < length && spent > 0) spent * length / elapsed else null

        val trend = (TREND_MONTHS - 1 downTo 0).map { back ->
            val m = month.minusMonths(back.toLong())
            val from = m.atDay(1).toEpochDay()
            val to = m.atEndOfMonth().toEpochDay()
            val slice = rows.filter { it.day in from..to }
            MonthTotal(
                month = m,
                spent = -slice.filter { it.amount < 0 }.sumOf { it.amount },
                received = slice.filter { it.amount > 0 }.sumOf { it.amount }
            )
        }

        return MonthSummary(
            month = month,
            spent = spent,
            received = received,
            daily = daily.toList(),
            categories = byCategory,
            previousSpent = previousSpent,
            previousToDate = previousToDate,
            elapsedDays = elapsed,
            averagePerDay = if (elapsed > 0) spent / elapsed else 0,
            projected = projected,
            biggest = spentRows.minByOrNull { it.amount },
            trend = trend,
            count = inMonth.size
        )
    }

    fun nextFreeSlot(used: Collection<Int>): Int = (0..7).firstOrNull { it !in used } ?: -1
}
