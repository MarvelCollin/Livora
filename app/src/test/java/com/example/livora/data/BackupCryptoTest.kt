package com.example.livora.data

import com.example.livora.data.people.cloud.BackupChunks
import com.example.livora.data.people.cloud.BackupCrypto
import com.example.livora.data.people.cloud.WrongBackupKeyException
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

class BackupCryptoTest {

    private val secret = "unit-test-secret"

    private fun sample(size: Int): ByteArray = ByteArray(size).also { Random(7).nextBytes(it) }

    @Test
    fun roundTripReturnsTheSameBytes() {
        val plain = sample(5000)
        val blob = BackupCrypto.encrypt(plain, secret)
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, secret))
    }

    @Test
    fun ciphertextDoesNotContainThePlainText() {
        val plain = "face-embedding-marker-face-embedding-marker".toByteArray()
        val blob = BackupCrypto.encrypt(plain, secret)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("face-embedding-marker"))
    }

    @Test
    fun everyEncryptionUsesFreshRandomness() {
        val plain = sample(100)
        assertFalse(BackupCrypto.encrypt(plain, secret).contentEquals(BackupCrypto.encrypt(plain, secret)))
    }

    @Test
    fun wrongKeyIsReportedClearly() {
        val blob = BackupCrypto.encrypt(sample(100), secret)
        try {
            BackupCrypto.decrypt(blob, "another-secret")
            fail("expected a wrong key error")
        } catch (e: WrongBackupKeyException) {
            assertTrue(e.message!!.contains("PEOPLE_BACKUP_KEY"))
        }
    }

    @Test
    fun tamperedDataIsRejected() {
        val blob = BackupCrypto.encrypt(sample(300), secret)
        blob[blob.size - 5] = (blob[blob.size - 5].toInt() xor 1).toByte()
        try {
            BackupCrypto.decrypt(blob, secret)
            fail("expected rejection")
        } catch (e: WrongBackupKeyException) {
            assertTrue(true)
        }
    }

    @Test
    fun foreignFilesAreNotMistakenForBackups() {
        try {
            BackupCrypto.decrypt(sample(200), secret)
            fail("expected rejection")
        } catch (e: IOException) {
            assertEquals("This is not a Livora cloud backup", e.message)
        }
    }

    @Test
    fun chunksJoinBackToTheOriginalAcrossSizes() {
        for (size in listOf(1, 99, 100, 101, 1000, 2500)) {
            val blob = sample(size)
            val parts = BackupChunks.split(blob, chunkBytes = 100)
            assertEquals((size + 99) / 100, parts.size)
            assertArrayEquals(blob, BackupChunks.join(parts))
        }
    }

    @Test
    fun aLargeBackupSplitsIntoParts() {
        val blob = sample(1_700_000)
        val parts = BackupChunks.split(blob)
        assertEquals(3, parts.size)
        assertArrayEquals(blob, BackupChunks.join(parts))
    }
}
