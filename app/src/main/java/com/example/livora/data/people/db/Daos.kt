package com.example.livora.data.people.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

class PhotoIndexRow(
    val mediaId: Long,
    val dateModified: Long,
    val size: Long,
    val status: Int,
    val pipelineVersion: Int,
    val retryCount: Int
)

class FaceVectorRow(
    val id: Long,
    val mediaId: Long,
    val embedding: ByteArray,
    val quality: Float,
    val personId: Long?,
    val locked: Boolean
)

class CandidateFaceRow(
    val id: Long,
    val mediaId: Long,
    val embedding: ByteArray,
    val quality: Float,
    val personId: Long?,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val dateTaken: Long,
    val orientation: Int
)

class FaceDiagRow(
    val id: Long,
    val mediaId: Long,
    val embedding: ByteArray,
    val quality: Float,
    val personId: Long?,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val score: Float
)

class FaceBoxRow(
    val id: Long,
    val mediaId: Long,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val orientation: Int
)

class PersonSummary(
    val id: Long,
    val name: String?,
    val hidden: Boolean,
    val kind: Int,
    val linkMode: Int,
    val linkedFolderName: String?,
    val photoCount: Int,
    val coverFaceId: Long?,
    val coverRefId: Long?
)

class PersonPhotoRow(
    val faceId: Long,
    val mediaId: Long,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val quality: Float,
    val dateTaken: Long,
    val orientation: Int
)

class PersonCountRow(val personId: Long, val photoCount: Int)

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos")
    suspend fun all(): List<PhotoEntity>

    @Query("SELECT mediaId, dateModified, size, status, pipelineVersion, retryCount FROM photos")
    suspend fun index(): List<PhotoIndexRow>

    @Query("SELECT COUNT(*) FROM photos")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM photos")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM photos WHERE status = 0")
    fun observeScannedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT mediaId FROM photos WHERE mediaId IN (:ids)")
    suspend fun existing(ids: List<Long>): List<Long>

    @Query("UPDATE photos SET dateModified = :dateModified, orientation = :orientation WHERE mediaId = :mediaId")
    suspend fun updateDates(mediaId: Long, dateModified: Long, orientation: Int)

    @Query("UPDATE photos SET size = :size WHERE mediaId = :mediaId AND size < 0")
    suspend fun backfillSize(mediaId: Long, size: Long)

    @Query("SELECT COUNT(*) FROM photos WHERE status = 1")
    suspend fun failedCount(): Int

    @Query("SELECT MAX(scannedAt) FROM photos WHERE status != 2")
    suspend fun lastScannedAt(): Long?

    @Query("DELETE FROM photos")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM photos WHERE scannedAt >= :since AND status = 0")
    suspend fun countScannedSince(since: Long): Int

    @Query("SELECT mediaId FROM photos WHERE scannedAt >= :since AND status = 0")
    suspend fun scannedSince(since: Long): List<Long>
}

@Dao
interface FaceDao {

