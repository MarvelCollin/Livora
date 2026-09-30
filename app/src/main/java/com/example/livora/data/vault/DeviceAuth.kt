package com.example.livora.data.vault

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.core.content.ContextCompat
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class AuthCancelledException(val userCancelled: Boolean, message: String) : Exception(message)

enum class DeviceAuthStatus { READY, NO_SCREEN_LOCK, NO_HARDWARE, UNAVAILABLE, UNSUPPORTED_OS }

@SuppressLint("NewApi")
object DeviceAuth {

    const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun status(context: Context): DeviceAuthStatus {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            return DeviceAuthStatus.UNSUPPORTED_OS
        }
        val manager = context.getSystemService(BiometricManager::class.java)
            ?: return DeviceAuthStatus.NO_HARDWARE
        return when (manager.canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> DeviceAuthStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> DeviceAuthStatus.NO_SCREEN_LOCK
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> DeviceAuthStatus.NO_HARDWARE
            else -> DeviceAuthStatus.UNAVAILABLE
        }
    }

    suspend fun authorize(context: Context, cipher: Cipher, title: String, subtitle: String): Cipher =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            val prompt = BiometricPrompt.Builder(context)
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            prompt.authenticate(
                BiometricPrompt.CryptoObject(cipher),
                signal,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authorized = result.cryptoObject?.cipher
                        if (authorized != null && continuation.isActive) {
                            continuation.resume(authorized)
                        } else if (continuation.isActive) {
                            continuation.resumeWithException(AuthCancelledException(false, "No key from the prompt"))
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        val userCancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                            errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                AuthCancelledException(userCancelled, errString?.toString() ?: "Authentication failed")
                            )
                        }
                    }
                }
            )
        }
}

@SuppressLint("NewApi")
class KeystoreKeyWrapper(
    private val context: Context,
    private val title: String,
    private val subtitle: String
) : KeyWrapper {

    override suspend fun wrap(dataKey: ByteArray): WrappedKey {
        reset()
        val key = createKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val authorized = DeviceAuth.authorize(context, cipher, title, subtitle)
        val sealed = authorized.doFinal(dataKey)
        return WrappedKey(authorized.iv, sealed)
    }

    override suspend fun unwrap(wrapped: WrappedKey): ByteArray {
        val key = try {
            loadKey() ?: throw VaultKeyLostException()
        } catch (e: UnrecoverableKeyException) {
            throw VaultKeyLostException(e)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, wrapped.iv))
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw VaultKeyLostException(e)
        } catch (e: java.security.InvalidKeyException) {
            throw VaultKeyLostException(e)
        }
        val authorized = DeviceAuth.authorize(context, cipher, title, subtitle)
        return try {
            authorized.doFinal(wrapped.sealed)
        } catch (e: java.security.GeneralSecurityException) {
            throw VaultCorruptException(e)
        }
    }

    override fun reset() {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }

    private fun loadKey(): SecretKey? {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey
    }

    private fun createKey(): SecretKey {
        return try {
            generate(strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            generate(strongBox = false)
        } catch (e: java.security.ProviderException) {
            generate(strongBox = false)
        } catch (e: java.security.InvalidAlgorithmParameterException) {
            generate(strongBox = false)
        }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            .setInvalidatedByBiometricEnrollment(false)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS = "livora.vault.kek"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
