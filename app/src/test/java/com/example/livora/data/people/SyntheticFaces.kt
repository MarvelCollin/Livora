package com.example.livora.data.people

import com.example.livora.data.people.cluster.FaceRecord
import com.example.livora.data.people.ml.VectorMath
import java.util.Random

class SyntheticFaces(seed: Long, private val dimension: Int = 192) {

    private val random = Random(seed)
    private var nextId = 1L

    fun randomUnit(): FloatArray {
        val v = FloatArray(dimension) { random.nextGaussian().toFloat() }
        return VectorMath.normalized(v)
    }

    fun around(center: FloatArray, sigma: Float): FloatArray {
        val v = FloatArray(dimension) {
            center[it] + sigma * (random.nextGaussian().toFloat() / Math.sqrt(dimension.toDouble()).toFloat())
        }
        return VectorMath.normalized(v)
    }

    fun face(vector: FloatArray, quality: Float = 0.8f): FaceRecord = FaceRecord(nextId++, vector, quality)

    fun faces(center: FloatArray, count: Int, sigma: Float = 0.7f, quality: Float = 0.8f): List<FaceRecord> =
        List(count) { face(around(center, sigma), quality) }
}

fun purity(clusters: Map<Long, Long?>, truth: Map<Long, Int>): Double {
    val byCluster = HashMap<Long, MutableList<Int>>()
    for ((faceId, clusterId) in clusters) {
        if (clusterId == null) continue
        byCluster.getOrPut(clusterId) { ArrayList() }.add(truth.getValue(faceId))
    }
    var correct = 0
    var total = 0
    for (labels in byCluster.values) {
        val majority = labels.groupingBy { it }.eachCount().values.max()
        correct += majority
        total += labels.size
    }
    return if (total == 0) 1.0 else correct.toDouble() / total
}
