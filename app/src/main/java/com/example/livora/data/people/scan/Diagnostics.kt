package com.example.livora.data.people.scan

import android.util.Log
import com.example.livora.data.people.cluster.ClusterParams
import com.example.livora.data.people.cluster.FaceClusterer
import com.example.livora.data.people.cluster.FaceRecord
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.ml.VectorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Diagnostics {

    private const val TAG = "PeopleDiag"

    suspend fun run(database: PeopleDatabase) = withContext(Dispatchers.Default) {
        val rows = database.faces().allForDiagnostics()
        val n = rows.size
        if (n < 2) return@withContext
        val vectors = Array(n) { VectorMath.fromBytes(rows[it].embedding) }
        val quality = FloatArray(n) { rows[it].quality }
        val media = LongArray(n) { rows[it].mediaId }
        Log.i(TAG, "faces=$n photos=${media.toSet().size}")
        Log.i(TAG, "qualityBins=" + bins(quality.toList(), 0f, 1f, 0.1f))
        val samePhoto = ArrayList<Float>()
        val nn = FloatArray(n) { -1f }
        val nnAny = FloatArray(n) { -1f }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val s = VectorMath.dot(vectors[i], vectors[j])
                if (media[i] == media[j]) {
                    samePhoto.add(s)
                    continue
                }
                if (s > nn[i]) nn[i] = s
                if (s > nn[j]) nn[j] = s
            }
        }
        var dupHigh = 0
        var dupOverlap = 0
        var dupIou = 0
        var highPairs = 0
        val sizeRatios = ArrayList<Float>()
        val overlapSamples = ArrayList<String>()
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (media[i] != media[j]) continue
                val s = VectorMath.dot(vectors[i], vectors[j])
                if (s < 0.7f) continue
                highPairs++
                val a = rows[i]
                val b = rows[j]
                val ix = maxOf(0f, minOf(a.boxRight, b.boxRight) - maxOf(a.boxLeft, b.boxLeft))
                val iy = maxOf(0f, minOf(a.boxBottom, b.boxBottom) - maxOf(a.boxTop, b.boxTop))
                val inter = ix * iy
                val areaA = (a.boxRight - a.boxLeft) * (a.boxBottom - a.boxTop)
                val areaB = (b.boxRight - b.boxLeft) * (b.boxBottom - b.boxTop)
                val iou = inter / (areaA + areaB - inter)
                val iomin = inter / minOf(areaA, areaB)
                if (iomin >= 0.5f) dupOverlap++
                if (iou >= 0.3f) dupIou++
                sizeRatios.add(minOf(areaA, areaB) / maxOf(areaA, areaB))
                if (overlapSamples.size < 12) overlapSamples.add("s=${"%.2f".format(s)} iou=${"%.2f".format(iou)} iomin=${"%.2f".format(iomin)} sizeRatio=${"%.2f".format(minOf(areaA, areaB) / maxOf(areaA, areaB))} q=${"%.2f".format(a.quality)}/${"%.2f".format(b.quality)}")
            }
        }
        Log.i(TAG, "samePhoteHigh pairs=$highPairs iomin>=0.5=$dupOverlap iou>=0.3=$dupIou medianSizeRatio=${if (sizeRatios.isEmpty()) 0f else sizeRatios.sorted()[sizeRatios.size / 2]}")
        for (line in overlapSamples) Log.i(TAG, "samePhotoSample $line")
        val sortedSame = samePhoto.sorted()
        if (sortedSame.isNotEmpty()) {
            Log.i(
                TAG,
                "samePhotoPairs=${sortedSame.size} p50=${pct(sortedSame, 0.5)} p90=${pct(sortedSame, 0.9)} " +
                    "p99=${pct(sortedSame, 0.99)} max=${sortedSame.last()} " +
                    "ge40=${frac(sortedSame, 0.4f)} ge50=${frac(sortedSame, 0.5f)} ge60=${frac(sortedSame, 0.6f)}"
            )
        }
        for ((label, lo, hi) in listOf(Triple("all", 0f, 1.01f), Triple("q>=0.5", 0.5f, 1.01f), Triple("q<0.5", 0f, 0.5f))) {
            val values = ArrayList<Float>()
            for (i in 0 until n) if (quality[i] >= lo && quality[i] < hi && nn[i] > -1f) values.add(nn[i])
            Log.i(TAG, "nnBins[$label] n=${values.size} ${bins(values, 0.1f, 1f, 0.05f)}")
        }
        val records = List(n) { FaceRecord(rows[it].id, vectors[it], quality[it], media[it]) }
        for (s in listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)) {
            val params = ClusterParams.forStrictness(s)
            var temp = 0L
            val started = System.currentTimeMillis()
            val clusterer = FaceClusterer(params, emptyList(), emptyMap(), emptySet()) { --temp }
            val outcome = clusterer.assign(records).merge().refine().outcome()
            val elapsed = System.currentTimeMillis() - started
            val photos = HashMap<Long, MutableSet<Long>>()
            val byId = rows.associateBy { it.id }
            for ((faceId, clusterId) in outcome.assignments) {
                if (clusterId == null) continue
                photos.getOrPut(clusterId) { HashSet() }.add(byId.getValue(faceId).mediaId)
            }
            val counts = photos.values.map { it.size }.sortedDescending()
            Log.i(
                TAG,
                "sweep strictness=$s join=${"%.2f".format(params.joinThreshold)} clusters=${counts.size} ge3=${counts.count { it >= 3 }} " +
                    "ge2=${counts.count { it >= 2 }} singletons=${counts.count { it == 1 }} largest=${counts.take(6)} " +
                    "unassigned=${outcome.assignments.values.count { it == null }} ms=$elapsed"
            )
        }
    }

    private fun pct(sorted: List<Float>, p: Double): Float = sorted[((sorted.size - 1) * p).toInt()]

    private fun frac(sorted: List<Float>, threshold: Float): String =
        "%.4f".format(sorted.count { it >= threshold }.toDouble() / sorted.size)

    private fun bins(values: List<Float>, lo: Float, hi: Float, step: Float): String {
        val count = ((hi - lo) / step).toInt()
        val hist = IntArray(count + 1)
        for (v in values) {
            val index = ((v - lo) / step).toInt().coerceIn(0, count)
            hist[index]++
        }
        return hist.joinToString(",")
    }
}
