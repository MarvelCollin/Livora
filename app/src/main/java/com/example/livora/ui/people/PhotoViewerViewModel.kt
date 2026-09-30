package com.example.livora.ui.people

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.media.FolderInfo
import com.example.livora.data.people.media.MediaImage
import com.example.livora.data.people.media.MediaWriter
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PhotoViewerViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val folders = services.folders
    private val repository = services.repository

    val key: String = Uri.decode(handle.get<String>(PeopleRoutes.ARG_KEY) ?: ALL_PHOTOS_KEY)
    val startId: Long = handle.get<Long>(PeopleRoutes.ARG_ID) ?: -1L

    private val consentState = ConsentBroker.request
    val consent: StateFlow<ConsentRequest?> = consentState.asStateFlow()

    val images: StateFlow<List<MediaImage>> = folders.images
        .map { imagesOfFolder(key, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, imagesOfFolder(key, folders.images.value))

    val supportsConsent: Boolean get() = MediaWriter.supportsConsentRequests

    init {
        if (folders.images.value.isEmpty()) viewModelScope.launch { folders.refresh() }
    }

    fun folderList(): List<FolderInfo> = (folders.state.value as? FoldersState.Ready)?.folders.orEmpty()

    fun createFolderAnd(name: String, action: (FolderInfo) -> Unit) {
        viewModelScope.launch {
            val created = folders.createFolder(name)
            if (created == null) Toaster.error("Enter a folder name") else action(created)
        }
    }

    private fun requestConsent(sender: IntentSender?, onGranted: suspend () -> Unit) {
        if (sender == null) {
            Toaster.error("This needs Android 11 or newer")
            return
        }
        consentState.value = ConsentRequest(sender) { ok ->
            consentState.value = null
            if (ok) viewModelScope.launch { onGranted() } else Toaster.info("Nothing was changed")
        }
    }

    fun delete(image: MediaImage) {
        val id = image.id
        requestConsent(MediaWriter.trashRequest(getApplication(), listOf(id), true)) {
            folders.refresh()
            Toaster.show(
                message = "Moved the photo to the trash",
                type = ToastType.Success,
                durationMs = 8000,
                actionLabel = "Undo",
                onAction = { restore(id) }
            )
        }
    }

    private fun restore(id: Long) {
        requestConsent(MediaWriter.trashRequest(getApplication(), listOf(id), false)) {
            folders.refresh()
            Toaster.success("Brought the photo back")
        }
    }

    fun move(image: MediaImage, target: FolderInfo) {
        val id = image.id
        val previous = image.relativePath.ifBlank { "Pictures/" }
        requestConsent(MediaWriter.writeRequest(getApplication(), listOf(id))) {
            val moved = MediaWriter.applyMove(getApplication(), listOf(id), target.relativePath)
            repository.syncPhotoDates(listOf(id))
            folders.refresh()
            if (moved == 0) {
                Toaster.error("The photo could not be moved")
            } else {
                Toaster.show(
                    message = "Moved to ${target.name}",
                    type = ToastType.Success,
                    durationMs = 8000,
                    actionLabel = "Undo",
                    onAction = { undoMove(id, previous) }
                )
            }
        }
    }

    private fun undoMove(id: Long, previous: String) {
        requestConsent(MediaWriter.writeRequest(getApplication(), listOf(id))) {
            MediaWriter.applyMove(getApplication(), listOf(id), previous)
            repository.syncPhotoDates(listOf(id))
            folders.refresh()
            Toaster.success("Moved the photo back")
        }
    }
}
