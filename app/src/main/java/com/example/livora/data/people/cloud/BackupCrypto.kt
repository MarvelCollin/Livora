package com.example.livora.data.people.cloud

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongBackupKeyException : IOException("The backup was saved with a different PEOPLE_BACKUP_KEY")

object BackupCrypto {

    private val MAGIC = byteArrayOf('L'.code.toByte(), 'V'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, secret: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(secret, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        val body = cipher.doFinal(plain)
        return MAGIC + salt + iv + body
    }

    fun decrypt(blob: ByteArray, secret: String): ByteArray {
        val headerSize = MAGIC.size + SALT_SIZE + IV_SIZE
        if (blob.size <= headerSize || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IOException("This is not a Livora cloud backup")
        }
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + SALT_SIZE)
        val iv = blob.copyOfRange(MAGIC.size + SALT_SIZE, headerSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(secret, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        return try {
            cipher.doFinal(blob, headerSize, blob.size - headerSize)
        } catch (e: GeneralSecurityException) {
            throw WrongBackupKeyException()
        }
    }

    private fun key(secret: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }
}
