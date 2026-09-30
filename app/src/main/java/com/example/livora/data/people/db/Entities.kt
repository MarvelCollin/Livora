package com.example.livora.data.people.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object PipelineVersion {
    const val MODEL = 1
    const val PIPELINE = 1
    const val CURRENT = MODEL * 100 + PIPELINE
    const val LEGACY = 1
}

object PhotoStatus {
    const val SCANNED = 0
    const val FAILED = 1
    const val SKIPPED = 2
}

object PersonKind {
    const val AUTO = 0
    const val ENROLLED = 1
}

object AiMoveKind {
    const val MOVE = 0
    const val COPY = 1
}

object LinkMode {
    const val NONE = 0
    const val REVIEW = 1
    const val AUTO = 2
}

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val mediaId: Long,
    val dateModified: Long,
    val dateTaken: Long,
    val scannedAt: Long,
    val faceCount: Int,
    val status: Int,
    val orientation: Int = 0,
    @ColumnInfo(defaultValue = "-1") val size: Long = -1,
    @ColumnInfo(defaultValue = "0") val retryCount: Int = 0,
    @ColumnInfo(defaultValue = "1") val pipelineVersion: Int = PipelineVersion.CURRENT
)

@Entity(
    tableName = "faces",
    indices = [Index("personId"), Index("mediaId")]
)
class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val score: Float,
    val quality: Float,
    val embedding: ByteArray,
    val personId: Long? = null,
    val locked: Boolean = false,
    @ColumnInfo(defaultValue = "1") val modelVersion: Int = PipelineVersion.MODEL,
    @ColumnInfo(defaultValue = "1") val pipelineVersion: Int = PipelineVersion.PIPELINE
)

@Entity(tableName = "persons")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String? = null,
    val hidden: Boolean = false,
    val kind: Int = PersonKind.AUTO,
    val createdAt: Long = 0,
    val threshold: Float = 0.62f,
    val linkedFolderPath: String? = null,
    val linkedFolderName: String? = null,
    val linkMode: Int = LinkMode.NONE,
    val pinned: Boolean = false
)

@Entity(
    tableName = "person_references",
    indices = [Index("personId")]
)
class ReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val embedding: ByteArray,
    val quality: Float,
    val faceId: Long? = null,
    val createdAt: Long = 0
)

@Entity(
    tableName = "rejections",
    primaryKeys = ["faceId", "personId"],
    indices = [Index("personId")]
)
data class RejectionEntity(
    val faceId: Long,
    val personId: Long,
    val similarity: Float
)

@Entity(
    tableName = "linked_copies",
    primaryKeys = ["personId", "mediaId"]
)
data class LinkedCopyEntity(
    val personId: Long,
    val mediaId: Long,
    val copiedAt: Long
)

@Entity(tableName = "virtual_folders")
data class VirtualFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val relativePath: String,
    val createdAt: Long
)

@Entity(
    tableName = "person_separations",
    primaryKeys = ["personA", "personB"]
)
data class SeparationEntity(
    val personA: Long,
    val personB: Long
)

@Entity(
    tableName = "ai_moves",
    primaryKeys = ["mediaId", "personId"],
    indices = [Index("personId")]
)
data class AiMoveEntity(
    val mediaId: Long,
    val personId: Long,
    val sourceMediaId: Long,
    val kind: Int,
    val fromPath: String,
    val toPath: String,
    val movedAt: Long
)
