package com.example.livora.data.people

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.example.livora.data.people.cluster.FaceRecord
import com.example.livora.data.people.cluster.PersonMatcher
import com.example.livora.data.people.db.AiMoveEntity
import com.example.livora.data.people.db.AiMoveKind
import com.example.livora.data.people.db.AiMoveRow
import com.example.livora.data.people.db.CandidateFaceRow
import com.example.livora.data.people.db.LinkMode
import com.example.livora.data.people.db.LinkedCopyEntity
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.db.PersonEntity
import com.example.livora.data.people.db.PersonKind
import com.example.livora.data.people.db.PersonPhotoRow
import com.example.livora.data.people.db.PersonSummary
import com.example.livora.data.people.db.PhotoEntity
import com.example.livora.data.people.db.PhotoStatus
import com.example.livora.data.people.db.ReferenceEntity
import com.example.livora.data.people.db.RejectionEntity
import com.example.livora.data.people.media.CopyResult
import com.example.livora.data.people.media.FaceImages
import com.example.livora.data.people.media.MediaImages
import com.example.livora.data.people.media.MediaWriter
import com.example.livora.data.people.media.PhotoDecoder
import com.example.livora.data.people.ml.FaceIssue
import com.example.livora.data.people.ml.VectorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ReferenceInput(
    val embedding: FloatArray,
    val quality: Float,
    val crop: Bitmap?
)

class EnrollFace(
    val index: Int,
    val embedding: FloatArray,
    val quality: Float,
    val issues: Set<FaceIssue>,
    val crop: Bitmap
) {
    val usable: Boolean get() = quality >= ENROLL_MIN_QUALITY

    companion object {
        const val ENROLL_MIN_QUALITY = 0.4f
    }
}

class EnrollPhoto(val uri: Uri, val faces: List<EnrollFace>, val failed: Boolean)

class Suggestion(
    val faceId: Long,
    val mediaId: Long,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val orientation: Int,
    val dateTaken: Long,
    val fromPersonId: Long?
)

class SuggestionSet(
    val items: List<Suggestion>,
    val baseThreshold: Float,
    val effectiveThreshold: Float,
    val prototypes: Int
) {
    fun atThreshold(threshold: Float): List<Suggestion> = items.filter { it.score >= threshold }
}

class FaceStateSnapshot(val faceId: Long, val personId: Long?, val locked: Boolean)

class UndoToken(val restore: suspend () -> Unit)

class SamePersonResult(val targetId: Long, val merged: Int, val undo: UndoToken)

class IndexStatus(
    val indexedPhotos: Int,
    val faces: Int,
    val failedPhotos: Int,
    val lastScanAt: Long,
    val lastNewPhotos: Int
)

