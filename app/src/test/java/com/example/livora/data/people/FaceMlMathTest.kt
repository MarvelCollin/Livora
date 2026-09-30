package com.example.livora.data.people

import com.example.livora.data.people.ml.FaceAlignment
import com.example.livora.data.people.ml.FaceIssue
import com.example.livora.data.people.ml.FaceQuality
import com.example.livora.data.people.ml.VectorMath
import com.example.livora.data.people.ml.YuNetDecoder
import com.example.livora.data.people.ml.YuNetHead
import com.example.livora.data.people.ml.RawFace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

class FaceMlMathTest {

    private fun transformTemplate(scale: Float, angle: Float, tx: Float, ty: Float): FloatArray {
        val a = scale * cos(angle)
        val b = scale * sin(angle)
        val out = FloatArray(10)
        for (i in 0 until 5) {
            val x = FaceAlignment.TEMPLATE[2 * i]
            val y = FaceAlignment.TEMPLATE[2 * i + 1]
            out[2 * i] = a * x - b * y + tx
            out[2 * i + 1] = b * x + a * y + ty
        }
        return out
    }

    @Test
    fun alignmentRecoversKnownTransform() {
        val scale = 2.5f
        val angle = 0.3f
        val landmarks = transformTemplate(scale, angle, 120f, 80f)
        val t = FaceAlignment.estimate(landmarks)
        assertNotNull(t)
        for (i in 0 until 5) {
            val mx = t!!.mapX(landmarks[2 * i], landmarks[2 * i + 1])
            val my = t.mapY(landmarks[2 * i], landmarks[2 * i + 1])
            assertEquals(FaceAlignment.TEMPLATE[2 * i], mx, 0.01f)
            assertEquals(FaceAlignment.TEMPLATE[2 * i + 1], my, 0.01f)
        }
        assertEquals(1f / scale, t!!.scale, 1e-3f)
    }

    @Test
    fun alignmentOfTemplateItselfIsIdentity() {
        val t = FaceAlignment.estimate(FaceAlignment.TEMPLATE.copyOf())!!
        assertEquals(1f, t.a, 1e-4f)
        assertEquals(0f, t.b, 1e-4f)
        assertEquals(0f, t.tx, 1e-3f)
        assertEquals(0f, t.ty, 1e-3f)
    }

    @Test
    fun alignmentRejectsDegenerateLandmarks() {
        assertNull(FaceAlignment.estimate(FloatArray(10)))
        assertNull(FaceAlignment.estimate(FloatArray(4)))
    }

    @Test
    fun matrixValuesMapPointsLikeTheTransform() {
        val t = FaceAlignment.estimate(transformTemplate(1.7f, -0.2f, 40f, 15f))!!
        val m = t.toMatrixValues()
        val x = 33f
        val y = 21f
        assertEquals(t.mapX(x, y), m[0] * x + m[1] * y + m[2], 1e-3f)
        assertEquals(t.mapY(x, y), m[3] * x + m[4] * y + m[5], 1e-3f)
    }

    @Test
    fun frontalFaceHasLowYawRatio() {
        val landmarks = transformTemplate(2f, 0f, 10f, 10f)
        assertTrue(FaceAlignment.yawRatio(landmarks) < 0.1f)
    }

    @Test
    fun turnedFaceHasHighYawRatio() {
        val landmarks = transformTemplate(2f, 0f, 10f, 10f)
        landmarks[4] += 30f
        assertTrue(FaceAlignment.yawRatio(landmarks) > FaceQuality.SIDE_VIEW_RATIO)
    }

    @Test
    fun qualityFlagsSmallBlurryAndSideFaces() {
        val good = FaceQuality.assess(0.9f, 60f, 0.05f, 200f)
        assertTrue(good.score > 0.85f)
        assertTrue(good.issues.isEmpty())
        val small = FaceQuality.assess(0.9f, 14f, 0.05f, 200f)
        assertTrue(FaceIssue.TooSmall in small.issues)
        assertTrue(small.score < good.score)
        val blurry = FaceQuality.assess(0.9f, 60f, 0.05f, 10f)
        assertTrue(FaceIssue.Blurry in blurry.issues)
        val side = FaceQuality.assess(0.9f, 60f, 0.5f, 200f)
        assertTrue(FaceIssue.SideView in side.issues)
        assertTrue(side.score < good.score)
    }

    @Test
    fun sharpnessSeparatesFlatFromTexturedImages() {
        val size = 16
        val flat = IntArray(size * size) { 120 }
        val textured = IntArray(size * size) { if ((it / size + it % size) % 2 == 0) 20 else 220 }
        assertEquals(0f, FaceQuality.sharpness(flat, size), 1e-3f)
        assertTrue(FaceQuality.sharpness(textured, size) > 1000f)
    }

    @Test
    fun embeddingBytesRoundTrip() {
        val v = FloatArray(VectorMath.EMBEDDING_SIZE) { (it - 96) / 50f }
        val back = VectorMath.fromBytes(VectorMath.toBytes(v))
        assertEquals(v.size, back.size)
        for (i in v.indices) assertEquals(v[i], back[i], 0f)
    }

    @Test
    fun normalizedVectorHasUnitLength() {
        val v = VectorMath.normalized(floatArrayOf(3f, 4f, 0f))
        assertEquals(1f, VectorMath.norm(v), 1e-6f)
        assertEquals(0.6f, v[0], 1e-6f)
    }

    private fun head(stride: Int, size: Int, hit: Int?, cls: Float, obj: Float): YuNetHead {
        val count = (size / stride) * (size / stride)
        val clsArr = FloatArray(count)
        val objArr = FloatArray(count)
        val bbox = FloatArray(count * 4)
        val kps = FloatArray(count * 10)
        if (hit != null) {
            clsArr[hit] = cls
            objArr[hit] = obj
            bbox[hit * 4] = 0.5f
            bbox[hit * 4 + 1] = 0.5f
            bbox[hit * 4 + 2] = ln(4f)
            bbox[hit * 4 + 3] = ln(4f)
        }
        return YuNetHead(stride, clsArr, objArr, bbox, kps)
    }

    @Test
    fun decoderConvertsAnchorsToBoxes() {
        val size = 64
        val heads = listOf(head(8, size, 9, 0.9f, 0.9f), head(16, size, null, 0f, 0f), head(32, size, null, 0f, 0f))
        val faces = YuNetDecoder.decode(size, size, heads, 0.6f, 0.3f)
        assertEquals(1, faces.size)
        val f = faces[0]
        assertEquals(0.9f, f.score, 1e-4f)
        assertEquals(12f - 16f, f.left, 1e-3f)
        assertEquals(8f * 1.5f - 16f, f.top, 1e-3f)
        assertEquals(32f, f.width, 1e-3f)
    }

    @Test
    fun decoderDropsLowScoresAndDuplicates() {
        val size = 64
        val low = head(8, size, 9, 0.3f, 0.3f)
        assertTrue(YuNetDecoder.decode(size, size, listOf(low), 0.6f, 0.3f).isEmpty())
        val a = RawFace(0.9f, 10f, 10f, 30f, 30f, FloatArray(10))
        val b = RawFace(0.8f, 12f, 12f, 30f, 30f, FloatArray(10))
        val c = RawFace(0.7f, 100f, 100f, 30f, 30f, FloatArray(10))
        val kept = YuNetDecoder.nonMaxSuppression(listOf(b, c, a), 0.3f)
        assertEquals(2, kept.size)
        assertEquals(0.9f, kept[0].score, 0f)
    }
}
