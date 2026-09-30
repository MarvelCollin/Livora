package com.example.livora.data.people.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CopyResult(val created: List<Pair<Long, Long>>, val failed: Int)

object MediaWriter {

    val supportsConsentRequests: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private val writeCollection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    suspend fun copyImages(
        context: Context,
        sourceIds: List<Long>,
        relativePath: String,
        onProgress: (Int) -> Unit = {}
    ): CopyResult = withContext(Dispatchers.IO) {
        val sources = MediaImages.queryByIds(context, sourceIds).associateBy { it.id }
        val created = ArrayList<Pair<Long, Long>>()
        var failed = 0
        var done = 0
        for (id in sourceIds) {
            val source = sources[id]
            val newId = if (source == null) null else copyOne(context, source, relativePath)
            if (newId == null) failed++ else created.add(Pair(id, newId))
            done++
            onProgress(done)
        }
        CopyResult(created, failed)
    }

    private fun copyOne(context: Context, source: MediaImage, relativePath: String): Long? {
        val resolver = context.contentResolver
        val name = source.displayName ?: "IMG_${source.id}.jpg"
        val mime = source.mime ?: "image/jpeg"
        val input = try {
            resolver.openInputStream(MediaImages.uri(source.id))
        } catch (e: Exception) {
            null
        } ?: return null
        input.use { stream ->
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyScoped(context, stream, name, mime, relativePath, source.dateTaken)
            } else {
                copyLegacy(context, stream, name, mime, relativePath, source.dateTaken)
            }
        }
    }

    private fun copyScoped(
        context: Context,
        input: java.io.InputStream,
        name: String,
        mime: String,
        relativePath: String,
        dateTaken: Long
    ): Long? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Images.Media.IS_PENDING, 1)
            if (dateTaken > 0) put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
        }
        val target = try {
            resolver.insert(writeCollection, values)
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            resolver.openOutputStream(target)?.use { out -> input.copyTo(out, 64 * 1024) } ?: throw IllegalStateException()
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(target, done, null, null)
            ContentUris.parseId(target)
        } catch (e: Exception) {
            try {
                resolver.delete(target, null, null)
            } catch (inner: Exception) {
                Unit
            }
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun copyLegacy(
        context: Context,
        input: java.io.InputStream,
        name: String,
        mime: String,
        relativePath: String,
        dateTaken: Long
    ): Long? {
        val root = Environment.getExternalStorageDirectory()
        val directory = File(root, relativePath)
        if (!directory.exists() && !directory.mkdirs()) return null
        var file = File(directory, name)
        var counter = 1
        while (file.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            file = File(directory, if (ext.isEmpty()) "$base ($counter)" else "$base ($counter).$ext")
            counter++
        }
        return try {
            FileOutputStream(file).use { out -> input.copyTo(out, 64 * 1024) }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                if (dateTaken > 0) put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
            }
            val uri = context.contentResolver.insert(writeCollection, values) ?: return null
            ContentUris.parseId(uri)
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    suspend fun copyFromUris(
        context: Context,
        uris: List<Uri>,
        relativePath: String,
        onProgress: (Int) -> Unit = {}
    ): List<Long> = withContext(Dispatchers.IO) {
        val created = ArrayList<Long>()
        var done = 0
        for (uri in uris) {
            val name = displayNameOf(context, uri) ?: "IMG_${System.currentTimeMillis()}_$done.jpg"
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                null
            }
            if (input != null) {
                val id = input.use { stream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        copyScoped(context, stream, name, mime, relativePath, 0L)
                    } else {
                        copyLegacy(context, stream, name, mime, relativePath, 0L)
                    }
                }
                if (id != null) created.add(id)
            }
            done++
            onProgress(done)
        }
        created
    }

    private fun displayNameOf(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun writeRequest(context: Context, ids: List<Long>): IntentSender? {
        if (!supportsConsentRequests || ids.isEmpty()) return null
        return MediaStore.createWriteRequest(context.contentResolver, ids.map { MediaImages.uri(it) }).intentSender
    }

    fun trashRequest(context: Context, ids: List<Long>, trash: Boolean): IntentSender? {
        if (!supportsConsentRequests || ids.isEmpty()) return null
        return MediaStore.createTrashRequest(context.contentResolver, ids.map { MediaImages.uri(it) }, trash).intentSender
    }

    suspend fun applyMove(context: Context, ids: List<Long>, relativePath: String): Int = withContext(Dispatchers.IO) {
        var moved = 0
        for (id in ids) {
            val values = ContentValues().apply { put(MediaStore.Images.Media.RELATIVE_PATH, relativePath) }
            try {
                if (context.contentResolver.update(MediaImages.uri(id), values, null, null) > 0) moved++
            } catch (e: Exception) {
                Unit
            }
        }
        moved
    }

    suspend fun deleteOwned(context: Context, ids: List<Long>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        for (id in ids) {
            try {
                if (context.contentResolver.delete(MediaImages.uri(id), null, null) > 0) deleted++
            } catch (e: Exception) {
                Unit
            }
        }
        deleted
    }
}
