package com.example.livora.ui.docs

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.livora.data.db.AppDatabase
import com.example.livora.data.docs.DocumentEntity
import com.example.livora.data.docs.DocumentNames
import com.example.livora.data.docs.DocumentPageEntity
import com.example.livora.data.docs.DocumentRepository
import com.example.livora.ui.components.Toaster
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DocumentItem(val document: DocumentEntity, val cover: File?)

class PageItem(val page: DocumentPageEntity, val file: File)

class DocumentsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application, AppDatabase.get(application))

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val documents: StateFlow<List<DocumentItem>?> = combine(repository.documents(), repository.covers()) { docs, covers ->
        val byDocument = covers.associate { it.documentId to it.fileName }
        docs.map { doc ->
            DocumentItem(doc, byDocument[doc.id]?.let { repository.pageFile(doc.folder, it) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(uris: List<Uri>, prefix: String, onCreated: (Long) -> Unit) {
        if (uris.isEmpty() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val name = DocumentNames.default(prefix, LocalDateTime.now())
            val id = repository.create(uris, name)
            _busy.value = false
            if (id == null) Toaster.error("Those pages could not be read") else onCreated(id)
        }
    }
}

class DocumentDetailViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application, AppDatabase.get(application))
    val id: Long = handle.get<Long>("id") ?: 0L

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    val document: StateFlow<DocumentEntity?> = repository.document(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pages: StateFlow<List<PageItem>?> = combine(repository.document(id), repository.pages(id)) { doc, pages ->
        if (doc == null) emptyList() else pages.map { PageItem(it, repository.pageFile(doc.folder, it.fileName)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addPages(uris: List<Uri>) {
        if (uris.isEmpty() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val added = repository.addPages(id, uris)
            _busy.value = false
            if (added == 0) Toaster.error("Those pages could not be read") else Toaster.success(if (added == 1) "Added 1 page" else "Added $added pages")
        }
    }

    fun rename(name: String) {
        val current = document.value ?: return
        viewModelScope.launch { repository.rename(id, DocumentNames.clean(name, current.name)) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }

    fun deletePage(pageId: Long) {
        viewModelScope.launch { repository.deletePage(id, pageId) }
    }

    fun movePage(pageId: Long, delta: Int) {
        viewModelScope.launch { repository.movePage(id, pageId, delta) }
    }

    fun rotatePage(pageId: Long) {
        viewModelScope.launch { repository.rotatePage(id, pageId) }
    }

    fun sharePdf(onReady: (File) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val file = repository.shareFile(id)
            _busy.value = false
            if (file == null) Toaster.error("Could not make the PDF") else onReady(file)
        }
    }

    fun savePdf(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = try {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { repository.writePdf(id, it) } ?: false
            } catch (e: Exception) {
                false
            }
            _busy.value = false
            if (ok) Toaster.success("PDF saved") else Toaster.error("Could not save the PDF")
        }
    }
}
