package com.example.livora.data.people.ml

import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable

class AnalyzedFace(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val detectionScore: Float,
    val quality: Float,
    val issues: Set<FaceIssue>,
    val eyeDistance: Float,
    val embedding: FloatArray,
    val aligned: Bitmap?
)

class AnalyzerTimings {
    var detectNanos = 0L
    var embedNanos = 0L
    var photos = 0
    var faces = 0
}

class FaceAnalyzer(context: Context, threads: Int = 4) : Closeable {

    private val detector = FaceDetector(context, threads)
    private val embedder = FaceEmbedder(context, threads)
    val timings = AnalyzerTimings()

    fun analyze(bitmap: Bitmap, keepAligned: Boolean = false, minQuality: Float = MIN_STORE_QUALITY): List<AnalyzedFace> {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val start = System.nanoTime()
        val detections = detector.detect(bitmap)
        timings.detectNanos += System.nanoTime() - start
        timings.photos++
        if (detections.isEmpty()) return emptyList()
        val ranked = detections
            .filter { FaceAlignment.eyeDistance(it.landmarks) >= FaceQuality.MIN_EYE_DISTANCE }
            .sortedByDescending { (it.right - it.left) * (it.bottom - it.top) * it.score }
            .take(MAX_FACES_PER_PHOTO)
        val result = ArrayList<AnalyzedFace>(ranked.size)
        for (face in ranked) {
            val embedStart = System.nanoTime()
            val aligned = FaceAligner.align(bitmap, face.landmarks) ?: continue
            val sharpness = FaceQuality.sharpness(FaceAligner.grayscale(aligned), FaceAlignment.OUTPUT_SIZE)
            val eyeDistance = FaceAlignment.eyeDistance(face.landmarks)
            val report = FaceQuality.assess(
                face.score,
                eyeDistance,
                FaceAlignment.yawRatio(face.landmarks),
                sharpness
            )
            if (report.score < minQuality) {
                aligned.recycle()
                timings.embedNanos += System.nanoTime() - embedStart
                continue
            }
            val embedding = embedder.embed(aligned)
            timings.embedNanos += System.nanoTime() - embedStart
            timings.faces++
            val keep = if (keepAligned) aligned else null
            if (keep == null) aligned.recycle()
            result.add(
                AnalyzedFace(
                    left = face.left / width,
                    top = face.top / height,
                    right = face.right / width,
                    bottom = face.bottom / height,
                    detectionScore = face.score,
                    quality = report.score,
                    issues = report.issues,
                    eyeDistance = eyeDistance,
                    embedding = embedding,
                    aligned = keep
                )
            )
        }
        return result
    }

    override fun close() {
        detector.close()
        embedder.close()
    }

    companion object {
        const val MIN_STORE_QUALITY = 0.2f
        const val MAX_FACES_PER_PHOTO = 12
    }
}
