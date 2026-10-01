package com.example.livora.data.people.cloud

import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
object BackupChunks {

    const val CHUNK_BYTES = 600_000

    fun split(blob: ByteArray, chunkBytes: Int = CHUNK_BYTES): List<String> {
        if (blob.isEmpty()) return listOf("")
        val out = ArrayList<String>((blob.size + chunkBytes - 1) / chunkBytes)
        var offset = 0
        while (offset < blob.size) {
            val end = minOf(blob.size, offset + chunkBytes)
            out.add(Base64.Default.encode(blob, offset, end))
            offset = end
        }
        return out
    }

    fun join(parts: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        for (part in parts) out.write(Base64.Default.decode(part))
        return out.toByteArray()
    }
}
