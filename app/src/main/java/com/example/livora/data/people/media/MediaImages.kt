package com.example.livora.data.people.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

class MediaImage(
    val id: Long,
    val dateTaken: Long,
    val dateModified: Long,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val mime: String?,
    val displayName: String?,
    val size: Long
) {
    val sortDate: Long get() = if (dateTaken > 0) dateTaken else dateModified * 1000L
}

object MediaImages {

    val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    fun uri(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    @Suppress("DEPRECATION")
    private val projection: Array<String>
        get() {
            val base = arrayListOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.ORIENTATION,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                base.add(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                base.add(MediaStore.Images.Media.DATA)
            }
            return base.toTypedArray()
        }

    fun queryAll(context: Context, bucketId: Long? = null): List<MediaImage> {
        val out = ArrayList<MediaImage>()
        val resolver = context.contentResolver
        val selection = if (bucketId != null) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null
        val args = if (bucketId != null) arrayOf(bucketId.toString()) else null
        val order = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_MODIFIED} DESC"
        val cursor = try {
            resolver.query(collection, projection, selection, args, order)
        } catch (e: SecurityException) {
            null
        } catch (e: RuntimeException) {
            null
        } ?: return out
        cursor.use { c -> while (c.moveToNext()) out.add(read(c)) }
        return out
    }

    fun read(c: android.database.Cursor): MediaImage {
        val pathValue = c.getString(11).orEmpty()
        val relative = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) pathValue else relativeFromData(pathValue)
        return MediaImage(
            id = c.getLong(0),
            dateTaken = if (c.isNull(1)) 0L else c.getLong(1),
            dateModified = if (c.isNull(2)) 0L else c.getLong(2),
            width = if (c.isNull(3)) 0 else c.getInt(3),
            height = if (c.isNull(4)) 0 else c.getInt(4),
            orientation = if (c.isNull(5)) 0 else c.getInt(5),
            bucketId = if (c.isNull(6)) 0L else c.getLong(6),
            bucketName = c.getString(7).orEmpty(),
            relativePath = relative,
            mime = c.getString(8),
            displayName = c.getString(9),
            size = if (c.isNull(10)) 0L else c.getLong(10)
        )
    }

    @Suppress("DEPRECATION")
    fun relativeFromData(data: String): String {
        if (data.isEmpty()) return ""
        val root = Environment.getExternalStorageDirectory().absolutePath
        val trimmed = if (data.startsWith(root)) data.removePrefix(root).trimStart('/') else data
        val slash = trimmed.lastIndexOf('/')
        return if (slash < 0) "" else trimmed.substring(0, slash + 1)
    }

    fun queryByIds(context: Context, ids: List<Long>): List<MediaImage> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<MediaImage>(ids.size)
        for (chunk in ids.chunked(400)) {
            val placeholders = chunk.joinToString(",") { "?" }
            val cursor = try {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Images.Media._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray(),
                    null
                )
            } catch (e: RuntimeException) {
                null
            } ?: continue
            cursor.use { c -> while (c.moveToNext()) out.add(read(c)) }
        }
        return out
    }

    @Suppress("DEPRECATION")
    fun idsInRelativePath(context: Context, relativePath: String): Set<Long> {
        val out = HashSet<Long>()
        val cursor = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                    arrayOf(relativePath),
                    null
                )
            } else {
                val root = Environment.getExternalStorageDirectory().absolutePath
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Images.Media._ID),
                    "${MediaStore.Images.Media.DATA} LIKE ?",
                    arrayOf("$root/$relativePath%"),
                    null
                )
            }
        } catch (e: RuntimeException) {
            null
        } ?: return out
        cursor.use { c -> while (c.moveToNext()) out.add(c.getLong(0)) }
        return out
    }

    fun count(context: Context, bucketId: Long? = null): Int {
        val selection = if (bucketId != null) "${MediaStore.Images.Media.BUCKET_ID} = ?" else null
        val args = if (bucketId != null) arrayOf(bucketId.toString()) else null
        val cursor = try {
            context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID), selection, args, null)
        } catch (e: RuntimeException) {
            null
        } ?: return 0
        return cursor.use { it.count }
    }
}
