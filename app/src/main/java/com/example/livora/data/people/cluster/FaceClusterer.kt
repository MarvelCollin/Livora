package com.example.livora.data.people.cluster

import com.example.livora.data.people.ml.VectorMath
import kotlin.math.sqrt

class FaceRecord(val id: Long, val vector: FloatArray, val quality: Float, val photoId: Long = 0L)

class ExistingCluster(
    val id: Long,
    val members: List<FaceRecord>,
    val locked: Boolean,
    val frozenFaceIds: Set<Long> = emptySet()
)

class ClusterParams(
    val joinThreshold: Float = 0.50f,
    val lowQualityJoinThreshold: Float = 0.56f,
    val coreQuality: Float = 0.45f,
    val linkThreshold: Float = 0.54f,
    val cohesionThreshold: Float = 0.42f,
    val outlierThreshold: Float = 0.36f,
    val candidateFloor: Float = 0.25f,
    val centroidFloor: Float = 0.35f,
    val duplicateSimilarity: Float = 0.82f,
    val neighbors: Int = 3,
    val maxMergeClusters: Int = 2000
) {
    val suggestThreshold: Float get() = linkThreshold - 0.12f

    companion object {
        const val DEFAULT_STRICTNESS = 0.5f

        fun forStrictness(strictness: Float): ClusterParams {
            val s = strictness.coerceIn(0f, 1f)
            val join = 0.40f + 0.25f * s
            return ClusterParams(
                joinThreshold = join,
                lowQualityJoinThreshold = join + 0.06f,
                linkThreshold = join + 0.04f,
                cohesionThreshold = join - 0.08f,
                outlierThreshold = join - 0.14f,
                candidateFloor = (join - 0.25f).coerceAtLeast(0.15f),
                centroidFloor = join - 0.15f
            )
        }
    }
}

class ClusterMerge(val from: Long, val into: Long)

class ClusteringOutcome(
    val assignments: Map<Long, Long?>,
    val createdClusters: List<Long>,
    val merges: List<ClusterMerge>
)

object Linkage {

    fun topMean(values: FloatArray, count: Int): Float {
        if (count == 0) return 0f
        var sum = 0f
        for (i in 0 until count) sum += values[i]
        return sum / count
    }

    fun insertTop(top: FloatArray, size: Int, value: Float): Int {
        val k = top.size
        if (size < k) {
            var i = size
            while (i > 0 && top[i - 1] < value) {
                top[i] = top[i - 1]
                i--
            }
            top[i] = value
            return size + 1
        }
        if (value <= top[k - 1]) return size
        var i = k - 1
        while (i > 0 && top[i - 1] < value) {
            top[i] = top[i - 1]
            i--
        }
        top[i] = value
        return size
    }

    fun clusterToCluster(
        centroidA: FloatArray,
        membersA: List<FaceRecord>,
        centroidB: FloatArray,
        membersB: List<FaceRecord>,
        k: Int
    ): Float {
        val top = FloatArray(k)
        var size = 0
        for (a in membersA) {
            for (b in membersB) size = insertTop(top, size, VectorMath.dot(a.vector, b.vector))
        }
        return 0.5f * VectorMath.dot(centroidA, centroidB) + 0.5f * topMean(top, size)
    }

    fun centroidOf(members: List<FaceRecord>): FloatArray {
        if (members.isEmpty()) return FloatArray(VectorMath.EMBEDDING_SIZE)
        val sum = FloatArray(members[0].vector.size)
        for (m in members) for (i in sum.indices) sum[i] += m.vector[i]
        return VectorMath.normalized(sum)
    }

    fun between(a: List<FaceRecord>, b: List<FaceRecord>, k: Int = 3): Float =
        clusterToCluster(centroidOf(a), a, centroidOf(b), b, k)
}

