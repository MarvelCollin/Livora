package com.example.livora.data.qr

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.livora.data.people.media.MediaImages
import java.io.File

object QrImageStore {

    private const val TARGET_PX = 1024

    fun bitmap(matrix: QrMatrix): Bitmap {
        val scale = QrCodec.scaleFor(matrix, TARGET_PX)
        val (side, pixels) = QrCodec.pixels(matrix, scale, 0xFF0E212E.toInt(), 0xFFFFFFFF.toInt())
        return Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    }

    @Suppress("DEPRECATION")
    fun savePng(context: Context, bitmap: Bitmap, name: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Livora")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Livora")
                dir.mkdirs()
                put(MediaStore.Images.Media.DATA, File(dir, "$name.png").absolutePath)
            }
        }
        val uri = try {
            resolver.insert(MediaImages.collection, values)
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
