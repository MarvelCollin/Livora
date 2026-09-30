package com.example.livora.data.people

import com.example.livora.data.people.cluster.PersonMatcher
import com.example.livora.data.people.ml.VectorMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonMatcherTest {

    @Test
    fun matchesOnlySimilarFacesAndSortsByScore() {
        val gen = SyntheticFaces(20)
        val target = gen.randomUnit()
        val prototypes = listOf(gen.around(target, 0.4f))
        val near = gen.face(gen.around(target, 0.5f))
        val nearer = gen.face(gen.around(target, 0.2f))
        val stranger = gen.face(gen.randomUnit())
        val matches = PersonMatcher.match(prototypes, emptyList(), listOf(near, stranger, nearer), 0.6f)
        assertEquals(listOf(nearer.id, near.id), matches.map { it.faceId })
        assertTrue(matches[0].score >= matches[1].score)
    }

    @Test
    fun multiplePrototypesCoverDifferentAngles() {
        val gen = SyntheticFaces(21)
        val front = gen.randomUnit()
        val side = gen.randomUnit()
        val prototypes = listOf(front, side)
        val sideFace = gen.face(gen.around(side, 0.3f))
        val single = PersonMatcher.match(listOf(front), emptyList(), listOf(sideFace), 0.6f)
        val multi = PersonMatcher.match(prototypes, emptyList(), listOf(sideFace), 0.6f)
        assertTrue(single.isEmpty())
        assertEquals(1, multi.size)
    }

    @Test
    fun negativesRemoveLookAlikes() {
        val gen = SyntheticFaces(22)
        val target = gen.randomUnit()
        val lookAlike = VectorMath.normalized(FloatArray(target.size) { target[it] + 0.5f * gen.randomUnit()[it] })
        val candidate = gen.face(gen.around(lookAlike, 0.05f))
        val without = PersonMatcher.match(listOf(target), emptyList(), listOf(candidate), 0.6f)
        val with = PersonMatcher.match(listOf(target), listOf(lookAlike), listOf(candidate), 0.6f)
        assertEquals(1, without.size)
        assertTrue(with.isEmpty())
    }

    @Test
    fun qualityFloorSkipsPoorCandidates() {
        val gen = SyntheticFaces(23)
        val target = gen.randomUnit()
        val poor = gen.face(gen.around(target, 0.2f), quality = 0.2f)
        assertTrue(PersonMatcher.match(listOf(target), emptyList(), listOf(poor), 0.6f, minQuality = 0.4f).isEmpty())
    }

    @Test
    fun prototypeSelectionIsDiverseAndBounded() {
        val gen = SyntheticFaces(24)
        val centers = List(3) { gen.randomUnit() }
        val members = centers.flatMap { gen.faces(it, 20, sigma = 0.3f) }
        val selected = PersonMatcher.selectPrototypes(members, limit = 6)
        assertEquals(6, selected.size)
        val groups = selected.map { s -> centers.indices.maxByOrNull { VectorMath.dot(s.vector, centers[it]) } }.toSet()
        assertEquals(3, groups.size)
    }

    @Test
    fun selectionSkipsLowQualityFaces() {
        val gen = SyntheticFaces(25)
        val center = gen.randomUnit()
        val good = gen.faces(center, 3, quality = 0.9f)
        val poor = gen.faces(center, 5, quality = 0.2f)
        val selected = PersonMatcher.selectPrototypes(good + poor)
        assertEquals(good.map { it.id }.toSet(), selected.map { it.id }.toSet())
    }

    @Test
    fun rejectionsRaiseThresholdWithinBounds() {
        assertEquals(0.62f, PersonMatcher.tightenedThreshold(0.62f, emptyList()), 1e-6f)
        assertEquals(0.66f, PersonMatcher.tightenedThreshold(0.62f, listOf(0.65f, 0.5f)), 1e-6f)
        assertEquals(0.74f, PersonMatcher.tightenedThreshold(0.62f, listOf(0.95f)), 1e-6f)
        assertEquals(0.62f, PersonMatcher.tightenedThreshold(0.62f, listOf(0.3f)), 1e-6f)
    }

    @Test
    fun thresholdClampStaysInRange() {
        assertEquals(PersonMatcher.MIN_THRESHOLD, PersonMatcher.clampThreshold(0.1f), 0f)
        assertEquals(PersonMatcher.MAX_THRESHOLD, PersonMatcher.clampThreshold(0.99f), 0f)
        assertEquals(0.7f, PersonMatcher.clampThreshold(0.7f), 0f)
    }
}
