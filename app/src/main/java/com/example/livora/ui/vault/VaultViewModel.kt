package com.example.livora.ui.vault

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.vault.AuthCancelledException
import com.example.livora.data.vault.DeviceAuth
import com.example.livora.data.vault.DeviceAuthStatus
import com.example.livora.data.vault.KeystoreKeyWrapper
import com.example.livora.data.vault.VaultClipboard
import com.example.livora.data.vault.VaultCorruptException
import com.example.livora.data.vault.VaultEntry
import com.example.livora.data.vault.VaultKeyLostException
import com.example.livora.data.vault.VaultRepository
import com.example.livora.data.vault.VaultSession
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface VaultStage {
    data class Unavailable(val status: DeviceAuthStatus) : VaultStage
    data object NeedsSetup : VaultStage
    data class ShowRecovery(val code: String) : VaultStage
    data object Locked : VaultStage
    data object KeyLost : VaultStage
    data object Damaged : VaultStage
    data object Unlocked : VaultStage
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val directory = File(application.filesDir, DIRECTORY)
    private val saveLock = Mutex()
    private var session: VaultSession? = null

    private val _stage = MutableStateFlow<VaultStage>(initialStage())
    val stage: StateFlow<VaultStage> = _stage.asStateFlow()

    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private fun initialStage(): VaultStage {
        val context = getApplication<Application>()
        val status = DeviceAuth.status(context)
        return when {
            status == DeviceAuthStatus.UNSUPPORTED_OS -> VaultStage.Unavailable(status)
            exists() -> VaultStage.Locked
            status != DeviceAuthStatus.READY -> VaultStage.Unavailable(status)
            else -> VaultStage.NeedsSetup
        }
    }

    private fun exists(): Boolean =
        File(directory, VaultRepository.VAULT_FILE).exists() && File(directory, VaultRepository.KEY_FILE).exists()

    @SuppressLint("NewApi")
    private fun repository(context: Context, title: String, subtitle: String) =
        VaultRepository(directory, KeystoreKeyWrapper(context, title, subtitle))

    fun recheckDevice() {
        _error.value = null
        _stage.value = initialStage()
    }

    fun clearError() {
        _error.value = null
    }

    fun createVault(context: Context) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val repo = repository(context, "Create your vault", "Confirm with your fingerprint or phone lock")
                val created = repo.create()
                session?.close()
                session = created.session
                _entries.value = emptyList()
                _stage.value = VaultStage.ShowRecovery(created.recoveryCode)
            } catch (e: AuthCancelledException) {
                _error.value = if (e.userCancelled) "Setup was cancelled" else "Could not confirm your identity"
            } catch (e: Exception) {
                _error.value = "Could not create the vault on this phone"
            } finally {
                _busy.value = false
            }
        }
    }

    fun finishSetup() {
        if (_stage.value is VaultStage.ShowRecovery) _stage.value = VaultStage.Unlocked
    }

    fun unlock(context: Context) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val repo = repository(context, "Unlock vault", "Use your fingerprint or phone lock")
                val opened = repo.unlock()
                val loaded = try {
                    withContext(Dispatchers.IO) { repo.load(opened) }
                } catch (e: Exception) {
                    opened.close()
                    throw e
                }
                session?.close()
                session = opened
                _entries.value = loaded
                _stage.value = VaultStage.Unlocked
            } catch (e: AuthCancelledException) {
                _error.value = if (e.userCancelled) "Unlock was cancelled" else "Could not confirm your identity"
            } catch (e: VaultKeyLostException) {
                _stage.value = VaultStage.KeyLost
            } catch (e: VaultCorruptException) {
                _stage.value = VaultStage.Damaged
            } catch (e: Exception) {
                _error.value = "Could not open the vault"
            } finally {
                _busy.value = false
            }
        }
    }

    fun restore(context: Context, code: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            try {
                val repo = repository(context, "Restore vault", "Confirm with your fingerprint or phone lock")
                val opened = repo.recover(code)
                if (opened == null) {
                    _error.value = "That recovery code is not right"
                } else {
                    val loaded = withContext(Dispatchers.IO) { repo.load(opened) }
                    session?.close()
                    session = opened
                    _entries.value = loaded
                    _stage.value = VaultStage.Unlocked
                }
            } catch (e: AuthCancelledException) {
                _error.value = if (e.userCancelled) "Restore was cancelled" else "Could not confirm your identity"
            } catch (e: VaultCorruptException) {
                _stage.value = VaultStage.Damaged
            } catch (e: Exception) {
                _error.value = "Could not restore the vault"
            } finally {
                _busy.value = false
            }
        }
    }

    fun lock() {
        if (_busy.value) return
        val stage = _stage.value
        if (stage !is VaultStage.Unlocked && stage !is VaultStage.ShowRecovery) return
        session?.close()
        session = null
        _entries.value = emptyList()
        VaultClipboard.clearNow(getApplication())
        _stage.value = if (exists()) VaultStage.Locked else initialStage()
    }

    fun erase(context: Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository(context, "", "").destroy()
            }
            session?.close()
            session = null
            _entries.value = emptyList()
            _error.value = null
            _stage.value = initialStage()
        }
    }

    fun newEntryId(): String = UUID.randomUUID().toString()

    fun upsert(entry: VaultEntry) {
        val list = _entries.value.toMutableList()
        val index = list.indexOfFirst { it.id == entry.id }
        if (index >= 0) list[index] = entry else list.add(entry)
        commit(list)
    }

    fun delete(id: String) {
        commit(_entries.value.filterNot { it.id == id })
    }

    private fun commit(list: List<VaultEntry>) {
        val current = session ?: return
        _entries.value = list
        viewModelScope.launch {
            saveLock.withLock {
                withContext(Dispatchers.IO) {
                    try {
                        VaultRepository(directory, NoWrapper).save(current, list)
                    } catch (e: Exception) {
                        _error.value = "Could not save. Your change may be lost."
                    }
                }
            }
        }
    }

    override fun onCleared() {
        session?.close()
        session = null
        VaultClipboard.clearNow(getApplication())
        super.onCleared()
    }

    private object NoWrapper : com.example.livora.data.vault.KeyWrapper {
        override suspend fun wrap(dataKey: ByteArray) = throw UnsupportedOperationException()
        override suspend fun unwrap(wrapped: com.example.livora.data.vault.WrappedKey) = throw UnsupportedOperationException()
        override fun reset() = Unit
    }

    companion object {
        const val DIRECTORY = "vault"
    }
}
