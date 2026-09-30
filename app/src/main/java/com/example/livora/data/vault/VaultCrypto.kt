package com.example.livora.data.vault

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VaultCorruptException(cause: Throwable? = null) : Exception("Vault data is damaged or the key is wrong", cause)

object VaultCrypto {

    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also { random.nextBytes(it) }

    fun encrypt(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plain)
    }

    fun decrypt(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        if (sealed.size < NONCE_BYTES + TAG_BITS / 8) throw VaultCorruptException()
        return try {
            val nonce = sealed.copyOfRange(0, NONCE_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, NONCE_BYTES, sealed.size - NONCE_BYTES)
        } catch (e: java.security.GeneralSecurityException) {
            throw VaultCorruptException(e)
        }
    }

    fun deriveRecoveryKey(recoveryBytes: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("livora.vault.recovery.v1".toByteArray(Charsets.UTF_8))
        digest.update(recoveryBytes)
        return digest.digest()
    }

    fun wipe(bytes: ByteArray) {
        bytes.fill(0)
    }
}

object RecoveryCode {

    const val BYTES = 20
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val random = SecureRandom()

    fun generate(): ByteArray = ByteArray(BYTES).also { random.nextBytes(it) }

    fun format(bytes: ByteArray): String {
        val encoded = Base32.encode(bytes)
        return encoded.chunked(4).joinToString("-")
    }

    fun parse(text: String): ByteArray? {
        val cleaned = text.uppercase().filter { it in ALPHABET }
        if (cleaned.length != BYTES * 8 / 5) return null
        val decoded = Base32.decode(cleaned) ?: return null
        return if (decoded.size == BYTES) decoded else null
    }
}

object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return out.toString()
    }

    fun decode(text: String): ByteArray? {
        val cleaned = text.uppercase().filter { it != ' ' && it != '-' && it != '=' }
        if (cleaned.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (char in cleaned) {
            val value = ALPHABET.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}

object Hex {

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            out.append("0123456789abcdef"[value shr 4])
            out.append("0123456789abcdef"[value and 0x0F])
        }
        return out.toString()
    }

    fun decode(text: String): ByteArray {
        require(text.length % 2 == 0)
        return ByteArray(text.length / 2) { index ->
            val high = Character.digit(text[index * 2], 16)
            val low = Character.digit(text[index * 2 + 1], 16)
            require(high >= 0 && low >= 0)
            ((high shl 4) or low).toByte()
        }
    }
}
