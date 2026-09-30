package com.example.livora.data.people.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object TfliteModels {

    const val DETECTOR = "people/yunet_640.tflite"
    const val EMBEDDER = "people/mobilefacenet.tflite"

    fun load(context: Context, assetName: String): MappedByteBuffer {
        context.assets.openFd(assetName).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    fun interpreter(context: Context, assetName: String, threads: Int): Interpreter {
        val options = Interpreter.Options()
        options.setNumThreads(threads)
        options.setUseXNNPACK(true)
        return Interpreter(load(context, assetName), options)
    }
}
