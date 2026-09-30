package com.example.livora.data.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTest {

    private fun roundTrip(text: String, level: QrLevel = QrLevel.M): String? {
        val matrix = QrCodec.encode(text, level) ?: return null
        val (side, pixels) = QrCodec.pixels(matrix, 6, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        return QrCodec.decode(side, side, pixels)
    }

    @Test
    fun generatedCodesDecodeToTheSameText() {
        listOf(
            "hello",
            "https://example.com/path?q=1&r=two",
            "Table 12, order 4471",
            "Ünïcödé and 日本語 text",
            "WIFI:T:WPA;S:KopiKita_5G;P:secret123;;"
        ).forEach { text ->
            QrLevel.entries.forEach { level ->
                assertEquals("$text at ${level.label}", text, roundTrip(text, level))
            }
        }
    }

    @Test
    fun longTextStillRoundTrips() {
        val text = "0123456789".repeat(60)
        assertEquals(text, roundTrip(text, QrLevel.L))
    }

    @Test
    fun emptyAndTooLongTextCannotBeEncoded() {
        assertNull(QrCodec.encode("", QrLevel.M))
        assertNull(QrCodec.encode("x".repeat(QrCodec.MAX_TEXT + 1), QrLevel.M))
    }

    @Test
    fun aBlankImageHasNoCode() {
        val pixels = IntArray(200 * 200) { 0xFFFFFFFF.toInt() }
        assertNull(QrCodec.decode(200, 200, pixels))
    }

    @Test
    fun invertedCodesAreStillRead() {
        val matrix = QrCodec.encode("inverted", QrLevel.M)!!
        val (side, pixels) = QrCodec.pixels(matrix, 6, 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        assertEquals("inverted", QrCodec.decode(side, side, pixels))
    }

    @Test
    fun higherLevelsMakeDenserCodes() {
        val low = QrCodec.encode("a fairly ordinary sentence for a code", QrLevel.L)!!
        val high = QrCodec.encode("a fairly ordinary sentence for a code", QrLevel.H)!!
        assertTrue(high.size >= low.size)
    }

    @Test
    fun websitesKeepTheirAddress() {
        val content = QrParser.parse("  https://example.com/menu  ")
        assertEquals(QrKind.Website, content.kind)
        assertEquals("https://example.com/menu", content.url)
        assertTrue(content.warnings.isEmpty())
    }

    @Test
    fun unencryptedAndShortenedLinksAreFlagged() {
        val http = QrParser.parse("http://menu.warungmakan.id/today")
        assertEquals(1, http.warnings.size)
        assertTrue(http.warnings.single().contains("not encrypted"))
        val short = QrParser.parse("https://bit.ly/3xYzAbc")
        assertTrue(short.warnings.single().contains("Shortened"))
    }

    @Test
    fun trickyLinksAreFlagged() {
        assertTrue(QrParser.parse("https://google.com@evil.example/login").warnings.any { it.contains("@") })
        assertTrue(QrParser.parse("https://xn--pple-43d.com").warnings.any { it.contains("look-alike") })
        assertTrue(QrParser.parse("https://192.168.0.1/admin").warnings.any { it.contains("number") })
        assertEquals("evil.example", QrParser.hostOf("https://google.com@evil.example/login"))
    }

    @Test
    fun wwwAddressesBecomeSecureWebsites() {
        val content = QrParser.parse("www.example.com")
        assertEquals(QrKind.Website, content.kind)
        assertEquals("https://www.example.com", content.url)
    }

    @Test
    fun wifiCodesExposeNetworkAndPassword() {
        val content = QrParser.parse("WIFI:T:WPA;S:Kopi\\;Kita;P:pa\\:ss;;")
        assertEquals(QrKind.WiFi, content.kind)
        assertEquals("Kopi;Kita", content.field("Network"))
        assertEquals("pa:ss", content.field("Password"))
        assertTrue(content.fields.first { it.label == "Password" }.secret)
        assertTrue(content.warnings.isEmpty())
    }

    @Test
    fun openAndWepNetworksAreFlagged() {
        assertTrue(QrParser.parse("WIFI:T:nopass;S:Cafe;;").warnings.single().contains("no password"))
        assertTrue(QrParser.parse("WIFI:T:WEP;S:Old;P:12345;;").warnings.single().contains("WEP"))
    }

    @Test
    fun contactsAreReadFromVCardAndMeCard() {
        val vcard = QrParser.parse("BEGIN:VCARD\nVERSION:3.0\nFN:Rina Putri\nTEL:0812 555 0142\nEMAIL:rina@example.com\nEND:VCARD")
        assertEquals(QrKind.Contact, vcard.kind)
        assertEquals("Rina Putri", vcard.title)
        assertEquals("0812 555 0142", vcard.field("Phone"))
        val me = QrParser.parse("MECARD:N:Putri,Rina;TEL:0812555;;")
        assertEquals("Rina Putri", me.title)
        assertEquals("0812555", me.field("Phone"))
    }

    @Test
    fun emailPhoneSmsAndLocationAreRecognised() {
        val mail = QrParser.parse("mailto:hi@example.com?subject=Hello%20there&body=Body")
        assertEquals(QrKind.Email, mail.kind)
        assertEquals("Hello there", mail.field("Subject"))
        assertEquals(QrKind.Phone, QrParser.parse("tel:+62812555").kind)
        val sms = QrParser.parse("SMSTO:0812555:Hi there")
        assertEquals(QrKind.Sms, sms.kind)
        assertEquals("Hi there", sms.field("Message"))
        val geo = QrParser.parse("geo:-6.2,106.8")
        assertEquals(QrKind.Location, geo.kind)
        assertEquals("-6.2", geo.field("Latitude"))
    }

    @Test
    fun anythingElseIsPlainText() {
        val content = QrParser.parse("Table 12, order 4471")
        assertEquals(QrKind.Text, content.kind)
        assertTrue(content.warnings.isEmpty())
    }

    @Test
    fun builtPayloadsAreReadBackByTheParser() {
        val wifi = QrParser.parse(QrPayloads.wifi("My;Net", "p:w,d", WifiSecurity.Wpa, false))
        assertEquals("My;Net", wifi.field("Network"))
        assertEquals("p:w,d", wifi.field("Password"))
        val open = QrParser.parse(QrPayloads.wifi("Cafe", "", WifiSecurity.None, true))
        assertEquals("None", open.field("Security"))
        assertEquals("Yes", open.field("Hidden"))
        val contact = QrParser.parse(QrPayloads.contact("Rina Putri", "0812 555", "rina@example.com"))
        assertEquals("Rina Putri", contact.title)
        assertEquals("rina@example.com", contact.field("Email"))
        val mail = QrParser.parse(QrPayloads.email("a@b.co", "Hi there", "Line one"))
        assertEquals("Hi there", mail.field("Subject"))
        assertEquals("Line one", mail.field("Message"))
        assertEquals("+62812555", QrParser.parse(QrPayloads.phone("+62 812-555")).title)
        assertEquals("Hello", QrParser.parse(QrPayloads.sms("0812", "Hello")).field("Message"))
        assertEquals("-6.2", QrParser.parse(QrPayloads.location("-6.2", "106.8")).field("Latitude"))
    }

    @Test
    fun buildersRejectIncompleteInput() {
        assertEquals("", QrPayloads.website("  "))
        assertEquals("https://example.com", QrPayloads.website("example.com"))
        assertEquals("http://example.com", QrPayloads.website("http://example.com"))
        assertEquals("", QrPayloads.wifi("", "x", WifiSecurity.Wpa, false))
        assertEquals("", QrPayloads.contact("", "1", ""))
        assertEquals("", QrPayloads.contact("Name", "", ""))
        assertEquals("", QrPayloads.location("95", "10"))
        assertEquals("", QrPayloads.location("abc", "10"))
        assertEquals("geo:-6.2,106.8", QrPayloads.location("-6,2", "106.8"))
    }

    @Test
    fun aRoundTripThroughACodeKeepsTheParsedKind() {
        val payload = QrPayloads.wifi("Kopi Kita", "hunter2", WifiSecurity.Wpa, false)
        val decoded = roundTrip(payload)
        assertNotNull(decoded)
        assertFalse(QrParser.parse(decoded!!).kind != QrKind.WiFi)
    }
}
