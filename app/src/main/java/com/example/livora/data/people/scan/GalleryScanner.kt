package com.example.livora.data.people.scan

import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.example.livora.data.people.AnalyzerHolder
import com.example.livora.data.people.ClusteringService
import com.example.livora.data.people.PeoplePrefs
import com.example.livora.data.people.PeopleRepository
import com.example.livora.data.people.db.FaceEntity
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.db.PhotoEntity
import com.example.livora.data.people.db.PhotoIndexRow
import com.example.livora.data.people.db.PhotoStatus
import com.example.livora.data.people.media.FaceImages
import com.example.livora.data.people.media.MediaImage
import com.example.livora.data.people.media.MediaImages
import com.example.livora.data.people.media.PhotoDecoder
import com.example.livora.data.people.ml.VectorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class ScanPlan(
    val pending: List<MediaImage>,
    val eligibleTotal: Int,
    val alreadyScanned: Int,
    val runStartedAt: Long,
    val index: Map<Long, PhotoIndexRow>,
    val generation: Long = -1L,
    val mediaCount: Int = 0
) {
    val isEmpty: Boolean get() = pending.isEmpty()
}

class ScanRunResult(
    val processed: Int,
    val faces: Int,
    val completed: Boolean,
    val seconds: Float
)

class GalleryScanner(
    private val context: Context,
    private val database: PeopleDatabase,
    private val prefs: PeoplePrefs,
    private val analyzer: AnalyzerHolder,
    private val clustering: ClusteringService,
    private val repository: PeopleRepository
) {

    private class Decoded(val image: MediaImage, val bitmap: Bitmap?)

    private val runLock = Mutex()

    suspend fun <T> exclusive(block: suspend () -> T): T? {
        if (!runLock.tryLock()) return null
        try {
            return block()
        } finally {
            runLock.unlock()
        }
    }

    private val inferenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "people-inference").apply { priority = Thread.NORM_PRIORITY }
    }.asCoroutineDispatcher()

    suspend fun prepare(): ScanPlan = withContext(Dispatchers.IO) {
        ScanStatus.publish(ScanProgress(phase = ScanPhase.Preparing))
        val generation = com.example.livora.data.people.media.MediaChange.generation(context)
        val all = MediaImages.queryAll(context)
        val index = database.photos().index().associateBy { it.mediaId }
        val plan = ScanPlanner.plan(all, index, prefs.skipScreenshots)
        if (plan.removedIds.isNotEmpty()) purge(plan.removedIds)
        for ((id, size) in plan.sizeBackfill) database.photos().backfillSize(id, size)
        prefs.scanTotal = plan.eligible.size
        ScanPlan(
            plan.pending,
            plan.eligible.size,
            plan.eligible.size - plan.pending.size,
            System.currentTimeMillis(),
            index,
            generation,
            all.size
        )
    }

    private suspend fun purge(ids: List<Long>) {
        for (chunk in ids.chunked(400)) {
            val faceIds = database.faces().idsByMedia(chunk)
            database.withTransaction {
                if (faceIds.isNotEmpty()) database.rejections().deleteForFaces(faceIds)
                database.faces().deleteByMedia(chunk)
                database.photos().deleteByIds(chunk)
            }
            FaceImages.forget(context, faceIds)
        }
        database.persons().deleteEmptyAuto()
    }

    suspend fun execute(
        plan: ScanPlan,
        onProgress: (ScanProgress) -> Unit,
        isStopped: () -> Boolean
    ): ScanRunResult = withContext(inferenceDispatcher) {
        val startedAt = SystemClock.elapsedRealtime()
        val pending = plan.pending
        var processed = 0
        var facesFound = 0
        var sinceGrouping = 0
        var facesSinceGrouping = 0
        var lastFlush = SystemClock.elapsedRealtime()
        var lastReport = 0L
        var peakPssKb = 0
        var decodeWaitNanos = 0L
        val recent = ArrayDeque<Long>()
        val photoBatch = ArrayList<PhotoEntity>()
        val faceBatch = ArrayList<Pair<Long, com.example.livora.data.people.ml.AnalyzedFace>>()
        val rescanned = ArrayList<Long>()

        suspend fun flush() {
            if (photoBatch.isEmpty()) return
            val photos = ArrayList(photoBatch)
            val faces = ArrayList(faceBatch)
            val replaced = ArrayList(rescanned)
            photoBatch.clear()
            faceBatch.clear()
            rescanned.clear()
            val entities = faces.map { (mediaId, face) ->
                FaceEntity(
                    mediaId = mediaId,
                    boxLeft = face.left,
                    boxTop = face.top,
                    boxRight = face.right,
                    boxBottom = face.bottom,
                    score = face.detectionScore,
                    quality = face.quality,
                    embedding = VectorMath.toBytes(face.embedding)
                )
            }
            database.withTransaction {
                if (replaced.isNotEmpty()) {
                    val old = database.faces().idsByMedia(replaced)
                    if (old.isNotEmpty()) database.rejections().deleteForFaces(old)
                    database.faces().deleteByMedia(replaced)
                }
                database.photos().upsertAll(photos)
                if (entities.isNotEmpty()) database.faces().insertAll(entities)
            }
            if (entities.isNotEmpty()) prefs.groupingPending = true
            lastFlush = SystemClock.elapsedRealtime()
        }

        val channel = Channel<Decoded>(capacity = 1)
        val cursor = AtomicInteger(0)
        val known = plan.index.keys

        coroutineScope {
            val producers = List(DECODE_PARALLELISM) {
                launch(Dispatchers.IO) {
                    while (true) {
                        if (isStopped()) break
                        val i = cursor.getAndIncrement()
                        if (i >= pending.size) break
                        val image = pending[i]
                        val bitmap = try {
                            PhotoDecoder.decode(
                                context,
                                MediaImages.uri(image.id),
                                image.orientation,
                                image.width,
                                image.height,
                                DECODE_LONG_SIDE
                            )
                        } catch (e: Exception) {
                            null
                        }
                        channel.send(Decoded(image, bitmap))
                    }
                }
            }
            launch {
                producers.joinAll()
                channel.close()
            }
            ScanStatus.publish(
                ScanProgress(ScanPhase.Scanning, plan.alreadyScanned, plan.eligibleTotal, 0, 0f)
            )
            var received = 0
            while (true) {
                val waitStart = System.nanoTime()
                val decoded = channel.receiveCatching().getOrNull() ?: break
                decodeWaitNanos += System.nanoTime() - waitStart
                received++
                val image = decoded.image
                val bitmap = decoded.bitmap
                var faceCount = 0
                var status = PhotoStatus.SCANNED
                if (bitmap == null) {
                    status = PhotoStatus.FAILED
                } else {
                    val faces = try {
                        analyzer.use { it.analyze(bitmap) }
                    } catch (e: Exception) {
                        status = PhotoStatus.FAILED
                        emptyList()
                    }
                    bitmap.recycle()
                    faceCount = faces.size
                    for (face in faces) faceBatch.add(Pair(image.id, face))
                    facesFound += faces.size
                    facesSinceGrouping += faces.size
                }
                if (image.id in known) rescanned.add(image.id)
                photoBatch.add(
                    ScanPlanner.record(image, faceCount, status == PhotoStatus.FAILED, plan.index[image.id], System.currentTimeMillis())
                )
                processed++
                sinceGrouping++
                val now = SystemClock.elapsedRealtime()
                recent.addLast(now)
                while (recent.size > 1 && now - recent.first() > RATE_WINDOW_MS) recent.removeFirst()
                if (photoBatch.size >= FLUSH_PHOTOS || now - lastFlush >= FLUSH_MS) flush()
                if (processed % 100 == 0) {
                    val info = Debug.MemoryInfo()
                    Debug.getMemoryInfo(info)
                    if (info.totalPss > peakPssKb) peakPssKb = info.totalPss
                }
                if (sinceGrouping >= GROUP_EVERY_PHOTOS && facesSinceGrouping > 0) {
                    flush()
                    clustering.run(finalPass = false)
                    sinceGrouping = 0
                    facesSinceGrouping = 0
                }
                val rate = if (recent.size > 1) (recent.size - 1) * 1000f / (now - recent.first()).coerceAtLeast(1L) else 0f
                val progress = ScanProgress(
                    ScanPhase.Scanning,
                    plan.alreadyScanned + processed,
                    plan.eligibleTotal,
                    facesFound,
                    rate
                )
                ScanStatus.publish(progress)
                if (now - lastReport >= REPORT_MS) {
                    lastReport = now
                    onProgress(progress)
                }
            }
        }
        flush()
        val stopped = isStopped()
        val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000f
        val timings = analyzer.use { it.timings }
        Log.i(
            TAG,
            "photos=$processed faces=$facesFound seconds=${"%.1f".format(seconds)} " +
                "photosPerSecond=${"%.1f".format(if (seconds > 0) processed / seconds else 0f)} " +
                "detectMsPerPhoto=${"%.1f".format(if (timings.photos > 0) timings.detectNanos / 1e6 / timings.photos else 0.0)} " +
                "embedMsPerFace=${"%.1f".format(if (timings.faces > 0) timings.embedNanos / 1e6 / timings.faces else 0.0)} " +
                "decodeWaitMsPerPhoto=${"%.1f".format(if (processed > 0) decodeWaitNanos / 1e6 / processed else 0.0)} " +
                "peakPssMb=${peakPssKb / 1024} stopped=$stopped"
        )
        ScanRunResult(processed, facesFound, !stopped, seconds)
    }

    suspend fun finish(plan: ScanPlan, result: ScanRunResult) {
        if (!result.completed) {
            ScanStatus.update { it.copy(phase = ScanPhase.Idle) }
            return
        }
        ScanStatus.update { it.copy(phase = ScanPhase.Grouping) }
        val started = SystemClock.elapsedRealtime()
        val summary = clustering.run(finalPass = true)
        repository.processAutoLinks(plan.runStartedAt)
        Log.i(
            TAG,
            "grouping created=${summary.created} merged=${summary.merged} assigned=${summary.assigned} " +
                "ejected=${summary.ejected} ms=${SystemClock.elapsedRealtime() - started}"
        )
        prefs.groupingPending = false
        prefs.initialScanDone = true
        prefs.lastScanNewCount = result.processed
        com.example.livora.data.people.media.MediaChange.remember(context, prefs, plan.generation, plan.mediaCount)
        prefs.lastScanFinishedAt = System.currentTimeMillis()
        val total = database.photos().count()
        ScanStatus.publish(
            ScanProgress(ScanPhase.Done, plan.eligibleTotal, plan.eligibleTotal, database.faces().count(), 0f)
        )
        Log.i(TAG, "indexedPhotos=$total")
        if (com.example.livora.BuildConfig.DEBUG) Diagnostics.run(database)
        analyzer.release()
    }

    companion object {
        const val TAG = "PeopleScan"
        const val DECODE_LONG_SIDE = 1280
        const val DECODE_PARALLELISM = 2
        const val FLUSH_PHOTOS = 25
        const val FLUSH_MS = 2000L
        const val GROUP_EVERY_PHOTOS = 250
        const val REPORT_MS = 1500L
        const val RATE_WINDOW_MS = 10_000L
    }
}
