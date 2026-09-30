package com.example.livora.data.people

import com.example.livora.data.people.media.CopyResult
import com.example.livora.data.people.media.MediaFolders
import com.example.livora.ui.components.ToastType
import com.example.livora.ui.components.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CopyProgress(val total: Int, val done: Int, val folder: String)

class CopyRunner(private val repository: PeopleRepository, private val folders: FoldersRepository) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = MutableStateFlow<CopyProgress?>(null)
    val progress: StateFlow<CopyProgress?> = state.asStateFlow()
    private var job: Job? = null

    val running: Boolean get() = state.value != null

    fun start(
        personId: Long?,
        mediaIds: List<Long>,
        relativePath: String,
        folderName: String,
        byAi: Boolean = false,
        onFinished: (CopyResult) -> Unit = {}
    ) {
        if (mediaIds.isEmpty()) return
        if (!MediaFolders.isWritableTarget(relativePath)) {
            scope.launch(Dispatchers.Main) { Toaster.error("Photos can only be copied into a Pictures or DCIM folder") }
            return
        }
        if (job?.isActive == true) {
            scope.launch(Dispatchers.Main) { Toaster.info("Another copy is still running") }
            return
        }
        job = scope.launch {
            state.value = CopyProgress(mediaIds.size, 0, folderName)
            val result = repository.copyPhotos(personId, mediaIds, relativePath, { done ->
                state.value = CopyProgress(mediaIds.size, done, folderName)
            }, byAi)
            state.value = null
            folders.refresh()
            withContext(Dispatchers.Main) {
                val copied = result.created.size
                when {
                    copied == 0 -> Toaster.error("No photos could be copied")
                    result.failed > 0 -> Toaster.show(
                        "Copied $copied photos to $folderName, ${result.failed} could not be copied",
                        ToastType.Info,
                        durationMs = 6000
                    )
                    else -> Toaster.show(
                        if (copied == 1) "Copied 1 photo to $folderName" else "Copied $copied photos to $folderName",
                        ToastType.Success,
                        durationMs = 7000,
                        actionLabel = "Undo",
                        onAction = { undo(personId, result) }
                    )
                }
                onFinished(result)
            }
        }
    }

    private fun undo(personId: Long?, result: CopyResult) {
        scope.launch {
            repository.undoCopies(personId, result)
            folders.refresh()
        }
    }
}
