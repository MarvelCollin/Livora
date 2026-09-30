package com.example.livora.data.people.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.livora.data.people.PeoplePrefs
import com.example.livora.data.people.db.FaceEntity
import com.example.livora.data.people.db.LinkedCopyEntity
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.db.PersonEntity
import com.example.livora.data.people.db.PhotoEntity
import com.example.livora.data.people.db.PhotoStatus
import com.example.livora.data.people.db.PipelineVersion
import com.example.livora.data.people.db.ReferenceEntity
import com.example.livora.data.people.db.RejectionEntity
import com.example.livora.data.people.db.SeparationEntity
import com.example.livora.data.people.db.VirtualFolderEntity
import com.example.livora.data.people.media.MediaImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ExportSummary(val persons: Int, val photos: Int, val faces: Int)

class ImportSummary(val persons: Int, val matchedPhotos: Int, val needRescan: Int, val faces: Int)

class PeopleBackup(
    private val context: Context,
    private val database: PeopleDatabase,
    private val prefs: PeoplePrefs
) {

    suspend fun export(uri: Uri): ExportSummary = withContext(Dispatchers.IO) {
        val persons = database.persons().all()
        val photos = database.photos().all()
        val faces = ArrayList<FaceEntity>()
        var offset = 0
        while (true) {
            val page = database.faces().page(2000, offset)
            if (page.isEmpty()) break
            faces.addAll(page)
            offset += page.size
        }
        val data = ArchiveData(
            schemaVersion = SCHEMA_VERSION,
            pipelineVersion = PipelineVersion.CURRENT,
            createdAt = System.currentTimeMillis(),
            strictness = prefs.strictness,
            minPhotos = prefs.minPhotos.value,
            persons = persons.map {
                ArchivePerson(it.id, it.name, it.hidden, it.kind, it.createdAt, it.threshold, it.linkedFolderPath, it.linkedFolderName, it.linkMode, it.pinned)
            },
            references = database.references().all().map {
                ArchiveReference(it.id, it.personId, it.quality, it.faceId, it.createdAt, it.embedding)
            },
            rejections = database.rejections().all().map { ArchiveRejection(it.faceId, it.personId, it.similarity) },
            separations = database.separations().all().map { ArchiveSeparation(it.personA, it.personB) },
            linkedCopies = database.linkedCopies().all().map { ArchiveLinkedCopy(it.personId, it.mediaId, it.copiedAt) },
            folders = database.virtualFolders().all().map { ArchiveFolder(it.id, it.name, it.relativePath, it.createdAt) },
            photos = photos.map {
                ArchivePhoto(it.mediaId, it.dateModified, it.size, it.dateTaken, it.scannedAt, it.faceCount, it.status, it.orientation, it.retryCount, it.pipelineVersion)
            },
            faces = faces.map {
                ArchiveFace(
                    it.id, it.mediaId, it.boxLeft, it.boxTop, it.boxRight, it.boxBottom, it.score, it.quality,
                    it.personId, it.locked, it.modelVersion, it.pipelineVersion, it.embedding
                )
            }
        )
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("The file could not be opened")
        PeopleArchive.write(data, output)
        ExportSummary(persons.size, photos.size, faces.size)
    }

    suspend fun import(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("The file could not be opened")
        val data = input.use { PeopleArchive.read(it) }
        val current = MediaImages.queryAll(context).associateBy { it.id }
        val keptPhotos = ArrayList<ArchivePhoto>()
        var needRescan = 0
        for (photo in data.photos) {
            val image = current[photo.mediaId]
            val matches = image != null &&
                (photo.status == PhotoStatus.SKIPPED || (
                    image.dateModified == photo.dateModified &&
                        (photo.size < 0 || image.size == photo.size) &&
                        photo.pipelineVersion == PipelineVersion.CURRENT
                    ))
            if (matches) keptPhotos.add(photo) else needRescan++
        }
        val keptIds = keptPhotos.map { it.mediaId }.toHashSet()
        val keptFaces = data.faces.filter { it.mediaId in keptIds }
        database.withTransaction {
            database.faces().clear()
            database.photos().clear()
            database.persons().clear()
            database.references().clear()
            database.rejections().clear()
            database.separations().clear()
            database.linkedCopies().clear()
            database.virtualFolders().clear()
            for (chunk in data.persons.chunked(300)) {
                for (p in chunk) {
                    database.persons().upsert(
                        PersonEntity(p.id, p.name, p.hidden, p.kind, p.createdAt, p.threshold, p.linkedFolderPath, p.linkedFolderName, p.linkMode, p.pinned)
                    )
                }
            }
            database.photos().upsertAll(
                keptPhotos.map {
                    PhotoEntity(it.mediaId, it.dateModified, it.dateTaken, it.scannedAt, it.faceCount, it.status, it.orientation, it.size, it.retryCount, it.pipelineVersion)
                }
            )
            for (chunk in keptFaces.chunked(500)) {
                database.faces().restoreAll(
                    chunk.map {
                        FaceEntity(
                            it.id, it.mediaId, it.boxLeft, it.boxTop, it.boxRight, it.boxBottom, it.score, it.quality,
                            it.embedding, it.personId, it.locked, it.modelVersion, it.pipelineVersion
                        )
                    }
                )
            }
            database.references().restoreAll(
                data.references.map { ReferenceEntity(it.id, it.personId, it.embedding, it.quality, it.faceId, it.createdAt) }
            )
            database.rejections().insertAll(data.rejections.map { RejectionEntity(it.faceId, it.personId, it.similarity) })
            database.separations().let { dao -> data.separations.forEach { dao.insert(SeparationEntity(it.personA, it.personB)) } }
            database.linkedCopies().insertAll(data.linkedCopies.map { LinkedCopyEntity(it.personId, it.mediaId, it.copiedAt) })
            database.virtualFolders().let { dao -> data.folders.forEach { dao.upsert(VirtualFolderEntity(it.id, it.name, it.relativePath, it.createdAt)) } }
            database.persons().deleteEmptyAuto()
        }
        prefs.strictness = data.strictness
        prefs.setMinPhotos(data.minPhotos)
        prefs.initialScanDone = true
        prefs.groupingPending = false
        prefs.lastGeneration = -1L
        prefs.lastMediaCount = -1
        ImportSummary(data.persons.size, keptPhotos.size, needRescan, keptFaces.size)
    }

    companion object {
        const val SCHEMA_VERSION = 3
    }
}
