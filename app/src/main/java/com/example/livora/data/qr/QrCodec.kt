package com.example.livora.data.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

enum class QrLevel(val label: String, internal val level: ErrorCorrectionLevel) {
    L("L", ErrorCorrectionLevel.L),
    M("M", ErrorCorrectionLevel.M),
    Q("Q", ErrorCorrectionLevel.Q),
    H("H", ErrorCorrectionLevel.H)
}

class QrMatrix(val size: Int, private val bits: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]
}

object QrCodec {

    const val QUIET_ZONE = 4
    const val MAX_TEXT = 1200

    fun encode(text: String, level: QrLevel): QrMatrix? {
        if (text.isEmpty() || text.length > MAX_TEXT) return null
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to level.level,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        return try {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
            val size = matrix.width
            val bits = BooleanArray(size * size)
            for (y in 0 until size) for (x in 0 until size) bits[y * size + x] = matrix[x, y]
            QrMatrix(size, bits)
        } catch (e: WriterException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun pixels(matrix: QrMatrix, scale: Int, foreground: Int, background: Int): Pair<Int, IntArray> {
        val side = (matrix.size + QUIET_ZONE * 2) * scale
        val out = IntArray(side * side) { background }
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix[x, y]) continue
                val left = (x + QUIET_ZONE) * scale
                val top = (y + QUIET_ZONE) * scale
                for (dy in 0 until scale) {
                    val row = (top + dy) * side
                    for (dx in 0 until scale) out[row + left + dx] = foreground
                }
            }
        }
        return side to out
    }

    fun scaleFor(matrix: QrMatrix, targetPx: Int): Int = (targetPx / (matrix.size + QUIET_ZONE * 2)).coerceAtLeast(1)

    fun decode(width: Int, height: Int, pixels: IntArray): String? {
        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        val attempts = listOf(
            { BinaryBitmap(HybridBinarizer(source)) },
            { BinaryBitmap(GlobalHistogramBinarizer(source)) },
            { BinaryBitmap(HybridBinarizer(source.invert())) }
        )
        for (attempt in attempts) {
            try {
                return MultiFormatReader().apply { setHints(hints) }.decodeWithState(attempt()).text
            } catch (e: NotFoundException) {
                continue
            } catch (e: com.google.zxing.ReaderException) {
                continue
            }
        }
        return null
    }
}
