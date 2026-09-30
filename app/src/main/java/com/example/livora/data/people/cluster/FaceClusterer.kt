package com.example.livora.data.people.cluster

import com.example.livora.data.people.ml.VectorMath
import kotlin.math.sqrt

class FaceRecord(val id: Long, val vector: FloatArray, val quality: Float)

class ExistingCluster(
    val id: Long,
    val members: List<FaceRecord>,
    val locked: Boolean,
    val frozenFaceIds: Set<Long> = emptySet()
)

class ClusterParams(
    val joinThreshold: Float = 0.60f,
    val lowQualityJoinThreshold: Float = 0.66f,
    val coreQuality: Float = 0.5f,
    val linkThreshold: Float = 0.50f,
    val cohesionThreshold: Float = 0.58f,
    val outlierThreshold: Float = 0.42f,
    val maxMergeClusters: Int = 3500
)

class ClusterMerge(val from: Long, val into: Long)

class ClusteringOutcome(
    val assignments: Map<Long, Long?>,
    val createdClusters: List<Long>,
    val merges: List<ClusterMerge>
)

class FaceClusterer(
    private val params: ClusterParams,
    existing: List<ExistingCluster>,
    private val rejectedFaces: Map<Long, Set<Long>> = emptyMap(),
    private val newClusterId: () -> Long
) {

    private class Cluster(val id: Long, val locked: Boolean, val dimension: Int) {
        val sum = FloatArray(dimension)
        val members = ArrayList<FaceRecord>()
        val memberIds = HashSet<Long>()
        val frozen = HashSet<Long>()
        private var cachedCentroid: FloatArray? = null

        val count: Int get() = members.size

        fun add(face: FaceRecord) {
            for (i in 0 until dimension) sum[i] += face.vector[i]
            members.add(face)
            memberIds.add(face.id)
            cachedCentroid = null
        }

        fun remove(face: FaceRecord) {
            for (i in 0 until dimension) sum[i] -= face.vector[i]
            members.remove(face)
            memberIds.remove(face.id)
            frozen.remove(face.id)
            cachedCentroid = null
        }

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

    fun assign(faces: List<FaceRecord>): FaceClusterer {
        val ordered = faces.sortedByDescending { it.quality }
        for (face in ordered) {
            val isCore = face.quality >= params.coreQuality
            val threshold = if (isCore) params.joinThreshold else params.lowQualityJoinThreshold
            var best: Cluster? = null
            var bestSim = -2f
            for (cluster in clusters.values) {
                if (cluster.count == 0) continue
                if (isRejected(face.id, cluster.id)) continue
                val sim = VectorMath.dot(face.vector, cluster.centroid())
                if (sim > bestSim) {
                    bestSim = sim
                    best = cluster
                }
            }
            if (best != null && bestSim >= threshold) {
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
        val sums = Array(n) { slots[it].sum.copyOf() }
        val counts = IntArray(n) { slots[it].count }
        val locked = BooleanArray(n) { slots[it].locked }
        val isNew = BooleanArray(n) { slots[it].id in created }
        val memberSets = Array(n) { HashSet(slots[it].memberIds) }
        val ids = LongArray(n) { slots[it].id }
        val sim = FloatArray(n * n)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val value = VectorMath.dot(sums[i], sums[j]) / (counts[i].toFloat() * counts[j])
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
            var valid = !(locked[bestI] && locked[bestJ]) &&
                !conflicts(ids[bestI], memberSets[bestI], ids[bestJ], memberSets[bestJ])
            if (valid) {
                var squared = 0f
                for (k in 0 until dimension) {
                    val v = sums[bestI][k] + sums[bestJ][k]
                    squared += v * v
                }
                val cohesion = sqrt(squared) / (counts[bestI] + counts[bestJ])
                valid = cohesion >= params.cohesionThreshold
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
            } else if (counts[bestI] >= counts[bestJ]) {
                keep = bestI
                drop = bestJ
            } else {
                keep = bestJ
                drop = bestI
            }
            for (k in 0 until dimension) sums[keep][k] += sums[drop][k]
            counts[keep] += counts[drop]
            memberSets[keep].addAll(memberSets[drop])
            alive[drop] = false
            for (other in 0 until n) {
                if (!alive[other] || other == keep) continue
                val value = VectorMath.dot(sums[keep], sums[other]) / (counts[keep].toFloat() * counts[other])
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
            absorb(ids[drop], ids[keep])
        }
        return this
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

    private fun conflicts(idA: Long, membersA: Set<Long>, idB: Long, membersB: Set<Long>): Boolean {
        val rejectedFromB = rejectedFaces[idB]
        if (rejectedFromB != null) {
            for (face in rejectedFromB) if (face in membersA) return true
        }
        val rejectedFromA = rejectedFaces[idA]
        if (rejectedFromA != null) {
            for (face in rejectedFromA) if (face in membersB) return true
        }
        return false
    }

    private fun isRejected(faceId: Long, clusterId: Long): Boolean =
        rejectedFaces[clusterId]?.contains(faceId) == true

    fun refine(): FaceClusterer {
        val ejected = ArrayList<FaceRecord>()
        for (cluster in clusters.values.toList()) {
            if (cluster.count < 3 || cluster.locked) continue
            for (face in cluster.members.toList()) {
                if (face.id in cluster.frozen) continue
                val rest = FloatArray(dimension) { cluster.sum[it] - face.vector[it] }
                val similarity = VectorMath.dot(face.vector, VectorMath.normalized(rest))
                if (similarity < params.outlierThreshold) {
                    cluster.remove(face)
                    ejected.add(face)
                }
            }
        }
        for (face in ejected) {
            var best: Cluster? = null
            var bestSim = -2f
            for (cluster in clusters.values) {
                if (cluster.count == 0) continue
                if (isRejected(face.id, cluster.id)) continue
                val sim = VectorMath.dot(face.vector, cluster.centroid())
                if (sim > bestSim) {
                    bestSim = sim
                    best = cluster
                }
            }
            if (best != null && bestSim >= params.joinThreshold) {
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

    companion object {
        fun similarityToCentroid(vector: FloatArray, members: List<FaceRecord>): Float {
            if (members.isEmpty()) return 0f
            val sum = FloatArray(vector.size)
            for (m in members) for (i in sum.indices) sum[i] += m.vector[i]
            return VectorMath.dot(vector, VectorMath.normalized(sum))
        }

        fun pairwiseCohesion(members: List<FaceRecord>): Float {
            if (members.isEmpty()) return 0f
            val sum = FloatArray(members[0].vector.size)
            for (m in members) for (i in sum.indices) sum[i] += m.vector[i]
            return VectorMath.norm(sum) / members.size
        }

    }
}
