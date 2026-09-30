package com.example.livora.data.people.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceEmbedder(context: Context, threads: Int = 4) : Closeable {

    private val interpreter: Interpreter = TfliteModels.interpreter(context, TfliteModels.EMBEDDER, threads)
    private val size = FaceAlignment.OUTPUT_SIZE
    private val input: ByteBuffer = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
    private val output: ByteBuffer = ByteBuffer.allocateDirect(VectorMath.EMBEDDING_SIZE * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(size * size)

    fun embed(aligned: Bitmap): FloatArray {
        aligned.getPixels(pixels, 0, size, 0, 0, size, size)
        input.clear()
        val floats = input.asFloatBuffer()
        val row = FloatArray(size * 3)
        for (y in 0 until size) {
            var o = 0
            var p = y * size
            for (x in 0 until size) {
                val c = pixels[p++]
                row[o++] = (((c shr 16) and 0xFF) - 127.5f) / 128f
                row[o++] = (((c shr 8) and 0xFF) - 127.5f) / 128f
                row[o++] = ((c and 0xFF) - 127.5f) / 128f
            }
            floats.put(row)
        }
        input.rewind()
        output.clear()
        interpreter.run(input, output)
        output.rewind()
        val result = FloatArray(VectorMath.EMBEDDING_SIZE)
        output.asFloatBuffer().get(result)
        return VectorMath.normalized(result)
    }

    override fun close() {
        interpreter.close()
    }
}
