package com.example.livora.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.livora.data.people.IndexStatus
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.db.PersonKind
import com.example.livora.data.people.db.PersonSummary
import com.example.livora.data.people.media.AccessLevel
import com.example.livora.data.people.media.MediaAccess
import com.example.livora.data.people.scan.ScanController
import com.example.livora.data.people.scan.ScanPhase
import com.example.livora.data.people.scan.ScanStatus
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PeopleListState {
    data object Loading : PeopleListState
    class Ready(
        val people: List<PersonSummary>,
        val hiddenCount: Int,
        val smallCount: Int
    ) : PeopleListState
}

enum class WorkState { Idle, Queued, Running }

@OptIn(FlowPreview::class)
class PeopleViewModel(application: Application) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val prefs = services.prefs
    private val repository = services.repository

    private val accessState = MutableStateFlow(MediaAccess.level(application))
    val access: StateFlow<AccessLevel> = accessState.asStateFlow()

    val progress = ScanStatus.progress

    private val showHiddenState = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = showHiddenState.asStateFlow()

    private val showSmallState = MutableStateFlow(false)
    val showSmall: StateFlow<Boolean> = showSmallState.asStateFlow()

    private val initialDoneState = MutableStateFlow(prefs.initialScanDone)
    val initialScanDone: StateFlow<Boolean> = initialDoneState.asStateFlow()

    private val selectedState = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = selectedState.asStateFlow()

    private val suggestionCountState = MutableStateFlow(0)
    val mergeSuggestionCount: StateFlow<Int> = suggestionCountState.asStateFlow()

    private val statusState = MutableStateFlow<IndexStatus?>(null)
    val status: StateFlow<IndexStatus?> = statusState.asStateFlow()

    val minPhotos: StateFlow<Int> = prefs.minPhotos

    val indexedFaces: StateFlow<Int> = repository.observeIndexedFaces()
        .sample(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val indexedPhotos: StateFlow<Int> = repository.observeScannedPhotos()
        .sample(500)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val workState: StateFlow<WorkState> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow("people_scan")
        .map { infos ->
            when {
                infos.any { it.state == WorkInfo.State.RUNNING } -> WorkState.Running
                infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED } -> WorkState.Queued
                else -> WorkState.Idle
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WorkState.Idle)

    val people: StateFlow<PeopleListState> = combine(
        repository.summaries.sample(400),
        prefs.minPhotos,
        showHiddenState,
        showSmallState
    ) { all, min, showHidden, showSmall ->
        val visible = ArrayList<PersonSummary>()
        var hidden = 0
        var small = 0
        for (person in all) {
            val named = person.name != null || person.kind == PersonKind.ENROLLED
            if (person.hidden) {
                hidden++
                if (showHidden) visible.add(person)
                continue
            }
            if (person.photoCount == 0 && !named) continue
            if (!named && person.photoCount < min) {
                small++
                if (showSmall) visible.add(person)
                continue
            }
            visible.add(person)
        }
        PeopleListState.Ready(visible, hidden, small) as PeopleListState
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleListState.Loading)

    init {
        viewModelScope.launch {
            progress.map { it.phase }.distinctUntilChanged().collect { phase ->
                if (phase == ScanPhase.Done || phase == ScanPhase.Idle) {
                    refreshStatus()
                    refreshMergeSuggestions()
                }
            }
        }
    }

    fun refreshAccess() {
        val level = MediaAccess.level(getApplication())
        accessState.value = level
        initialDoneState.value = prefs.initialScanDone
        if (level == AccessLevel.None) return
        ScanController.cancelPeriodic(getApplication())
        viewModelScope.launch {
            refreshStatus()
            refreshMergeSuggestions()
        }
    }

    fun startScan() {
        if (MediaAccess.level(getApplication()) == AccessLevel.None) return
        ScanController.start(getApplication())
    }

    private suspend fun refreshStatus() {
        statusState.value = repository.indexStatus()
    }

    private var suggestionJob: kotlinx.coroutines.Job? = null

    private var suggestionsAt = 0L
    private var suggestionsFaces = -1

    fun refreshMergeSuggestions() {
        if (progress.value.active) return
        val now = System.currentTimeMillis()
        val faces = indexedFaces.value
        if (now - suggestionsAt < 30_000L && faces == suggestionsFaces) return
        suggestionsAt = now
        suggestionsFaces = faces
        suggestionJob?.cancel()
        suggestionJob = viewModelScope.launch {
            suggestionCountState.value = repository.mergeSuggestions().size
        }
    }

    fun toggleHidden() {
        showHiddenState.value = !showHiddenState.value
    }

    fun toggleSmall() {
        showSmallState.value = !showSmallState.value
    }

    fun toggleSelect(id: Long) {
        val current = selectedState.value
        selectedState.value = if (id in current) current - id else current + id
    }

    fun clearSelection() {
        selectedState.value = emptySet()
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch {
            repository.rename(id, name)
            Toaster.success("Saved the name")
        }
    }

    fun sameSelected() {
        val ids = selectedState.value.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            val result = repository.samePerson(ids)
            selectedState.value = emptySet()
            if (result == null) {
                Toaster.error("Those groups could not be merged")
            } else {
                Toaster.show(
                    message = "Merged ${ids.size} groups into one person",
                    type = ToastType.Success,
                    durationMs = 7000,
                    actionLabel = "Undo",
                    onAction = { viewModelScope.launch { result.undo.restore() } }
                )
                refreshMergeSuggestions()
            }
        }
    }

    fun hideSelected() {
        val ids = selectedState.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (id in ids) repository.setHidden(id, true)
            selectedState.value = emptySet()
            Toaster.show(
                message = if (ids.size == 1) "Hid 1 person" else "Hid ${ids.size} people",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = { viewModelScope.launch { for (id in ids) repository.setHidden(id, false) } }
            )
        }
    }

    fun markNotificationAsked() {
        prefs.notificationAsked = true
    }

    val notificationAsked: Boolean get() = prefs.notificationAsked
}
