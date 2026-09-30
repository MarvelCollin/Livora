package com.example.livora.data.people

import com.example.livora.data.people.cluster.ClusterParams
import com.example.livora.data.people.cluster.ExistingCluster
import com.example.livora.data.people.cluster.FaceClusterer
import com.example.livora.data.people.cluster.FaceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceClustererTest {

    private var temp = 0L
    private val newId: () -> Long = { --temp }

    private fun clusterer(
        existing: List<ExistingCluster> = emptyList(),
        rejected: Map<Long, Set<Long>> = emptyMap(),
        params: ClusterParams = ClusterParams()
    ): FaceClusterer {
        temp = 0L
        return FaceClusterer(params, existing, rejected, newId)
    }

    @Test
    fun separatesIdentitiesWithFullPurity() {
        val gen = SyntheticFaces(1)
        val truth = HashMap<Long, Int>()
        val faces = ArrayList<FaceRecord>()
        repeat(6) { identity ->
            val center = gen.randomUnit()
            gen.faces(center, 30).forEach {
                faces.add(it)
                truth[it.id] = identity
            }
        }
        val outcome = clusterer().assign(faces).merge().refine().outcome()
        assertEquals(1.0, purity(outcome.assignments, truth), 0.0)
        val distinct = outcome.assignments.values.filterNotNull().toSet()
        assertEquals(6, distinct.size)
    }

    @Test
    fun keepsDifferentIdentitiesApartEvenWhenNoisy() {
        val gen = SyntheticFaces(2)
        val truth = HashMap<Long, Int>()
        val faces = ArrayList<FaceRecord>()
        repeat(15) { identity ->
            val center = gen.randomUnit()
            gen.faces(center, 20, sigma = 0.9f).forEach {
                faces.add(it)
                truth[it.id] = identity
            }
        }
        val outcome = clusterer().assign(faces).merge().refine().outcome()
        assertTrue(purity(outcome.assignments, truth) >= 0.98)
    }

    @Test
    fun incrementalAssignmentKeepsExistingClusterIds() {
        val gen = SyntheticFaces(3)
        val centers = List(4) { gen.randomUnit() }
        val first = centers.flatMap { gen.faces(it, 12) }
        val firstOutcome = clusterer().assign(first).outcome()
        val existing = firstOutcome.createdClusters.mapIndexed { index, id ->
            val members = first.filter { firstOutcome.assignments[it.id] == id }
            ExistingCluster(100L + index, members, locked = false)
        }
        val idMap = firstOutcome.createdClusters.zip(existing.map { it.id }).toMap()
        val second = centers.flatMap { gen.faces(it, 5) }
        val outcome = clusterer(existing).assign(second).merge().refine().outcome()
        assertTrue(outcome.createdClusters.isEmpty())
        val used = outcome.assignments.values.filterNotNull().toSet()
        assertTrue(idMap.values.containsAll(used))
        assertEquals(20, outcome.assignments.size)
    }

    @Test
    fun mergesSplitIdentityButNotDifferentPeople() {
        val gen = SyntheticFaces(4)
        val person = gen.randomUnit()
        val poseShift = gen.randomUnit()
        val frontal = gen.faces(person, 25, sigma = 0.55f)
        val sideCenter = com.example.livora.data.people.ml.VectorMath.normalized(
            FloatArray(person.size) { person[it] * 0.8f + poseShift[it] * 0.6f }
        )
        val side = gen.faces(sideCenter, 25, sigma = 0.55f)
        val other = gen.faces(gen.randomUnit(), 25, sigma = 0.55f)
        val truth = HashMap<Long, Int>()
        frontal.forEach { truth[it.id] = 0 }
        side.forEach { truth[it.id] = 0 }
        other.forEach { truth[it.id] = 1 }
        val outcome = clusterer().assign(frontal + side + other).merge().refine().outcome()
        assertEquals(1.0, purity(outcome.assignments, truth), 0.0)
        val ofPerson = (frontal + side).map { outcome.assignments[it.id] }.toSet()
        assertEquals(1, ofPerson.size)
    }

    @Test
    fun lowQualityFacesNeverCreateClusters() {
        val gen = SyntheticFaces(5)
        val faces = List(10) { gen.face(gen.randomUnit(), quality = 0.3f) }
        val outcome = clusterer().assign(faces).outcome()
        assertTrue(outcome.createdClusters.isEmpty())
        assertTrue(outcome.assignments.values.all { it == null })
    }

    @Test
    fun lowQualityFacesJoinOnlyWhenClearlySimilar() {
        val gen = SyntheticFaces(6)
        val center = gen.randomUnit()
        val core = gen.faces(center, 10, sigma = 0.5f)
        val existing = ExistingCluster(7L, core, locked = false)
        val close = gen.face(gen.around(center, 0.4f), quality = 0.3f)
        val far = gen.face(gen.randomUnit(), quality = 0.3f)
        val outcome = clusterer(listOf(existing)).assign(listOf(close, far)).outcome()
        assertEquals(7L, outcome.assignments[close.id])
        assertNull(outcome.assignments[far.id])
    }

    @Test
    fun rejectedFaceDoesNotRejoinItsCluster() {
        val gen = SyntheticFaces(7)
        val center = gen.randomUnit()
        val members = gen.faces(center, 10, sigma = 0.5f)
        val outsider = gen.face(gen.around(center, 0.4f))
        val existing = ExistingCluster(9L, members, locked = false)
        val outcome = clusterer(listOf(existing), rejected = mapOf(9L to setOf(outsider.id)))
            .assign(listOf(outsider)).outcome()
        assertNotEquals(9L, outcome.assignments[outsider.id])
        assertFalse(outcome.assignments[outsider.id] == 9L)
    }

    @Test
    fun rejectionBlocksMergeOfClusters() {
        val gen = SyntheticFaces(8)
        val center = gen.randomUnit()
        val a = gen.faces(center, 10, sigma = 0.5f)
        val b = gen.faces(center, 10, sigma = 0.5f)
        val rejected = mapOf(2L to setOf(a[0].id))
        val outcome = clusterer(
            listOf(ExistingCluster(1L, a, false), ExistingCluster(2L, b, false)),
            rejected
        ).merge().outcome()
        assertTrue(outcome.merges.isEmpty())
    }

    @Test
    fun lockedClustersAreNeverMergedTogether() {
        val gen = SyntheticFaces(9)
        val center = gen.randomUnit()
        val a = gen.faces(center, 10, sigma = 0.5f)
        val b = gen.faces(center, 10, sigma = 0.5f)
        val outcome = clusterer(
            listOf(ExistingCluster(1L, a, true), ExistingCluster(2L, b, true))
        ).merge().outcome()
        assertTrue(outcome.merges.isEmpty())
    }

    @Test
    fun unlockedClusterIsAbsorbedIntoLockedOne() {
        val gen = SyntheticFaces(10)
        val center = gen.randomUnit()
        val named = gen.faces(center, 10, sigma = 0.5f)
        val loose = gen.faces(center, 6, sigma = 0.5f)
        val outcome = clusterer(
            listOf(ExistingCluster(1L, named, true), ExistingCluster(2L, loose, false))
        ).merge().outcome()
        assertEquals(1, outcome.merges.size)
        assertEquals(2L, outcome.merges[0].from)
        assertEquals(1L, outcome.merges[0].into)
        loose.forEach { assertEquals(1L, outcome.assignments[it.id]) }
    }

    @Test
    fun existingClusterIdSurvivesMergeWithNewCluster() {
        val gen = SyntheticFaces(11)
        val center = gen.randomUnit()
        val old = gen.faces(center, 4, sigma = 0.5f)
        val existing = ExistingCluster(5L, old, false)
        val strays = gen.faces(gen.randomUnit(), 3, sigma = 0.3f)
        val outcome = clusterer(listOf(existing)).assign(strays).merge().outcome()
        assertTrue(outcome.merges.none { it.into != 5L && it.from == 5L })
    }

    @Test
    fun refineEjectsOutliersFromUnlockedClusters() {
        val gen = SyntheticFaces(12)
        val center = gen.randomUnit()
        val members = gen.faces(center, 12, sigma = 0.4f)
        val intruder = gen.face(gen.randomUnit())
        val existing = ExistingCluster(3L, members + intruder, locked = false)
        val outcome = clusterer(listOf(existing)).refine().outcome()
        assertNull(outcome.assignments[intruder.id])
        assertFalse(outcome.assignments.containsKey(members[0].id))
    }

    @Test
    fun refineKeepsFrozenFaces() {
        val gen = SyntheticFaces(13)
        val center = gen.randomUnit()
        val members = gen.faces(center, 12, sigma = 0.4f)
        val pinned = gen.face(gen.randomUnit())
        val existing = ExistingCluster(3L, members + pinned, locked = false, frozenFaceIds = setOf(pinned.id))
        val outcome = clusterer(listOf(existing)).refine().outcome()
        assertFalse(outcome.assignments.containsKey(pinned.id))
    }

    @Test
    fun largeRunFinishesQuickly() {
        val gen = SyntheticFaces(14)
        val faces = ArrayList<FaceRecord>()
        repeat(120) {
            val center = gen.randomUnit()
            faces.addAll(gen.faces(center, 25, sigma = 0.8f))
        }
        val started = System.currentTimeMillis()
        val outcome = clusterer().assign(faces).merge().refine().outcome()
        val elapsed = System.currentTimeMillis() - started
        assertTrue("took $elapsed ms", elapsed < 20000)
        assertEquals(faces.size, outcome.assignments.size)
    }
}
