package com.example.livora.ui.people

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.EnrollDraft
import com.example.livora.data.people.EnrollFace
import com.example.livora.data.people.EnrollPhoto
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.ReferenceInput
import com.example.livora.data.people.db.PersonSummary
import com.example.livora.data.people.scan.ScanController
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EnrollItem(
    val uri: Uri,
    val analyzed: EnrollPhoto?,
    val selectedFace: Int?
) {
    val pending: Boolean get() = analyzed == null
}

class EnrollViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val repository = services.repository

    val personId: Long? = handle.get<Long>(PeopleRoutes.ARG_PERSON)?.takeIf { it > 0L }

    private val scope = EnrollDraft.takeScope()

    private val itemsState = MutableStateFlow<List<EnrollItem>>(emptyList())
    val items: StateFlow<List<EnrollItem>> = itemsState.asStateFlow()

    private val nameState = MutableStateFlow("")
    val name: StateFlow<String> = nameState.asStateFlow()

    private val existingName = MutableStateFlow<String?>(null)
    val personName: StateFlow<String?> = existingName.asStateFlow()

    val people: StateFlow<List<PersonSummary>> = repository.summaries
        .map { all -> all.filter { it.name != null && !it.hidden }.sortedByDescending { it.photoCount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val chosenState = MutableStateFlow<PersonSummary?>(null)
    val chosen: StateFlow<PersonSummary?> = chosenState.asStateFlow()

    private val savingState = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = savingState.asStateFlow()

    private val doneState = MutableStateFlow<Long?>(null)
    val done: StateFlow<Long?> = doneState.asStateFlow()

    init {
        if (personId != null) {
            viewModelScope.launch { existingName.value = repository.person(personId)?.name }
        }
        val draft = EnrollDraft.take()
        if (draft.isNotEmpty()) addUris(draft)
    }

    fun choose(person: PersonSummary?) {
        chosenState.value = if (chosenState.value?.id == person?.id) null else person
    }

    fun setName(value: String) {
        nameState.value = value.take(60)
    }

    fun addUris(uris: List<Uri>) {
        val known = itemsState.value.map { it.uri }.toSet()
        val fresh = uris.filter { it !in known }.take(MAX_PHOTOS - itemsState.value.size)
        if (fresh.isEmpty()) return
        itemsState.value = itemsState.value + fresh.map { EnrollItem(it, null, null) }
        viewModelScope.launch {
            for (uri in fresh) {
                val analyzed = try {
                    repository.analyzeForEnrollment(uri)
                } catch (e: Exception) {
                    EnrollPhoto(uri, emptyList(), true)
                }
                val auto = if (analyzed.faces.size == 1 && analyzed.faces[0].usable) 0 else null
                itemsState.value = itemsState.value.map { if (it.uri == uri) EnrollItem(uri, analyzed, auto) else it }
            }
        }
    }

    fun select(uri: Uri, faceIndex: Int) {
        itemsState.value = itemsState.value.map {
            if (it.uri == uri) EnrollItem(uri, it.analyzed, if (it.selectedFace == faceIndex) null else faceIndex) else it
        }
    }

    fun remove(uri: Uri) {
        itemsState.value = itemsState.value.filter { it.uri != uri }
    }

    val selectedCount: Int get() = itemsState.value.count { it.selectedFace != null }

    fun save() {
        if (savingState.value) return
        val chosen = itemsState.value.mapNotNull { item ->
            val face: EnrollFace? = item.selectedFace?.let { index -> item.analyzed?.faces?.getOrNull(index) }
            face
        }
        if (chosen.isEmpty()) return
        val references = chosen.map { ReferenceInput(it.embedding, it.quality, it.crop) }
        viewModelScope.launch {
            savingState.value = true
            try {
                val trimmed = nameState.value.trim()
                val target = chosenState.value
                    ?: people.value.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
                val id: Long
                val label: String
                if (personId != null) {
                    repository.addReferences(personId, references)
                    id = personId
                    label = existingName.value ?: "this person"
                } else if (target != null) {
                    repository.addReferences(target.id, references)
                    id = target.id
                    label = target.name.orEmpty()
                } else {
                    if (trimmed.isEmpty()) {
                        Toaster.error("Enter a name first")
                        savingState.value = false
                        return@launch
                    }
                    id = repository.createEnrolled(trimmed, references)
                    label = trimmed
                }
                val photos = if (references.size == 1) "photo" else "photos"
                Toaster.success(
                    if (personId != null || target != null) "Added ${references.size} reference $photos to $label"
                    else "Saved $label with ${references.size} reference $photos"
                )
                if (scope.first.isNotEmpty()) {
                    ScanController.startFolders(getApplication(), scope.first)
                    Toaster.info("Checking ${scope.second ?: "the folder"} for this person")
                }
                doneState.value = id
            } catch (e: Exception) {
                Toaster.error("This could not be saved")
            } finally {
                savingState.value = false
            }
        }
    }

    override fun onCleared() {
        for (item in itemsState.value) item.analyzed?.faces?.forEach { it.crop.recycle() }
        super.onCleared()
    }

    companion object {
        const val MAX_PHOTOS = 24
    }
}
