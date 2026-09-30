package com.example.livora.data.people.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

class DetectedFace(
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val landmarks: FloatArray
)

class FaceDetector(context: Context, threads: Int = 4) : Closeable {

    private val interpreter: Interpreter = TfliteModels.interpreter(context, TfliteModels.DETECTOR, threads)
    private val inputSize: Int
    private val inputBuffer: ByteBuffer
    private val pixels: IntArray
    private val outputBuffers = HashMap<Int, ByteBuffer>()
    private val outputSpecs = ArrayList<OutputSpec>()

    private class OutputSpec(val index: Int, val kind: String, val stride: Int, val count: Int, val channels: Int)

    init {
        val shape = interpreter.getInputTensor(0).shape()
        inputSize = shape[1]
        inputBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).order(ByteOrder.nativeOrder())
        pixels = IntArray(inputSize * inputSize)
        for (i in 0 until interpreter.outputTensorCount) {
            val tensor = interpreter.getOutputTensor(i)
            val outShape = tensor.shape()
            val count = outShape[1]
            val channels = outShape[2]
            val kind = when (channels) {
                4 -> "bbox"
                10 -> "kps"
                else -> if (tensor.name().contains("obj")) "obj" else "cls"
            }
            val stride = inputSize / kotlin.math.sqrt(count.toDouble()).roundToInt()
            outputSpecs.add(OutputSpec(i, kind, stride, count, channels))
            outputBuffers[i] = ByteBuffer.allocateDirect(count * channels * 4).order(ByteOrder.nativeOrder())
        }
    }

    fun detect(bitmap: Bitmap, scoreThreshold: Float = FaceQuality.MIN_DETECTION_SCORE): List<DetectedFace> {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 16 || height < 16) return emptyList()
        val scale = inputSize.toFloat() / max(width, height)
        val targetWidth = max(1, (width * scale).roundToInt()).coerceAtMost(inputSize)
        val targetHeight = max(1, (height * scale).roundToInt()).coerceAtMost(inputSize)
        val scaled = BitmapScaler.scale(bitmap, targetWidth, targetHeight)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        if (scaled !== bitmap) scaled.recycle()
        fillInput(targetWidth, targetHeight)

        val outputs = HashMap<Int, Any>()
        for ((index, buffer) in outputBuffers) {
            buffer.clear()
            outputs[index] = buffer
        }
        inputBuffer.rewind()
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(inputBuffer), outputs)

        val heads = ArrayList<YuNetHead>()
        val strides = outputSpecs.map { it.stride }.distinct()
        for (stride in strides) {
            val cls = readFloats(outputSpecs.first { it.stride == stride && it.kind == "cls" })
            val obj = readFloats(outputSpecs.first { it.stride == stride && it.kind == "obj" })
            val bbox = readFloats(outputSpecs.first { it.stride == stride && it.kind == "bbox" })
            val kps = readFloats(outputSpecs.first { it.stride == stride && it.kind == "kps" })
            heads.add(YuNetHead(stride, cls, obj, bbox, kps))
        }
        val raw = YuNetDecoder.decode(inputSize, inputSize, heads, scoreThreshold, 0.3f)
        val inverse = 1f / scale
        return raw.mapNotNull { face ->
            val landmarks = FloatArray(10) { face.landmarks[it] * inverse }
            val left = (face.left * inverse).coerceIn(0f, width.toFloat())
            val top = (face.top * inverse).coerceIn(0f, height.toFloat())
            val right = ((face.left + face.width) * inverse).coerceIn(0f, width.toFloat())
            val bottom = ((face.top + face.height) * inverse).coerceIn(0f, height.toFloat())
            if (right - left < 4f || bottom - top < 4f) null
            else DetectedFace(face.score, left, top, right, bottom, landmarks)
        }
    }

    private fun readFloats(spec: OutputSpec): FloatArray {
        val buffer = outputBuffers.getValue(spec.index)
        buffer.rewind()
        val out = FloatArray(spec.count * spec.channels)
        buffer.asFloatBuffer().get(out)
        return out
    }

    private fun fillInput(width: Int, height: Int) {
        inputBuffer.clear()
        val floats = inputBuffer.asFloatBuffer()
        val row = FloatArray(inputSize * 3)
        for (y in 0 until inputSize) {
            if (y < height) {
                var p = y * width
                var o = 0
                for (x in 0 until width) {
                    val c = pixels[p++]
                    row[o++] = (c and 0xFF).toFloat()
                    row[o++] = ((c shr 8) and 0xFF).toFloat()
                    row[o++] = ((c shr 16) and 0xFF).toFloat()
                }
                java.util.Arrays.fill(row, o, row.size, 0f)
            } else {
                java.util.Arrays.fill(row, 0f)
            }
            floats.put(row)
        }
    }

    override fun close() {
        interpreter.close()
    }
}
