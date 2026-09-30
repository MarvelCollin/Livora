package com.example.livora.data.docs

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfWriterTest {

    private fun jpeg(size: Int, seed: Int): ByteArray =
        ByteArray(size) { ((it * 31 + seed) and 0xFF).toByte() }.also {
            it[0] = 0xFF.toByte()
            it[1] = 0xD8.toByte()
        }

    private fun page(width: Int, height: Int, data: ByteArray) =
        PdfPage(width, height, data.size.toLong()) { ByteArrayInputStream(data) }

    private fun build(vararg pages: PdfPage): ByteArray {
        val out = ByteArrayOutputStream()
        PdfWriter.write(pages.toList(), out)
        return out.toByteArray()
    }

    private fun text(bytes: ByteArray) = String(bytes, Charsets.ISO_8859_1)

    @Test
    fun theFileHasAHeaderAndAnEndMarker() {
        val pdf = text(build(page(100, 200, jpeg(50, 1))))
        assertTrue(pdf.startsWith("%PDF-1.4\n"))
        assertTrue(pdf.trimEnd().endsWith("%%EOF"))
    }

    @Test
    fun everyCrossReferenceEntryPointsAtItsObject() {
        val bytes = build(page(800, 1000, jpeg(300, 1)), page(1000, 500, jpeg(120, 2)), page(600, 900, jpeg(999, 3)))
        val pdf = text(bytes)
        val startxref = Regex("startxref\n(\\d+)\n").find(pdf)!!.groupValues[1].toInt()
        assertTrue(pdf.substring(startxref).startsWith("xref\n"))
        val lines = pdf.substring(startxref).split("\n")
        val count = lines[1].substringAfter(' ').toInt()
        assertEquals(1 + 2 + 3 * 3, count)
        assertEquals(20, (lines[2] + "\n").length)
        for (number in 1 until count) {
            val entry = lines[2 + number]
            assertEquals(20, (entry + "\n").length)
            val offset = entry.substring(0, 10).toInt()
            assertTrue("object $number at $offset", pdf.substring(offset).startsWith("$number 0 obj\n"))
        }
    }

    @Test
    fun theTrailerNamesTheSizeAndTheCatalog() {
        val pdf = text(build(page(100, 100, jpeg(40, 1)), page(100, 100, jpeg(40, 2))))
        assertTrue(pdf.contains("/Size 9 /Root 1 0 R"))
        assertTrue(pdf.contains("/Count 2"))
        assertTrue(pdf.contains("/Kids [3 0 R 6 0 R]"))
    }

    @Test
    fun theImageBytesArePassedThroughUntouched() {
        val data = jpeg(500, 7)
        val bytes = build(page(640, 480, data))
        val marker = "stream\n".toByteArray(Charsets.ISO_8859_1)
        var index = -1
        outer@ for (i in 0..bytes.size - marker.size) {
            for (j in marker.indices) if (bytes[i + j] != marker[j]) continue@outer
            index = i + marker.size
            break
        }
        assertTrue(index > 0)
        assertTrue(bytes.copyOfRange(index, index + data.size).contentEquals(data))
        assertTrue(text(bytes).contains("/Length ${data.size} >>"))
        assertTrue(text(bytes).contains("/Filter /DCTDecode"))
    }

    @Test
    fun pagesKeepTheirAspectRatioAtA4Width() {
        val pdf = text(build(page(1000, 2000, jpeg(30, 1)), page(2000, 1000, jpeg(30, 2))))
        assertTrue(pdf.contains("/MediaBox [0 0 595.00 1190.00]"))
        assertTrue(pdf.contains("/MediaBox [0 0 595.00 297.50]"))
        assertEquals(1190.0, PdfWriter.pageHeight(page(1000, 2000, jpeg(30, 1))), 0.001)
    }

    @Test
    fun theCountOfPageObjectsMatchesThePages() {
        val pdf = text(build(page(10, 10, jpeg(20, 1)), page(10, 10, jpeg(20, 2)), page(10, 10, jpeg(20, 3)), page(10, 10, jpeg(20, 4))))
        assertEquals(4, Regex("/Type /Page /Parent").findAll(pdf).count())
        assertEquals(4, Regex("/Subtype /Image").findAll(pdf).count())
    }

    @Test
    fun defaultNamesCarryTheDate() {
        assertEquals("Scan 30 Sep 2026, 21:05", DocumentNames.default("Scan", LocalDateTime.of(2026, 9, 30, 21, 5)))
    }

    @Test
    fun namesAreTrimmedAndFallBack() {
        assertEquals("Rent agreement", DocumentNames.clean("  Rent agreement  ", "Scan"))
        assertEquals("Scan", DocumentNames.clean("   ", "Scan"))
        assertEquals(60, DocumentNames.clean("x".repeat(100), "Scan").length)
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Rent-agreement", DocumentNames.fileName("Rent agreement"))
        assertEquals("a-b-c", DocumentNames.fileName("a/b\\c"))
        assertEquals("Scan-30-Sep-2026-21-05", DocumentNames.fileName("Scan 30 Sep 2026, 21:05"))
        assertEquals("document", DocumentNames.fileName("///"))
    }
}
