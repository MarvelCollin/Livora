package com.example.livora.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.Suggestion
import com.example.livora.data.people.SuggestionSet
import com.example.livora.data.people.UndoToken
import com.example.livora.data.people.db.LinkMode
import com.example.livora.data.people.db.PersonEntity
import com.example.livora.data.people.media.FolderInfo
import com.example.livora.data.people.media.MediaImages
import com.example.livora.data.people.media.MediaWriter
import com.example.livora.data.people.scan.ScanPhase
import com.example.livora.data.people.scan.ScanStatus
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SuggestionsState {
    data object Loading : SuggestionsState
    class Ready(
        val person: PersonEntity,
        val all: SuggestionSet,
        val threshold: Float,
        val visible: List<Suggestion>
    ) : SuggestionsState
    data object Missing : SuggestionsState
}

class SuggestionsViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val repository = services.repository
    private val prefs = services.prefs
    val personId: Long = handle.get<Long>(PeopleRoutes.ARG_ID) ?: 0L

    private val listState = MutableStateFlow<SuggestionsState>(SuggestionsState.Loading)
    val state: StateFlow<SuggestionsState> = listState.asStateFlow()

    private val selectedState = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = selectedState.asStateFlow()

    val consent: StateFlow<ConsentRequest?> = ConsentBroker.request.asStateFlow()

    val progress = ScanStatus.progress

    val indexedFaces: StateFlow<Int> = repository.observeIndexedFaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val moveMatches: StateFlow<Boolean> = prefs.moveMatches

    val supportsMove: Boolean get() = MediaWriter.supportsConsentRequests

    private var decided = HashSet<Long>()

    init {
        load()
        viewModelScope.launch { services.folders.refresh() }
        viewModelScope.launch {
            progress.map { it.phase }.distinctUntilChanged().drop(1).collect { phase ->
                if (phase == ScanPhase.Done) load()
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            listState.value = SuggestionsState.Loading
            val person = repository.person(personId)
            if (person == null) {
                listState.value = SuggestionsState.Missing
                return@launch
            }
            val set = repository.suggestions(personId)
            decided = HashSet()
            selectedState.value = emptySet()
            publish(person, set, set.effectiveThreshold)
        }
    }

    private fun publish(person: PersonEntity, set: SuggestionSet, threshold: Float) {
        val visible = set.atThreshold(threshold).filter { it.faceId !in decided }
        listState.value = SuggestionsState.Ready(person, set, threshold, visible)
    }

    private fun republish() {
        val current = listState.value as? SuggestionsState.Ready ?: return
        publish(current.person, current.all, current.threshold)
    }

    fun setThreshold(value: Float) {
        val current = listState.value as? SuggestionsState.Ready ?: return
        publish(current.person, current.all, value)
    }

    fun saveThreshold() {
        val current = listState.value as? SuggestionsState.Ready ?: return
        viewModelScope.launch { repository.setThreshold(personId, current.threshold) }
    }

    fun setMoveMatches(value: Boolean) {
        prefs.setMoveMatches(value)
    }

    fun folderList(): List<FolderInfo> = (services.folders.state.value as? FoldersState.Ready)?.folders.orEmpty()

    fun chooseFolder(folder: FolderInfo, thenApprove: Boolean) {
        viewModelScope.launch {
            repository.linkFolder(personId, folder.relativePath, folder.name, LinkMode.REVIEW)
            val person = repository.person(personId) ?: return@launch
            val current = listState.value as? SuggestionsState.Ready
            if (current != null) publish(person, current.all, current.threshold)
            if (thenApprove) approve()
        }
    }

    fun createFolderAnd(name: String, thenApprove: Boolean) {
        viewModelScope.launch {
            val folder = services.folders.createFolder(name)
            if (folder == null) Toaster.error("Enter a folder name") else chooseFolder(folder, thenApprove)
        }
    }

    fun toggle(faceId: Long) {
        val set = selectedState.value
        selectedState.value = if (faceId in set) set - faceId else set + faceId
    }

    fun selectAll() {
        val current = listState.value as? SuggestionsState.Ready ?: return
        selectedState.value = current.visible.map { it.faceId }.toSet()
    }

    fun clearSelection() {
        selectedState.value = emptySet()
    }

    private fun chosen(): List<Suggestion> {
        val current = listState.value as? SuggestionsState.Ready ?: return emptyList()
        val ids = selectedState.value
        return current.visible.filter { it.faceId in ids }
    }

    private suspend fun confirmPicks(picks: List<Suggestion>): UndoToken? {
        val undo = repository.confirm(personId, picks)
        decided.addAll(picks.map { it.faceId })
        selectedState.value = emptySet()
        republish()
        return undo
    }

    private fun restorePicks(picks: List<Suggestion>, undo: UndoToken?) {
        viewModelScope.launch {
            undo?.restore()
            decided.removeAll(picks.map { it.faceId }.toSet())
            republish()
        }
    }

    fun approve() {
        val current = listState.value as? SuggestionsState.Ready ?: return
        val picks = chosen()
        if (picks.isEmpty()) return
        val person = current.person
        val path = person.linkedFolderPath
        val name = person.linkedFolderName ?: "the folder"
        val mediaIds = picks.map { it.mediaId }.distinct()
        val linked = path != null && person.linkMode != LinkMode.NONE
        if (path != null && linked && moveMatches.value && supportsMove) {
            moveAndConfirm(picks, mediaIds, path, name)
            return
        }
        viewModelScope.launch {
            val undo = confirmPicks(picks)
            if (path != null && linked) {
                services.copyRunner.start(personId, mediaIds, path, name, byAi = true)
            }
            Toaster.show(
                message = if (picks.size == 1) "Added 1 photo to this person" else "Added ${picks.size} photos to this person",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = { restorePicks(picks, undo) }
            )
        }
    }

    private fun moveAndConfirm(picks: List<Suggestion>, mediaIds: List<Long>, path: String, name: String) {
        viewModelScope.launch {
            val already = withContext(Dispatchers.IO) { MediaImages.idsInRelativePath(getApplication(), path) }
            val toMove = mediaIds.filter { it !in already }
            if (toMove.isEmpty()) {
                val undo = confirmPicks(picks)
                Toaster.show(
                    message = "Added ${picks.size} ${if (picks.size == 1) "photo" else "photos"}, already in $name",
                    type = ToastType.Success,
                    durationMs = 7000,
                    actionLabel = "Undo",
                    onAction = { restorePicks(picks, undo) }
                )
                return@launch
            }
            val previous = withContext(Dispatchers.IO) {
                MediaImages.queryByIds(getApplication(), toMove).associate { it.id to it.relativePath }
            }
            ConsentBroker.ask(MediaWriter.writeRequest(getApplication(), toMove), viewModelScope) {
                val moved = MediaWriter.applyMove(getApplication(), toMove, path)
                repository.syncPhotoDates(toMove)
                repository.recordAiMoves(personId, toMove, previous, path)
                val undo = confirmPicks(picks)
                services.folders.refresh()
                Toaster.show(
                    message = "Moved $moved ${if (moved == 1) "photo" else "photos"} to $name",
                    type = ToastType.Success,
                    durationMs = 8000,
                    actionLabel = "Undo",
                    onAction = { undoMove(toMove, previous, picks, undo) }
                )
            }
        }
    }

    private fun undoMove(ids: List<Long>, previous: Map<Long, String>, picks: List<Suggestion>, undo: UndoToken?) {
        ConsentBroker.ask(MediaWriter.writeRequest(getApplication(), ids), viewModelScope) {
            for ((path, group) in ids.groupBy { previous[it] ?: "Pictures/" }) {
                MediaWriter.applyMove(getApplication(), group, path)
            }
            repository.syncPhotoDates(ids)
            repository.dropAiMoves(ids)
            undo?.restore()
            decided.removeAll(picks.map { it.faceId }.toSet())
            republish()
            services.folders.refresh()
            Toaster.success("Moved the photos back")
        }
    }

    fun reject() {
        val picks = chosen()
        if (picks.isEmpty()) return
        viewModelScope.launch {
            val undo = repository.reject(personId, picks)
            decided.addAll(picks.map { it.faceId })
            selectedState.value = emptySet()
            republish()
            Toaster.show(
                message = if (picks.size == 1) "Marked 1 photo as not this person" else "Marked ${picks.size} photos as not this person",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = { restorePicks(picks, undo) }
            )
        }
    }
}