class FaceClusterer(
    private val params: ClusterParams,
    existing: List<ExistingCluster>,
    private val rejectedFaces: Map<Long, Set<Long>> = emptyMap(),
    private val separated: Set<Pair<Long, Long>> = emptySet(),
    private val newClusterId: () -> Long
) {

    private class Cluster(val id: Long, val locked: Boolean, val dimension: Int) {
        val sum = FloatArray(dimension)
        val members = ArrayList<FaceRecord>()
        val memberIds = HashSet<Long>()
        val photoFaces = HashMap<Long, MutableList<FaceRecord>>()
        val frozen = HashSet<Long>()
        private var cachedCentroid: FloatArray? = null

        val count: Int get() = members.size

        fun add(face: FaceRecord) {
            for (i in 0 until dimension) sum[i] += face.vector[i]
            members.add(face)
            memberIds.add(face.id)
            if (face.photoId != 0L) photoFaces.getOrPut(face.photoId) { ArrayList(1) }.add(face)
            cachedCentroid = null
        }

        fun remove(face: FaceRecord) {
            for (i in 0 until dimension) sum[i] -= face.vector[i]
            members.remove(face)
            memberIds.remove(face.id)
            frozen.remove(face.id)
            if (face.photoId != 0L) {
                val list = photoFaces[face.photoId]
                if (list != null) {
                    list.remove(face)
                    if (list.isEmpty()) photoFaces.remove(face.photoId)
                }
            }
            cachedCentroid = null
        }

        val photoCount: Int get() = photoFaces.size

        fun centroid(): FloatArray {
            val cached = cachedCentroid
            if (cached != null) return cached
            val c = VectorMath.normalized(sum)
            cachedCentroid = c
            return c
        }

        fun cohesion(): Float = if (count == 0) 0f else VectorMath.norm(sum) / count
    }

    private val clusters = LinkedHashMap<Long, Cluster>()
    private val assignments = LinkedHashMap<Long, Long?>()
    private val created = ArrayList<Long>()
    private val merges = ArrayList<ClusterMerge>()
    private val originalAssignment = HashMap<Long, Long>()
    private val dimension: Int

    init {
        var dim = VectorMath.EMBEDDING_SIZE
        for (cluster in existing) {
            val first = cluster.members.firstOrNull()
            if (first != null) {
                dim = first.vector.size
                break
            }
        }
        dimension = dim
        for (seed in existing) {
            val cluster = Cluster(seed.id, seed.locked, dimension)
            for (face in seed.members) {
                cluster.add(face)
                originalAssignment[face.id] = seed.id
            }
            cluster.frozen.addAll(seed.frozenFaceIds)
            clusters[seed.id] = cluster
        }
    }

    private fun score(vector: FloatArray, cluster: Cluster, skip: FaceRecord? = null): Float {
        val centroid = if (skip == null) {
            cluster.centroid()
        } else {
            val rest = FloatArray(dimension) { cluster.sum[it] - skip.vector[it] }
            VectorMath.normalized(rest)
        }
        val centroidSim = VectorMath.dot(vector, centroid)
        if (centroidSim < params.centroidFloor) return centroidSim
        val top = FloatArray(params.neighbors)
        var size = 0
        for (m in cluster.members) {
            if (skip != null && m.id == skip.id) continue
            size = Linkage.insertTop(top, size, VectorMath.dot(vector, m.vector))
        }
        return 0.5f * centroidSim + 0.5f * Linkage.topMean(top, size)
    }

    private fun photoConflict(face: FaceRecord, cluster: Cluster): Boolean {
        if (face.photoId == 0L) return false
        val others = cluster.photoFaces[face.photoId] ?: return false
        for (other in others) {
            if (VectorMath.dot(face.vector, other.vector) < params.duplicateSimilarity) return true
        }
        return false
    }

    private fun bestCluster(face: FaceRecord): Pair<Cluster?, Float> {
        var best: Cluster? = null
        var bestScore = -2f
        for (cluster in clusters.values) {
            if (cluster.count == 0) continue
            if (isRejected(face.id, cluster.id)) continue
            if (photoConflict(face, cluster)) continue
            val s = score(face.vector, cluster)
            if (s > bestScore) {
                bestScore = s
                best = cluster
            }
        }
        return Pair(best, bestScore)
    }

    fun assign(faces: List<FaceRecord>): FaceClusterer {
        val ordered = faces.sortedByDescending { it.quality }
        for (face in ordered) {
            val isCore = face.quality >= params.coreQuality
            val threshold = if (isCore) params.joinThreshold else params.lowQualityJoinThreshold
            val (best, bestScore) = bestCluster(face)
            if (best != null && bestScore >= threshold) {
                best.add(face)
                assignments[face.id] = best.id
            } else if (isCore) {
                val id = newClusterId()
                val cluster = Cluster(id, false, dimension)
                cluster.add(face)
                clusters[id] = cluster
                created.add(id)
                assignments[face.id] = id
            } else {
                assignments[face.id] = null
            }
        }
        return this
    }

    fun merge(): FaceClusterer {
        val candidates = clusters.values
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .take(params.maxMergeClusters)
        val n = candidates.size
        if (n < 2) return this
        val slots = candidates.toTypedArray()
        val alive = BooleanArray(n) { true }
        val locked = BooleanArray(n) { slots[it].locked }
        val isNew = BooleanArray(n) { slots[it].id in created }
        val ids = LongArray(n) { slots[it].id }
        val sim = FloatArray(n * n)

        fun linkage(i: Int, j: Int): Float {
            val a = slots[i]
            val b = slots[j]
            val centroidCos = VectorMath.dot(a.centroid(), b.centroid())
            if (centroidCos < params.candidateFloor) return -1f
            return Linkage.clusterToCluster(a.centroid(), a.members, b.centroid(), b.members, params.neighbors)
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val value = linkage(i, j)
                sim[i * n + j] = value
                sim[j * n + i] = value
            }
        }
        val rowBestJ = IntArray(n) { -1 }
        val rowBestV = FloatArray(n) { -2f }

        fun rescan(i: Int) {
            var bj = -1
            var bv = -2f
            val base = i * n
            for (j in 0 until n) {
                if (j == i || !alive[j]) continue
                val v = sim[base + j]
                if (v > bv) {
                    bv = v
                    bj = j
                }
            }
            rowBestJ[i] = bj
            rowBestV[i] = bv
        }

        for (i in 0 until n) rescan(i)

        while (true) {
            var bestI = -1
            var bestValue = params.linkThreshold
            for (i in 0 until n) {
                if (alive[i] && rowBestV[i] > bestValue) {
                    bestValue = rowBestV[i]
                    bestI = i
                }
            }
            if (bestI < 0) break
            val bestJ = rowBestJ[bestI]
            val a = slots[bestI]
            val b = slots[bestJ]
            var valid = !(locked[bestI] && locked[bestJ]) && !blocked(a, b)
            if (valid) {
                var squared = 0f
                for (k in 0 until dimension) {
                    val v = a.sum[k] + b.sum[k]
                    squared += v * v
                }
                valid = sqrt(squared) / (a.count + b.count) >= params.cohesionThreshold
            }
            if (!valid) {
                sim[bestI * n + bestJ] = -1f
                sim[bestJ * n + bestI] = -1f
                rescan(bestI)
                rescan(bestJ)
                continue
            }
            val keep: Int
            val drop: Int
            if (locked[bestI] != locked[bestJ]) {
                keep = if (locked[bestI]) bestI else bestJ
                drop = if (keep == bestI) bestJ else bestI
            } else if (isNew[bestI] != isNew[bestJ]) {
                keep = if (isNew[bestI]) bestJ else bestI
                drop = if (keep == bestI) bestJ else bestI
            } else if (a.count >= b.count) {
                keep = bestI
                drop = bestJ
            } else {
                keep = bestJ
                drop = bestI
            }
            alive[drop] = false
            absorb(ids[drop], ids[keep])
            for (other in 0 until n) {
                if (!alive[other] || other == keep) continue
                val value = linkage(keep, other)
                sim[keep * n + other] = value
                sim[other * n + keep] = value
            }
            rescan(keep)
            for (other in 0 until n) {
                if (!alive[other] || other == keep) continue
                if (rowBestJ[other] == keep || rowBestJ[other] == drop) {
                    rescan(other)
                } else {
                    val v = sim[other * n + keep]
                    if (v > rowBestV[other]) {
                        rowBestV[other] = v
                        rowBestJ[other] = keep
                    }
                }
            }
        }
        return this
    }

    private fun blocked(a: Cluster, b: Cluster): Boolean {
        val small = if (a.photoFaces.size <= b.photoFaces.size) a else b
        val large = if (small === a) b else a
        for ((photo, smallFaces) in small.photoFaces) {
            val largeFaces = large.photoFaces[photo] ?: continue
            for (x in smallFaces) for (y in largeFaces) {
                if (VectorMath.dot(x.vector, y.vector) < params.duplicateSimilarity) return true
            }
        }
        if (separated.contains(Pair(a.id, b.id)) || separated.contains(Pair(b.id, a.id))) return true
        val rejectedFromB = rejectedFaces[b.id]
        if (rejectedFromB != null) {
            for (face in rejectedFromB) if (face in a.memberIds) return true
        }
        val rejectedFromA = rejectedFaces[a.id]
        if (rejectedFromA != null) {
            for (face in rejectedFromA) if (face in b.memberIds) return true
        }
        return false
    }

    private fun absorb(fromId: Long, intoId: Long) {
        val from = clusters[fromId] ?: return
        val into = clusters[intoId] ?: return
        for (face in from.members.toList()) {
            into.add(face)
            assignments[face.id] = intoId
        }
        into.frozen.addAll(from.frozen)
        clusters.remove(fromId)
        created.remove(fromId)
        merges.add(ClusterMerge(fromId, intoId))
    }

    private fun isRejected(faceId: Long, clusterId: Long): Boolean =
        rejectedFaces[clusterId]?.contains(faceId) == true

    fun refine(): FaceClusterer {
        val moved = ArrayList<FaceRecord>()
        for (cluster in clusters.values.toList()) {
            if (cluster.count < 3 || cluster.locked) continue
            for (face in cluster.members.toList()) {
                if (face.id in cluster.frozen) continue
                val own = score(face.vector, cluster, face)
                if (own < params.outlierThreshold) {
                    cluster.remove(face)
                    moved.add(face)
                }
            }
        }
        for (face in moved) {
            val (best, bestScore) = bestCluster(face)
            if (best != null && bestScore >= params.joinThreshold) {
                best.add(face)
                assignments[face.id] = best.id
            } else {
                assignments[face.id] = null
            }
        }
        for (id in clusters.keys.toList()) {
            if (clusters[id]?.count == 0 && id in created) {
                clusters.remove(id)
                created.remove(id)
            }
        }
        return this
    }

    fun outcome(): ClusteringOutcome {
        val changed = LinkedHashMap<Long, Long?>()
        for ((faceId, clusterId) in assignments) {
            if (originalAssignment[faceId] != clusterId) changed[faceId] = clusterId
        }
        return ClusteringOutcome(changed, created.toList(), merges.toList())
    }

    fun clusterIds(): Set<Long> = clusters.keys.filter { (clusters[it]?.count ?: 0) > 0 }.toSet()

    fun clusterCohesion(id: Long): Float = clusters[id]?.cohesion() ?: 0f

    fun clusterSize(id: Long): Int = clusters[id]?.count ?: 0

    fun clusterPhotoCount(id: Long): Int = clusters[id]?.photoCount ?: 0

    fun clusterMembers(id: Long): List<FaceRecord> = clusters[id]?.members ?: emptyList()
}
