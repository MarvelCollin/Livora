package com.example.livora.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppMigrationSql {

    val V1_TO_V2 = listOf(
        "ALTER TABLE `expense_categories` ADD COLUMN `iconKey` TEXT NOT NULL DEFAULT 'other'",
        "UPDATE `expense_categories` SET `iconKey` = 'food' WHERE `name` = 'Food'",
        "UPDATE `expense_categories` SET `iconKey` = 'groceries' WHERE `name` = 'Groceries'",
        "UPDATE `expense_categories` SET `iconKey` = 'transport' WHERE `name` = 'Transport'",
        "UPDATE `expense_categories` SET `iconKey` = 'bills' WHERE `name` = 'Bills'",
        "UPDATE `expense_categories` SET `iconKey` = 'shopping' WHERE `name` = 'Shopping'",
        "UPDATE `expense_categories` SET `iconKey` = 'fun' WHERE `name` = 'Fun'",
        "UPDATE `expense_categories` SET `iconKey` = 'health' WHERE `name` = 'Health'",
        "UPDATE `expense_categories` SET `iconKey` = 'allowance' WHERE `name` = 'Allowance'",
        "UPDATE `expense_categories` SET `iconKey` = 'salary' WHERE `name` = 'Salary'",
        "UPDATE `expense_categories` SET `iconKey` = 'freelance' WHERE `name` = 'Freelance'",
        "UPDATE `expense_categories` SET `iconKey` = 'income' WHERE `name` = 'Other income'"
    )

    val V2_TO_V3 = listOf(
        "CREATE TABLE IF NOT EXISTS `usage_days` (`day` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `millis` INTEGER NOT NULL, PRIMARY KEY(`day`, `packageName`))",
        "CREATE TABLE IF NOT EXISTS `usage_hours` (`hourStart` INTEGER NOT NULL, `millis` INTEGER NOT NULL, PRIMARY KEY(`hourStart`))"
    )

    val V3_TO_V4 = listOf(
        "CREATE TABLE IF NOT EXISTS `cleaner_kept` (`fileKey` TEXT NOT NULL, `keptAt` INTEGER NOT NULL, PRIMARY KEY(`fileKey`))"
    )

    val V4_TO_V5 = listOf(
        "CREATE TABLE IF NOT EXISTS `qr_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `value` TEXT NOT NULL, `kind` TEXT NOT NULL, `scannedAt` INTEGER NOT NULL, `fromPhoto` INTEGER NOT NULL)"
    )
}

object AppMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppMigrationSql.V1_TO_V2.forEach { db.execSQL(it) }
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppMigrationSql.V2_TO_V3.forEach { db.execSQL(it) }
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppMigrationSql.V3_TO_V4.forEach { db.execSQL(it) }
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            AppMigrationSql.V4_TO_V5.forEach { db.execSQL(it) }
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
