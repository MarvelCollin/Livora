package com.example.livora.data.people.ml

import android.graphics.Bitmap

object BitmapScaler {

    fun scale(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) return source
        var current = source
        while (current.width >= targetWidth * 2 && current.height >= targetHeight * 2) {
            val next = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
            if (current !== source) current.recycle()
            current = next
        }
        if (current.width == targetWidth && current.height == targetHeight) return current
        val result = Bitmap.createScaledBitmap(current, targetWidth, targetHeight, true)
        if (current !== source && current !== result) current.recycle()
        return result
    }
}
