package com.example.livora.data.people.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object FaceAligner {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    fun align(source: Bitmap, landmarks: FloatArray): Bitmap? {
        val transform = FaceAlignment.estimate(landmarks) ?: return null
        var working = source
        var workingTransform = transform
        var cropped: Bitmap? = null
        if (transform.scale < 0.5f) {
            val prepared = prepareDownscaled(source, transform) ?: return null
            cropped = prepared.first
            working = prepared.first
            workingTransform = prepared.second
        }
        val output = Bitmap.createBitmap(FaceAlignment.OUTPUT_SIZE, FaceAlignment.OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val matrix = Matrix()
        matrix.setValues(workingTransform.toMatrixValues())
        canvas.drawBitmap(working, matrix, paint)
        if (cropped != null && cropped !== source) cropped.recycle()
        return output
    }

    private fun prepareDownscaled(source: Bitmap, transform: SimilarityTransform): Pair<Bitmap, SimilarityTransform>? {
        val det = transform.a * transform.a + transform.b * transform.b
        if (det < 1e-9f) return null
        val size = FaceAlignment.OUTPUT_SIZE.toFloat()
        val corners = floatArrayOf(0f, 0f, size, 0f, 0f, size, size, size)
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (i in 0 until 4) {
            val dx = corners[2 * i] - transform.tx
            val dy = corners[2 * i + 1] - transform.ty
            val sx = (transform.a * dx + transform.b * dy) / det
            val sy = (-transform.b * dx + transform.a * dy) / det
            minX = min(minX, sx)
            minY = min(minY, sy)
            maxX = max(maxX, sx)
            maxY = max(maxY, sy)
        }
        val left = max(0, floor(minX).toInt())
        val top = max(0, floor(minY).toInt())
        val right = min(source.width, ceil(maxX).toInt())
        val bottom = min(source.height, ceil(maxY).toInt())
        if (right - left < 8 || bottom - top < 8) return null
        var region = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        var a = transform.a
        var b = transform.b
        var tx = transform.tx + (transform.a * left - transform.b * top)
        var ty = transform.ty + (transform.b * left + transform.a * top)
        var scale = transform.scale
        while (scale < 0.5f && region.width >= 16 && region.height >= 16) {
            val next = Bitmap.createScaledBitmap(region, region.width / 2, region.height / 2, true)
            if (region !== source) region.recycle()
            region = next
            a *= 2f
            b *= 2f
            scale *= 2f
        }
        return Pair(region, SimilarityTransform(a, b, tx, ty))
    }

    fun grayscale(aligned: Bitmap): IntArray {
        val size = FaceAlignment.OUTPUT_SIZE
        val pixels = IntArray(size * size)
        aligned.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            pixels[i] = (299 * r + 587 * g + 114 * b) / 1000
        }
        return pixels
    }
}
