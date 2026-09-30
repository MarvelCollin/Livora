package com.example.livora.data.people.ml

import kotlin.math.sqrt

class SimilarityTransform(val a: Float, val b: Float, val tx: Float, val ty: Float) {

    val scale: Float get() = sqrt(a * a + b * b)

    fun mapX(x: Float, y: Float): Float = a * x - b * y + tx

    fun mapY(x: Float, y: Float): Float = b * x + a * y + ty

    fun toMatrixValues(): FloatArray = floatArrayOf(a, -b, tx, b, a, ty, 0f, 0f, 1f)
}

object FaceAlignment {

    const val OUTPUT_SIZE = 112

    val TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        56.0252f, 71.7366f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f
    )

    fun estimate(landmarks: FloatArray): SimilarityTransform? {
        if (landmarks.size < 10) return null
        var sx = 0f
        var sy = 0f
        var dx = 0f
        var dy = 0f
        for (i in 0 until 5) {
            sx += landmarks[2 * i]
            sy += landmarks[2 * i + 1]
            dx += TEMPLATE[2 * i]
            dy += TEMPLATE[2 * i + 1]
        }
        sx /= 5f
        sy /= 5f
        dx /= 5f
        dy /= 5f
        var denominator = 0f
        var dotSum = 0f
        var crossSum = 0f
        for (i in 0 until 5) {
            val px = landmarks[2 * i] - sx
            val py = landmarks[2 * i + 1] - sy
            val qx = TEMPLATE[2 * i] - dx
            val qy = TEMPLATE[2 * i + 1] - dy
            denominator += px * px + py * py
            dotSum += px * qx + py * qy
            crossSum += px * qy - py * qx
        }
        if (denominator < 1e-6f) return null
        val a = dotSum / denominator
        val b = crossSum / denominator
        val tx = dx - (a * sx - b * sy)
        val ty = dy - (b * sx + a * sy)
        return SimilarityTransform(a, b, tx, ty)
    }

    fun eyeDistance(landmarks: FloatArray): Float {
        val dx = landmarks[2] - landmarks[0]
        val dy = landmarks[3] - landmarks[1]
        return sqrt(dx * dx + dy * dy)
    }

    fun yawRatio(landmarks: FloatArray): Float {
        val eyeDist = eyeDistance(landmarks)
        if (eyeDist < 1e-3f) return 1f
        val eyeMidX = (landmarks[0] + landmarks[2]) / 2f
        val eyeMidY = (landmarks[1] + landmarks[3]) / 2f
        val dx = landmarks[4] - eyeMidX
        val dy = landmarks[5] - eyeMidY
        val ux = (landmarks[2] - landmarks[0]) / eyeDist
        val uy = (landmarks[3] - landmarks[1]) / eyeDist
        val lateral = dx * ux + dy * uy
        return kotlin.math.abs(lateral) / eyeDist
    }
}
