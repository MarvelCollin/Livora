package com.example.livora.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.SuggestionSet
import com.example.livora.data.people.cluster.PersonMatcher
import com.example.livora.data.people.db.LinkMode
import com.example.livora.data.people.db.PersonEntity
import com.example.livora.data.people.db.ReferenceEntity
import com.example.livora.data.people.media.FolderInfo
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecognitionViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val repository = services.repository
    val personId: Long = handle.get<Long>(PeopleRoutes.ARG_ID) ?: 0L

    val person: StateFlow<PersonEntity?> = repository.observePerson(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val references: StateFlow<List<ReferenceEntity>> = repository.observeReferences(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val setState = MutableStateFlow<SuggestionSet?>(null)
    val suggestionSet: StateFlow<SuggestionSet?> = setState.asStateFlow()

    private val thresholdState = MutableStateFlow(PersonMatcher.DEFAULT_THRESHOLD)
    val threshold: StateFlow<Float> = thresholdState.asStateFlow()

    val folders: StateFlow<FoldersState> = services.folders.state

    init {
        load()
        viewModelScope.launch { services.folders.refresh() }
    }

    fun load() {
        viewModelScope.launch {
            val set = repository.suggestions(personId)
            setState.value = set
            thresholdState.value = set.effectiveThreshold
        }
    }

    fun setThreshold(value: Float) {
        thresholdState.value = PersonMatcher.clampThreshold(value)
    }

    fun saveThreshold() {
        viewModelScope.launch { repository.setThreshold(personId, thresholdState.value) }
    }

    fun matchCount(): Int? = setState.value?.atThreshold(thresholdState.value)?.size

    fun link(folder: FolderInfo) {
        viewModelScope.launch {
            repository.linkFolder(personId, folder.relativePath, folder.name, LinkMode.REVIEW)
            load()
            Toaster.success("Linked to ${folder.name}. Matches are reviewed before they are copied.")
        }
    }

    fun createAndLink(name: String) {
        viewModelScope.launch {
            val folder = services.folders.createFolder(name)
            if (folder == null) {
                Toaster.error("Enter a folder name")
            } else {
                repository.linkFolder(personId, folder.relativePath, folder.name, LinkMode.REVIEW)
                load()
                Toaster.success("Created and linked ${folder.name}")
            }
        }
    }

    fun unlink() {
        val previous = person.value
        viewModelScope.launch {
            repository.unlinkFolder(personId)
            load()
            if (previous?.linkedFolderPath != null) {
                Toaster.show(
                    message = "Unlinked ${previous.linkedFolderName ?: "the folder"}",
                    type = com.example.livora.ui.components.ToastType.Success,
                    durationMs = 7000,
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch {
                            repository.linkFolder(personId, previous.linkedFolderPath, previous.linkedFolderName ?: "", previous.linkMode)
                            load()
                        }
                    }
                )
            }
        }
    }

    fun setMode(mode: Int) {
        viewModelScope.launch { repository.setLinkMode(personId, mode) }
    }
}
