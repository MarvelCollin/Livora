package com.example.livora.data.people.ml

import kotlin.math.max
import kotlin.math.min

enum class FaceIssue { TooSmall, Blurry, SideView }

class FaceQualityReport(val score: Float, val issues: Set<FaceIssue>)

object FaceQuality {

    const val MIN_EYE_DISTANCE = 12f
    const val GOOD_EYE_DISTANCE = 40f
    const val MIN_DETECTION_SCORE = 0.6f
    const val CORE_QUALITY = 0.5f
    const val SIDE_VIEW_RATIO = 0.32f
    const val BLUR_FLOOR = 25f
    const val BLUR_SHARP = 120f
    const val SMALL_EYE_DISTANCE = 22f

    fun sharpness(gray: IntArray, size: Int): Float {
        if (size < 3 || gray.size < size * size) return 0f
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                val i = y * size + x
                val lap = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - size] - gray[i + size]
                sum += lap
                sumSq += lap.toDouble() * lap
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSq / count - mean * mean).toFloat()
    }

    fun assess(
        detectionScore: Float,
        eyeDistance: Float,
        yawRatio: Float,
        sharpness: Float
    ): FaceQualityReport {
        val sizeScore = clamp01((eyeDistance - MIN_EYE_DISTANCE) / (GOOD_EYE_DISTANCE - MIN_EYE_DISTANCE))
        val frontalScore = clamp01(1f - (yawRatio - 0.12f) / (0.5f - 0.12f))
        val sharpScore = clamp01((sharpness - BLUR_FLOOR) / (BLUR_SHARP - BLUR_FLOOR))
        val detScore = clamp01((detectionScore - 0.5f) / 0.4f)
        val score = clamp01(0.35f * sizeScore + 0.25f * frontalScore + 0.30f * sharpScore + 0.10f * detScore)
        val issues = HashSet<FaceIssue>()
        if (eyeDistance < SMALL_EYE_DISTANCE) issues.add(FaceIssue.TooSmall)
        if (sharpness < BLUR_FLOOR * 1.6f) issues.add(FaceIssue.Blurry)
        if (yawRatio > SIDE_VIEW_RATIO) issues.add(FaceIssue.SideView)
        return FaceQualityReport(score, issues)
    }

    private fun clamp01(v: Float): Float = max(0f, min(1f, v))
}
