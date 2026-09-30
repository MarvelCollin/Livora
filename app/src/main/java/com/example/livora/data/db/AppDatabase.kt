package com.example.livora.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.livora.data.expenses.ExpenseAccountDao
import com.example.livora.data.expenses.ExpenseAccountEntity
import com.example.livora.data.expenses.ExpenseBudgetDao
import com.example.livora.data.expenses.ExpenseBudgetEntity
import com.example.livora.data.expenses.ExpenseCategoryDao
import com.example.livora.data.expenses.ExpenseCategoryEntity
import com.example.livora.data.expenses.ExpenseDao
import com.example.livora.data.expenses.ExpenseEntity

@Database(
    entities = [
        ExpenseEntity::class,
        ExpenseCategoryEntity::class,
        ExpenseAccountEntity::class,
        ExpenseBudgetEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenses(): ExpenseDao
    abstract fun categories(): ExpenseCategoryDao
    abstract fun accounts(): ExpenseAccountDao
    abstract fun budgets(): ExpenseBudgetDao

    companion object {

        const val FILE_NAME = "livora.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*AppMigrations.ALL)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        AppSeed.SQL.forEach { db.execSQL(it) }
                    }
                })
                .build()
    }
}

object AppSeed {

    val SQL = listOf(
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Food', 0, 1, 0, 'food')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Groceries', 0, 2, 1, 'groceries')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Transport', 0, 0, 2, 'transport')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Bills', 0, 6, 3, 'bills')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Shopping', 0, 3, 4, 'shopping')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Fun', 0, 4, 5, 'fun')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Health', 0, 7, 6, 'health')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Other', 0, -1, 7, 'other')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Allowance', 1, 5, 8, 'allowance')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Salary', 1, 5, 9, 'salary')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Freelance', 1, 5, 10, 'freelance')",
        "INSERT INTO expense_categories (name, income, slot, position, iconKey) VALUES ('Other income', 1, 5, 11, 'income')",
        "INSERT INTO expense_accounts (name, position) VALUES ('Cash', 0)",
        "INSERT INTO expense_accounts (name, position) VALUES ('Bank', 1)",
        "INSERT INTO expense_accounts (name, position) VALUES ('E-wallet', 2)"
    )
}