    @Insert
    suspend fun insertAll(faces: List<FaceEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(faces: List<FaceEntity>)

    @Query("SELECT * FROM faces ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<FaceEntity>

    @Query("DELETE FROM faces")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM faces")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM faces")
    suspend fun count(): Int

    @Query("SELECT id, mediaId, embedding, quality, personId, locked FROM faces")
    suspend fun allVectors(): List<FaceVectorRow>

    @Query("SELECT id, mediaId, embedding, quality, personId, boxLeft, boxTop, boxRight, boxBottom, score FROM faces")
    suspend fun allForDiagnostics(): List<FaceDiagRow>

    @Query("SELECT id, mediaId, embedding, quality, personId, locked FROM faces WHERE personId IN (:personIds)")
    suspend fun vectorsOfPersons(personIds: List<Long>): List<FaceVectorRow>

    @Query("SELECT id, mediaId, embedding, quality, personId, locked FROM faces WHERE personId IS NULL AND quality >= :minQuality")
    suspend fun unassignedVectors(minQuality: Float): List<FaceVectorRow>

    @Query("SELECT id, mediaId, embedding, quality, personId, locked FROM faces WHERE personId = :personId")
    suspend fun vectorsOfPerson(personId: Long): List<FaceVectorRow>

    @Query("SELECT id, mediaId, embedding, quality, personId, locked FROM faces WHERE id IN (:ids)")
    suspend fun vectorsByIds(ids: List<Long>): List<FaceVectorRow>

    @Query(
        "SELECT f.id AS id, f.mediaId AS mediaId, f.boxLeft AS boxLeft, f.boxTop AS boxTop, f.boxRight AS boxRight, f.boxBottom AS boxBottom, " +
            "ph.orientation AS orientation FROM faces f JOIN photos ph ON ph.mediaId = f.mediaId WHERE f.id = :id"
    )
    suspend fun box(id: Long): FaceBoxRow?

    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun byId(id: Long): FaceEntity?

    @Query("UPDATE faces SET personId = :personId WHERE id IN (:ids)")
    suspend fun assign(ids: List<Long>, personId: Long?)

    @Query("UPDATE faces SET personId = :personId, locked = :locked WHERE id IN (:ids)")
    suspend fun assignLocked(ids: List<Long>, personId: Long?, locked: Boolean)

    @Query("UPDATE faces SET personId = :toId WHERE personId = :fromId")
    suspend fun moveAll(fromId: Long, toId: Long)

    @Query("SELECT id FROM faces WHERE personId = :personId")
    suspend fun idsOfPerson(personId: Long): List<Long>

    @Query("SELECT id FROM faces WHERE personId = :personId ORDER BY quality DESC LIMIT :limit")
    suspend fun topFaceIds(personId: Long, limit: Int): List<Long>

    @Query("SELECT id FROM faces WHERE personId = :personId AND mediaId IN (:mediaIds)")
    suspend fun idsOfPersonInMedia(personId: Long, mediaIds: List<Long>): List<Long>

    @Query("DELETE FROM faces WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByMedia(mediaIds: List<Long>)

    @Query("SELECT id FROM faces WHERE mediaId IN (:mediaIds)")
    suspend fun idsByMedia(mediaIds: List<Long>): List<Long>

    @Query("SELECT COUNT(DISTINCT mediaId) FROM faces WHERE personId = :personId")
    suspend fun photoCount(personId: Long): Int

    @Query("SELECT COUNT(DISTINCT mediaId) FROM faces WHERE personId = :personId")
    fun observePhotoCount(personId: Long): Flow<Int>

    @Query(
        "SELECT f.id AS faceId, f.mediaId AS mediaId, f.boxLeft AS boxLeft, f.boxTop AS boxTop, f.boxRight AS boxRight, f.boxBottom AS boxBottom, " +
            "MAX(f.quality) AS quality, ph.dateTaken AS dateTaken, ph.orientation AS orientation " +
            "FROM faces f JOIN photos ph ON ph.mediaId = f.mediaId " +
            "WHERE f.personId = :personId " +
            "GROUP BY f.mediaId " +
            "ORDER BY ph.dateTaken DESC, f.mediaId DESC"
    )
    fun pagingPhotos(personId: Long): PagingSource<Int, PersonPhotoRow>

    @Query(
        "SELECT f.id AS id, f.mediaId AS mediaId, f.embedding AS embedding, f.quality AS quality, f.personId AS personId, " +
            "f.boxLeft AS boxLeft, f.boxTop AS boxTop, f.boxRight AS boxRight, f.boxBottom AS boxBottom, ph.dateTaken AS dateTaken, ph.orientation AS orientation " +
            "FROM faces f JOIN photos ph ON ph.mediaId = f.mediaId " +
            "LEFT JOIN persons p ON p.id = f.personId " +
            "WHERE (f.personId IS NULL OR f.personId != :personId) " +
            "AND (f.personId IS NULL OR (p.name IS NULL AND p.kind = 0)) " +
            "AND f.quality >= :minQuality " +
            "AND f.id NOT IN (SELECT faceId FROM rejections WHERE personId = :personId)"
    )
    suspend fun candidatesFor(personId: Long, minQuality: Float): List<CandidateFaceRow>

    @Query(
        "SELECT f.id AS id, f.mediaId AS mediaId, f.embedding AS embedding, f.quality AS quality, f.personId AS personId, " +
            "f.boxLeft AS boxLeft, f.boxTop AS boxTop, f.boxRight AS boxRight, f.boxBottom AS boxBottom, ph.dateTaken AS dateTaken, ph.orientation AS orientation " +
            "FROM faces f JOIN photos ph ON ph.mediaId = f.mediaId " +
            "WHERE f.mediaId IN (:mediaIds) AND f.quality >= :minQuality"
    )
    suspend fun candidatesInMedia(mediaIds: List<Long>, minQuality: Float): List<CandidateFaceRow>

    @Query("SELECT mediaId FROM faces WHERE personId = :personId")
    suspend fun mediaOfPerson(personId: Long): List<Long>
}

@Dao
interface PersonDao {

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(person: PersonEntity)

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun byId(id: Long): PersonEntity?

    @Query("SELECT * FROM persons WHERE id = :id")
    fun observe(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM persons")
    suspend fun all(): List<PersonEntity>

    @Query("DELETE FROM persons")
    suspend fun clear()

    @Query("DELETE FROM persons WHERE kind = 0 AND name IS NULL AND pinned = 0 AND linkedFolderPath IS NULL")
    suspend fun deleteUnlockedAuto()

    @Query("SELECT * FROM persons WHERE linkMode = :mode AND linkedFolderPath IS NOT NULL")
    suspend fun withLinkMode(mode: Int): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE linkedFolderPath IS NOT NULL AND linkMode != 0")
    suspend fun linked(): List<PersonEntity>

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE persons SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE persons SET threshold = :threshold WHERE id = :id")
    suspend fun setThreshold(id: Long, threshold: Float)

    @Query("UPDATE persons SET linkedFolderPath = :path, linkedFolderName = :name, linkMode = :mode WHERE id = :id")
    suspend fun setLink(id: Long, path: String?, name: String?, mode: Int)

    @Query("UPDATE persons SET linkMode = :mode WHERE id = :id")
    suspend fun setLinkMode(id: Long, mode: Int)

    @Query("UPDATE persons SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE persons SET kind = :kind WHERE id = :id")
    suspend fun setKind(id: Long, kind: Int)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM persons WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)

    @Query(
        "SELECT p.id AS id, p.name AS name, p.hidden AS hidden, p.kind AS kind, p.linkMode AS linkMode, " +
            "p.linkedFolderName AS linkedFolderName, " +
            "(SELECT COUNT(DISTINCT f.mediaId) FROM faces f WHERE f.personId = p.id) AS photoCount, " +
            "(SELECT f2.id FROM faces f2 WHERE f2.personId = p.id ORDER BY f2.quality DESC LIMIT 1) AS coverFaceId, " +
            "(SELECT r.id FROM person_references r WHERE r.personId = p.id ORDER BY r.quality DESC LIMIT 1) AS coverRefId " +
            "FROM persons p " +
            "ORDER BY photoCount DESC, p.id ASC"
    )
    fun observeSummaries(): Flow<List<PersonSummary>>

    @Query(
        "SELECT p.id AS id, p.name AS name, p.hidden AS hidden, p.kind AS kind, p.linkMode AS linkMode, " +
            "p.linkedFolderName AS linkedFolderName, " +
            "(SELECT COUNT(DISTINCT f.mediaId) FROM faces f WHERE f.personId = p.id) AS photoCount, " +
            "(SELECT f2.id FROM faces f2 WHERE f2.personId = p.id ORDER BY f2.quality DESC LIMIT 1) AS coverFaceId, " +
            "(SELECT r.id FROM person_references r WHERE r.personId = p.id ORDER BY r.quality DESC LIMIT 1) AS coverRefId " +
            "FROM persons p WHERE p.id = :id"
    )
    suspend fun summary(id: Long): PersonSummary?

    @Query("DELETE FROM persons WHERE id NOT IN (SELECT DISTINCT personId FROM faces WHERE personId IS NOT NULL) AND kind = 0 AND name IS NULL AND id NOT IN (SELECT personId FROM person_references)")
    suspend fun deleteEmptyAuto()
}

@Dao
interface ReferenceDao {

    @Insert
    suspend fun insert(reference: ReferenceEntity): Long

    @Insert
    suspend fun insertAll(references: List<ReferenceEntity>): List<Long>

    @Query("SELECT * FROM person_references WHERE personId = :personId ORDER BY quality DESC")
    suspend fun ofPerson(personId: Long): List<ReferenceEntity>

    @Query("SELECT * FROM person_references")
    suspend fun all(): List<ReferenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAll(references: List<ReferenceEntity>)

    @Query("DELETE FROM person_references")
    suspend fun clear()

    @Query("SELECT * FROM person_references WHERE personId = :personId ORDER BY quality DESC")
    fun observeOfPerson(personId: Long): Flow<List<ReferenceEntity>>

    @Query("SELECT COUNT(*) FROM person_references WHERE personId = :personId")
    suspend fun count(personId: Long): Int

    @Query("UPDATE person_references SET personId = :toId WHERE personId = :fromId")
    suspend fun moveAll(fromId: Long, toId: Long)

    @Query("UPDATE person_references SET personId = :toId WHERE id IN (:ids)")
    suspend fun moveIds(ids: List<Long>, toId: Long)

    @Query("DELETE FROM person_references WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("DELETE FROM person_references WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM person_references WHERE personId = :personId")
    suspend fun deleteOfPerson(personId: Long)

    @Query("SELECT COUNT(*) FROM person_references WHERE personId = :personId AND faceId = :faceId")
    suspend fun countForFace(personId: Long, faceId: Long): Int
}

@Dao
interface RejectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rejections: List<RejectionEntity>)

    @Query("DELETE FROM rejections WHERE personId = :personId AND faceId IN (:faceIds)")
    suspend fun remove(personId: Long, faceIds: List<Long>)

    @Query("SELECT * FROM rejections")
    suspend fun all(): List<RejectionEntity>

    @Query("DELETE FROM rejections")
    suspend fun clear()

    @Query("SELECT * FROM rejections WHERE personId = :personId")
    suspend fun ofPerson(personId: Long): List<RejectionEntity>

    @Query("UPDATE OR REPLACE rejections SET personId = :toId WHERE personId = :fromId")
    suspend fun moveAll(fromId: Long, toId: Long)

    @Query("DELETE FROM rejections WHERE personId = :personId")
    suspend fun deleteOfPerson(personId: Long)

    @Query("DELETE FROM rejections WHERE faceId IN (:faceIds)")
    suspend fun deleteForFaces(faceIds: List<Long>)
}

@Dao
interface LinkedCopyDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(copies: List<LinkedCopyEntity>)

