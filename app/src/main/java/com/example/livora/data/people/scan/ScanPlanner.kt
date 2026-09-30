package com.example.livora.data.people.scan

import com.example.livora.data.people.db.PhotoEntity
import com.example.livora.data.people.db.PhotoIndexRow
import com.example.livora.data.people.db.PhotoStatus
import com.example.livora.data.people.db.PipelineVersion
import com.example.livora.data.people.media.MediaImage

class PlanResult(
    val eligible: List<MediaImage>,
    val pending: List<MediaImage>,
    val removedIds: List<Long>,
    val sizeBackfill: List<Pair<Long, Long>>
)

object ScanPlanner {

    const val MIN_SIDE = 96

    fun isEligible(image: MediaImage, skipScreenshots: Boolean): Boolean {
        val mime = image.mime?.lowercase().orEmpty()
        if (mime == "image/gif" || mime == "image/svg+xml") return false
        if ((image.width in 1 until MIN_SIDE) || (image.height in 1 until MIN_SIDE)) return false
        if (skipScreenshots) {
            if (image.relativePath.contains("screenshot", ignoreCase = true)) return false
            if (image.displayName?.startsWith("Screenshot", ignoreCase = true) == true) return false
        }
        return true
    }

    fun needsScan(row: PhotoIndexRow?, image: MediaImage, pipelineVersion: Int): Boolean {
        if (row == null) return true
        if (row.status == PhotoStatus.SKIPPED) return false
        if (row.pipelineVersion != pipelineVersion) return true
        if (row.dateModified != image.dateModified) return true
        if (row.size >= 0 && row.size != image.size) return true
        return false
    }

    fun plan(
        images: List<MediaImage>,
        index: Map<Long, PhotoIndexRow>,
        skipScreenshots: Boolean,
        pipelineVersion: Int = PipelineVersion.CURRENT
    ): PlanResult {
        val present = HashSet<Long>(images.size * 2)
        for (image in images) present.add(image.id)
        val removed = index.keys.filter { it !in present }
        val eligible = images.filter { isEligible(it, skipScreenshots) }
        val pending = ArrayList<MediaImage>()
        val backfill = ArrayList<Pair<Long, Long>>()
        for (image in eligible) {
            val row = index[image.id]
            if (needsScan(row, image, pipelineVersion)) {
                pending.add(image)
            } else if (row != null && row.size < 0 && image.size >= 0) {
                backfill.add(Pair(image.id, image.size))
            }
        }
        return PlanResult(eligible, pending, removed, backfill)
    }

    fun record(
        image: MediaImage,
        faceCount: Int,
        failed: Boolean,
        previous: PhotoIndexRow?,
        now: Long
    ): PhotoEntity = PhotoEntity(
        mediaId = image.id,
        dateModified = image.dateModified,
        dateTaken = image.sortDate,
        scannedAt = now,
        faceCount = faceCount,
        status = if (failed) PhotoStatus.FAILED else PhotoStatus.SCANNED,
        orientation = image.orientation,
        size = image.size,
        retryCount = if (failed) (previous?.retryCount ?: 0) + 1 else 0,
        pipelineVersion = PipelineVersion.CURRENT
    )

    fun index(entity: PhotoEntity): PhotoIndexRow = PhotoIndexRow(
        entity.mediaId, entity.dateModified, entity.size, entity.status, entity.pipelineVersion, entity.retryCount
    )
}
