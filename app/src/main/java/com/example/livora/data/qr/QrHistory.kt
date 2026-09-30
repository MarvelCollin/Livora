package com.example.livora.data.qr

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "qr_history")
data class QrHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val value: String,
    val kind: String,
    val scannedAt: Long,
    val fromPhoto: Boolean
)

@Dao
interface QrHistoryDao {

    @Query("SELECT * FROM qr_history ORDER BY scannedAt DESC, id DESC LIMIT 200")
    fun observe(): Flow<List<QrHistoryEntity>>

    @Query("SELECT * FROM qr_history WHERE value = :value LIMIT 1")
    suspend fun find(value: String): QrHistoryEntity?

    @Insert
    suspend fun insert(item: QrHistoryEntity): Long

    @Update
    suspend fun update(item: QrHistoryEntity)

    @Query("DELETE FROM qr_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM qr_history")
    suspend fun clear()

    @Query("DELETE FROM qr_history WHERE id NOT IN (SELECT id FROM qr_history ORDER BY scannedAt DESC, id DESC LIMIT 200)")
    suspend fun trim()
}
