package com.example.livora.ui.usage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.livora.data.db.AppDatabase
import com.example.livora.data.usage.UnusedApp
import com.example.livora.data.usage.UsageAccess
import com.example.livora.data.usage.UsageRepository
import com.example.livora.data.usage.UsageSnapshotWorker
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsageUiState(
    val permission: Boolean?,
    val loading: Boolean,
    val range: UsageRange,
    val anchor: LocalDate,
    val view: UsageView?,
    val unused: List<UnusedApp>?,
    val lastUsed: Map<String, Long>
)

class UsageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageRepository(application, AppDatabase.get(application))
    private val _state = MutableStateFlow(
        UsageUiState(
            permission = null,
            loading = true,
            range = UsageRange.Day,
            anchor = LocalDate.now(),
            view = null,
            unused = null,
            lastUsed = emptyMap()
        )
    )
    val state: StateFlow<UsageUiState> = _state

    private var refreshJob: Job? = null
    private var loadJob: Job? = null

    fun onResume() {
        val app = getApplication<Application>()
        if (!UsageAccess.isGranted(app)) {
            _state.update { it.copy(permission = false, loading = false, view = null, unused = null) }
            return
        }
        UsageSnapshotWorker.schedule(app)
        _state.update { it.copy(permission = true, loading = it.view == null) }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            withContext(Dispatchers.Default) { runCatching { repository.refreshRecent() } }
            load()
        }
        viewModelScope.launch {
            val used = withContext(Dispatchers.IO) { runCatching { repository.lastUsedTimes() }.getOrDefault(emptyMap()) }
            val unused = withContext(Dispatchers.IO) { runCatching { repository.unusedApps(used) }.getOrDefault(emptyList()) }
            _state.update { it.copy(unused = unused, lastUsed = used) }
        }
    }

    fun setRange(range: UsageRange) {
        _state.update { it.copy(range = range, anchor = LocalDate.now()) }
        load()
    }

    fun previous() = step(-1)

    fun next() = step(1)

    private fun step(direction: Int) {
        _state.update { it.copy(anchor = UsageViews.shift(it.range, it.anchor, direction)) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val snapshot = _state.value
            val zone = ZoneId.systemDefault()
            val view = withContext(Dispatchers.Default) {
                val (from, to) = UsageViews.rangeOf(snapshot.range, snapshot.anchor)
                val before = UsageViews.rangeOf(snapshot.range, UsageViews.shift(snapshot.range, snapshot.anchor, -1))
                UsageViews.build(
                    range = snapshot.range,
                    anchor = snapshot.anchor,
                    today = LocalDate.now(zone),
                    current = repository.load(from, to),
                    previous = repository.load(before.first, before.second),
                    firstRecorded = repository.firstRecordedDay(),
                    zone = zone
                )
            }
            _state.update { if (it.range == snapshot.range && it.anchor == snapshot.anchor) it.copy(view = view, loading = false) else it }
        }
    }
}
