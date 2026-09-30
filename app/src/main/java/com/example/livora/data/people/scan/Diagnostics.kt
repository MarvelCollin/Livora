package com.example.livora.data.people.scan

import android.content.Context
import android.util.Log
import com.example.livora.data.people.cluster.ClusterParams
import com.example.livora.data.people.cluster.FaceClusterer
import com.example.livora.data.people.cluster.FaceRecord
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.media.MediaImages
import com.example.livora.data.people.ml.VectorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Diagnostics {

    private const val TAG = "PeopleDiag"

    suspend fun run(context: Context, database: PeopleDatabase) = withContext(Dispatchers.Default) {
        lfwReport(context, database)
        val lfwIds = MediaImages.queryAll(context).filter { it.relativePath.contains("LfwTest", ignoreCase = true) }.map { it.id }.toHashSet()
        val rows = database.faces().allForDiagnostics().filter { it.mediaId !in lfwIds }
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
        val gate = ClusterParams.forStrictness(0.4f)
        var t = 0L
        val cl = FaceClusterer(gate, emptyList(), emptyMap(), emptySet()) { --t }
        val res = cl.assign(records).merge().refine().outcome()
        val members = HashMap<Long, MutableList<FaceRecord>>()
        val byFace = records.associateBy { it.id }
        for ((f, c) in res.assignments) if (c != null) members.getOrPut(c) { ArrayList() }.add(byFace.getValue(f))
        val top = members.entries.sortedByDescending { it.value.size }.take(10)
        Log.i(TAG, "topClusters=" + top.joinToString(",") { "${it.key}:${it.value.size}f/${it.value.map { m -> m.photoId }.toSet().size}p/coh=${"%.2f".format(com.example.livora.data.people.cluster.Linkage.centroidOf(it.value).let { c -> it.value.map { m -> VectorMath.dot(m.vector, c) }.average() })}" })
        for (i in top.indices) {
            val line = StringBuilder()
            for (j in top.indices) {
                if (j <= i) continue
                val a = top[i].value
                val b = top[j].value
                val link = com.example.livora.data.people.cluster.Linkage.between(a, b)
                val pa = a.map { it.photoId }.toSet()
                val co = b.count { it.photoId in pa }
                line.append("[${i}-${j} link=${"%.2f".format(link)} co=$co] ")
            }
            Log.i(TAG, "rel $line")
        }
    }

    private suspend fun lfwReport(context: Context, database: PeopleDatabase) {
        val lfw = MediaImages.queryAll(context).filter { it.relativePath.contains("LfwTest", ignoreCase = true) }
        if (lfw.isEmpty()) return
        val labelOf = lfw.associate { it.id to (it.displayName ?: "").substringBefore("__") }
        val nameOf = lfw.associate { it.id to (it.displayName ?: "") }
        val rows = database.faces().allForDiagnostics().filter { it.mediaId in labelOf }
        val best = HashMap<Long, com.example.livora.data.people.db.FaceDiagRow>()
        for (row in rows) {
            val current = best[row.mediaId]
            if (current == null || row.quality > current.quality) best[row.mediaId] = row
        }
        val faces = best.values.toList()
        val labels = faces.map { labelOf.getValue(it.mediaId) }
        Log.i(TAG, "lfw images=${lfw.size} withFace=${faces.size} identities=${labels.toSet().size}")
        val records = faces.map { FaceRecord(it.id, VectorMath.fromBytes(it.embedding), it.quality, it.mediaId) }
        val truth = HashMap<Long, String>()
        for (i in faces.indices) truth[faces[i].id] = labels[i]
        val counts = labels.groupingBy { it }.eachCount()
        val big = counts.filter { it.value >= 12 }.keys
        var samePairsAll = 0L
        for (c in counts.values) samePairsAll += c.toLong() * (c - 1) / 2
        for (s in listOf(0f, 0.2f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 1f)) {
            var temp = 0L
            val params = ClusterParams.forStrictness(s)
            val outcome = FaceClusterer(params, emptyList(), emptyMap(), emptySet()) { --temp }
                .assign(records).merge().refine().outcome()
            val clusters = HashMap<Long, MutableList<String>>()
            for ((f, c) in outcome.assignments) if (c != null) clusters.getOrPut(c) { ArrayList() }.add(truth.getValue(f))
            var samePairsIn = 0L
            var pairsIn = 0L
            for (members in clusters.values) {
                pairsIn += members.size.toLong() * (members.size - 1) / 2
                for (c in members.groupingBy { it }.eachCount().values) samePairsIn += c.toLong() * (c - 1) / 2
            }
            val perIdentity = HashMap<String, MutableSet<Long>>()
            for ((f, c) in outcome.assignments) if (c != null && truth.getValue(f) in big) perIdentity.getOrPut(truth.getValue(f)) { HashSet() }.add(c)
            val fragments = perIdentity.values.map { it.size }
            val impure = clusters.values.count { m -> m.groupingBy { it }.eachCount().let { it.values.sum() - it.values.max() } > 0 }
            Log.i(
                TAG,
                "lfwSweep strictness=$s join=${"%.2f".format(params.joinThreshold)} clusters=${clusters.size} " +
                    "pairPrecision=${"%.4f".format(if (pairsIn == 0L) 1.0 else samePairsIn.toDouble() / pairsIn)} " +
                    "pairRecall=${"%.4f".format(if (samePairsAll == 0L) 1.0 else samePairsIn.toDouble() / samePairsAll)} " +
                    "avgFragments=${"%.2f".format(if (fragments.isEmpty()) 0.0 else fragments.average())} impure=$impure"
            )
        }
        for (face in faces.sortedBy { nameOf.getValue(it.mediaId) }.take(40)) {
            val v = VectorMath.fromBytes(face.embedding)
            Log.i(TAG, "lfwEmb ${nameOf.getValue(face.mediaId)} q=${"%.3f".format(face.quality)} " + v.joinToString(",") { "%.5f".format(it) })
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
