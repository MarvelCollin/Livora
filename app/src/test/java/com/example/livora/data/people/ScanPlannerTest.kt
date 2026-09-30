package com.example.livora.data.people

import com.example.livora.data.people.db.PhotoIndexRow
import com.example.livora.data.people.db.PhotoStatus
import com.example.livora.data.people.db.PipelineVersion
import com.example.livora.data.people.media.MediaImage
import com.example.livora.data.people.scan.ScanPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPlannerTest {

    private fun image(
        id: Long,
        modified: Long = 1000L + id,
        size: Long = 2_000_000L + id,
        path: String = "DCIM/Camera/",
        name: String = "IMG_$id.jpg",
        width: Int = 4000,
        height: Int = 3000,
        mime: String = "image/jpeg"
    ) = MediaImage(
        id = id,
        dateTaken = 1_700_000_000_000L + id,
        dateModified = modified,
        width = width,
        height = height,
        orientation = 0,
        bucketId = 1L,
        bucketName = "Camera",
        relativePath = path,
        mime = mime,
        displayName = name,
        size = size
    )

    private class Gallery(var images: List<MediaImage>) {
        val index = HashMap<Long, PhotoIndexRow>()
        val processed = ArrayList<Long>()

        fun scan(skipScreenshots: Boolean = true, version: Int = PipelineVersion.CURRENT, failing: Set<Long> = emptySet()): Int {
            val plan = ScanPlanner.plan(images, index, skipScreenshots, version)
            for (id in plan.removedIds) index.remove(id)
            for (image in plan.pending) {
                processed.add(image.id)
                val failed = image.id in failing
                val entity = ScanPlanner.record(image, if (failed) 0 else 1, failed, index[image.id], 5L)
                val row = ScanPlanner.index(entity)
                index[image.id] = PhotoIndexRow(row.mediaId, row.dateModified, row.size, row.status, version, row.retryCount)
            }
            return plan.pending.size
        }
    }

    @Test
    fun secondPassProcessesNothing() {
        val gallery = Gallery(List(120) { image(it.toLong() + 1) })
        assertEquals(120, gallery.scan())
        assertEquals(0, gallery.scan())
        assertEquals(0, gallery.scan())
        assertEquals(120, gallery.processed.size)
    }

    @Test
    fun changedModifiedDateReprocessesExactlyThatPhoto() {
        val gallery = Gallery(List(50) { image(it.toLong() + 1) })
        gallery.scan()
        gallery.processed.clear()
        gallery.images = gallery.images.map { if (it.id == 17L) image(17L, modified = 999_999L) else it }
        assertEquals(1, gallery.scan())
        assertEquals(listOf(17L), gallery.processed)
        assertEquals(0, gallery.scan())
    }

    @Test
    fun changedSizeReprocessesExactlyThatPhoto() {
        val gallery = Gallery(List(30) { image(it.toLong() + 1) })
        gallery.scan()
        gallery.processed.clear()
        gallery.images = gallery.images.map { if (it.id == 4L) image(4L, size = 7L) else it }
        assertEquals(1, gallery.scan())
        assertEquals(listOf(4L), gallery.processed)
    }

    @Test
    fun newPhotosAreTheOnlyOnesScannedAfterTheFirstPass() {
        val gallery = Gallery(List(20) { image(it.toLong() + 1) })
        gallery.scan()
        gallery.processed.clear()
        gallery.images = gallery.images + listOf(image(100L), image(101L))
        assertEquals(2, gallery.scan())
        assertEquals(listOf(100L, 101L), gallery.processed)
    }

    @Test
    fun photosWithoutFacesAndFailedPhotosAreNeverReprocessed() {
        val gallery = Gallery(List(10) { image(it.toLong() + 1) })
        assertEquals(10, gallery.scan(failing = setOf(3L, 4L)))
        assertEquals(PhotoStatus.FAILED, gallery.index.getValue(3L).status)
        assertEquals(1, gallery.index.getValue(3L).retryCount)
        assertEquals(0, gallery.scan())
    }

    @Test
    fun failedPhotoIsRetriedOnlyWhenItChangesAndCountsRetries() {
        val gallery = Gallery(listOf(image(1L), image(2L)))
        gallery.scan(failing = setOf(1L))
        gallery.images = listOf(image(1L, modified = 5L), image(2L))
        gallery.processed.clear()
        assertEquals(1, gallery.scan(failing = setOf(1L)))
        assertEquals(2, gallery.index.getValue(1L).retryCount)
        assertEquals(0, gallery.scan())
    }

    @Test
    fun deletedPhotosAreReportedForPruning() {
        val gallery = Gallery(List(6) { image(it.toLong() + 1) })
        gallery.scan()
        val plan = ScanPlanner.plan(gallery.images.filter { it.id != 2L && it.id != 5L }, gallery.index, true)
        assertEquals(setOf(2L, 5L), plan.removedIds.toSet())
        assertTrue(plan.pending.isEmpty())
    }

    @Test
    fun pipelineVersionChangeTriggersRescanOfEverything() {
        val gallery = Gallery(List(15) { image(it.toLong() + 1) })
        gallery.scan(version = PipelineVersion.CURRENT - 1)
        gallery.processed.clear()
        assertEquals(15, gallery.scan())
        assertEquals(0, gallery.scan())
    }

    @Test
    fun legacyRowsWithUnknownSizeAreBackfilledNotRescanned() {
        val images = List(8) { image(it.toLong() + 1) }
        val index = images.associate { it.id to PhotoIndexRow(it.id, it.dateModified, -1L, PhotoStatus.SCANNED, PipelineVersion.CURRENT, 0) }
        val plan = ScanPlanner.plan(images, index, true)
        assertTrue(plan.pending.isEmpty())
        assertEquals(8, plan.sizeBackfill.size)
    }

    @Test
    fun ineligiblePhotosAreSkipped() {
        val images = listOf(
            image(1L),
            image(2L, path = "Pictures/Screenshots/", name = "Screenshot_1.png"),
            image(3L, width = 60, height = 60),
            image(4L, name = "anim.gif", mime = "image/gif")
        )
        val plan = ScanPlanner.plan(images, emptyMap(), skipScreenshots = true)
        assertEquals(listOf(1L), plan.pending.map { it.id })
        val withShots = ScanPlanner.plan(images, emptyMap(), skipScreenshots = false)
        assertEquals(listOf(1L, 2L), withShots.pending.map { it.id })
    }

    @Test
    fun copiesCreatedByTheAppAreNeverScanned() {
        val images = listOf(image(1L), image(2L))
        val index = mapOf(2L to PhotoIndexRow(2L, 0L, -1L, PhotoStatus.SKIPPED, PipelineVersion.CURRENT, 0))
        val plan = ScanPlanner.plan(images, index, true)
        assertEquals(listOf(1L), plan.pending.map { it.id })
    }
}
