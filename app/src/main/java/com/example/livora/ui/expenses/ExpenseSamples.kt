package com.example.livora.ui.expenses

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

class Transaction(
    val id: Int,
    val day: String,
    val note: String,
    val category: String,
    val account: String,
    val amount: Long
)

object ExpenseSamples {

    const val monthLabel = "September 2026"
    const val spent = 4_280_000L
    const val budget = 6_000_000L
    const val lastMonthDifference = 620_000L

    val categories = listOf(
        "Food" to 1_450_000L,
        "Groceries" to 980_000L,
        "Transport" to 720_000L,
        "Bills" to 640_000L,
        "Fun" to 490_000L
    )

    val transactions = listOf(
        Transaction(1, "Today", "Lunch at the campus canteen", "Food", "Cash", -38_000),
        Transaction(2, "Today", "Ride to campus", "Transport", "GoPay", -21_000),
        Transaction(3, "Yesterday", "Weekly groceries", "Groceries", "BCA", -286_000),
        Transaction(4, "Yesterday", "Freelance payment", "Income", "BCA", 750_000),
        Transaction(5, "28 Sep", "Phone data plan", "Bills", "BCA", -120_000),
        Transaction(6, "28 Sep", "Coffee with friends", "Food", "GoPay", -64_000),
        Transaction(7, "26 Sep", "Cinema tickets", "Fun", "GoPay", -100_000),
        Transaction(8, "26 Sep", "Electricity token", "Bills", "BCA", -200_000),
        Transaction(9, "24 Sep", "Monthly allowance", "Income", "BCA", 3_000_000)
    )
}

private val rupiahNumber: NumberFormat = NumberFormat.getIntegerInstance(Locale("id", "ID"))

fun formatRupiah(value: Long): String = "Rp ${rupiahNumber.format(abs(value))}"

fun signedRupiah(value: Long): String = when {
    value > 0 -> "+${formatRupiah(value)}"
    value < 0 -> "-${formatRupiah(value)}"
    else -> formatRupiah(0)
}
