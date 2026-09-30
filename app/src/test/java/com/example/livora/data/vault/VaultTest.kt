package com.example.livora.data.vault

import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class SoftwareKeyWrapper(private val kek: ByteArray = VaultCrypto.newKey()) : KeyWrapper {
    var resets = 0
    var lost = false
    private val aad = "test.kek".toByteArray()

    override suspend fun wrap(dataKey: ByteArray): WrappedKey {
        val sealed = VaultCrypto.encrypt(kek, dataKey, aad)
        return WrappedKey(ByteArray(0), sealed)
    }

    override suspend fun unwrap(wrapped: WrappedKey): ByteArray {
        if (lost) throw VaultKeyLostException()
        return VaultCrypto.decrypt(kek, wrapped.sealed, aad)
    }

    override fun reset() {
        resets++
    }
}

class VaultTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(id: String, title: String = "Site $id", password: String = "pw-$id") = VaultEntry(
        id = id,
        title = title,
        username = "user@$id.test",
        password = password,
        url = "https://$id.test",
        notes = "note for $id",
        totpSecret = "JBSWY3DPEHPK3PXP",
        favorite = id == "1",
        createdAt = 1000L,
        updatedAt = 2000L
    )

    private fun repository(wrapper: KeyWrapper = SoftwareKeyWrapper(), dir: File = folder.newFolder()) =
        VaultRepository(dir, wrapper)

    @Test
    fun encryptDecryptRoundTrip() {
        val key = VaultCrypto.newKey()
        val plain = "correct horse battery staple".toByteArray()
        val sealed = VaultCrypto.encrypt(key, plain, "aad".toByteArray())
        assertArrayEquals(plain, VaultCrypto.decrypt(key, sealed, "aad".toByteArray()))
    }

    @Test
    fun nonceIsNeverReused() {
        val key = VaultCrypto.newKey()
        val seen = HashSet<String>()
        repeat(500) {
            val sealed = VaultCrypto.encrypt(key, byteArrayOf(1, 2, 3), byteArrayOf())
            assertTrue(seen.add(Hex.encode(sealed.copyOfRange(0, VaultCrypto.NONCE_BYTES))))
        }
    }

    @Test
    fun wrongKeyIsRejected() {
        val sealed = VaultCrypto.encrypt(VaultCrypto.newKey(), byteArrayOf(9, 9, 9), byteArrayOf())
        try {
            VaultCrypto.decrypt(VaultCrypto.newKey(), sealed, byteArrayOf())
            fail("expected failure")
        } catch (expected: VaultCorruptException) {
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val key = VaultCrypto.newKey()
        val sealed = VaultCrypto.encrypt(key, "secret".toByteArray(), byteArrayOf())
        for (index in sealed.indices) {
            val copy = sealed.copyOf()
            copy[index] = (copy[index].toInt() xor 0x01).toByte()
            try {
                VaultCrypto.decrypt(key, copy, byteArrayOf())
                fail("byte $index was accepted")
            } catch (expected: VaultCorruptException) {
            }
        }
    }

    @Test
    fun wrongAssociatedDataIsRejected() {
        val key = VaultCrypto.newKey()
        val sealed = VaultCrypto.encrypt(key, "secret".toByteArray(), "a".toByteArray())
        try {
            VaultCrypto.decrypt(key, sealed, "b".toByteArray())
            fail("expected failure")
        } catch (expected: VaultCorruptException) {
        }
    }

    @Test
    fun createSaveAndUnlockRoundTrip() = runBlocking {
        val wrapper = SoftwareKeyWrapper()
        val dir = folder.newFolder()
        val repo = VaultRepository(dir, wrapper)
        assertFalse(repo.isSetUp())
        val created = repo.create()
        assertTrue(repo.isSetUp())
        val entries = listOf(entry("1"), entry("2"))
        repo.save(created.session, entries)
        created.session.close()

        val reopened = VaultRepository(dir, wrapper).unlock()
        assertEquals(entries, VaultRepository(dir, wrapper).load(reopened))
    }

    @Test
    fun vaultFileHoldsNoPlaintext() = runBlocking {
        val dir = folder.newFolder()
        val repo = VaultRepository(dir, SoftwareKeyWrapper())
        val created = repo.create()
        repo.save(created.session, listOf(entry("1", title = "MyBankTitle", password = "SuperSecretPassword42")))
        val raw = File(dir, VaultRepository.VAULT_FILE).readBytes()
        val text = String(raw, Charsets.ISO_8859_1)
        assertFalse(text.contains("MyBankTitle"))
        assertFalse(text.contains("SuperSecretPassword42"))
        assertFalse(text.contains("bank"))
        val keyText = File(dir, VaultRepository.KEY_FILE).readText()
        assertFalse(keyText.contains("SuperSecret"))
    }

    @Test
    fun tamperedVaultFileFailsToLoad() = runBlocking {
        val dir = folder.newFolder()
        val repo = VaultRepository(dir, SoftwareKeyWrapper())
        val created = repo.create()
        repo.save(created.session, listOf(entry("1")))
        val file = File(dir, VaultRepository.VAULT_FILE)
        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x40).toByte()
        file.writeBytes(bytes)
        try {
            repo.load(created.session)
            fail("expected failure")
        } catch (expected: VaultCorruptException) {
        }
    }

    @Test
    fun wrongSessionKeyCannotLoad() = runBlocking {
        val dir = folder.newFolder()
        val repo = VaultRepository(dir, SoftwareKeyWrapper())
        val created = repo.create()
        repo.save(created.session, listOf(entry("1")))
        try {
            repo.load(VaultSession(VaultCrypto.newKey()))
            fail("expected failure")
        } catch (expected: VaultCorruptException) {
        }
    }

    @Test
    fun recoveryCodeRestoresAfterKeyLoss() = runBlocking {
        val wrapper = SoftwareKeyWrapper()
        val dir = folder.newFolder()
        val repo = VaultRepository(dir, wrapper)
        val created = repo.create()
        val entries = listOf(entry("1"), entry("2"), entry("3"))
        repo.save(created.session, entries)
        val code = created.recoveryCode
        created.session.close()

        wrapper.lost = true
        try {
            repo.unlock()
            fail("expected key loss")
        } catch (expected: VaultKeyLostException) {
        }

        wrapper.lost = false
        val restored = repo.recover(code)
        assertNotNull(restored)
        assertEquals(entries, repo.load(restored!!))
        assertEquals(1, wrapper.resets)
    }

    @Test
    fun wrongRecoveryCodeIsRejected() = runBlocking {
        val repo = repository()
        repo.create()
        val other = RecoveryCode.format(RecoveryCode.generate())
        assertNull(repo.recover(other))
        assertNull(repo.recover("not a code"))
    }

    @Test
    fun recoveryCodeFormatRoundTrips() {
        repeat(50) {
            val bytes = RecoveryCode.generate()
            val text = RecoveryCode.format(bytes)
            assertEquals(8 * 4 + 7, text.length)
            assertArrayEquals(bytes, RecoveryCode.parse(text))
            assertArrayEquals(bytes, RecoveryCode.parse(text.lowercase().replace("-", " ")))
        }
    }

    @Test
    fun destroyRemovesFiles() = runBlocking {
        val dir = folder.newFolder()
        val wrapper = SoftwareKeyWrapper()
        val repo = VaultRepository(dir, wrapper)
        repo.create()
        repo.destroy()
        assertFalse(repo.isSetUp())
        assertTrue(wrapper.resets >= 1)
    }

    @Test
    fun jsonRoundTripKeepsEverything() {
        val entries = listOf(
            entry("1", title = "Quote \" and \\ slash", password = "pässwörd😀"),
            entry("2").copy(notes = "line one\nline two", favorite = false)
        )
        assertEquals(entries, VaultJson.decode(VaultJson.encode(entries)))
    }

    @Test
    fun base32MatchesRfc4648Vectors() {
        assertEquals("", Base32.encode(byteArrayOf()))
        assertEquals("MY", Base32.encode("f".toByteArray()))
        assertEquals("MZXQ", Base32.encode("fo".toByteArray()))
        assertEquals("MZXW6", Base32.encode("foo".toByteArray()))
        assertEquals("MZXW6YQ", Base32.encode("foob".toByteArray()))
        assertEquals("MZXW6YTB", Base32.encode("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI", Base32.encode("foobar".toByteArray()))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("mzxw 6ytb-oi"))
        assertNull(Base32.decode("not*base32"))
    }

    private val sha1Secret = "12345678901234567890".toByteArray()
    private val sha256Secret = "12345678901234567890123456789012".toByteArray()
    private val sha512Secret = "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    private fun totp(secret: ByteArray, algorithm: String, seconds: Long) =
        Totp.generate(TotpConfig(secret, digits = 8, algorithm = algorithm), seconds * 1000)

    @Test
    fun totpMatchesRfc6238Sha1() {
        assertEquals("94287082", totp(sha1Secret, "SHA1", 59))
        assertEquals("07081804", totp(sha1Secret, "SHA1", 1111111109))
        assertEquals("14050471", totp(sha1Secret, "SHA1", 1111111111))
        assertEquals("89005924", totp(sha1Secret, "SHA1", 1234567890))
        assertEquals("69279037", totp(sha1Secret, "SHA1", 2000000000))
        assertEquals("65353130", totp(sha1Secret, "SHA1", 20000000000))
    }

    @Test
    fun totpMatchesRfc6238Sha256() {
        assertEquals("46119246", totp(sha256Secret, "SHA256", 59))
        assertEquals("68084774", totp(sha256Secret, "SHA256", 1111111109))
        assertEquals("67062674", totp(sha256Secret, "SHA256", 1111111111))
        assertEquals("91819424", totp(sha256Secret, "SHA256", 1234567890))
        assertEquals("90698825", totp(sha256Secret, "SHA256", 2000000000))
        assertEquals("77737706", totp(sha256Secret, "SHA256", 20000000000))
    }

    @Test
    fun totpMatchesRfc6238Sha512() {
        assertEquals("90693936", totp(sha512Secret, "SHA512", 59))
        assertEquals("25091201", totp(sha512Secret, "SHA512", 1111111109))
        assertEquals("99943326", totp(sha512Secret, "SHA512", 1111111111))
        assertEquals("93441116", totp(sha512Secret, "SHA512", 1234567890))
        assertEquals("38618901", totp(sha512Secret, "SHA512", 2000000000))
        assertEquals("47863826", totp(sha512Secret, "SHA512", 20000000000))
    }

    @Test
    fun totpParsesOtpauthUri() {
        val secret = Base32.encode(sha1Secret)
        val config = Totp.parse("otpauth://totp/Example:alice@example.com?secret=$secret&issuer=Example&digits=8&period=60&algorithm=SHA256")
        assertNotNull(config)
        assertEquals(8, config!!.digits)
        assertEquals(60, config.periodSeconds)
        assertEquals("SHA256", config.algorithm)
        assertEquals("Example", config.issuer)
        assertEquals("alice@example.com", config.account)
        assertArrayEquals(sha1Secret, config.secret)
    }

    @Test
    fun totpParsesPlainSecretAndRejectsJunk() {
        val plain = Totp.parse("JBSW Y3DP EHPK 3PXP")
        assertNotNull(plain)
        assertEquals(6, plain!!.digits)
        assertNull(Totp.parse(""))
        assertNull(Totp.parse("otpauth://hotp/x?secret=ABC"))
        assertNull(Totp.parse("otpauth://totp/x?issuer=nothing"))
        assertNull(Totp.parse("!!!"))
    }

    @Test
    fun totpCountdownIsWithinPeriod() {
        val config = TotpConfig(sha1Secret)
        assertEquals(30, Totp.secondsRemaining(config, 0))
        assertEquals(1, Totp.secondsRemaining(config, 29_000))
        assertEquals(30, Totp.secondsRemaining(config, 30_000))
    }

    @Test
    fun generatorHonoursLengthAndClasses() {
        val random = SecureRandom()
        repeat(300) {
            val options = GeneratorOptions(length = 8 + random.nextInt(50))
            val password = PasswordGenerator.generate(options, random)
            assertEquals(options.length, password.length)
            assertTrue(password.any { it in 'a'..'z' })
            assertTrue(password.any { it in 'A'..'Z' })
            assertTrue(password.any { it in '0'..'9' })
            assertTrue(password.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun generatorRespectsDisabledClassesAndLookAlikes() {
        val password = PasswordGenerator.generate(
            GeneratorOptions(length = 40, uppercase = false, symbols = false, avoidLookAlikes = true)
        )
        assertTrue(password.all { it in 'a'..'z' || it in '0'..'9' })
        assertTrue(password.none { it in "0O1lI|" })
        assertEquals("", PasswordGenerator.generate(GeneratorOptions(lowercase = false, uppercase = false, digits = false, symbols = false)))
    }

    @Test
    fun generatorProducesDifferentPasswords() {
        val set = HashSet<String>()
        repeat(200) { set.add(PasswordGenerator.generate(GeneratorOptions())) }
        assertEquals(200, set.size)
    }

    @Test
    fun strengthOrdersObviousCases() {
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate(""))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("password"))
        assertEquals(Strength.VERY_WEAK, PasswordStrength.rate("123456"))
        assertTrue(PasswordStrength.rate("abcdef").level <= Strength.VERY_WEAK.level)
        assertTrue(PasswordStrength.rate("aaaaaaaaaa").level <= Strength.WEAK.level)
        assertTrue(PasswordStrength.bits("Tr0ub4dor&3xyz") > PasswordStrength.bits("trombone"))
        assertEquals(Strength.VERY_STRONG, PasswordStrength.rate(PasswordGenerator.generate(GeneratorOptions(length = 24))))
        assertNotEquals(
            PasswordStrength.rate("Zx9!kQ2#"),
            PasswordStrength.rate("Zx9!kQ2#mV7@pL4$")
        )
    }
}
