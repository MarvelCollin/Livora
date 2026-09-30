package com.example.livora.data.cleaner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.livora.data.people.media.MediaImages

class CleanerFile(
    val id: Long,
    val video: Boolean,
    val name: String,
    val size: Long,
    val dateMs: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,
    val bucket: String,
    val durationMs: Long
) {
    val key: String get() = (if (video) "v:" else "i:") + id

    val uri: Uri get() = if (video) CleanerMedia.videoUri(id) else MediaImages.uri(id)

    val folder: String get() = relativePath.trimEnd('/').substringAfterLast('/').ifBlank { bucket }
}

object CleanerMedia {

    private val videoCollection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    fun videoUri(id: Long): Uri = ContentUris.withAppendedId(videoCollection, id)

    fun queryAll(context: Context): List<CleanerFile> = query(context, false) + query(context, true)

    @Suppress("DEPRECATION")
    private fun query(context: Context, video: Boolean): List<CleanerFile> {
        val collection = if (video) videoCollection else MediaImages.collection
        val columns = arrayListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        if (video) columns.add(MediaStore.Video.Media.DURATION)
        val out = ArrayList<CleanerFile>()
        val cursor = try {
            context.contentResolver.query(collection, columns.toTypedArray(), null, null, null)
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return out
        cursor.use { c ->
            while (c.moveToNext()) {
                val pathValue = c.getString(7).orEmpty()
                val relative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pathValue else MediaImages.relativeFromData(pathValue)
                val taken = if (c.isNull(3)) 0L else c.getLong(3)
                val modified = if (c.isNull(4)) 0L else c.getLong(4) * 1000L
                out.add(
                    CleanerFile(
                        id = c.getLong(0),
                        video = video,
                        name = c.getString(1).orEmpty(),
                        size = if (c.isNull(2)) 0L else c.getLong(2),
                        dateMs = if (taken > 0) taken else modified,
                        width = if (c.isNull(5)) 0 else c.getInt(5),
                        height = if (c.isNull(6)) 0 else c.getInt(6),
                        relativePath = relative,
                        bucket = c.getString(8).orEmpty(),
                        durationMs = if (video && !c.isNull(9)) c.getLong(9) else 0L
                    )
                )
            }
        }
        return out
    }
}
