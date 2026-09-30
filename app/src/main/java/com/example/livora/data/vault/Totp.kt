package com.example.livora.data.vault

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TotpConfig(
    val secret: ByteArray,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val algorithm: String = "SHA1",
    val issuer: String = "",
    val account: String = ""
)

object Totp {

    fun parse(input: String): TotpConfig? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (!text.startsWith("otpauth://", ignoreCase = true)) {
            val secret = Base32.decode(text) ?: return null
            return if (secret.isEmpty()) null else TotpConfig(secret)
        }
        val afterScheme = text.substringAfter("://")
        val type = afterScheme.substringBefore('/')
        if (!type.equals("totp", ignoreCase = true)) return null
        val rest = afterScheme.substringAfter('/', "")
        val label = decode(rest.substringBefore('?'))
        val params = rest.substringAfter('?', "")
            .split('&')
            .filter { it.contains('=') }
            .associate { decode(it.substringBefore('=')).lowercase() to decode(it.substringAfter('=')) }
        val secret = Base32.decode(params["secret"] ?: return null) ?: return null
        val algorithm = when ((params["algorithm"] ?: "SHA1").uppercase()) {
            "SHA256" -> "SHA256"
            "SHA512" -> "SHA512"
            else -> "SHA1"
        }
        val digits = (params["digits"]?.toIntOrNull() ?: 6).coerceIn(6, 8)
        val period = (params["period"]?.toIntOrNull() ?: 30).coerceIn(5, 300)
        val issuer = params["issuer"] ?: label.substringBefore(':', "")
        val account = if (label.contains(':')) label.substringAfter(':').trim() else label
        return TotpConfig(secret, digits, period, algorithm, issuer, account)
    }

    fun generate(config: TotpConfig, timeMillis: Long): String {
        val counter = timeMillis / 1000 / config.periodSeconds
        return hotp(config.secret, counter, config.digits, config.algorithm)
    }

    fun secondsRemaining(config: TotpConfig, timeMillis: Long): Int {
        val elapsed = (timeMillis / 1000) % config.periodSeconds
        return (config.periodSeconds - elapsed).toInt()
    }

    fun hotp(secret: ByteArray, counter: Long, digits: Int, algorithm: String): String {
        val macName = when (algorithm) {
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> "HmacSHA1"
        }
        val mac = Mac.getInstance(macName)
        mac.init(SecretKeySpec(secret, macName))
        val message = ByteArray(8) { index -> (counter shr (56 - index * 8)).toByte() }
        val hash = mac.doFinal(message)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        var modulus = 1
        repeat(digits) { modulus *= 10 }
        return (binary % modulus).toString().padStart(digits, '0')
    }

    private fun decode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: IllegalArgumentException) {
        value
    }
}
