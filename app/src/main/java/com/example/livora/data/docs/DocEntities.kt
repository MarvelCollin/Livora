package com.example.livora.data.docs

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val folder: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val sizeBytes: Long
)

@Entity(tableName = "document_pages", indices = [Index("documentId")])
data class DocumentPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    val fileName: String
)

class DocumentCover(val documentId: Long, val fileName: String)

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observe(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeOne(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): DocumentEntity?

    @Insert
    suspend fun insert(item: DocumentEntity): Long

    @Update
    suspend fun update(item: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT documentId, fileName FROM document_pages WHERE position = 0")
    fun observeCovers(): Flow<List<DocumentCover>>

    @Query("SELECT * FROM document_pages WHERE documentId = :id ORDER BY position")
    fun observePages(id: Long): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :id ORDER BY position")
    suspend fun pages(id: Long): List<DocumentPageEntity>

    @Insert
    suspend fun insertPages(items: List<DocumentPageEntity>)

    @Update
    suspend fun updatePages(items: List<DocumentPageEntity>)

    @Query("DELETE FROM document_pages WHERE id = :id")
    suspend fun deletePage(id: Long)

    @Query("DELETE FROM document_pages WHERE documentId = :id")
    suspend fun deletePages(id: Long)
}
