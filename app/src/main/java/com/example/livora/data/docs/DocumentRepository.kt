package com.example.livora.data.docs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.livora.data.db.AppDatabase
import com.example.livora.data.people.media.PhotoDecoder
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DocumentRepository(private val context: Context, db: AppDatabase) {

    private val dao = db.documents()
    private val root = File(context.filesDir, "documents")

    fun documents(): Flow<List<DocumentEntity>> = dao.observe()

    fun covers(): Flow<List<DocumentCover>> = dao.observeCovers()

    fun document(id: Long): Flow<DocumentEntity?> = dao.observeOne(id)

    fun pages(id: Long): Flow<List<DocumentPageEntity>> = dao.observePages(id)

    fun pageFile(folder: String, fileName: String): File = File(File(root, folder), fileName)

    suspend fun create(uris: List<Uri>, name: String): Long? = withContext(Dispatchers.IO) {
        val folder = UUID.randomUUID().toString()
        val dir = File(root, folder).apply { mkdirs() }
        val names = importInto(dir, uris)
        if (names.isEmpty()) {
            dir.deleteRecursively()
            return@withContext null
        }
        val now = System.currentTimeMillis()
        val id = dao.insert(
            DocumentEntity(
                name = name,
                folder = folder,
                createdAt = now,
                updatedAt = now,
                pageCount = names.size,
                sizeBytes = names.sumOf { File(dir, it).length() }
            )
        )
        dao.insertPages(names.mapIndexed { index, file -> DocumentPageEntity(documentId = id, position = index, fileName = file) })
        id
    }

    suspend fun addPages(id: Long, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext 0
        val dir = File(root, doc.folder).apply { mkdirs() }
        val names = importInto(dir, uris)
        if (names.isNotEmpty()) {
            val start = dao.pages(id).size
            dao.insertPages(names.mapIndexed { index, file -> DocumentPageEntity(documentId = id, position = start + index, fileName = file) })
            refresh(doc)
        }
        names.size
    }

    suspend fun deletePage(id: Long, pageId: Long) = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext
        val pages = dao.pages(id)
        val target = pages.firstOrNull { it.id == pageId } ?: return@withContext
        pageFile(doc.folder, target.fileName).delete()
        dao.deletePage(pageId)
        val rest = pages.filter { it.id != pageId }.mapIndexed { index, page -> page.copy(position = index) }
        dao.updatePages(rest)
        refresh(doc)
    }

    suspend fun movePage(id: Long, pageId: Long, delta: Int) = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext
        val pages = dao.pages(id).toMutableList()
        val from = pages.indexOfFirst { it.id == pageId }
        val to = from + delta
        if (from < 0 || to !in pages.indices) return@withContext
        val moved = pages.removeAt(from)
        pages.add(to, moved)
        dao.updatePages(pages.mapIndexed { index, page -> page.copy(position = index) })
        refresh(doc)
    }

    suspend fun rotatePage(id: Long, pageId: Long) = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext
        val page = dao.pages(id).firstOrNull { it.id == pageId } ?: return@withContext
        val source = pageFile(doc.folder, page.fileName)
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return@withContext
        val rotated = PhotoDecoder.rotate(bitmap, 90)
        val name = newName()
        val target = pageFile(doc.folder, name)
        val written = try {
            target.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        } catch (e: Exception) {
            false
        }
        rotated.recycle()
        if (!written) {
            target.delete()
            return@withContext
        }
        source.delete()
        dao.updatePages(listOf(page.copy(fileName = name)))
        refresh(doc)
    }

    suspend fun rename(id: Long, name: String) = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext
        dao.update(doc.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext
        File(root, doc.folder).deleteRecursively()
        File(context.cacheDir, "exports").listFiles()?.forEach { it.delete() }
        dao.deletePages(id)
        dao.delete(id)
    }

    suspend fun writePdf(id: Long, out: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext false
        val pages = dao.pages(id).mapNotNull { page ->
            val file = pageFile(doc.folder, page.fileName)
            if (!file.exists()) return@mapNotNull null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null
            else PdfPage(bounds.outWidth, bounds.outHeight, file.length()) { FileInputStream(file) }
        }
        if (pages.isEmpty()) return@withContext false
        PdfWriter.write(pages, out)
        true
    }

    suspend fun shareFile(id: Long): File? = withContext(Dispatchers.IO) {
        val doc = dao.get(id) ?: return@withContext null
        val dir = File(context.cacheDir, "exports").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        val file = File(dir, DocumentNames.fileName(doc.name) + ".pdf")
        val ok = file.outputStream().use { writePdf(id, it) }
        if (ok) file else null
    }

    private suspend fun refresh(doc: DocumentEntity) {
        val pages = dao.pages(doc.id)
        val size = pages.sumOf { pageFile(doc.folder, it.fileName).length() }
        dao.update(doc.copy(pageCount = pages.size, sizeBytes = size, updatedAt = System.currentTimeMillis()))
    }

    private fun newName(): String = "p-${System.nanoTime()}.jpg"

    private fun importInto(dir: File, uris: List<Uri>): List<String> {
        val names = ArrayList<String>()
        uris.forEach { uri ->
            val name = newName()
            if (normalize(uri, File(dir, name))) names.add(name) else File(dir, name).delete()
        }
        return names
    }

    private fun normalize(uri: Uri, target: File): Boolean {
        val decoded = PhotoDecoder.decode(context, uri, PhotoDecoder.exifRotation(context, uri), 0, 0, MAX_SIDE) ?: return false
        val longSide = max(decoded.width, decoded.height)
        val bitmap = if (longSide > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / longSide
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else {
            decoded
        }
        return try {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        } catch (e: Exception) {
            false
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    companion object {
        private const val MAX_SIDE = 2200
        private const val QUALITY = 88
    }
}
