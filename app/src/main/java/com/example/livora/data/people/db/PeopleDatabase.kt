package com.example.livora.data.people.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

@Database(
    entities = [
        PhotoEntity::class,
        FaceEntity::class,
        PersonEntity::class,
        ReferenceEntity::class,
        RejectionEntity::class,
        LinkedCopyEntity::class,
        VirtualFolderEntity::class,
        SeparationEntity::class,
        AiMoveEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class PeopleDatabase : RoomDatabase() {

    abstract fun photos(): PhotoDao
    abstract fun faces(): FaceDao
    abstract fun persons(): PersonDao
    abstract fun references(): ReferenceDao
    abstract fun rejections(): RejectionDao
    abstract fun linkedCopies(): LinkedCopyDao
    abstract fun virtualFolders(): VirtualFolderDao
    abstract fun separations(): SeparationDao
    abstract fun aiMoves(): AiMoveDao

    companion object {

        const val FILE_NAME = "people/people.db"

        fun create(context: Context): PeopleDatabase {
            val file = File(context.noBackupFilesDir, FILE_NAME)
            file.parentFile?.mkdirs()
            return try {
                open(context, file)
            } catch (e: Exception) {
                Log.e("PeopleDatabase", "database could not be opened, keeping a backup and starting fresh", e)
                backup(file)
                open(context, file)
            }
        }

        private fun open(context: Context, file: File): PeopleDatabase {
            val database = Room.databaseBuilder(context.applicationContext, PeopleDatabase::class.java, file.absolutePath)
                .addMigrations(*PeopleMigrations.ALL)
                .build()
            database.openHelper.writableDatabase
            return database
        }

        private fun backup(file: File) {
            val stamp = System.currentTimeMillis()
            for (suffix in listOf("", "-wal", "-shm", "-journal")) {
                val source = File(file.path + suffix)
                if (source.exists()) source.renameTo(File(file.path + suffix + ".$stamp.bak"))
            }
        }
    }
}
