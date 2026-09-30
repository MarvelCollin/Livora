package com.example.livora.data.people.media

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object ThumbnailLoader {

    private val memory = object : LruCache<String, Bitmap>(budget()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(4)

    private fun budget(): Int = (Runtime.getRuntime().maxMemory() / 10L).toInt().coerceIn(8 * 1024 * 1024, 40 * 1024 * 1024)

    suspend fun load(context: Context, mediaId: Long, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$mediaId:$sizePx"
        memory.get(key)?.let { return@withContext it }
        val bitmap = gate.withPermit { decode(context, mediaId, sizePx) } ?: return@withContext null
        memory.put(key, bitmap)
        bitmap
    }

    fun peek(mediaId: Long, sizePx: Int): Bitmap? = memory.get("$mediaId:$sizePx")

    fun clear() {
        memory.evictAll()
    }

    private fun decode(context: Context, mediaId: Long, sizePx: Int): Bitmap? {
        val uri = MediaImages.uri(mediaId)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                val rotation = PhotoDecoder.exifRotation(context, uri)
                PhotoDecoder.decode(context, uri, rotation, 0, 0, sizePx)
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
