package com.example.livora.data.people.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object PeopleMigrationSql {

    val V1_TO_V2 = listOf(
        "ALTER TABLE `persons` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `person_separations` (`personA` INTEGER NOT NULL, `personB` INTEGER NOT NULL, PRIMARY KEY(`personA`, `personB`))"
    )

    val V2_TO_V3 = listOf(
        "ALTER TABLE `photos` ADD COLUMN `size` INTEGER NOT NULL DEFAULT -1",
        "ALTER TABLE `photos` ADD COLUMN `retryCount` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `photos` ADD COLUMN `pipelineVersion` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `faces` ADD COLUMN `modelVersion` INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE `faces` ADD COLUMN `pipelineVersion` INTEGER NOT NULL DEFAULT 1"
    )
}

object PeopleMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            PeopleMigrationSql.V1_TO_V2.forEach { db.execSQL(it) }
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            PeopleMigrationSql.V2_TO_V3.forEach { db.execSQL(it) }
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
