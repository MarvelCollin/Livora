package com.example.livora.data.usage

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "usage_days", primaryKeys = ["day", "packageName"])
data class UsageDayEntity(
    val day: Long,
    val packageName: String,
    val millis: Long
)

@Entity(tableName = "usage_hours")
data class UsageHourEntity(
    @PrimaryKey val hourStart: Long,
    val millis: Long
)

class DayTotal(val day: Long, val millis: Long)

class AppTotal(val packageName: String, val millis: Long)

@Dao
interface UsageDao {

    @Query("SELECT day, SUM(millis) AS millis FROM usage_days WHERE day BETWEEN :from AND :to GROUP BY day")
    suspend fun dayTotals(from: Long, to: Long): List<DayTotal>

    @Query("SELECT packageName, SUM(millis) AS millis FROM usage_days WHERE day BETWEEN :from AND :to GROUP BY packageName ORDER BY millis DESC")
    suspend fun appTotals(from: Long, to: Long): List<AppTotal>

    @Query("SELECT * FROM usage_days WHERE day BETWEEN :from AND :to")
    suspend fun days(from: Long, to: Long): List<UsageDayEntity>

    @Query("SELECT * FROM usage_hours WHERE hourStart BETWEEN :from AND :to")
    suspend fun hours(from: Long, to: Long): List<UsageHourEntity>

    @Query("SELECT MIN(day) FROM usage_days")
    suspend fun firstDay(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDays(items: List<UsageDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHours(items: List<UsageHourEntity>)
}