    @Query("SELECT mediaId FROM linked_copies WHERE personId = :personId")
    suspend fun mediaOf(personId: Long): List<Long>

    @Query("SELECT * FROM linked_copies")
    suspend fun all(): List<LinkedCopyEntity>

    @Query("DELETE FROM linked_copies")
    suspend fun clear()

    @Query("DELETE FROM linked_copies WHERE personId = :personId")
    suspend fun deleteOfPerson(personId: Long)

    @Query("DELETE FROM linked_copies WHERE personId = :personId AND mediaId IN (:mediaIds)")
    suspend fun remove(personId: Long, mediaIds: List<Long>)

    @Query("SELECT COUNT(*) FROM linked_copies WHERE personId = :personId")
    fun observeCount(personId: Long): Flow<Int>
}

@Dao
interface VirtualFolderDao {

    @Insert
    suspend fun insert(folder: VirtualFolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: VirtualFolderEntity)

    @Query("SELECT * FROM virtual_folders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VirtualFolderEntity>>

    @Query("SELECT * FROM virtual_folders")
    suspend fun all(): List<VirtualFolderEntity>

    @Query("DELETE FROM virtual_folders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM virtual_folders")
    suspend fun clear()

    @Query("DELETE FROM virtual_folders WHERE relativePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("UPDATE virtual_folders SET name = :name, relativePath = :path WHERE id = :id")
    suspend fun rename(id: Long, name: String, path: String)
}

@Dao
interface SeparationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(separation: SeparationEntity)

    @Query("SELECT * FROM person_separations")
    suspend fun all(): List<SeparationEntity>

    @Query("DELETE FROM person_separations WHERE personA = :id OR personB = :id")
    suspend fun deleteOfPerson(id: Long)

    @Query("DELETE FROM person_separations")
    suspend fun clear()

    @Query("DELETE FROM person_separations WHERE (personA = :a AND personB = :b) OR (personA = :b AND personB = :a)")
    suspend fun remove(a: Long, b: Long)
}
