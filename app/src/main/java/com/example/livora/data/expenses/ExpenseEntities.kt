package com.example.livora.data.expenses

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val income: Boolean,
    val slot: Int,
    val position: Int,
    val iconKey: String = "other"
)

@Entity(tableName = "expense_accounts")
data class ExpenseAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int
)

@Entity(
    tableName = "expense_transactions",
    indices = [Index("day"), Index("categoryId"), Index("accountId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String,
    val day: Long,
    val createdAt: Long
)

@Entity(tableName = "expense_budgets")
data class ExpenseBudgetEntity(
    @PrimaryKey val categoryId: Long,
    val monthlyLimit: Long
)
