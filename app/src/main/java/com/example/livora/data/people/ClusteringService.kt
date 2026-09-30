package com.example.livora.data.people

import androidx.room.withTransaction
import com.example.livora.data.people.cluster.ClusterParams
import com.example.livora.data.people.cluster.ClusteringOutcome
import com.example.livora.data.people.cluster.ExistingCluster
import com.example.livora.data.people.cluster.FaceClusterer
import com.example.livora.data.people.cluster.FaceRecord
import com.example.livora.data.people.cluster.Linkage
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.db.PersonEntity
import com.example.livora.data.people.db.PersonKind
import com.example.livora.data.people.ml.VectorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ClusteringSummary(val created: Int, val merged: Int, val assigned: Int, val ejected: Int)

class MergeSuggestion(
    val personA: Long,
    val personB: Long,
    val score: Float,
    val photosA: Int,
    val photosB: Int
)

class ClusteringService(private val database: PeopleDatabase, private val prefs: PeoplePrefs) {

    companion object {
        const val ALGORITHM_VERSION = 2
    }

    private val mutex = Mutex()

    fun currentParams(): ClusterParams = ClusterParams.forStrictness(prefs.strictness)

    private class Input(
        val persons: Map<Long, PersonEntity>,
        val existing: List<ExistingCluster>,
        val pool: List<FaceRecord>,
        val pooledWithPerson: List<Long>,
        val rejected: Map<Long, Set<Long>>,
        val separated: Set<Pair<Long, Long>>
    )

    private fun isLocked(person: PersonEntity): Boolean =
        person.name != null || person.hidden || person.pinned || person.linkedFolderPath != null

    private suspend fun loadInput(regroup: Boolean): Input {
        val faces = database.faces().allVectors()
        val persons = database.persons().all().associateBy { it.id }
        val rejected = HashMap<Long, MutableSet<Long>>()
        for (r in database.rejections().all()) rejected.getOrPut(r.personId) { HashSet() }.add(r.faceId)
        val separated = HashSet<Pair<Long, Long>>()
        for (s in database.separations().all()) separated.add(Pair(s.personA, s.personB))
        val members = HashMap<Long, MutableList<FaceRecord>>()
        val frozen = HashMap<Long, MutableSet<Long>>()
        val pool = ArrayList<FaceRecord>()
        val pooledWithPerson = ArrayList<Long>()
        for (row in faces) {
            val record = FaceRecord(row.id, VectorMath.fromBytes(row.embedding), row.quality, row.mediaId)
            val personId = row.personId
            val person = if (personId == null) null else persons[personId]
            if (person == null) {
                pool.add(record)
                continue
            }
            if (person.kind == PersonKind.ENROLLED) continue
            val dissolve = regroup && !isLocked(person) && !row.locked
            if (dissolve) {
                pool.add(record)
                pooledWithPerson.add(row.id)
                continue
            }
            members.getOrPut(person.id) { ArrayList() }.add(record)
            if (row.locked) frozen.getOrPut(person.id) { HashSet() }.add(row.id)
        }
        val existing = members.map { (id, list) ->
            ExistingCluster(id, list, isLocked(persons.getValue(id)), frozen[id] ?: emptySet())
        }
        return Input(persons, existing, pool, pooledWithPerson, rejected, separated)
    }

    suspend fun run(finalPass: Boolean): ClusteringSummary = mutex.withLock {
        withContext(Dispatchers.Default) {
            val input = loadInput(false)
            var temp = 0L
            val clusterer = FaceClusterer(currentParams(), input.existing, input.rejected, input.separated) { --temp }
            clusterer.assign(input.pool)
            if (finalPass) clusterer.merge().refine()
            apply(clusterer.outcome(), emptyList())
        }
    }

    suspend fun regroup(strictness: Float): ClusteringSummary = mutex.withLock {
        withContext(Dispatchers.Default) {
            val input = loadInput(true)
            var temp = 0L
            val params = ClusterParams.forStrictness(strictness)
            val clusterer = FaceClusterer(params, input.existing, input.rejected, input.separated) { --temp }
            clusterer.assign(input.pool).merge().refine()
            apply(clusterer.outcome(), input.pooledWithPerson)
        }
    }

