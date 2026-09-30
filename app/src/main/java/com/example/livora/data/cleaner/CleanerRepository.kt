package com.example.livora.data.cleaner

import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import com.example.livora.data.db.AppDatabase
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CleanerLibrary(val files: List<CleanerFile>, val kept: Set<String>) {
    val candidates: List<CleanerFile> by lazy { files.filter { it.key !in kept } }
    val screenshots: List<CleanerFile> by lazy { candidates.filter { CleanerRules.isScreenshot(it) } }
    val large: List<CleanerFile> by lazy { candidates.filter { CleanerRules.isLarge(it) } }
    val messaging: List<CleanerFile> by lazy { candidates.filter { CleanerRules.isMessaging(it) } }

    fun forSource(source: CleanerSource, duplicates: DuplicateResult?): List<CleanerFile> = when (source) {
        CleanerSource.All -> candidates
        CleanerSource.Duplicates -> duplicates?.copies.orEmpty().filter { it.key !in kept }
        else -> candidates.filter { CleanerRules.matches(source, it) }
    }.sortedByDescending { it.size }
}

class CleanerRepository(private val context: Context, db: AppDatabase) {

    private val dao = db.cleaner()

    suspend fun scan(): CleanerLibrary = withContext(Dispatchers.IO) {
        val files = CleanerMedia.queryAll(context)
        CleanerLibrary(files, dao.keys().toSet())
    }

    suspend fun duplicates(files: List<CleanerFile>): DuplicateResult = withContext(Dispatchers.IO) {
        val signature = files.size to files.sumOf { it.size }
        cached?.takeIf { it.first == signature }?.let { return@withContext it.second }
        val result = CleanerRules.groupDuplicates(CleanerRules.duplicateCandidates(files)) { sampleHash(it) }
        cached = signature to result
        result
    }

    suspend fun keep(file: CleanerFile) = dao.add(CleanerKeptEntity(file.key, System.currentTimeMillis()))

    suspend fun unkeep(file: CleanerFile) = dao.remove(file.key)

    suspend fun clearKept() = dao.clear()

    fun trashRequest(files: List<CleanerFile>): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || files.isEmpty()) return null
        return MediaStore.createTrashRequest(context.contentResolver, files.map { it.uri }, true).intentSender
    }

    private fun sampleHash(file: CleanerFile): String? = try {
        context.contentResolver.openFileDescriptor(file.uri, "r")?.use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val channel = input.channel
                val size = channel.size()
                val digest = MessageDigest.getInstance("MD5")
                val buffer = ByteBuffer.allocate(SAMPLE)
                listOf(0L, (size / 2 - SAMPLE / 2).coerceAtLeast(0L), (size - SAMPLE).coerceAtLeast(0L)).distinct().forEach { offset ->
                    buffer.clear()
                    channel.position(offset)
                    val read = channel.read(buffer)
                    if (read > 0) digest.update(buffer.array(), 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val SAMPLE = 65536

        @Volatile
        private var cached: Pair<Pair<Int, Long>, DuplicateResult>? = null
    }
}
