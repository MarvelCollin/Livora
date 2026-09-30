package com.example.livora.data.docs

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DocumentNames {

    private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

    fun default(prefix: String, now: LocalDateTime): String = "$prefix ${stamp.format(now)}"

    fun clean(input: String, fallback: String): String {
        val trimmed = input.trim().take(60)
        return if (trimmed.isEmpty()) fallback else trimmed
    }

    fun fileName(name: String): String {
        val safe = name.map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '-' }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(' ', '-')
            .replace(Regex("-{2,}"), "-")
            .trim('-')
            .ifEmpty { "document" }
        return safe.take(60)
    }
}
