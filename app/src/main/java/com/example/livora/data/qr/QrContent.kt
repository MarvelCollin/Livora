package com.example.livora.data.qr

enum class QrKind(val label: String) {
    Website("Website"),
    WiFi("Wi-Fi"),
    Contact("Contact"),
    Email("Email"),
    Phone("Phone"),
    Sms("SMS"),
    Location("Location"),
    Text("Text")
}

class QrField(val label: String, val value: String, val secret: Boolean = false)

class QrContent(
    val raw: String,
    val kind: QrKind,
    val title: String,
    val fields: List<QrField>,
    val warnings: List<String>,
    val url: String? = null
) {
    fun field(label: String): String? = fields.firstOrNull { it.label == label }?.value
}

object QrParser {

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "cutt.ly", "rebrand.ly", "buff.ly",
        "shorturl.at", "tiny.cc", "rb.gy", "s.id", "t.ly", "lnkd.in"
    )

    private val urlPattern = Regex("^(https?)://([^/?#\\s]+)([^\\s]*)$", RegexOption.IGNORE_CASE)
    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun parse(input: String): QrContent {
        val raw = input.trim()
        val lower = raw.lowercase()
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> website(raw)
            lower.startsWith("www.") && !raw.contains(' ') -> website("https://$raw")
            lower.startsWith("wifi:") -> wifi(raw)
            lower.startsWith("begin:vcard") -> vcard(raw)
            lower.startsWith("mecard:") -> mecard(raw)
            lower.startsWith("mailto:") -> email(raw)
            lower.startsWith("tel:") -> phone(raw)
            lower.startsWith("smsto:") || lower.startsWith("sms:") -> sms(raw)
            lower.startsWith("geo:") -> location(raw)
            else -> QrContent(raw, QrKind.Text, raw, emptyList(), emptyList())
        }
    }

    fun linkWarnings(url: String): List<String> {
        val match = urlPattern.matchEntire(url) ?: return emptyList()
        val scheme = match.groupValues[1].lowercase()
        val authority = match.groupValues[2]
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        val out = ArrayList<String>()
        if (scheme == "http") out.add("This address is not encrypted. Do not enter passwords on it.")
        if (host in shorteners) out.add("Shortened link. Check where it goes before you open it.")
        if (authority.contains('@')) out.add("This address hides its real destination behind an @ sign.")
        if (host.startsWith("xn--") || host.contains(".xn--")) out.add("This address uses look-alike characters. It may pretend to be another site.")
        if (ipv4.matches(host)) out.add("This address is a number, not a website name.")
        return out
    }

    fun hostOf(url: String): String? =
        urlPattern.matchEntire(url)?.groupValues?.get(2)?.substringAfterLast('@')?.substringBefore(':')

    private fun website(url: String): QrContent =
        QrContent(url, QrKind.Website, url, listOf(QrField("Address", url)), linkWarnings(url), url)

    private fun splitUnescaped(text: String, separator: Char): List<String> {
        val parts = ArrayList<String>()
        val current = StringBuilder()
        var escaped = false
        for (char in text) {
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == separator -> {
                    parts.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        parts.add(current.toString())
        return parts
    }

    private fun wifi(raw: String): QrContent {
        val values = HashMap<String, String>()
        splitUnescaped(raw.substring(5), ';').forEach { part ->
            val index = part.indexOf(':')
            if (index > 0) values[part.substring(0, index).uppercase()] = part.substring(index + 1)
        }
        val ssid = values["S"].orEmpty()
        val password = values["P"].orEmpty()
        val type = values["T"].orEmpty().uppercase()
        val security = when {
            type == "WEP" -> "WEP"
            type.isEmpty() || type == "NOPASS" -> "None"
            else -> "WPA or WPA2"
        }
        val warnings = ArrayList<String>()
        if (security == "None") warnings.add("This network has no password. Anyone nearby can join and watch unencrypted traffic.")
        if (security == "WEP") warnings.add("WEP security is old and easy to break.")
        val fields = ArrayList<QrField>()
        fields.add(QrField("Network", ssid))
        fields.add(QrField("Security", security))
        if (password.isNotEmpty()) fields.add(QrField("Password", password, secret = true))
        if (values["H"].equals("true", ignoreCase = true)) fields.add(QrField("Hidden", "Yes"))
        return QrContent(raw, QrKind.WiFi, ssid.ifBlank { "Wi-Fi network" }, fields, warnings)
    }

    private fun vcard(raw: String): QrContent {
        val lines = raw.replace("\r\n", "\n").split('\n')
        fun value(vararg keys: String): String? = lines.firstNotNullOfOrNull { line ->
            val name = line.substringBefore(':').substringBefore(';').uppercase()
            if (name in keys && line.contains(':')) line.substringAfter(':').trim().ifBlank { null } else null
        }
        val name = value("FN") ?: value("N")?.split(';')?.filter { it.isNotBlank() }?.reversed()?.joinToString(" ") ?: "Contact"
        val fields = ArrayList<QrField>()
        fields.add(QrField("Name", name))
        value("TEL")?.let { fields.add(QrField("Phone", it)) }
        value("EMAIL")?.let { fields.add(QrField("Email", it)) }
        value("ORG")?.let { fields.add(QrField("Organization", it.replace(";", " "))) }
        return QrContent(raw, QrKind.Contact, name, fields, emptyList())
    }

    private fun mecard(raw: String): QrContent {
        val values = HashMap<String, String>()
        splitUnescaped(raw.substring(7), ';').forEach { part ->
            val index = part.indexOf(':')
            if (index > 0) values[part.substring(0, index).uppercase()] = part.substring(index + 1)
        }
        val name = values["N"].orEmpty().split(',').filter { it.isNotBlank() }.reversed().joinToString(" ").ifBlank { "Contact" }
        val fields = ArrayList<QrField>()
        fields.add(QrField("Name", name))
        values["TEL"]?.let { fields.add(QrField("Phone", it)) }
        values["EMAIL"]?.let { fields.add(QrField("Email", it)) }
        return QrContent(raw, QrKind.Contact, name, fields, emptyList())
    }

    private fun email(raw: String): QrContent {
        val body = raw.substring(7)
        val address = body.substringBefore('?')
        val query = body.substringAfter('?', "")
        val params = query.split('&').filter { it.contains('=') }.associate { it.substringBefore('=').lowercase() to decode(it.substringAfter('=')) }
        val fields = ArrayList<QrField>()
        fields.add(QrField("Address", address))
        params["subject"]?.takeIf { it.isNotBlank() }?.let { fields.add(QrField("Subject", it)) }
        params["body"]?.takeIf { it.isNotBlank() }?.let { fields.add(QrField("Message", it)) }
        return QrContent(raw, QrKind.Email, address, fields, emptyList())
    }

    private fun phone(raw: String): QrContent {
        val number = raw.substring(4).trim()
        return QrContent(raw, QrKind.Phone, number, listOf(QrField("Number", number)), emptyList())
    }

    private fun sms(raw: String): QrContent {
        val body = raw.substringAfter(':')
        val number = body.substringBefore(':').substringBefore('?')
        val message = if (body.contains(':')) body.substringAfter(':') else decode(body.substringAfter("body=", ""))
        val fields = ArrayList<QrField>()
        fields.add(QrField("Number", number))
        if (message.isNotBlank()) fields.add(QrField("Message", message))
        return QrContent(raw, QrKind.Sms, number, fields, emptyList())
    }

    private fun location(raw: String): QrContent {
        val body = raw.substring(4).substringBefore('?').substringBefore(';')
        val parts = body.split(',')
        val lat = parts.getOrNull(0)?.trim().orEmpty()
        val lon = parts.getOrNull(1)?.trim().orEmpty()
        return QrContent(raw, QrKind.Location, "$lat, $lon", listOf(QrField("Latitude", lat), QrField("Longitude", lon)), emptyList())
    }

    private fun decode(text: String): String = try {
        java.net.URLDecoder.decode(text, "UTF-8")
    } catch (e: Exception) {
        text
    }
}

enum class WifiSecurity(val label: String, val code: String) {
    Wpa("WPA or WPA2", "WPA"),
    Wep("WEP", "WEP"),
    None("No password", "nopass")
}

object QrPayloads {

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

    fun website(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.contains("://")) trimmed else "https://$trimmed"
    }

    fun wifi(ssid: String, password: String, security: WifiSecurity, hidden: Boolean): String {
        if (ssid.isBlank()) return ""
        val builder = StringBuilder("WIFI:T:").append(security.code).append(";S:").append(escape(ssid)).append(';')
        if (security != WifiSecurity.None) builder.append("P:").append(escape(password)).append(';')
        if (hidden) builder.append("H:true;")
        return builder.append(';').toString()
    }

    fun contact(name: String, phone: String, email: String): String {
        if (name.isBlank() || (phone.isBlank() && email.isBlank())) return ""
        val lines = ArrayList<String>()
        lines.add("BEGIN:VCARD")
        lines.add("VERSION:3.0")
        lines.add("FN:${name.trim()}")
        lines.add("N:${name.trim()};;;;")
        if (phone.isNotBlank()) lines.add("TEL:${phone.trim()}")
        if (email.isNotBlank()) lines.add("EMAIL:${email.trim()}")
        lines.add("END:VCARD")
        return lines.joinToString("\n")
    }

    fun email(address: String, subject: String, body: String): String {
        if (address.isBlank()) return ""
        val params = ArrayList<String>()
        if (subject.isNotBlank()) params.add("subject=" + java.net.URLEncoder.encode(subject.trim(), "UTF-8").replace("+", "%20"))
        if (body.isNotBlank()) params.add("body=" + java.net.URLEncoder.encode(body.trim(), "UTF-8").replace("+", "%20"))
        return "mailto:${address.trim()}" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }

    fun phone(number: String): String {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        return if (cleaned.isEmpty()) "" else "tel:$cleaned"
    }

    fun sms(number: String, message: String): String {
        val cleaned = number.filter { it.isDigit() || it == '+' }
        if (cleaned.isEmpty()) return ""
        return "SMSTO:$cleaned:${message.trim()}"
    }

    fun location(latitude: String, longitude: String): String {
        val lat = latitude.trim().replace(',', '.').toDoubleOrNull() ?: return ""
        val lon = longitude.trim().replace(',', '.').toDoubleOrNull() ?: return ""
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return ""
        return "geo:$lat,$lon"
    }
}
