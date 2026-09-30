package com.example.livora.ui.qr

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.db.AppDatabase
import com.example.livora.data.people.media.PhotoDecoder
import com.example.livora.data.qr.QrCodec
import com.example.livora.data.qr.QrHistoryEntity
import com.example.livora.data.qr.QrParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).qr()

    val history: StateFlow<List<QrHistoryEntity>?> = dao.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun record(raw: String, fromPhoto: Boolean, onSaved: (Long) -> Unit) {
        val value = raw.trim()
        if (value.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = dao.find(value)
            val id = if (existing != null) {
                dao.update(existing.copy(scannedAt = now, fromPhoto = fromPhoto))
                existing.id
            } else {
                dao.insert(QrHistoryEntity(value = value, kind = QrParser.parse(value).kind.name, scannedAt = now, fromPhoto = fromPhoto))
            }
            dao.trim()
            onSaved(id)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { dao.delete(id) }
    }

    fun clear() {
        viewModelScope.launch { dao.clear() }
    }

    fun readPhoto(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.Default) {
                val context = getApplication<Application>()
                val bitmap = PhotoDecoder.decode(context, uri, PhotoDecoder.exifRotation(context, uri), 0, 0, 2000)
                bitmap?.let { decode(it) }
            }
            onResult(text)
        }
    }

    private fun decode(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return QrCodec.decode(bitmap.width, bitmap.height, pixels)
    }
}
