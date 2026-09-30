package com.example.livora.data.people.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VectorMath {

    const val EMBEDDING_SIZE = 192

    fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        val n = if (a.size < b.size) a.size else b.size
        for (i in 0 until n) sum += a[i] * b[i]
        return sum
    }

    fun norm(a: FloatArray): Float = sqrt(dot(a, a))

    fun normalized(a: FloatArray): FloatArray {
        val n = norm(a)
        if (n < 1e-9f) return a.copyOf()
        return FloatArray(a.size) { a[it] / n }
    }

    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(vector)
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val out = FloatArray(bytes.size / 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out)
        return out
    }
}
