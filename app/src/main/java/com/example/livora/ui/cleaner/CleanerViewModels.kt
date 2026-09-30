package com.example.livora.ui.cleaner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.cleaner.CleanerAccess
import com.example.livora.data.cleaner.CleanerFile
import com.example.livora.data.cleaner.CleanerLibrary
import com.example.livora.data.cleaner.CleanerRepository
import com.example.livora.data.cleaner.CleanerSource
import com.example.livora.data.cleaner.DuplicateResult
import com.example.livora.data.cleaner.StorageOverview
import com.example.livora.data.cleaner.StorageReader
import com.example.livora.data.db.AppDatabase
import com.example.livora.ui.components.Toaster
import com.example.livora.ui.people.ConsentBroker
import com.example.livora.ui.people.ConsentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanerState(
    val access: Boolean,
    val loading: Boolean,
    val library: CleanerLibrary?,
    val storage: StorageOverview?,
    val duplicates: DuplicateResult?,
    val checkingDuplicates: Boolean
)

class CleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CleanerRepository(application, AppDatabase.get(application))
    private val _state = MutableStateFlow(
        CleanerState(
            access = CleanerAccess.hasAny(application),
            loading = true,
            library = null,
            storage = null,
            duplicates = null,
            checkingDuplicates = false
        )
    )
    val state: StateFlow<CleanerState> = _state.asStateFlow()

    fun refresh() {
        val app = getApplication<Application>()
        val access = CleanerAccess.hasAny(app)
        _state.update { it.copy(access = access, loading = it.library == null && access) }
        if (!access) return
        viewModelScope.launch {
            val library = repository.scan()
            val storage = withContext(Dispatchers.IO) { StorageReader.read(app, library.files) }
            _state.update { it.copy(loading = false, library = library, storage = storage, checkingDuplicates = true) }
            val duplicates = repository.duplicates(library.candidates)
            _state.update { it.copy(duplicates = duplicates, checkingDuplicates = false) }
        }
    }

    fun clearKept() {
        viewModelScope.launch {
            repository.clearKept()
            Toaster.success("Kept files will show up in the review again")
            refresh()
        }
    }
}

class ReviewState(
    val loading: Boolean,
    val queue: List<CleanerFile>,
    val decisions: List<Boolean>,
    val trashed: Int,
    val trashedBytes: Long
) {
    val index: Int get() = decisions.size
    val done: Boolean get() = !loading && index >= queue.size
    val finished: Boolean get() = trashed > 0
    val toTrash: List<CleanerFile> get() = queue.filterIndexed { i, _ -> decisions.getOrNull(i) == false }
    val keptCount: Int get() = decisions.count { it }
}

class CleanerReviewViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val repository = CleanerRepository(application, AppDatabase.get(application))
    val source: CleanerSource = CleanerSource.of(handle.get<String>("source"))

    private val _state = MutableStateFlow(ReviewState(true, emptyList(), emptyList(), 0, 0L))
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    val consent: StateFlow<ConsentRequest?> = ConsentBroker.request.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val library = repository.scan()
            val duplicates = if (source == CleanerSource.Duplicates) repository.duplicates(library.candidates) else null
            val queue = library.forSource(source, duplicates)
            _state.value = ReviewState(false, queue, emptyList(), 0, 0L)
        }
    }

    fun decide(keep: Boolean) {
        val current = _state.value
        val file = current.queue.getOrNull(current.index) ?: return
        _state.update { it.copy(decisions = it.decisions + keep) }
        if (keep) viewModelScope.launch { repository.keep(file) }
    }

    fun undo() {
        val current = _state.value
        if (current.decisions.isEmpty()) return
        val index = current.decisions.lastIndex
        val wasKeep = current.decisions.last()
        _state.update { it.copy(decisions = it.decisions.dropLast(1)) }
        if (wasKeep) current.queue.getOrNull(index)?.let { file -> viewModelScope.launch { repository.unkeep(file) } }
    }

    fun restart() {
        val current = _state.value
        val keptFiles = current.queue.filterIndexed { i, _ -> current.decisions.getOrNull(i) == true }
        viewModelScope.launch { keptFiles.forEach { repository.unkeep(it) } }
        _state.update { it.copy(decisions = emptyList()) }
    }

    fun trash() {
        val files = _state.value.toTrash
        if (files.isEmpty()) return
        if (!CleanerAccess.canTrash) {
            Toaster.error("Moving files to the trash needs Android 11 or newer")
            return
        }
        ConsentBroker.ask(repository.trashRequest(files), viewModelScope) {
            _state.update { it.copy(trashed = files.size, trashedBytes = files.sumOf { file -> file.size }) }
            Toaster.success("Moved ${files.size} to the trash")
        }
    }

    private fun ReviewState.copy(
        decisions: List<Boolean> = this.decisions,
        trashed: Int = this.trashed,
        trashedBytes: Long = this.trashedBytes
    ) = ReviewState(loading, queue, decisions, trashed, trashedBytes)
}
