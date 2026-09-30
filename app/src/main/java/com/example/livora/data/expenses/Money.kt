package com.example.livora.data.expenses

import kotlin.math.abs

object Money {

    fun group(value: Long): String {
        val digits = abs(value).toString()
        val out = StringBuilder()
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) out.append('.')
            out.append(char)
        }
        return out.toString()
    }

    fun format(value: Long): String = "Rp ${group(value)}"

    fun signed(value: Long): String = when {
        value > 0 -> "+${format(value)}"
        value < 0 -> "-${format(value)}"
        else -> format(0)
    }

    fun compact(value: Long): String {
        val v = abs(value)
        return when {
            v >= 1_000_000_000 -> "Rp ${trim(v / 1_000_000_000.0)} m"
            v >= 1_000_000 -> "Rp ${trim(v / 1_000_000.0)} jt"
            v >= 1_000 -> "Rp ${v / 1_000} rb"
            else -> "Rp $v"
        }
    }

    private fun trim(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString().replace('.', ',')
    }

    fun parseDigits(text: String): Long = text.toLongOrNull() ?: 0L
}
