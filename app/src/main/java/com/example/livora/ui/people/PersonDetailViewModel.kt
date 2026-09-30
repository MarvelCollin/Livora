package com.example.livora.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.db.PersonEntity
import com.example.livora.data.people.db.PersonPhotoRow
import com.example.livora.data.people.db.PersonSummary
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PersonDetailViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val repository = services.repository

    val personId: Long = handle.get<Long>(PeopleRoutes.ARG_ID) ?: 0L

    val person: StateFlow<PersonEntity?> = repository.observePerson(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val photoCount: StateFlow<Int> = repository.observePhotoCount(personId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val photos: Flow<PagingData<PersonPhotoRow>> = repository.personPhotos(personId).cachedIn(viewModelScope)

    val others: StateFlow<List<PersonSummary>> = repository.summaries
        .map { all -> all.filter { it.id != personId && !it.hidden && (it.photoCount > 0 || it.name != null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedState = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = selectedState.asStateFlow()

    private val summaryState = MutableStateFlow<PersonSummary?>(null)
    val summary: StateFlow<PersonSummary?> = summaryState.asStateFlow()

    private val matchCountState = MutableStateFlow<Int?>(null)
    val matchCount: StateFlow<Int?> = matchCountState.asStateFlow()

    private val leftState = MutableStateFlow(false)
    val left: StateFlow<Boolean> = leftState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            summaryState.value = repository.summary(personId)
            val set = repository.suggestions(personId)
            matchCountState.value = set.atThreshold(set.effectiveThreshold).size
        }
    }

    fun toggle(mediaId: Long) {
        val current = selectedState.value
        selectedState.value = if (mediaId in current) current - mediaId else current + mediaId
    }

    fun clearSelection() {
        selectedState.value = emptySet()
    }

    fun selectAll(ids: List<Long>) {
        selectedState.value = ids.toSet()
    }

    fun rename(name: String) {
        viewModelScope.launch {
            repository.rename(personId, name)
            refresh()
            Toaster.success("Saved the name")
        }
    }

    fun setHidden(hidden: Boolean) {
        viewModelScope.launch {
            repository.setHidden(personId, hidden)
            Toaster.show(
                message = if (hidden) "Hid this person" else "This person is visible again",
                type = ToastType.Success,
                durationMs = 7000,
                actionLabel = "Undo",
                onAction = { viewModelScope.launch { repository.setHidden(personId, !hidden) } }
            )
            if (hidden) leftState.value = true
        }
    }

    fun notThisPerson() {
        val ids = selectedState.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val undo = repository.removeFromPerson(personId, ids)
            selectedState.value = emptySet()
            if (undo == null) {
                Toaster.error("Those photos could not be changed")
            } else {
                Toaster.show(
                    message = if (ids.size == 1) "Removed 1 photo from this person" else "Removed ${ids.size} photos from this person",
                    type = ToastType.Success,
                    durationMs = 7000,
                    actionLabel = "Undo",
                    onAction = { viewModelScope.launch { undo.restore(); refresh() } }
                )
                refresh()
            }
        }
    }

    fun mergeInto(targetId: Long, targetName: String?) {
        viewModelScope.launch {
            val result = repository.absorb(targetId, personId)
            if (result == null) {
                Toaster.error("These people could not be merged")
            } else {
                Toaster.show(
                    message = "Merged into ${targetName ?: "the other person"}",
                    type = ToastType.Success,
                    durationMs = 7000,
                    actionLabel = "Undo",
                    onAction = { viewModelScope.launch { result.undo.restore() } }
                )
                leftState.value = true
            }
        }
    }

    fun copyToFolder(mediaIds: List<Long>, relativePath: String, name: String) {
        services.copyRunner.start(personId, mediaIds, relativePath, name)
        selectedState.value = emptySet()
    }

    fun copyAllToFolder(relativePath: String, name: String) {
        viewModelScope.launch {
            val ids = repository.mediaIdsOfPerson(personId)
            services.copyRunner.start(personId, ids, relativePath, name)
        }
    }

    fun deletePerson() {
        viewModelScope.launch {
            repository.deletePerson(personId)
            Toaster.success("Deleted this person. Your photos are untouched.")
            leftState.value = true
        }
    }

    suspend fun createFolder(name: String) = services.folders.createFolder(name)
}
