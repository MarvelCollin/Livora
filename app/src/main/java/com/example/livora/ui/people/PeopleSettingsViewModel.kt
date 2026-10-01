package com.example.livora.ui.people

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.IndexStatus
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.cloud.CloudState
import com.example.livora.data.people.cloud.RemoteBackup
import com.example.livora.data.people.cloud.RestoreOutcome
import com.example.livora.data.people.cloud.SaveOutcome
import com.example.livora.data.people.db.PersonKind
import com.example.livora.data.people.scan.ScanController
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val prefs = services.prefs
    private val repository = services.repository

    private val strictnessState = MutableStateFlow(prefs.strictness)
    val strictness: StateFlow<Float> = strictnessState.asStateFlow()

    private val previewState = MutableStateFlow<Int?>(null)
    val preview: StateFlow<Int?> = previewState.asStateFlow()

    private val busyState = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = busyState.asStateFlow()

    private val statusState = MutableStateFlow<IndexStatus?>(null)
    val status: StateFlow<IndexStatus?> = statusState.asStateFlow()

    private val skipState = MutableStateFlow(prefs.skipScreenshots)
    val skipScreenshots: StateFlow<Boolean> = skipState.asStateFlow()

    val minPhotos: StateFlow<Int> = prefs.minPhotos

    val currentPeople: StateFlow<Int> = repository.summaries
        .map { all ->
            val min = prefs.minPhotos.value
            all.count { !it.hidden && (it.name != null || it.kind == PersonKind.ENROLLED || it.photoCount >= min) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var previewJob: Job? = null

    init {
        refreshStatus()
        refreshCloud()
    }

    fun refreshStatus() {
        viewModelScope.launch { statusState.value = repository.indexStatus() }
    }

    fun setStrictness(value: Float) {
        strictnessState.value = value
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(350)
            previewState.value = null
            previewState.value = services.clustering.preview(value, prefs.minPhotos.value)
        }
    }

    fun setMinPhotos(value: Int) {
        prefs.setMinPhotos(value)
    }

    fun setSkipScreenshots(value: Boolean) {
        prefs.skipScreenshots = value
        skipState.value = value
        prefs.lastGeneration = -1L
    }

    fun groupAgain() {
        val value = strictnessState.value
        viewModelScope.launch {
            busyState.value = "Grouping again"
            val result = services.scanner.exclusive {
                prefs.strictness = value
                services.clustering.regroup(value)
            }
            busyState.value = null
            previewState.value = null
            if (result == null) {
                Toaster.info("Wait for the scan to finish, then group again")
            } else {
                Toaster.success("Grouped again. Names, merges and corrections were kept.")
            }
            refreshStatus()
        }
    }

    fun scanNew() {
        ScanController.start(getApplication())
        Toaster.info("Looking for new photos in the background")
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            busyState.value = "Saving people data"
            try {
                val summary = services.backup.export(uri)
                Toaster.success("Saved ${formatCount(summary.faces)} faces of ${formatCount(summary.persons)} people")
            } catch (e: Exception) {
                Toaster.error("The file could not be saved")
            } finally {
                busyState.value = null
            }
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            busyState.value = "Restoring people data"
            try {
                val summary = services.scanner.exclusive { services.backup.import(uri) }
                if (summary == null) {
                    Toaster.info("Wait for the scan to finish, then import")
                } else {
                    val rescan = if (summary.needRescan == 0) "" else ", ${formatCount(summary.needRescan)} photos will be checked again"
                    Toaster.show(
                        "Restored ${formatCount(summary.persons)} people and ${formatCount(summary.matchedPhotos)} photos$rescan",
                        ToastType.Success,
                        durationMs = 6000
                    )
                    ScanController.start(getApplication())
                    strictnessState.value = prefs.strictness
                }
            } catch (e: Exception) {
                Toaster.error(e.message ?: "The file could not be read")
            } finally {
                busyState.value = null
                refreshStatus()
            }
        }
    }

    val cloudState: StateFlow<CloudState> = services.cloud.state
    val cloudRemote: StateFlow<RemoteBackup?> = services.cloud.remote
    val cloudSavedAt: StateFlow<Long> = services.cloud.savedAt
    val cloudLinked: StateFlow<Boolean> = services.cloud.linked

    fun saveToCloud(replaceExisting: Boolean) {
        viewModelScope.launch {
            when (val result = services.cloud.saveNow(replaceExisting)) {
                SaveOutcome.Saved -> Toaster.success("Saved your people to the cloud")
                SaveOutcome.NothingToSave -> Toaster.info("Scan your photos first, there is nothing to save yet")
                SaveOutcome.NeedsDecision -> Toaster.info("A cloud backup already exists. Restore it or choose Replace.")
                SaveOutcome.Busy -> Toaster.info("Wait for the scan to finish, then save")
                SaveOutcome.NotConfigured -> Toaster.error("Cloud backup is not set up in this build")
                is SaveOutcome.Failed -> Toaster.error(result.message)
            }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            when (val result = services.cloud.restore()) {
                is RestoreOutcome.Restored -> {
                    strictnessState.value = prefs.strictness
                    val summary = result.summary
                    val rescan = if (summary.needRescan == 0) "" else ", ${formatCount(summary.needRescan)} photos will be checked again"
                    Toaster.show(
                        "Restored ${formatCount(summary.persons)} people and ${formatCount(summary.matchedPhotos)} photos$rescan",
                        ToastType.Success,
                        durationMs = 6000
                    )
                    refreshStatus()
                }
                RestoreOutcome.Busy -> Toaster.info("Wait for the scan to finish, then restore")
                RestoreOutcome.NoBackup -> Toaster.info("There is no cloud backup to restore")
                RestoreOutcome.NotConfigured -> Toaster.error("Cloud backup is not set up in this build")
                is RestoreOutcome.Failed -> Toaster.error(result.message)
            }
        }
    }

    fun refreshCloud() {
        viewModelScope.launch { services.cloud.refreshRemote() }
    }

    fun rescanEverything() {
        viewModelScope.launch {
            busyState.value = "Clearing the index"
            val done = services.scanner.exclusive { repository.resetIndex() }
            busyState.value = null
            if (done == null) {
                Toaster.info("Wait for the scan to finish first")
            } else {
                ScanController.start(getApplication())
                Toaster.info("Scanning everything again in the background")
            }
            refreshStatus()
        }
    }
}
