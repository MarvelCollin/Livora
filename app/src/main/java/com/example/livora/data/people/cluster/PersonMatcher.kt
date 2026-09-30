package com.example.livora.data.people.cluster

import com.example.livora.data.people.ml.VectorMath
import kotlin.math.max
import kotlin.math.min

class PersonMatch(val faceId: Long, val score: Float)

object PersonMatcher {

    const val DEFAULT_THRESHOLD = 0.62f
    const val MIN_THRESHOLD = 0.45f
    const val MAX_THRESHOLD = 0.85f
    const val AUTO_ADD_MARGIN = 0.06f
    const val MAX_PROTOTYPES = 16
    const val PROTOTYPE_MIN_QUALITY = 0.45f
    const val NEGATIVE_MARGIN = 0.02f

    fun bestScore(vector: FloatArray, prototypes: List<FloatArray>): Float {
        var best = -2f
        for (p in prototypes) {
            val s = VectorMath.dot(vector, p)
            if (s > best) best = s
        }
        return best
    }

    fun match(
        prototypes: List<FloatArray>,
        negatives: List<FloatArray>,
        candidates: List<FaceRecord>,
        threshold: Float,
        minQuality: Float = 0f
    ): List<PersonMatch> {
        if (prototypes.isEmpty()) return emptyList()
        val out = ArrayList<PersonMatch>()
        for (candidate in candidates) {
            if (candidate.quality < minQuality) continue
            val positive = bestScore(candidate.vector, prototypes)
            if (positive < threshold) continue
            if (negatives.isNotEmpty()) {
                val negative = bestScore(candidate.vector, negatives)
                if (negative >= positive - NEGATIVE_MARGIN) continue
            }
            out.add(PersonMatch(candidate.id, positive))
        }
        out.sortByDescending { it.score }
        return out
    }

    fun selectPrototypes(
        members: List<FaceRecord>,
        limit: Int = MAX_PROTOTYPES,
        minQuality: Float = PROTOTYPE_MIN_QUALITY
    ): List<FaceRecord> {
        val eligible = members.filter { it.quality >= minQuality }
        if (eligible.isEmpty()) return emptyList()
        if (eligible.size <= limit) return eligible
        val selected = ArrayList<FaceRecord>()
        selected.add(eligible.maxByOrNull { it.quality }!!)
        val nearest = FloatArray(eligible.size) { -2f }
        while (selected.size < limit) {
            val last = selected.last()
            var bestIndex = -1
            var bestDistance = 2f
            for (i in eligible.indices) {
                val s = VectorMath.dot(eligible[i].vector, last.vector)
                if (s > nearest[i]) nearest[i] = s
                if (nearest[i] < bestDistance && eligible[i] !in selected) {
                    bestDistance = nearest[i]
                    bestIndex = i
                }
            }
            if (bestIndex < 0) break
            selected.add(eligible[bestIndex])
        }
        return selected
    }

    fun tightenedThreshold(base: Float, rejectedScores: List<Float>): Float {
        if (rejectedScores.isEmpty()) return base
        val worst = rejectedScores.max()
        return max(base, min(base + 0.12f, worst + 0.01f))
    }

    fun clampThreshold(value: Float): Float = max(MIN_THRESHOLD, min(MAX_THRESHOLD, value))
}
