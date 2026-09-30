package com.example.livora.data.expenses

import com.example.livora.data.db.AppDatabase
import java.io.OutputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ExpenseRepository(private val db: AppDatabase) {

    fun between(from: Long, to: Long): Flow<List<ExpenseEntity>> = db.expenses().between(from, to)

    fun firstDay(): Flow<Long?> = db.expenses().firstDay()

    fun categories(): Flow<List<ExpenseCategoryEntity>> = db.categories().observe()

    fun accounts(): Flow<List<ExpenseAccountEntity>> = db.accounts().observe()

    fun budgets(): Flow<List<ExpenseBudgetEntity>> = db.budgets().observe()

    suspend fun add(item: ExpenseEntity): Long = db.expenses().insert(item)

    suspend fun update(item: ExpenseEntity) = db.expenses().update(item)

    suspend fun delete(item: ExpenseEntity) = db.expenses().delete(item)

    suspend fun restore(item: ExpenseEntity) {
        db.expenses().insert(item)
    }

    suspend fun addCategory(name: String, income: Boolean, iconKey: String): ExpenseCategoryEntity? {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return null
        val existing = db.categories().observe().first()
        existing.firstOrNull { it.income == income && it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val slot = if (income) 5 else ExpenseMath.nextFreeSlot(existing.filter { !it.income }.map { it.slot })
        val draft = ExpenseCategoryEntity(
            name = clean,
            income = income,
            slot = slot,
            position = db.categories().lastPosition() + 1,
            iconKey = iconKey
        )
        return draft.copy(id = db.categories().insert(draft))
    }

    suspend fun addAccount(name: String): ExpenseAccountEntity? {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return null
        val existing = db.accounts().observe().first()
        existing.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it }
        val draft = ExpenseAccountEntity(name = clean, position = db.accounts().lastPosition() + 1)
        return draft.copy(id = db.accounts().insert(draft))
    }

    suspend fun setBudget(limit: Long?) {
        if (limit == null || limit <= 0) db.budgets().remove(TOTAL_BUDGET)
        else db.budgets().upsert(ExpenseBudgetEntity(TOTAL_BUDGET, limit))
    }

    suspend fun exportCsv(out: OutputStream): Int {
        val categories = db.categories().observe().first().associateBy { it.id }
        val accounts = db.accounts().observe().first().associateBy { it.id }
        val rows = db.expenses().all()
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write("Date,Type,Category,Account,Note,Amount\r\n")
        rows.forEach { row ->
            val fields = listOf(
                LocalDate.ofEpochDay(row.day).toString(),
                if (row.amount >= 0) "Income" else "Expense",
                safeText(categories[row.categoryId]?.name ?: "Uncategorized"),
                safeText(accounts[row.accountId]?.name ?: "Unknown"),
                safeText(row.note),
                row.amount.toString()
            )
            writer.write(fields.joinToString(",") { csvField(it) })
            writer.write("\r\n")
        }
        writer.flush()
        return rows.size
    }

    companion object {
        const val TOTAL_BUDGET = 0L

        fun safeText(value: String): String =
            if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value

        fun csvField(value: String): String {
            val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
        }
    }
}
