package com.example.livora.data.people.media

import kotlin.math.max
import kotlin.math.min

class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object RegionMath {

    fun orientedSize(rawWidth: Int, rawHeight: Int, degrees: Int): Pair<Int, Int> =
        if (normalize(degrees) % 180 == 90) Pair(rawHeight, rawWidth) else Pair(rawWidth, rawHeight)

    fun normalize(degrees: Int): Int = ((degrees % 360) + 360) % 360

    fun squareAroundFace(
        orientedWidth: Int,
        orientedHeight: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        scale: Float
    ): PixelRect {
        val cx = (left + right) / 2f * orientedWidth
        val cy = (top + bottom) / 2f * orientedHeight
        val faceSize = max((right - left) * orientedWidth, (bottom - top) * orientedHeight)
        val side = min(min(orientedWidth, orientedHeight).toFloat(), max(16f, faceSize * scale))
        val x0 = (cx - side / 2f).coerceIn(0f, orientedWidth - side)
        val y0 = (cy - side / 2f).coerceIn(0f, orientedHeight - side)
        return PixelRect(x0.toInt(), y0.toInt(), (x0 + side).toInt(), (y0 + side).toInt())
    }

    fun toRaw(rect: PixelRect, orientedWidth: Int, orientedHeight: Int, degrees: Int): PixelRect =
        when (normalize(degrees)) {
            90 -> PixelRect(rect.top, orientedWidth - rect.right, rect.bottom, orientedWidth - rect.left)
            180 -> PixelRect(
                orientedWidth - rect.right,
                orientedHeight - rect.bottom,
                orientedWidth - rect.left,
                orientedHeight - rect.top
            )
            270 -> PixelRect(orientedHeight - rect.bottom, rect.left, orientedHeight - rect.top, rect.right)
            else -> rect
        }
}
