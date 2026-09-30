package com.example.livora.ui.people

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.people.FoldersState
import com.example.livora.data.people.PeopleServices
import com.example.livora.data.people.EnrollDraft
import com.example.livora.data.people.db.AiMoveKind
import com.example.livora.data.people.db.AiMoveRow
import com.example.livora.data.people.media.FolderInfo
import com.example.livora.data.people.media.MediaFolders
import com.example.livora.data.people.media.MediaImage
import com.example.livora.data.people.media.MediaImages
import com.example.livora.data.people.media.MediaWriter
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConsentRequest(val sender: IntentSender, val onResult: (Boolean) -> Unit)

object ConsentBroker {
    val request = MutableStateFlow<ConsentRequest?>(null)

    fun ask(sender: IntentSender?, scope: CoroutineScope, onGranted: suspend () -> Unit) {
        if (sender == null) {
            Toaster.error("This needs Android 11 or newer")
            return
        }
        request.value = ConsentRequest(sender) { ok ->
            request.value = null
            if (ok) scope.launch { onGranted() } else Toaster.info("Nothing was changed")
        }
    }
}

class FolderDetailViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val services = PeopleServices.get(application)
    private val folders = services.folders
    private val repository = services.repository

    val key: String = Uri.decode(handle.get<String>(PeopleRoutes.ARG_KEY) ?: ALL_PHOTOS_KEY)
    val pickMode: Boolean = handle.get<Boolean>(PeopleRoutes.ARG_PICK) ?: false

    private val folderState = MutableStateFlow<FolderInfo?>(null)
    val folder: StateFlow<FolderInfo?> = folderState.asStateFlow()

    private val loadedState = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = loadedState.asStateFlow()

    private val selectedState = MutableStateFlow<Set<Long>>(emptySet())
    val selected: StateFlow<Set<Long>> = selectedState.asStateFlow()

    private val consentState = ConsentBroker.request
    val consent: StateFlow<ConsentRequest?> = consentState.asStateFlow()

    private val leftState = MutableStateFlow(false)
    val left: StateFlow<Boolean> = leftState.asStateFlow()

    val images: StateFlow<List<MediaImage>> = folders.images
        .map { imagesOfFolder(key, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, imagesOfFolder(key, folders.images.value))

    val aiMoves: StateFlow<Map<Long, AiMoveRow>> = repository.aiMoves
        .map { list -> list.associateBy { it.mediaId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val aiLabels: StateFlow<Map<Long, String>> = aiMoves
        .map { map -> map.mapValues { it.value.personName.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val aiCount: StateFlow<Int> = combine(images, aiMoves) { list, ai -> list.count { it.id in ai } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val reviewState = MutableStateFlow(false)
    val reviewing: StateFlow<Boolean> = reviewState.asStateFlow()

    val rows: StateFlow<List<GalleryRow>?> = combine(
        folders.state.map { it is FoldersState.Loading }.distinctUntilChanged(),
        images,
        aiMoves,
        reviewState
    ) { loading, list, ai, review ->
        if (loading) null else GalleryGrouping.group(getApplication(), if (review) list.filter { it.id in ai } else list)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        reload()
        viewModelScope.launch {
            aiCount.collect { if (it == 0 && reviewState.value) reviewState.value = false }
        }
    }

    fun reload() {
        viewModelScope.launch {
            folders.refresh()
            folderState.value = if (key == ALL_PHOTOS_KEY) null else folders.folderByKey(key)
            loadedState.value = true
            if (key != ALL_PHOTOS_KEY && folderState.value == null) leftState.value = true
        }
    }

    val isAll: Boolean get() = key == ALL_PHOTOS_KEY

    val supportsConsent: Boolean get() = MediaWriter.supportsConsentRequests

    fun toggle(id: Long) {
        val current = selectedState.value
        selectedState.value = if (id in current) current - id else current + id
    }

    fun clearSelection() {
        selectedState.value = emptySet()
    }

    fun selectAll() {
        val ai = aiMoves.value
        val list = if (reviewState.value) images.value.filter { it.id in ai } else images.value
        selectedState.value = list.map { it.id }.toSet()
    }

    fun startReview() {
        selectedState.value = emptySet()
        reviewState.value = true
    }

    fun stopReview() {
        selectedState.value = emptySet()
        reviewState.value = false
    }

    fun looksRight() {
        val ids = selectedState.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.dropAiMoves(ids)
            selectedState.value = emptySet()
            Toaster.success(if (ids.size == 1) "Marked 1 photo as right" else "Marked ${ids.size} photos as right")
        }
    }

    fun markWrong() {
        val ai = aiMoves.value
        val rows = selectedState.value.mapNotNull { ai[it] }
        if (rows.isEmpty()) return
        val moves = rows.filter { it.kind == AiMoveKind.MOVE }
        val finish: suspend () -> Unit = {
            if (moves.isNotEmpty()) {
                for ((path, group) in moves.groupBy { it.fromPath.ifBlank { "Pictures/" } }) {
                    MediaWriter.applyMove(getApplication(), group.map { it.mediaId }, path)
                }
                repository.syncPhotoDates(moves.map { it.mediaId })
            }
            repository.rejectAiMoves(rows)
            selectedState.value = emptySet()
            folders.refresh()
            val who = rows.mapNotNull { it.personName }.distinct().firstOrNull()
            Toaster.success(if (who == null) "Thanks, Livora will remember this" else "Thanks, Livora will be stricter about $who")
        }
        if (moves.isEmpty()) {
            viewModelScope.launch { finish() }
        } else {
            requestConsent(MediaWriter.writeRequest(getApplication(), moves.map { it.mediaId }), finish)
        }
    }

    fun toggleGroup(ids: List<Long>) {
        val current = selectedState.value
        selectedState.value = if (current.containsAll(ids)) current - ids.toSet() else current + ids
    }

    fun copySelected(target: FolderInfo) {
        val ids = selectedState.value.toList()
        services.copyRunner.start(null, ids, target.relativePath, target.name) { reload() }
        selectedState.value = emptySet()
    }

    fun createFolderAnd(name: String, action: (FolderInfo) -> Unit) {
        viewModelScope.launch {
            val created = folders.createFolder(name)
            if (created == null) Toaster.error("Enter a folder name") else action(created)
        }
    }

    fun requestConsent(sender: IntentSender?, onGranted: suspend () -> Unit) {
        ConsentBroker.ask(sender, viewModelScope, onGranted)
    }

    fun moveSelected(target: FolderInfo) {
        val ids = selectedState.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val previous = MediaImages.queryByIds(getApplication(), ids).associate { it.id to it.relativePath }
            requestConsent(MediaWriter.writeRequest(getApplication(), ids)) {
                val moved = MediaWriter.applyMove(getApplication(), ids, target.relativePath)
                repository.syncPhotoDates(ids)
                folders.refresh()
                selectedState.value = emptySet()
                if (moved == 0) {
                    Toaster.error("No photos could be moved")
                } else {
                    Toaster.show(
                        message = if (moved == 1) "Moved 1 photo to ${target.name}" else "Moved $moved photos to ${target.name}",
                        type = ToastType.Success,
                        durationMs = 8000,
                        actionLabel = "Undo",
                        onAction = { undoMove(ids, previous) }
                    )
                }
                reload()
            }
        }
    }

    private fun undoMove(ids: List<Long>, previous: Map<Long, String>) {
        requestConsent(MediaWriter.writeRequest(getApplication(), ids)) {
            val byPath = ids.groupBy { previous[it] ?: "Pictures/" }
            for ((path, group) in byPath) MediaWriter.applyMove(getApplication(), group, path)
            repository.syncPhotoDates(ids)
            folders.refresh()
            Toaster.success("Moved the photos back")
            reload()
        }
    }

    fun trashSelected() {
        val ids = selectedState.value.toList()
        if (ids.isEmpty()) return
        trash(ids, "Moved ${ids.size} ${if (ids.size == 1) "photo" else "photos"} to the trash")
    }

    private fun trash(ids: List<Long>, message: String) {
        requestConsent(MediaWriter.trashRequest(getApplication(), ids, true)) {
            selectedState.value = emptySet()
            folders.refresh()
            Toaster.show(
                message = message,
                type = ToastType.Success,
                durationMs = 8000,
                actionLabel = "Undo",
                onAction = { untrash(ids) }
            )
            reload()
        }
    }

    private fun untrash(ids: List<Long>) {
        requestConsent(MediaWriter.trashRequest(getApplication(), ids, false)) {
            folders.refresh()
            Toaster.success("Brought the photos back")
            reload()
        }
    }

    fun deleteFolder() {
        val current = folderState.value ?: return
        viewModelScope.launch {
            if (current.virtual) {
                folders.removeVirtual(current.relativePath)
                Toaster.show(
                    message = "Removed the empty folder ${current.name}",
                    type = ToastType.Success,
                    durationMs = 7000,
                    actionLabel = "Undo",
                    onAction = { viewModelScope.launch { folders.restoreVirtual(current.name, current.relativePath) } }
                )
                leftState.value = true
            } else {
                val ids = folders.idsInFolder(current.bucketId)
                trash(ids, "Removed the folder ${current.name} to the trash")
            }
        }
    }

    fun renameFolder(rawName: String) {
        val current = folderState.value ?: return
        val name = MediaFolders.sanitizeName(rawName)
        if (name.isBlank() || name == current.name) return
        viewModelScope.launch {
            val parent = current.relativePath.trimEnd('/').substringBeforeLast('/', "Pictures")
            val newPath = "$parent/$name/"
            if (current.virtual) {
                folders.removeVirtual(current.relativePath)
                folders.createFolder(name)
                Toaster.success("Renamed to $name")
                leftState.value = true
                return@launch
            }
            if (!MediaFolders.isWritableTarget(newPath)) {
                Toaster.error("This folder cannot be renamed here")
                return@launch
            }
            val ids = folders.idsInFolder(current.bucketId)
            requestConsent(MediaWriter.writeRequest(getApplication(), ids)) {
                val moved = MediaWriter.applyMove(getApplication(), ids, newPath)
                repository.syncPhotoDates(ids)
                folders.refresh()
                Toaster.show(
                    message = "Renamed to $name, $moved photos moved",
                    type = ToastType.Success,
                    durationMs = 8000,
                    actionLabel = "Undo",
                    onAction = {
                        requestConsent(MediaWriter.writeRequest(getApplication(), ids)) {
                            MediaWriter.applyMove(getApplication(), ids, current.relativePath)
                            repository.syncPhotoDates(ids)
                            folders.refresh()
                            Toaster.success("Renamed back to ${current.name}")
                        }
                    }
                )
                leftState.value = true
            }
        }
    }

    fun addPicked(uris: List<Uri>) {
        val current = folderState.value ?: return
        if (uris.isEmpty()) return
        viewModelScope.launch {
            Toaster.show("Adding photos to ${current.name}", ToastType.Info, durationMs = 2500)
            val created = MediaWriter.copyFromUris(getApplication(), uris, current.relativePath)
            repository.registerCopies(created)
            folders.refresh()
            if (created.isEmpty()) {
                Toaster.error("No photos could be added")
            } else {
                Toaster.show(
                    message = "Added ${created.size} ${if (created.size == 1) "photo" else "photos"} to ${current.name}",
                    type = ToastType.Success,
                    durationMs = 7000,
                    actionLabel = "Undo",
                    onAction = {
                        viewModelScope.launch {
                            MediaWriter.deleteOwned(getApplication(), created)
                            folders.refresh()
                            reload()
                        }
                    }
                )
            }
            reload()
        }
    }

    fun useAsReferences(): Boolean {
        val ids = selectedState.value.toList()
        if (ids.isEmpty()) return false
        val bucket = if (key.startsWith("b:")) key.removePrefix("b:").toLongOrNull() else null
        EnrollDraft.set(
            ids.map { MediaImages.uri(it) },
            buckets = if (bucket == null) emptySet() else setOf(bucket),
            folderName = folderState.value?.name
        )
        selectedState.value = emptySet()
        return true
    }

    fun folderList(): List<FolderInfo> = (folders.state.value as? FoldersState.Ready)?.folders.orEmpty()
}
