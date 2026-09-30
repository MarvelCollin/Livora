package com.example.livora.data.expenses

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expense_transactions WHERE day BETWEEN :from AND :to ORDER BY day DESC, id DESC")
    fun between(from: Long, to: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT MIN(day) FROM expense_transactions")
    fun firstDay(): Flow<Long?>

    @Query("SELECT * FROM expense_transactions ORDER BY day DESC, id DESC")
    suspend fun all(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExpenseEntity): Long

    @Update
    suspend fun update(item: ExpenseEntity)

    @Delete
    suspend fun delete(item: ExpenseEntity)
}

@Dao
interface ExpenseCategoryDao {

    @Query("SELECT * FROM expense_categories ORDER BY position, id")
    fun observe(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM expense_categories")
    suspend fun lastPosition(): Int

    @Insert
    suspend fun insert(item: ExpenseCategoryEntity): Long
}

@Dao
interface ExpenseAccountDao {

    @Query("SELECT * FROM expense_accounts ORDER BY position, id")
    fun observe(): Flow<List<ExpenseAccountEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM expense_accounts")
    suspend fun lastPosition(): Int

    @Insert
    suspend fun insert(item: ExpenseAccountEntity): Long
}

@Dao
interface ExpenseBudgetDao {

    @Query("SELECT * FROM expense_budgets")
    fun observe(): Flow<List<ExpenseBudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ExpenseBudgetEntity)

    @Query("DELETE FROM expense_budgets WHERE categoryId = :categoryId")
    suspend fun remove(categoryId: Long)
}
