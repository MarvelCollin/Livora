package com.example.livora.data.docs

import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class PdfPage(val width: Int, val height: Int, val length: Long, val open: () -> InputStream)

object PdfWriter {

    const val PAGE_WIDTH = 595.0

    private class Counter(private val target: OutputStream) : OutputStream() {
        var count = 0L
            private set

        override fun write(b: Int) {
            target.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            target.write(b, off, len)
            count += len
        }
    }

    private fun Counter.text(value: String) = write(value.toByteArray(Charsets.ISO_8859_1))

    fun pageHeight(page: PdfPage): Double = PAGE_WIDTH * page.height / page.width.coerceAtLeast(1)

    fun write(pages: List<PdfPage>, out: OutputStream) {
        val counter = Counter(out)
        val objects = 2 + pages.size * 3
        val positions = LongArray(objects + 1)

        counter.text("%PDF-1.4\n%âãÏÓ\n")

        positions[1] = counter.count
        counter.text("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        positions[2] = counter.count
        val kids = pages.indices.joinToString(" ") { "${3 + it * 3} 0 R" }
        counter.text("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\nendobj\n")

        pages.forEachIndexed { index, page ->
            val pageObject = 3 + index * 3
            val imageObject = pageObject + 1
            val contentObject = pageObject + 2
            val width = String.format(Locale.ROOT, "%.2f", PAGE_WIDTH)
            val height = String.format(Locale.ROOT, "%.2f", pageHeight(page))

            positions[pageObject] = counter.count
            counter.text(
                "$pageObject 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] " +
                    "/Resources << /XObject << /Im0 $imageObject 0 R >> >> /Contents $contentObject 0 R >>\nendobj\n"
            )

            positions[imageObject] = counter.count
            counter.text(
                "$imageObject 0 obj\n<< /Type /XObject /Subtype /Image /Width ${page.width} /Height ${page.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${page.length} >>\nstream\n"
            )
            page.open().use { it.copyTo(counter) }
            counter.text("\nendstream\nendobj\n")

            val content = "q $width 0 0 $height 0 0 cm /Im0 Do Q\n"
            positions[contentObject] = counter.count
            counter.text("$contentObject 0 obj\n<< /Length ${content.length} >>\nstream\n$content" + "endstream\nendobj\n")
        }

        val xref = counter.count
        counter.text("xref\n0 ${objects + 1}\n0000000000 65535 f \n")
        for (number in 1..objects) counter.text(String.format(Locale.ROOT, "%010d 00000 n \n", positions[number]))
        counter.text("trailer\n<< /Size ${objects + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        counter.flush()
    }
}
