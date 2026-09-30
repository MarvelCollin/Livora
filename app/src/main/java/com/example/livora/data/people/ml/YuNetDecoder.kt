package com.example.livora.data.people.ml

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class RawFace(
    val score: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val landmarks: FloatArray
)

class YuNetHead(
    val stride: Int,
    val cls: FloatArray,
    val obj: FloatArray,
    val bbox: FloatArray,
    val kps: FloatArray
)

object YuNetDecoder {

    fun decode(
        inputWidth: Int,
        inputHeight: Int,
        heads: List<YuNetHead>,
        scoreThreshold: Float,
        nmsThreshold: Float
    ): List<RawFace> {
        val candidates = ArrayList<RawFace>()
        for (head in heads) {
            val stride = head.stride
            val cols = inputWidth / stride
            val rows = inputHeight / stride
            val count = cols * rows
            if (head.cls.size < count || head.obj.size < count) continue
            for (index in 0 until count) {
                val cls = head.cls[index].coerceIn(0f, 1f)
                val obj = head.obj[index].coerceIn(0f, 1f)
                val score = sqrt(cls * obj)
                if (score < scoreThreshold) continue
                val row = index / cols
                val col = index % cols
                val cx = (col + head.bbox[index * 4]) * stride
                val cy = (row + head.bbox[index * 4 + 1]) * stride
                val w = exp(head.bbox[index * 4 + 2]) * stride
                val h = exp(head.bbox[index * 4 + 3]) * stride
                val landmarks = FloatArray(10)
                for (n in 0 until 5) {
                    landmarks[2 * n] = (head.kps[index * 10 + 2 * n] + col) * stride
                    landmarks[2 * n + 1] = (head.kps[index * 10 + 2 * n + 1] + row) * stride
                }
                candidates.add(RawFace(score, cx - w / 2f, cy - h / 2f, w, h, landmarks))
            }
        }
        return nonMaxSuppression(candidates, nmsThreshold)
    }

    fun nonMaxSuppression(faces: List<RawFace>, iouThreshold: Float): List<RawFace> {
        val sorted = faces.sortedByDescending { it.score }
        val kept = ArrayList<RawFace>()
        for (face in sorted) {
            var suppressed = false
            for (other in kept) {
                if (iou(face, other) > iouThreshold) {
                    suppressed = true
                    break
                }
            }
            if (!suppressed) kept.add(face)
        }
        return kept
    }

    fun iou(a: RawFace, b: RawFace): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.left + a.width, b.left + b.width)
        val y2 = min(a.top + a.height, b.top + b.height)
        val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
        val union = a.width * a.height + b.width * b.height - inter
        return if (union <= 0f) 0f else inter / union
    }
}
