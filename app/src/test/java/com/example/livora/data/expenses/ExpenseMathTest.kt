package com.example.livora.data.expenses

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseMathTest {

    private val food = ExpenseCategoryEntity(1, "Food", false, 1, 0)
    private val bills = ExpenseCategoryEntity(2, "Bills", false, 6, 1)
    private val salary = ExpenseCategoryEntity(3, "Salary", true, 5, 2)
    private val categories = listOf(food, bills, salary).associateBy { it.id }

    private fun row(id: Long, date: LocalDate, amount: Long, category: Long) =
        ExpenseEntity(id, amount, category, 1, "", date.toEpochDay(), 0)

    private val sept = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 10)

    @Test
    fun totalsSplitSpentAndReceived() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 9, 1), -50_000, 1),
            row(2, LocalDate.of(2026, 9, 3), -30_000, 2),
            row(3, LocalDate.of(2026, 9, 5), 1_000_000, 3)
        )
        val summary = ExpenseMath.summarize(rows, categories, sept, today)
        assertEquals(80_000L, summary.spent)
        assertEquals(1_000_000L, summary.received)
        assertEquals(920_000L, summary.net)
        assertEquals(3, summary.count)
    }

    @Test
    fun dailyHasOneSlotPerDayAndSumsBack() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 9, 1), -50_000, 1),
            row(2, LocalDate.of(2026, 9, 1), -10_000, 1),
            row(3, LocalDate.of(2026, 9, 30), -5_000, 2)
        )
        val summary = ExpenseMath.summarize(rows, categories, sept, today)
        assertEquals(30, summary.daily.size)
        assertEquals(60_000L, summary.daily[0])
        assertEquals(5_000L, summary.daily[29])
        assertEquals(summary.spent, summary.daily.sum())
    }

    @Test
    fun categoriesAreSortedByAmountAndIgnoreIncome() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 9, 1), -10_000, 1),
            row(2, LocalDate.of(2026, 9, 2), -90_000, 2),
            row(3, LocalDate.of(2026, 9, 3), 500_000, 3)
        )
        val summary = ExpenseMath.summarize(rows, categories, sept, today)
        assertEquals(listOf("Bills", "Food"), summary.categories.map { it.name })
        assertEquals(90_000L, summary.categories.first().amount)
    }

    @Test
    fun deletedCategoryFallsBackToUncategorized() {
        val rows = listOf(row(1, LocalDate.of(2026, 9, 1), -10_000, 99))
        val summary = ExpenseMath.summarize(rows, categories, sept, today)
        assertEquals("Uncategorized", summary.categories.single().name)
        assertEquals(-1, summary.categories.single().slot)
    }

    @Test
    fun comparisonUsesTheSameDaysOfLastMonthWhileTheMonthIsRunning() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 8, 5), -100_000, 1),
            row(2, LocalDate.of(2026, 8, 20), -400_000, 1),
            row(3, LocalDate.of(2026, 9, 4), -150_000, 1)
        )
        val summary = ExpenseMath.summarize(rows, categories, sept, today)
        assertEquals(500_000L, summary.previousSpent)
        assertEquals(100_000L, summary.previousToDate)
        assertEquals(50_000L, summary.change)
    }

    @Test
    fun finishedMonthComparesFullMonths() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 8, 20), -400_000, 1),
            row(2, LocalDate.of(2026, 9, 28), -150_000, 1)
        )
        val summary = ExpenseMath.summarize(rows, categories, sept, LocalDate.of(2026, 10, 15))
        assertEquals(400_000L, summary.previousToDate)
        assertEquals(30, summary.elapsedDays)
        assertNull(summary.projected)
    }

    @Test
    fun projectionScalesSpendingToTheWholeMonth() {
        val rows = listOf(row(1, LocalDate.of(2026, 9, 2), -100_000, 1))
        val summary = ExpenseMath.summarize(rows, categories, sept, today)
        assertEquals(10, summary.elapsedDays)
        assertEquals(10_000L, summary.averagePerDay)
        assertEquals(300_000L, summary.projected)
    }

    @Test
    fun noProjectionWithoutSpending() {
        val summary = ExpenseMath.summarize(emptyList(), categories, sept, today)
        assertNull(summary.projected)
        assertEquals(0L, summary.averagePerDay)
    }

    @Test
    fun trendCoversSixMonthsEndingWithTheSelectedOne() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 4, 10), -10_000, 1),
            row(2, LocalDate.of(2026, 9, 10), -20_000, 1),
            row(3, LocalDate.of(2026, 9, 11), 70_000, 3)
        )
        val summary = ExpenseMath.summarize(rows, categories, sept, today)
        assertEquals(6, summary.trend.size)
        assertEquals(YearMonth.of(2026, 4), summary.trend.first().month)
        assertEquals(10_000L, summary.trend.first().spent)
        assertEquals(20_000L, summary.trend.last().spent)
        assertEquals(70_000L, summary.trend.last().received)
    }

    @Test
    fun biggestExpenseIsTheLargestSpend() {
        val rows = listOf(
            row(1, LocalDate.of(2026, 9, 1), -10_000, 1),
            row(2, LocalDate.of(2026, 9, 2), -90_000, 2),
            row(3, LocalDate.of(2026, 9, 3), 900_000, 3)
        )
        assertEquals(2L, ExpenseMath.summarize(rows, categories, sept, today).biggest?.id)
    }

    @Test
    fun trendWindowStartsFiveMonthsBack() {
        assertEquals(LocalDate.of(2026, 4, 1).toEpochDay(), ExpenseMath.firstDayOfTrend(sept))
    }

    @Test
    fun newCategoriesTakeTheFirstFreeColorSlot() {
        assertEquals(2, ExpenseMath.nextFreeSlot(listOf(0, 1, 3)))
        assertEquals(-1, ExpenseMath.nextFreeSlot((0..7).toList()))
    }

    @Test
    fun moneyGroupsThousandsWithDots() {
        assertEquals("Rp 0", Money.format(0))
        assertEquals("Rp 999", Money.format(999))
        assertEquals("Rp 1.000", Money.format(1_000))
        assertEquals("Rp 1.450.000", Money.format(-1_450_000))
        assertEquals("Rp 12.345.678.901", Money.format(12_345_678_901))
    }

    @Test
    fun signedMoneyShowsDirection() {
        assertEquals("+Rp 5.000", Money.signed(5_000))
        assertEquals("-Rp 5.000", Money.signed(-5_000))
        assertEquals("Rp 0", Money.signed(0))
    }

    @Test
    fun compactMoneyUsesIndonesianUnits() {
        assertEquals("Rp 320 rb", Money.compact(320_000))
        assertEquals("Rp 1,4 jt", Money.compact(1_420_000))
        assertEquals("Rp 1,5 jt", Money.compact(1_450_000))
        assertEquals("Rp 4 jt", Money.compact(4_000_000))
        assertEquals("Rp 500", Money.compact(500))
    }

    @Test
    fun digitParsingIgnoresJunk() {
        assertEquals(0L, Money.parseDigits(""))
        assertEquals(1_200L, Money.parseDigits("1200"))
        assertEquals(0L, Money.parseDigits("abc"))
    }

    @Test
    fun csvQuotesFieldsThatNeedIt() {
        assertEquals("plain", ExpenseRepository.csvField("plain"))
        assertEquals("\"a,b\"", ExpenseRepository.csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", ExpenseRepository.csvField("say \"hi\""))
        assertTrue(ExpenseRepository.csvField("line\nbreak").startsWith("\""))
    }

    @Test
    fun csvNeutralisesSpreadsheetFormulas() {
        assertEquals("'=SUM(A1)", ExpenseRepository.safeText("=SUM(A1)"))
        assertEquals("'@cmd", ExpenseRepository.safeText("@cmd"))
        assertEquals("lunch", ExpenseRepository.safeText("lunch"))
        assertEquals("", ExpenseRepository.safeText(""))
    }
}