class PeopleRepository(
    private val context: Context,
    private val database: PeopleDatabase,
    private val prefs: PeoplePrefs,
    private val clustering: ClusteringService,
    private val analyzer: AnalyzerHolder,
    private val folders: FoldersRepository
) {

    val summaries: Flow<List<PersonSummary>> = database.persons().observeSummaries()

    fun observePerson(id: Long): Flow<PersonEntity?> = database.persons().observe(id)

    fun observeReferences(id: Long) = database.references().observeOfPerson(id)

    fun observePhotoCount(id: Long): Flow<Int> = database.faces().observePhotoCount(id)

    fun observeIndexedFaces(): Flow<Int> = database.faces().observeCount()

    fun observeScannedPhotos(): Flow<Int> = database.photos().observeScannedCount()

    val aiMoves: Flow<List<AiMoveRow>> = database.aiMoves().observeAll()

    suspend fun recordAiMoves(personId: Long, mediaIds: List<Long>, previous: Map<Long, String>, toPath: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.aiMoves().insertAll(
                mediaIds.map { AiMoveEntity(it, personId, it, AiMoveKind.MOVE, previous[it].orEmpty(), toPath, now) }
            )
        }

    suspend fun dropAiMoves(mediaIds: List<Long>) = withContext(Dispatchers.IO) {
        for (chunk in mediaIds.chunked(400)) database.aiMoves().delete(chunk)
    }

    suspend fun rejectAiMoves(rows: List<AiMoveRow>) = withContext(Dispatchers.IO) {
        for ((personId, group) in rows.groupBy { it.personId }) {
            val sources = group.map { it.sourceMediaId }.distinct()
            if (removeFromPerson(personId, sources) == null) rejectBestFaces(personId, sources)
        }
        val copies = rows.filter { it.kind == AiMoveKind.COPY }
        if (copies.isNotEmpty()) {
            val ids = copies.map { it.mediaId }
            MediaWriter.deleteOwned(context, ids)
            database.photos().deleteByIds(ids)
            for ((personId, group) in copies.groupBy { it.personId }) {
                database.linkedCopies().remove(personId, group.map { it.sourceMediaId })
            }
        }
        dropAiMoves(rows.map { it.mediaId })
    }

    private suspend fun rejectBestFaces(personId: Long, mediaIds: List<Long>) {
        val person = database.persons().byId(personId) ?: return
        val prototypes = prototypesOf(person)
        if (prototypes.isEmpty()) return
        val rejections = ArrayList<RejectionEntity>()
        for (chunk in mediaIds.chunked(400)) {
            val faces = database.faces().candidatesInMedia(chunk, 0f)
            for ((_, inPhoto) in faces.groupBy { it.mediaId }) {
                val scored = inPhoto.map { it to PersonMatcher.bestScore(VectorMath.fromBytes(it.embedding), prototypes) }
                val best = scored.maxByOrNull { it.second } ?: continue
                rejections.add(RejectionEntity(best.first.id, personId, best.second))
            }
        }
        if (rejections.isNotEmpty()) database.rejections().insertAll(rejections)
    }

    fun personPhotos(personId: Long): Flow<PagingData<PersonPhotoRow>> =
        Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, enablePlaceholders = false)) {
            database.faces().pagingPhotos(personId)
        }.flow

    suspend fun person(id: Long): PersonEntity? = database.persons().byId(id)

    suspend fun summary(id: Long): PersonSummary? = database.persons().summary(id)

    suspend fun rename(id: Long, name: String?) {
        val cleaned = name?.trim()?.takeIf { it.isNotEmpty() }
        database.persons().rename(id, cleaned)
        if (cleaned != null && database.references().count(id) == 0) addExemplarReferences(id, 8)
    }

    suspend fun addExemplarReferences(personId: Long, limit: Int): List<Long> = withContext(Dispatchers.IO) {
        val members = database.faces().vectorsOfPerson(personId)
            .map { FaceRecord(it.id, VectorMath.fromBytes(it.embedding), it.quality, it.mediaId) }
        val existing = database.references().ofPerson(personId)
        val room = MAX_REFERENCES - existing.size
        if (room <= 0) return@withContext emptyList()
        val vectors = existing.map { VectorMath.fromBytes(it.embedding) }.toMutableList()
        val chosen = PersonMatcher.selectPrototypes(members, limit = minOf(limit, room))
        val now = System.currentTimeMillis()
        val entities = ArrayList<ReferenceEntity>()
        for (face in chosen) {
            if (vectors.isNotEmpty() && PersonMatcher.bestScore(face.vector, vectors) > REFERENCE_DUPLICATE) continue
            vectors.add(face.vector)
            entities.add(ReferenceEntity(personId = personId, embedding = VectorMath.toBytes(face.vector), quality = face.quality, faceId = face.id, createdAt = now))
        }
        if (entities.isEmpty()) emptyList() else database.references().insertAll(entities)
    }

    suspend fun samePerson(personIds: List<Long>): SamePersonResult? = withContext(Dispatchers.IO) {
        val summaries = personIds.distinct().mapNotNull { database.persons().summary(it) }
        if (summaries.size < 2) return@withContext null
        val target = summaries.sortedWith(
            compareByDescending<PersonSummary> { it.name != null }.thenByDescending { it.photoCount }
        ).first()
        val others = summaries.filter { it.id != target.id }
        val undos = ArrayList<UndoToken>()
        for (other in others) mergePeople(target.id, other.id)?.let { undos.add(it) }
        val before = database.persons().byId(target.id)
        val wasPinned = before?.pinned == true
        database.persons().setPinned(target.id, true)
        val added = addExemplarReferences(target.id, 8)
        SamePersonResult(
            target.id,
            others.size,
            UndoToken {
                if (added.isNotEmpty()) database.references().deleteIds(added)
                for (undo in undos.reversed()) undo.restore()
                if (!wasPinned) database.persons().setPinned(target.id, false)
            }
        )
    }

    suspend fun absorb(intoId: Long, fromId: Long): SamePersonResult? = withContext(Dispatchers.IO) {
        val merged = mergePeople(intoId, fromId) ?: return@withContext null
        val wasPinned = database.persons().byId(intoId)?.pinned == true
        database.persons().setPinned(intoId, true)
        val added = addExemplarReferences(intoId, 8)
        SamePersonResult(
            intoId,
            1,
            UndoToken {
                if (added.isNotEmpty()) database.references().deleteIds(added)
                merged.restore()
                if (!wasPinned) database.persons().setPinned(intoId, false)
            }
        )
    }

    suspend fun mergeGroups(pairs: List<Pair<Long, Long>>): UndoToken? = withContext(Dispatchers.IO) {
        val parent = HashMap<Long, Long>()
        fun find(x: Long): Long {
            var root = x
            while (parent[root] != null && parent[root] != root) root = parent.getValue(root)
            parent[x] = root
            return root
        }
        for ((a, b) in pairs) {
            parent.putIfAbsent(a, a)
            parent.putIfAbsent(b, b)
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        val groups = parent.keys.groupBy { find(it) }.values.filter { it.size > 1 }
        val undos = ArrayList<UndoToken>()
        for (group in groups) samePerson(group)?.let { undos.add(it.undo) }
        if (undos.isEmpty()) null else UndoToken { for (undo in undos.reversed()) undo.restore() }
    }

    suspend fun separate(a: Long, b: Long): UndoToken = withContext(Dispatchers.IO) {
        val low = minOf(a, b)
        val high = maxOf(a, b)
        database.separations().insert(com.example.livora.data.people.db.SeparationEntity(low, high))
        UndoToken { database.separations().remove(low, high) }
    }

    suspend fun indexStatus(): IndexStatus = withContext(Dispatchers.IO) {
        IndexStatus(
            indexedPhotos = database.photos().count(),
            faces = database.faces().count(),
            failedPhotos = database.photos().failedCount(),
            lastScanAt = prefs.lastScanFinishedAt,
            lastNewPhotos = prefs.lastScanNewCount
        )
    }

    suspend fun resetIndex() = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.faces().clear()
            database.photos().clear()
            database.rejections().clear()
            database.persons().deleteUnlockedAuto()
        }
        prefs.initialScanDone = false
        prefs.groupingPending = false
        prefs.lastGeneration = -1L
        prefs.lastMediaCount = -1
    }

    suspend fun setHidden(id: Long, hidden: Boolean) = database.persons().setHidden(id, hidden)

    suspend fun photoCount(id: Long): Int = database.faces().photoCount(id)

    suspend fun topFaceIds(personId: Long, limit: Int): List<Long> = database.faces().topFaceIds(personId, limit)

    suspend fun mergeSuggestions(): List<MergeSuggestion> = clustering.suggestMerges()

    suspend fun mediaIdsOfPerson(id: Long): List<Long> =
        database.faces().mediaOfPerson(id).distinct()

    suspend fun avatarSource(summary: PersonSummary): Pair<Long?, Long?> = Pair(summary.coverFaceId, summary.coverRefId)

    suspend fun mergePeople(intoId: Long, fromId: Long): UndoToken? = withContext(Dispatchers.IO) {
        if (intoId == fromId) return@withContext null
        val from = database.persons().byId(fromId) ?: return@withContext null
        val faceRows = database.faces().vectorsOfPerson(fromId)
        val states = faceRows.map { FaceStateSnapshot(it.id, fromId, it.locked) }
        val refs = database.references().ofPerson(fromId).map { it.id }
        val rejections = database.rejections().ofPerson(fromId)
        database.withTransaction {
            database.faces().assignLocked(faceRows.map { it.id }, intoId, true)
            database.references().moveAll(fromId, intoId)
            database.rejections().moveAll(fromId, intoId)
            database.linkedCopies().deleteOfPerson(fromId)
            database.aiMoves().moveAll(fromId, intoId)
            database.persons().delete(fromId)
        }
        UndoToken {
            database.withTransaction {
                database.persons().upsert(from)
                for (chunk in states.chunked(400)) {
                    for (locked in listOf(true, false)) {
                        val ids = chunk.filter { it.locked == locked }.map { it.faceId }
                        if (ids.isNotEmpty()) database.faces().assignLocked(ids, fromId, locked)
                    }
                }
                if (refs.isNotEmpty()) database.references().moveIds(refs, fromId)
                if (rejections.isNotEmpty()) database.rejections().insertAll(rejections)
            }
        }
    }

    suspend fun removeFromPerson(personId: Long, mediaIds: List<Long>): UndoToken? = withContext(Dispatchers.IO) {
        val faceIds = ArrayList<Long>()
        for (chunk in mediaIds.chunked(400)) faceIds.addAll(database.faces().idsOfPersonInMedia(personId, chunk))
        if (faceIds.isEmpty()) return@withContext null
        val rows = ArrayList<com.example.livora.data.people.db.FaceVectorRow>()
        for (chunk in faceIds.chunked(400)) rows.addAll(database.faces().vectorsByIds(chunk))
        val states = rows.map { FaceStateSnapshot(it.id, it.personId, it.locked) }
        val vectors = rows.associate { it.id to VectorMath.fromBytes(it.embedding) }
        val references = database.references().ofPerson(personId)
        val prototypes = references.map { VectorMath.fromBytes(it.embedding) }
        val rejections = faceIds.map { id ->
            val v = vectors[id]
            val similarity = if (v == null || prototypes.isEmpty()) 0f else PersonMatcher.bestScore(v, prototypes)
            RejectionEntity(id, personId, similarity)
        }
        database.withTransaction {
            for (chunk in faceIds.chunked(400)) database.faces().assignLocked(chunk, null, false)
            database.rejections().insertAll(rejections)
            val drop = references.filter { it.faceId != null && it.faceId in faceIds }.map { it.id }
            if (drop.isNotEmpty()) database.references().deleteIds(drop)
        }
        val dropped = references.filter { it.faceId != null && it.faceId in faceIds }
        UndoToken {
            database.withTransaction {
                for (locked in listOf(true, false)) {
                    val byPerson = states.filter { it.locked == locked }.groupBy { it.personId }
                    for ((pid, list) in byPerson) {
                        for (chunk in list.map { it.faceId }.chunked(400)) database.faces().assignLocked(chunk, pid, locked)
                    }
                }
                database.rejections().remove(personId, faceIds)
                if (dropped.isNotEmpty()) database.references().insertAll(dropped)
            }
        }
    }

    suspend fun deletePerson(id: Long) = withContext(Dispatchers.IO) {
        val faceIds = database.faces().idsOfPerson(id)
        database.withTransaction {
            for (chunk in faceIds.chunked(400)) database.faces().assignLocked(chunk, null, false)
            database.references().deleteOfPerson(id)
            database.rejections().deleteOfPerson(id)
            database.linkedCopies().deleteOfPerson(id)
            database.aiMoves().deleteOfPerson(id)
            database.persons().delete(id)
        }
    }

    suspend fun createEnrolled(name: String, references: List<ReferenceInput>): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = database.persons().insert(
            PersonEntity(
                name = name.trim(),
                kind = PersonKind.ENROLLED,
                createdAt = now,
                threshold = PersonMatcher.DEFAULT_THRESHOLD
            )
        )
        insertReferences(id, references)
        id
    }

    suspend fun addReferences(personId: Long, references: List<ReferenceInput>) = withContext(Dispatchers.IO) {
        insertReferences(personId, references)
    }

    private suspend fun insertReferences(personId: Long, references: List<ReferenceInput>) {
        val now = System.currentTimeMillis()
        for (input in references) {
            val id = database.references().insert(
                ReferenceEntity(
                    personId = personId,
                    embedding = VectorMath.toBytes(input.embedding),
                    quality = input.quality,
                    createdAt = now
                )
            )
            input.crop?.let { FaceImages.saveReference(context, id, it) }
        }
    }

    suspend fun promoteToNamed(personId: Long, name: String) {
        rename(personId, name)
    }

    suspend fun analyzeForEnrollment(uri: Uri): EnrollPhoto = withContext(Dispatchers.IO) {
        val rotation = PhotoDecoder.exifRotation(context, uri)
        val bitmap = PhotoDecoder.decode(context, uri, rotation, 0, 0, 1600)
            ?: return@withContext EnrollPhoto(uri, emptyList(), true)
        try {
            val analyzed = analyzer.use { it.analyze(bitmap, keepAligned = false, minQuality = 0f) }
            val faces = analyzed.mapIndexed { index, face ->
                val crop = cropFace(bitmap, face.left, face.top, face.right, face.bottom)
                EnrollFace(index, face.embedding, face.quality, face.issues, crop)
            }
            EnrollPhoto(uri, faces, false)
        } finally {
            bitmap.recycle()
        }
    }

    private fun cropFace(source: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val w = source.width
        val h = source.height
        val cx = (left + right) / 2f * w
        val cy = (top + bottom) / 2f * h
        val size = maxOf((right - left) * w, (bottom - top) * h) * 1.7f
        val side = size.coerceIn(16f, minOf(w, h).toFloat())
        val x0 = (cx - side / 2f).coerceIn(0f, w - side).toInt()
        val y0 = (cy - side / 2f).coerceIn(0f, h - side).toInt()
        val region = Bitmap.createBitmap(source, x0, y0, side.toInt().coerceAtLeast(1), side.toInt().coerceAtLeast(1))
        val scaled = Bitmap.createScaledBitmap(region, ENROLL_CROP_SIZE, ENROLL_CROP_SIZE, true)
        if (scaled !== region) region.recycle()
        return scaled
    }

    suspend fun prototypesOf(person: PersonEntity): List<FloatArray> = withContext(Dispatchers.Default) {
        val refs = database.references().ofPerson(person.id).map { VectorMath.fromBytes(it.embedding) }
        val members = database.faces().vectorsOfPerson(person.id)
            .filter { person.kind == PersonKind.AUTO || it.locked }
            .map { FaceRecord(it.id, VectorMath.fromBytes(it.embedding), it.quality) }
        val exemplars = PersonMatcher.selectPrototypes(members, limit = 10).map { it.vector }
        (refs + exemplars).take(PersonMatcher.MAX_PROTOTYPES + 8)
    }

    private suspend fun negativesOf(personId: Long): Pair<List<FloatArray>, List<Float>> {
        val rejections = database.rejections().ofPerson(personId)
        if (rejections.isEmpty()) return Pair(emptyList(), emptyList())
        val vectors = ArrayList<FloatArray>()
        for (chunk in rejections.map { it.faceId }.chunked(400)) {
            database.faces().vectorsByIds(chunk).forEach { vectors.add(VectorMath.fromBytes(it.embedding)) }
        }
        return Pair(vectors, rejections.map { it.similarity })
    }

    suspend fun suggestions(personId: Long): SuggestionSet = withContext(Dispatchers.Default) {
        val person = database.persons().byId(personId)
            ?: return@withContext SuggestionSet(emptyList(), PersonMatcher.DEFAULT_THRESHOLD, PersonMatcher.DEFAULT_THRESHOLD, 0)
        val prototypes = prototypesOf(person)
        if (prototypes.isEmpty()) {
            return@withContext SuggestionSet(emptyList(), person.threshold, person.threshold, 0)
        }
        val (negatives, rejectedScores) = negativesOf(personId)
        val candidates = withContext(Dispatchers.IO) { database.faces().candidatesFor(personId, SUGGEST_MIN_QUALITY) }
        val records = candidates.map { FaceRecord(it.id, VectorMath.fromBytes(it.embedding), it.quality) }
        val effective = PersonMatcher.tightenedThreshold(person.threshold, rejectedScores)
        val matches = PersonMatcher.match(prototypes, negatives, records, PersonMatcher.MIN_THRESHOLD)
        val byId = candidates.associateBy { it.id }
        val ownMedia = database.faces().mediaOfPerson(personId).toHashSet()
        val copied = database.linkedCopies().mediaOf(personId).toHashSet()
        val inFolder = person.linkedFolderPath?.let { MediaImages.idsInRelativePath(context, it) } ?: emptySet()
        val bestPerPhoto = HashMap<Long, Suggestion>()
        for (match in matches) {
            val row: CandidateFaceRow = byId[match.faceId] ?: continue
            if (row.mediaId in ownMedia || row.mediaId in copied || row.mediaId in inFolder) continue
            val existing = bestPerPhoto[row.mediaId]
            if (existing == null || existing.score < match.score) {
                bestPerPhoto[row.mediaId] = Suggestion(
                    row.id, row.mediaId, match.score, row.boxLeft, row.boxTop, row.boxRight, row.boxBottom,
                    row.orientation, row.dateTaken, row.personId
                )
            }
        }
        SuggestionSet(
            bestPerPhoto.values.sortedByDescending { it.score },
            person.threshold,
            effective,
            prototypes.size
        )
    }

    suspend fun setThreshold(personId: Long, value: Float) =
        database.persons().setThreshold(personId, PersonMatcher.clampThreshold(value))

    suspend fun confirm(personId: Long, suggestions: List<Suggestion>): UndoToken? = withContext(Dispatchers.IO) {
        if (suggestions.isEmpty()) return@withContext null
        val person = database.persons().byId(personId) ?: return@withContext null
        val ids = suggestions.map { it.faceId }
        val rows = ArrayList<com.example.livora.data.people.db.FaceVectorRow>()
        for (chunk in ids.chunked(400)) rows.addAll(database.faces().vectorsByIds(chunk))
        val states = rows.map { FaceStateSnapshot(it.id, it.personId, it.locked) }
        val newReferences = ArrayList<ReferenceEntity>()
        if (person.kind == PersonKind.ENROLLED) {
            val existing = database.references().ofPerson(personId).map { VectorMath.fromBytes(it.embedding) }.toMutableList()
            val budget = MAX_REFERENCES - existing.size
            if (budget > 0) {
                val candidates = rows.filter { it.quality >= PersonMatcher.PROTOTYPE_MIN_QUALITY }
                    .sortedByDescending { it.quality }
                val now = System.currentTimeMillis()
                for (row in candidates) {
                    if (newReferences.size >= budget) break
                    val v = VectorMath.fromBytes(row.embedding)
                    if (existing.isNotEmpty() && PersonMatcher.bestScore(v, existing) > REFERENCE_DUPLICATE) continue
                    existing.add(v)
                    newReferences.add(ReferenceEntity(personId = personId, embedding = row.embedding, quality = row.quality, faceId = row.id, createdAt = now))
                }
            }
        }
        var insertedIds: List<Long> = emptyList()
        database.withTransaction {
            for (chunk in ids.chunked(400)) database.faces().assignLocked(chunk, personId, true)
            database.rejections().remove(personId, ids)
            if (newReferences.isNotEmpty()) insertedIds = database.references().insertAll(newReferences)
        }
        UndoToken {
            database.withTransaction {
                for (locked in listOf(true, false)) {
                    val byPerson = states.filter { it.locked == locked }.groupBy { it.personId }
                    for ((pid, list) in byPerson) {
                        for (chunk in list.map { it.faceId }.chunked(400)) database.faces().assignLocked(chunk, pid, locked)
                    }
                }
                if (insertedIds.isNotEmpty()) database.references().deleteIds(insertedIds)
            }
        }
    }

    suspend fun reject(personId: Long, suggestions: List<Suggestion>): UndoToken? = withContext(Dispatchers.IO) {
        if (suggestions.isEmpty()) return@withContext null
        val rows = suggestions.map { RejectionEntity(it.faceId, personId, it.score) }
        database.rejections().insertAll(rows)
        val ids = suggestions.map { it.faceId }
        UndoToken { database.rejections().remove(personId, ids) }
    }

    suspend fun linkFolder(personId: Long, path: String, name: String, mode: Int) {
        database.persons().setLink(personId, path, name, mode)
    }

    suspend fun unlinkFolder(personId: Long) {
        database.persons().setLink(personId, null, null, LinkMode.NONE)
    }

    suspend fun setLinkMode(personId: Long, mode: Int) = database.persons().setLinkMode(personId, mode)

    suspend fun copyPhotos(
        personId: Long?,
        mediaIds: List<Long>,
        relativePath: String,
        onProgress: (Int) -> Unit = {},
        byAi: Boolean = false
    ): CopyResult {
        val result = MediaWriter.copyImages(context, mediaIds, relativePath, onProgress)
        registerCopies(result.created.map { it.second })
        if (byAi && personId != null && result.created.isNotEmpty()) {
            val now = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                database.aiMoves().insertAll(
                    result.created.map { AiMoveEntity(it.second, personId, it.first, AiMoveKind.COPY, "", relativePath, now) }
                )
            }
        }
        if (personId != null && result.created.isNotEmpty()) {
            database.linkedCopies().insertAll(
                result.created.map { LinkedCopyEntity(personId, it.first, System.currentTimeMillis()) }
            )
        }
        return result
    }

    suspend fun undoCopies(personId: Long?, result: CopyResult) = withContext(Dispatchers.IO) {
        val newIds = result.created.map { it.second }
        MediaWriter.deleteOwned(context, newIds)
        if (newIds.isNotEmpty()) database.photos().deleteByIds(newIds)
        if (personId != null) database.linkedCopies().remove(personId, result.created.map { it.first })
    }

    suspend fun registerCopies(newIds: List<Long>) = withContext(Dispatchers.IO) {
        if (newIds.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        database.photos().upsertAll(
            newIds.map {
                PhotoEntity(
                    mediaId = it,
                    dateModified = 0,
                    dateTaken = now,
                    scannedAt = now,
                    faceCount = 0,
                    status = PhotoStatus.SKIPPED
                )
            }
        )
    }

    suspend fun syncPhotoDates(ids: List<Long>) = withContext(Dispatchers.IO) {
        val images = MediaImages.queryByIds(context, ids)
        for (image in images) database.photos().updateDates(image.id, image.dateModified, image.orientation)
    }

    suspend fun processAutoLinks(since: Long) = withContext(Dispatchers.IO) {
        val persons = database.persons().withLinkMode(LinkMode.AUTO)
        if (persons.isEmpty()) return@withContext
        val newMedia = database.photos().scannedSince(since)
        if (newMedia.isEmpty()) return@withContext
        for (person in persons) {
            val path = person.linkedFolderPath ?: continue
            if (!com.example.livora.data.people.media.MediaFolders.isWritableTarget(path)) continue
            val prototypes = prototypesOf(person)
            if (prototypes.isEmpty()) continue
            val (negatives, rejectedScores) = negativesOf(person.id)
            val threshold = PersonMatcher.tightenedThreshold(person.threshold, rejectedScores) + PersonMatcher.AUTO_ADD_MARGIN
            val candidates = ArrayList<CandidateFaceRow>()
            for (chunk in newMedia.chunked(400)) candidates.addAll(database.faces().candidatesInMedia(chunk, 0.5f))
            val byId = candidates.associateBy { it.id }
            val matches = withContext(Dispatchers.Default) {
                PersonMatcher.match(
                    prototypes,
                    negatives,
                    candidates.map { FaceRecord(it.id, VectorMath.fromBytes(it.embedding), it.quality) },
                    threshold
                )
            }
            val copied = database.linkedCopies().mediaOf(person.id).toHashSet()
            val inFolder = MediaImages.idsInRelativePath(context, path)
            val media = matches.mapNotNull { byId[it.faceId]?.mediaId }.distinct()
                .filter { it !in copied && it !in inFolder }
            if (media.isEmpty()) continue
            copyPhotos(person.id, media, path, byAi = true)
            val faceIds = matches.filter { byId[it.faceId]?.personId == null && byId[it.faceId]?.mediaId in media }.map { it.faceId }
            for (chunk in faceIds.chunked(400)) database.faces().assign(chunk, person.id)
        }
    }

    companion object {
        const val ENROLL_CROP_SIZE = 192
        const val SUGGEST_MIN_QUALITY = 0.35f
        const val MAX_REFERENCES = 24
        const val REFERENCE_DUPLICATE = 0.93f
    }
}
