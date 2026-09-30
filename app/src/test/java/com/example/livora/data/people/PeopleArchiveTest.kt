package com.example.livora.data.people

import com.example.livora.data.people.backup.ArchiveData
import com.example.livora.data.people.backup.ArchiveFace
import com.example.livora.data.people.backup.ArchiveFolder
import com.example.livora.data.people.backup.ArchiveLinkedCopy
import com.example.livora.data.people.backup.ArchivePerson
import com.example.livora.data.people.backup.ArchivePhoto
import com.example.livora.data.people.backup.ArchiveReference
import com.example.livora.data.people.backup.ArchiveRejection
import com.example.livora.data.people.backup.ArchiveSeparation
import com.example.livora.data.people.backup.PeopleArchive
import com.example.livora.data.people.ml.VectorMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class PeopleArchiveTest {

    private fun sample(): ArchiveData {
        val gen = SyntheticFaces(60)
        val faces = List(40) {
            ArchiveFace(
                id = it + 1L,
                mediaId = 1000L + it / 2,
                boxLeft = 0.1f,
                boxTop = 0.2f,
                boxRight = 0.5f,
                boxBottom = 0.7f,
                score = 0.9f,
                quality = 0.8f,
                personId = if (it % 3 == 0) null else 7L,
                locked = it % 5 == 0,
                modelVersion = 1,
                pipelineVersion = 1,
                embedding = VectorMath.toBytes(gen.randomUnit())
            )
        }
        return ArchiveData(
            schemaVersion = 3,
            pipelineVersion = 101,
            createdAt = 1234L,
            strictness = 0.5f,
            minPhotos = 3,
            persons = listOf(
                ArchivePerson(7L, "Ana", false, 1, 10L, 0.62f, "Pictures/Ana/", "Ana", 2, true),
                ArchivePerson(8L, null, true, 0, 11L, 0.6f, null, null, 0, false)
            ),
            references = listOf(ArchiveReference(3L, 7L, 0.9f, 5L, 12L, VectorMath.toBytes(gen.randomUnit()))),
            rejections = listOf(ArchiveRejection(4L, 7L, 0.55f)),
            separations = listOf(ArchiveSeparation(7L, 8L)),
            linkedCopies = listOf(ArchiveLinkedCopy(7L, 1001L, 99L)),
            folders = listOf(ArchiveFolder(1L, "Trips", "Pictures/Trips/", 13L)),
            photos = List(20) { ArchivePhoto(1000L + it, 5L, 2000L, 6L, 7L, 2, 0, 90, 0, 101) },
            faces = faces
        )
    }

    @Test
    fun roundTripKeepsEveryRecord() {
        val data = sample()
        val buffer = ByteArrayOutputStream()
        PeopleArchive.write(data, buffer)
        val back = PeopleArchive.read(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(data.schemaVersion, back.schemaVersion)
        assertEquals(data.strictness, back.strictness, 0f)
        assertEquals(2, back.persons.size)
        assertEquals("Ana", back.persons[0].name)
        assertNull(back.persons[1].name)
        assertEquals(true, back.persons[0].pinned)
        assertEquals("Pictures/Ana/", back.persons[0].linkedFolderPath)
        assertEquals(5L, back.references[0].faceId)
        assertArrayEquals(data.references[0].embedding, back.references[0].embedding)
        assertEquals(0.55f, back.rejections[0].similarity, 0f)
        assertEquals(8L, back.separations[0].personB)
        assertEquals(1001L, back.linkedCopies[0].mediaId)
        assertEquals("Pictures/Trips/", back.folders[0].relativePath)
        assertEquals(20, back.photos.size)
        assertEquals(90, back.photos[3].orientation)
        assertEquals(40, back.faces.size)
        assertNull(back.faces[0].personId)
        assertEquals(7L, back.faces[1].personId)
        assertEquals(true, back.faces[0].locked)
        for (i in data.faces.indices) assertArrayEquals(data.faces[i].embedding, back.faces[i].embedding)
    }

    @Test
    fun archiveIsCompressed() {
        val data = sample()
        val raw = data.faces.sumOf { it.embedding.size }
        val buffer = ByteArrayOutputStream()
        PeopleArchive.write(data, buffer)
        assertTrue(buffer.size() < raw * 2)
    }

    @Test
    fun foreignAndTruncatedFilesAreRejected() {
        var failed = 0
        try {
            PeopleArchive.read(ByteArrayInputStream(ByteArray(64) { 3 }))
        } catch (e: IOException) {
            failed++
        }
        val buffer = ByteArrayOutputStream()
        PeopleArchive.write(sample(), buffer)
        val bytes = buffer.toByteArray()
        try {
            PeopleArchive.read(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)))
        } catch (e: IOException) {
            failed++
        }
        assertEquals(2, failed)
    }
}
