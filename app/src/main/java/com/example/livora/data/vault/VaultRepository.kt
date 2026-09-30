package com.example.livora.data.vault

import org.json.JSONObject
import java.io.File

class VaultKeyLostException(cause: Throwable? = null) : Exception("The device key for the vault is gone", cause)

class WrappedKey(val iv: ByteArray, val sealed: ByteArray)

interface KeyWrapper {
    suspend fun wrap(dataKey: ByteArray): WrappedKey
    suspend fun unwrap(wrapped: WrappedKey): ByteArray
    fun reset()
}

class VaultSession(val dataKey: ByteArray) {
    fun close() {
        VaultCrypto.wipe(dataKey)
    }
}

class VaultRepository(private val directory: File, private val wrapper: KeyWrapper) {

    private val vaultFile get() = File(directory, VAULT_FILE)
    private val keyFile get() = File(directory, KEY_FILE)

    fun isSetUp(): Boolean = vaultFile.exists() && keyFile.exists()

    class Created(val session: VaultSession, val recoveryCode: String)

    suspend fun create(): Created {
        val dataKey = VaultCrypto.newKey()
        val recoveryBytes = RecoveryCode.generate()
        val wrapped = wrapper.wrap(dataKey)
        val recoveryKey = VaultCrypto.deriveRecoveryKey(recoveryBytes)
        val recoverySealed = VaultCrypto.encrypt(recoveryKey, dataKey, RECOVERY_AAD)
        VaultCrypto.wipe(recoveryKey)
        writeKeyFile(wrapped, recoverySealed)
        val session = VaultSession(dataKey)
        save(session, emptyList())
        val code = RecoveryCode.format(recoveryBytes)
        VaultCrypto.wipe(recoveryBytes)
        return Created(session, code)
    }

    suspend fun unlock(): VaultSession {
        val stored = readKeyFile()
        val dataKey = wrapper.unwrap(WrappedKey(stored.iv, stored.sealed))
        return VaultSession(dataKey)
    }

    suspend fun recover(code: String): VaultSession? {
        val recoveryBytes = RecoveryCode.parse(code) ?: return null
        val stored = readKeyFile()
        val recoveryKey = VaultCrypto.deriveRecoveryKey(recoveryBytes)
        VaultCrypto.wipe(recoveryBytes)
        val dataKey = try {
            VaultCrypto.decrypt(recoveryKey, stored.recoverySealed, RECOVERY_AAD)
        } catch (e: VaultCorruptException) {
            return null
        } finally {
            VaultCrypto.wipe(recoveryKey)
        }
        wrapper.reset()
        val wrapped = wrapper.wrap(dataKey)
        writeKeyFile(wrapped, stored.recoverySealed)
        return VaultSession(dataKey)
    }

    fun load(session: VaultSession): List<VaultEntry> {
        val sealed = vaultFile.readBytes()
        if (sealed.isEmpty() || sealed[0].toInt() != FORMAT_VERSION) throw VaultCorruptException()
        val plain = VaultCrypto.decrypt(session.dataKey, sealed.copyOfRange(1, sealed.size), VAULT_AAD)
        return try {
            VaultJson.decode(plain)
        } catch (e: org.json.JSONException) {
            throw VaultCorruptException(e)
        } finally {
            VaultCrypto.wipe(plain)
        }
    }

    fun save(session: VaultSession, entries: List<VaultEntry>) {
        val plain = VaultJson.encode(entries)
        val sealed = VaultCrypto.encrypt(session.dataKey, plain, VAULT_AAD)
        VaultCrypto.wipe(plain)
        writeAtomically(vaultFile, byteArrayOf(FORMAT_VERSION.toByte()) + sealed)
    }

    fun destroy() {
        vaultFile.delete()
        keyFile.delete()
        wrapper.reset()
    }

    private class StoredKey(val iv: ByteArray, val sealed: ByteArray, val recoverySealed: ByteArray)

    private fun writeKeyFile(wrapped: WrappedKey, recoverySealed: ByteArray) {
        val json = JSONObject()
            .put("v", 1)
            .put("iv", Hex.encode(wrapped.iv))
            .put("key", Hex.encode(wrapped.sealed))
            .put("recovery", Hex.encode(recoverySealed))
        writeAtomically(keyFile, json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun readKeyFile(): StoredKey {
        val json = try {
            JSONObject(String(keyFile.readBytes(), Charsets.UTF_8))
        } catch (e: Exception) {
            throw VaultCorruptException(e)
        }
        return try {
            StoredKey(
                iv = Hex.decode(json.getString("iv")),
                sealed = Hex.decode(json.getString("key")),
                recoverySealed = Hex.decode(json.getString("recovery"))
            )
        } catch (e: Exception) {
            throw VaultCorruptException(e)
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        directory.mkdirs()
        val temp = File(directory, target.name + ".tmp")
        temp.outputStream().use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        if (!temp.renameTo(target)) {
            target.delete()
            if (!temp.renameTo(target)) throw java.io.IOException("Could not save the vault")
        }
    }

    companion object {
        const val VAULT_FILE = "vault.bin"
        const val KEY_FILE = "vault_key.json"
        private const val FORMAT_VERSION = 1
        private val VAULT_AAD = "livora.vault.entries.v1".toByteArray(Charsets.UTF_8)
        private val RECOVERY_AAD = "livora.vault.recovery.v1".toByteArray(Charsets.UTF_8)
    }
}
