package com.example.livora.data.people.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.max

object PhotoDecoder {

    fun decode(
        context: Context,
        uri: Uri,
        orientationDegrees: Int,
        knownWidth: Int,
        knownHeight: Int,
        minLongSide: Int
    ): Bitmap? {
        val resolver = context.contentResolver
        var width = knownWidth
        var height = knownHeight
        if (width <= 0 || height <= 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            } catch (e: Exception) {
                return null
            }
            width = bounds.outWidth
            height = bounds.outHeight
        }
        if (width <= 0 || height <= 0) return null
        val rawLong = max(width, height)
        var sample = 1
        while (rawLong / (sample * 2) >= minLongSide) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } ?: return null
        return rotate(decoded, orientationDegrees)
    }

    fun exifRotation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
                    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(normalized.toFloat())
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            bitmap
        }
    }
}
