package com.example.livora.data.people.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class ArchivePerson(
    val id: Long,
    val name: String?,
    val hidden: Boolean,
    val kind: Int,
    val createdAt: Long,
    val threshold: Float,
    val linkedFolderPath: String?,
    val linkedFolderName: String?,
    val linkMode: Int,
    val pinned: Boolean
)

class ArchiveReference(
    val id: Long,
    val personId: Long,
    val quality: Float,
    val faceId: Long?,
    val createdAt: Long,
    val embedding: ByteArray
)

class ArchiveRejection(val faceId: Long, val personId: Long, val similarity: Float)

class ArchiveSeparation(val personA: Long, val personB: Long)

class ArchiveLinkedCopy(val personId: Long, val mediaId: Long, val copiedAt: Long)

class ArchiveFolder(val id: Long, val name: String, val relativePath: String, val createdAt: Long)

class ArchivePhoto(
    val mediaId: Long,
    val dateModified: Long,
    val size: Long,
    val dateTaken: Long,
    val scannedAt: Long,
    val faceCount: Int,
    val status: Int,
    val orientation: Int,
    val retryCount: Int,
    val pipelineVersion: Int
)

class ArchiveFace(
    val id: Long,
    val mediaId: Long,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    val score: Float,
    val quality: Float,
    val personId: Long?,
    val locked: Boolean,
    val modelVersion: Int,
    val pipelineVersion: Int,
    val embedding: ByteArray
)

class ArchiveData(
    val schemaVersion: Int,
    val pipelineVersion: Int,
    val createdAt: Long,
    val strictness: Float,
    val minPhotos: Int,
    val persons: List<ArchivePerson>,
    val references: List<ArchiveReference>,
    val rejections: List<ArchiveRejection>,
    val separations: List<ArchiveSeparation>,
    val linkedCopies: List<ArchiveLinkedCopy>,
    val folders: List<ArchiveFolder>,
    val photos: List<ArchivePhoto>,
    val faces: List<ArchiveFace>
)

object PeopleArchive {

    private const val MAGIC = 0x4C565250
    const val FORMAT = 1

