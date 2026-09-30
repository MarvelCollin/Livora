package com.example.livora.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.Suggestion
import com.example.livora.data.people.SuggestionSet
import com.example.livora.data.people.db.LinkMode
import com.example.livora.data.people.db.PersonEntity
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val personId: Long = handle.get<Long>(PeopleRoutes.ARG_ID) ?: 0L

    private val listState = MutableStateFlow<SuggestionsState>(SuggestionsState.Loading)
    val state: StateFlow<SuggestionsState> = listState.asStateFlow()

    private val selectedState = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = selectedState.asStateFlow()

    private var decided = HashSet<Long>()

    init {
        load()
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

    fun setThreshold(value: Float) {
        val current = listState.value as? SuggestionsState.Ready ?: return
        publish(current.person, current.all, value)
    }

    fun saveThreshold() {
        val current = listState.value as? SuggestionsState.Ready ?: return
        viewModelScope.launch { repository.setThreshold(personId, current.threshold) }
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

    fun approve() {
        val current = listState.value as? SuggestionsState.Ready ?: return
        val picks = chosen()
        if (picks.isEmpty()) return
        viewModelScope.launch {
            val undo = repository.confirm(personId, picks)
            decided.addAll(picks.map { it.faceId })
            selectedState.value = emptySet()
            publish(current.person, current.all, current.threshold)
            val person = current.person
            val path = person.linkedFolderPath
            val copies = if (path != null && person.linkMode != LinkMode.NONE) picks.map { it.mediaId }.distinct() else emptyList()
            if (copies.isNotEmpty() && path != null) {
                services.copyRunner.start(personId, copies, path, person.linkedFolderName ?: "the folder")
            }
            Toaster.show(
                message = if (picks.size == 1) "Added 1 photo to this person" else "Added ${picks.size} photos to this person",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = {
                    viewModelScope.launch {
                        undo?.restore()
                        decided.removeAll(picks.map { it.faceId }.toSet())
                        (listState.value as? SuggestionsState.Ready)?.let { publish(it.person, it.all, it.threshold) }
                    }
                }
            )
        }
    }

    fun reject() {
        val current = listState.value as? SuggestionsState.Ready ?: return
        val picks = chosen()
        if (picks.isEmpty()) return
        viewModelScope.launch {
            val undo = repository.reject(personId, picks)
            decided.addAll(picks.map { it.faceId })
            selectedState.value = emptySet()
            publish(current.person, current.all, current.threshold)
            Toaster.show(
                message = if (picks.size == 1) "Marked 1 photo as not this person" else "Marked ${picks.size} photos as not this person",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = {
                    viewModelScope.launch {
                        undo?.restore()
                        decided.removeAll(picks.map { it.faceId }.toSet())
                        (listState.value as? SuggestionsState.Ready)?.let { publish(it.person, it.all, it.threshold) }
                    }
                }
            )
        }
    }
}