    suspend fun preview(strictness: Float, minPhotos: Int): Int = mutex.withLock {
        withContext(Dispatchers.Default) {
            val input = loadInput(true)
            var temp = 0L
            val clusterer = FaceClusterer(ClusterParams.forStrictness(strictness), input.existing, input.rejected, input.separated) { --temp }
            clusterer.assign(input.pool).merge().refine()
            var count = 0
            for (id in clusterer.clusterIds()) {
                val person = input.persons[id]
                val named = person != null && isLocked(person)
                if (named || clusterer.clusterPhotoCount(id) >= minPhotos) count++
            }
            count
        }
    }

    suspend fun suggestMerges(limit: Int = 60): List<MergeSuggestion> = mutex.withLock {
        withContext(Dispatchers.Default) {
            val params = currentParams()
            val faces = database.faces().allVectors()
            val persons = database.persons().all().associateBy { it.id }
            val separated = HashSet<Pair<Long, Long>>()
            for (s in database.separations().all()) separated.add(Pair(s.personA, s.personB))
            val byPerson = HashMap<Long, MutableList<FaceRecord>>()
            for (row in faces) {
                val personId = row.personId ?: continue
                val person = persons[personId] ?: continue
                if (person.hidden || person.kind != PersonKind.AUTO) continue
                byPerson.getOrPut(personId) { ArrayList() }.add(FaceRecord(row.id, VectorMath.fromBytes(row.embedding), row.quality, row.mediaId))
            }
            val ids = byPerson.keys.toList()
            val centroids = HashMap<Long, FloatArray>()
            val photoSets = HashMap<Long, Set<Long>>()
            for (id in ids) {
                centroids[id] = Linkage.centroidOf(byPerson.getValue(id))
                photoSets[id] = byPerson.getValue(id).map { it.photoId }.toHashSet()
            }
            val out = ArrayList<MergeSuggestion>()
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val a = ids[i]
                    val b = ids[j]
                    if (separated.contains(Pair(a, b)) || separated.contains(Pair(b, a))) continue
                    if (VectorMath.dot(centroids.getValue(a), centroids.getValue(b)) < params.candidateFloor) continue
                    val pa = photoSets.getValue(a)
                    val pb = photoSets.getValue(b)
                    val small = if (pa.size <= pb.size) pa else pb
                    val large = if (small === pa) pb else pa
                    if (small.any { it in large }) continue
                    val score = Linkage.clusterToCluster(
                        centroids.getValue(a), byPerson.getValue(a), centroids.getValue(b), byPerson.getValue(b), params.neighbors
                    )
                    if (score >= params.suggestThreshold) out.add(MergeSuggestion(a, b, score, pa.size, pb.size))
                }
            }
            out.sortedByDescending { it.score }.take(limit)
        }
    }

    private suspend fun apply(outcome: ClusteringOutcome, pooledWithPerson: List<Long>): ClusteringSummary {
        var assigned = 0
        var ejected = 0
        var merged = 0
        database.withTransaction {
            val now = System.currentTimeMillis()
            for (chunk in pooledWithPerson.chunked(400)) database.faces().assign(chunk, null)
            val realIds = HashMap<Long, Long>()
            for (temp in outcome.createdClusters) {
                realIds[temp] = database.persons().insert(PersonEntity(createdAt = now))
            }
            fun resolve(id: Long): Long = if (id < 0) realIds[id] ?: id else id
            val byTarget = HashMap<Long?, MutableList<Long>>()
            for ((faceId, clusterId) in outcome.assignments) {
                val target = if (clusterId == null) null else resolve(clusterId)
                byTarget.getOrPut(target) { ArrayList() }.add(faceId)
                if (target == null) ejected++ else assigned++
            }
            for ((target, ids) in byTarget) {
                for (chunk in ids.chunked(400)) database.faces().assign(chunk, target)
            }
            for (merge in outcome.merges) {
                if (merge.from < 0) continue
                val into = resolve(merge.into)
                if (into < 0 || into == merge.from) continue
                database.references().moveAll(merge.from, into)
                database.rejections().moveAll(merge.from, into)
                database.persons().delete(merge.from)
                merged++
            }
            database.persons().deleteEmptyAuto()
        }
        return ClusteringSummary(outcome.createdClusters.size, merged, assigned, ejected)
    }
}
