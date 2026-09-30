package com.example.livora.data.people.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import com.example.livora.data.people.db.FaceBoxRow
import com.example.livora.data.people.db.PeopleDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object FaceImages {

    private const val AVATAR_SIZE = 192
    private const val AVATAR_SCALE = 1.9f
    private const val TILE_SCALE = 3.4f

    private val memory = object : LruCache<String, Bitmap>(memoryBudget()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(3)

    private fun memoryBudget(): Int = (Runtime.getRuntime().maxMemory() / 12L).toInt().coerceIn(6 * 1024 * 1024, 32 * 1024 * 1024)

    private fun cropDir(context: Context): File {
        val dir = File(context.noBackupFilesDir, "people/crops")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun referenceFile(context: Context, referenceId: Long): File = File(cropDir(context), "ref_$referenceId.jpg")

    fun saveReference(context: Context, referenceId: Long, bitmap: Bitmap) {
        try {
            FileOutputStream(referenceFile(context, referenceId)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        } catch (e: Exception) {
            return
        }
    }

    suspend fun referenceAvatar(context: Context, referenceId: Long): Bitmap? = withContext(Dispatchers.IO) {
        val key = "r$referenceId"
        memory.get(key)?.let { return@withContext it }
        val file = referenceFile(context, referenceId)
        if (!file.exists()) return@withContext null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
        memory.put(key, bitmap)
        bitmap
    }

    suspend fun avatar(context: Context, database: PeopleDatabase, faceId: Long): Bitmap? = withContext(Dispatchers.IO) {
        val key = "a$faceId"
        memory.get(key)?.let { return@withContext it }
        val file = File(cropDir(context), "face_$faceId.jpg")
        if (file.exists()) {
            val cached = BitmapFactory.decodeFile(file.absolutePath)
            if (cached != null) {
                memory.put(key, cached)
                return@withContext cached
            }
        }
        val row = database.faces().box(faceId) ?: return@withContext null
        val bitmap = gate.withPermit { cropAround(context, row, AVATAR_SCALE, AVATAR_SIZE) } ?: return@withContext null
        try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        } catch (e: Exception) {
            file.delete()
        }
        memory.put(key, bitmap)
        bitmap
    }

    suspend fun tile(
        context: Context,
        faceId: Long,
        mediaId: Long,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        orientation: Int,
        sizePx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val key = "t$faceId:$sizePx"
        memory.get(key)?.let { return@withContext it }
        val row = FaceBoxRow(faceId, mediaId, left, top, right, bottom, orientation)
        val bitmap = gate.withPermit { cropAround(context, row, TILE_SCALE, sizePx) } ?: return@withContext null
        memory.put(key, bitmap)
        bitmap
    }

    fun forget(context: Context, faceIds: Collection<Long>) {
        for (id in faceIds) {
            File(cropDir(context), "face_$id.jpg").delete()
            memory.remove("a$id")
        }
    }

    fun clearMemory() {
        memory.evictAll()
    }

    @Suppress("DEPRECATION")
    private fun cropAround(context: Context, row: FaceBoxRow, scale: Float, targetPx: Int): Bitmap? {
        val uri = MediaImages.uri(row.mediaId)
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        } ?: return null
        try {
            val decoder = try {
                BitmapRegionDecoder.newInstance(stream, false)
            } catch (e: Exception) {
                null
            }
            if (decoder == null) return null
            try {
                val rawWidth = decoder.width
                val rawHeight = decoder.height
                val (orientedWidth, orientedHeight) = RegionMath.orientedSize(rawWidth, rawHeight, row.orientation)
                val square = RegionMath.squareAroundFace(
                    orientedWidth,
                    orientedHeight,
                    row.boxLeft,
                    row.boxTop,
                    row.boxRight,
                    row.boxBottom,
                    scale
                )
                val raw = RegionMath.toRaw(square, orientedWidth, orientedHeight, row.orientation)
                val rect = Rect(
                    raw.left.coerceIn(0, rawWidth - 1),
                    raw.top.coerceIn(0, rawHeight - 1),
                    raw.right.coerceIn(1, rawWidth),
                    raw.bottom.coerceIn(1, rawHeight)
                )
                if (rect.width() < 4 || rect.height() < 4) return null
                var sample = 1
                while (maxOf(rect.width(), rect.height()) / (sample * 2) >= targetPx) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val region = decoder.decodeRegion(rect, options) ?: return null
                val rotated = PhotoDecoder.rotate(region, row.orientation)
                val scaled = if (rotated.width == targetPx && rotated.height == targetPx) {
                    rotated
                } else {
                    Bitmap.createScaledBitmap(rotated, targetPx, targetPx, true)
                }
                if (scaled !== rotated) rotated.recycle()
                return scaled
            } finally {
                decoder.recycle()
            }
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        } finally {
            try {
                stream.close()
            } catch (e: Exception) {
                Unit
            }
        }
    }
}
