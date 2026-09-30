package com.example.livora.data.cleaner

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "cleaner_kept")
data class CleanerKeptEntity(
    @PrimaryKey val fileKey: String,
    val keptAt: Long
)

@Dao
interface CleanerKeptDao {

    @Query("SELECT fileKey FROM cleaner_kept")
    suspend fun keys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: CleanerKeptEntity)

    @Query("DELETE FROM cleaner_kept WHERE fileKey = :fileKey")
    suspend fun remove(fileKey: String)

    @Query("DELETE FROM cleaner_kept")
    suspend fun clear()
}
