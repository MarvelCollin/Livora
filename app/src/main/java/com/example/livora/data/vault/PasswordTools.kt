package com.example.livora.data.vault

import java.security.SecureRandom
import kotlin.math.ln

data class GeneratorOptions(
    val length: Int = 20,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidLookAlikes: Boolean = false
)

object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
    private const val LOOK_ALIKES = "0O1lI|"
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64

    fun generate(options: GeneratorOptions, random: SecureRandom = SecureRandom()): String {
        val pools = pools(options)
        if (pools.isEmpty()) return ""
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH).coerceAtLeast(pools.size)
        val all = pools.joinToString("")
        val chars = CharArray(length)
        for (index in 0 until length) chars[index] = all[random.nextInt(all.length)]
        val positions = (0 until length).toMutableList()
        for (pool in pools) {
            val slot = random.nextInt(positions.size)
            val position = positions.removeAt(slot)
            chars[position] = pool[random.nextInt(pool.length)]
        }
        return String(chars)
    }

    private fun pools(options: GeneratorOptions): List<String> {
        val list = ArrayList<String>()
        if (options.lowercase) list.add(LOWER)
        if (options.uppercase) list.add(UPPER)
        if (options.digits) list.add(DIGITS)
        if (options.symbols) list.add(SYMBOLS)
        return if (options.avoidLookAlikes) {
            list.map { pool -> pool.filter { it !in LOOK_ALIKES } }.filter { it.isNotEmpty() }
        } else {
            list
        }
    }
}

enum class Strength(val label: String, val level: Int) {
    VERY_WEAK("Very weak", 0),
    WEAK("Weak", 1),
    FAIR("Fair", 2),
    STRONG("Strong", 3),
    VERY_STRONG("Very strong", 4)
}

object PasswordStrength {

    private val common = setOf(
        "password", "123456", "12345678", "123456789", "qwerty", "abc123", "111111", "123123",
        "admin", "letmein", "welcome", "monkey", "dragon", "iloveyou", "sunshine", "princess",
        "football", "baseball", "master", "login", "passw0rd", "starwars", "1234567890",
        "qwertyuiop", "asdfghjkl", "zxcvbnm", "000000", "654321", "superman", "trustno1"
    )

    fun bits(password: String): Double {
        if (password.isEmpty()) return 0.0
        if (password.lowercase() in common) return 5.0
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 32
        if (pool == 0) pool = 26
        var effectiveLength = password.length.toDouble()
        effectiveLength -= repeatedRuns(password) * 0.75
        effectiveLength -= sequentialRuns(password) * 0.75
        effectiveLength -= (password.length - password.toSet().size) * 0.25
        if (effectiveLength < 1.0) effectiveLength = 1.0
        return effectiveLength * (ln(pool.toDouble()) / ln(2.0))
    }

    fun rate(password: String): Strength {
        val bits = bits(password)
        return when {
            password.isEmpty() -> Strength.VERY_WEAK
            bits < 28 -> Strength.VERY_WEAK
            bits < 40 -> Strength.WEAK
            bits < 60 -> Strength.FAIR
            bits < 80 -> Strength.STRONG
            else -> Strength.VERY_STRONG
        }
    }

    private fun repeatedRuns(text: String): Int {
        var count = 0
        for (index in 1 until text.length) if (text[index] == text[index - 1]) count++
        return count
    }

    private fun sequentialRuns(text: String): Int {
        var count = 0
        for (index in 1 until text.length) {
            val step = text[index].code - text[index - 1].code
            if (step == 1 || step == -1) count++
        }
        return count
    }
}