    fun write(data: ArchiveData, output: OutputStream) {
        DataOutputStream(GZIPOutputStream(output, 64 * 1024).buffered(64 * 1024)).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(FORMAT)
            out.writeInt(data.schemaVersion)
            out.writeInt(data.pipelineVersion)
            out.writeLong(data.createdAt)
            out.writeFloat(data.strictness)
            out.writeInt(data.minPhotos)
            out.writeInt(data.persons.size)
            for (p in data.persons) {
                out.writeLong(p.id)
                writeNullable(out, p.name)
                out.writeBoolean(p.hidden)
                out.writeInt(p.kind)
                out.writeLong(p.createdAt)
                out.writeFloat(p.threshold)
                writeNullable(out, p.linkedFolderPath)
                writeNullable(out, p.linkedFolderName)
                out.writeInt(p.linkMode)
                out.writeBoolean(p.pinned)
            }
            out.writeInt(data.references.size)
            for (r in data.references) {
                out.writeLong(r.id)
                out.writeLong(r.personId)
                out.writeFloat(r.quality)
                out.writeLong(r.faceId ?: -1L)
                out.writeLong(r.createdAt)
                writeBytes(out, r.embedding)
            }
            out.writeInt(data.rejections.size)
            for (r in data.rejections) {
                out.writeLong(r.faceId)
                out.writeLong(r.personId)
                out.writeFloat(r.similarity)
            }
            out.writeInt(data.separations.size)
            for (s in data.separations) {
                out.writeLong(s.personA)
                out.writeLong(s.personB)
            }
            out.writeInt(data.linkedCopies.size)
            for (c in data.linkedCopies) {
                out.writeLong(c.personId)
                out.writeLong(c.mediaId)
                out.writeLong(c.copiedAt)
            }
            out.writeInt(data.folders.size)
            for (f in data.folders) {
                out.writeLong(f.id)
                out.writeUTF(f.name)
                out.writeUTF(f.relativePath)
                out.writeLong(f.createdAt)
            }
            out.writeInt(data.photos.size)
            for (p in data.photos) {
                out.writeLong(p.mediaId)
                out.writeLong(p.dateModified)
                out.writeLong(p.size)
                out.writeLong(p.dateTaken)
                out.writeLong(p.scannedAt)
                out.writeInt(p.faceCount)
                out.writeInt(p.status)
                out.writeInt(p.orientation)
                out.writeInt(p.retryCount)
                out.writeInt(p.pipelineVersion)
            }
            out.writeInt(data.faces.size)
            for (f in data.faces) {
                out.writeLong(f.id)
                out.writeLong(f.mediaId)
                out.writeFloat(f.boxLeft)
                out.writeFloat(f.boxTop)
                out.writeFloat(f.boxRight)
                out.writeFloat(f.boxBottom)
                out.writeFloat(f.score)
                out.writeFloat(f.quality)
                out.writeLong(f.personId ?: -1L)
                out.writeBoolean(f.locked)
                out.writeInt(f.modelVersion)
                out.writeInt(f.pipelineVersion)
                writeBytes(out, f.embedding)
            }
        }
    }

    fun read(input: InputStream): ArchiveData {
        DataInputStream(GZIPInputStream(input, 64 * 1024).buffered(64 * 1024)).use { inp ->
            if (inp.readInt() != MAGIC) throw IOException("This is not a Livora people file")
            val format = inp.readInt()
            if (format != FORMAT) throw IOException("This people file comes from a newer version of Livora")
            val schema = inp.readInt()
            val pipeline = inp.readInt()
            val createdAt = inp.readLong()
            val strictness = inp.readFloat()
            val minPhotos = inp.readInt()
            val persons = List(readCount(inp)) {
                ArchivePerson(
                    inp.readLong(), readNullable(inp), inp.readBoolean(), inp.readInt(), inp.readLong(), inp.readFloat(),
                    readNullable(inp), readNullable(inp), inp.readInt(), inp.readBoolean()
                )
            }
            val references = List(readCount(inp)) {
                ArchiveReference(
                    inp.readLong(), inp.readLong(), inp.readFloat(), inp.readLong().takeIf { it >= 0 }, inp.readLong(), readBytes(inp)
                )
            }
            val rejections = List(readCount(inp)) { ArchiveRejection(inp.readLong(), inp.readLong(), inp.readFloat()) }
            val separations = List(readCount(inp)) { ArchiveSeparation(inp.readLong(), inp.readLong()) }
            val linkedCopies = List(readCount(inp)) { ArchiveLinkedCopy(inp.readLong(), inp.readLong(), inp.readLong()) }
            val folders = List(readCount(inp)) { ArchiveFolder(inp.readLong(), inp.readUTF(), inp.readUTF(), inp.readLong()) }
            val photos = List(readCount(inp)) {
                ArchivePhoto(
                    inp.readLong(), inp.readLong(), inp.readLong(), inp.readLong(), inp.readLong(),
                    inp.readInt(), inp.readInt(), inp.readInt(), inp.readInt(), inp.readInt()
                )
            }
            val faces = List(readCount(inp)) {
                ArchiveFace(
                    inp.readLong(), inp.readLong(), inp.readFloat(), inp.readFloat(), inp.readFloat(), inp.readFloat(),
                    inp.readFloat(), inp.readFloat(), inp.readLong().takeIf { it >= 0 }, inp.readBoolean(), inp.readInt(),
                    inp.readInt(), readBytes(inp)
                )
            }
            return ArchiveData(
                schema, pipeline, createdAt, strictness, minPhotos, persons, references, rejections,
                separations, linkedCopies, folders, photos, faces
            )
        }
    }

    private fun readCount(inp: DataInputStream): Int {
        val n = inp.readInt()
        if (n < 0 || n > 5_000_000) throw IOException("The people file is damaged")
        return n
    }

    private fun writeNullable(out: DataOutputStream, value: String?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeUTF(value)
    }

    private fun readNullable(inp: DataInputStream): String? = if (inp.readBoolean()) inp.readUTF() else null

    private fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readBytes(inp: DataInputStream): ByteArray {
        val size = inp.readInt()
        if (size < 0 || size > 1_000_000) throw IOException("The people file is damaged")
        val bytes = ByteArray(size)
        inp.readFully(bytes)
        return bytes
    }
}
