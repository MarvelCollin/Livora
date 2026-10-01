package com.example.livora.data.people.cloud

import android.content.Context
import com.example.livora.BuildConfig
import com.example.livora.data.people.PeoplePrefs
import com.example.livora.data.people.backup.ImportSummary
import com.example.livora.data.people.backup.PeopleBackup
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.scan.GalleryScanner
import com.example.livora.data.people.scan.ScanController
import com.example.livora.data.supabase.SupabaseClient
import com.example.livora.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

sealed interface CloudState {
    data object NotConfigured : CloudState
    data object NotReady : CloudState
    data object Idle : CloudState
    data object Saving : CloudState
    data object Restoring : CloudState
    data class Failed(val message: String) : CloudState
}

class RemoteBackup(val generation: Long, val persons: Int, val photos: Int, val faces: Int, val parts: Int)

sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data object NothingToSave : SaveOutcome
    data object NeedsDecision : SaveOutcome
    data object Busy : SaveOutcome
    data object NotConfigured : SaveOutcome
    data class Failed(val message: String) : SaveOutcome
}

sealed interface RestoreOutcome {
    data class Restored(val summary: ImportSummary) : RestoreOutcome
    data object NoBackup : RestoreOutcome
    data object Busy : RestoreOutcome
    data object NotConfigured : RestoreOutcome
    data class Failed(val message: String) : RestoreOutcome
}

@OptIn(FlowPreview::class)
class PeopleCloud(
    private val context: Context,
    private val database: PeopleDatabase,
    private val prefs: PeoplePrefs,
    private val backup: PeopleBackup,
    private val scanner: GalleryScanner,
    private val scope: CoroutineScope,
    private val secret: String = BuildConfig.PEOPLE_BACKUP_KEY,
    private val enabled: Boolean = SupabaseClient.isConfigured
) {

    private val api: PeopleCloudApi by lazy { SupabaseClient.bulkRetrofit.create(PeopleCloudApi::class.java) }
    private val work = Mutex()
    private val dirtySignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val configured: Boolean get() = enabled && secret.isNotBlank()

    private val stateFlow = MutableStateFlow<CloudState>(if (configured) CloudState.Idle else CloudState.NotConfigured)
    val state: StateFlow<CloudState> = stateFlow.asStateFlow()

    private val remoteFlow = MutableStateFlow<RemoteBackup?>(null)
    val remote: StateFlow<RemoteBackup?> = remoteFlow.asStateFlow()

    private val linkedFlow = MutableStateFlow(prefs.cloudLinked)
    val linked: StateFlow<Boolean> = linkedFlow.asStateFlow()

    private val savedAtFlow = MutableStateFlow(prefs.cloudSavedAt)
    val savedAt: StateFlow<Long> = savedAtFlow.asStateFlow()

    private var started = false

    fun start() {
        if (started || !configured) return
        started = true
        scope.launch(Dispatchers.IO) {
            refreshRemote()
            if (hasLocalData() && (!linkedFlow.value || prefs.cloudDirty)) dirtySignal.tryEmit(Unit)
        }
        scope.launch(Dispatchers.IO) {
            database.invalidationTracker.createFlow(*WATCHED_TABLES, emitInitialState = false).collect {
                prefs.cloudDirty = true
                dirtySignal.tryEmit(Unit)
            }
        }
        scope.launch(Dispatchers.IO) {
            dirtySignal.debounce(QUIET_MS).collect { autoSave() }
        }
    }

    suspend fun refreshRemote() {
        if (!configured) return
        try {
            val latest = fetchLatest()
            remoteFlow.value = latest
            if (stateFlow.value == CloudState.NotReady) stateFlow.value = CloudState.Idle
        } catch (e: Exception) {
            if (isMissingTable(e)) stateFlow.value = CloudState.NotReady
            Logger.debug(TAG, "refreshRemote failed: ${e.message}")
        }
    }

    fun startFresh() {
        prefs.cloudLinked = true
        linkedFlow.value = true
        scope.launch(Dispatchers.IO) {
            if (hasLocalData()) dirtySignal.tryEmit(Unit)
        }
    }

    suspend fun saveNow(replaceExisting: Boolean = false): SaveOutcome = work.withLock {
        if (!configured) return@withLock SaveOutcome.NotConfigured
        withContext(Dispatchers.IO) {
            if (!hasLocalData()) return@withContext SaveOutcome.NothingToSave
            stateFlow.value = CloudState.Saving
            try {
                if (!linkedFlow.value && !replaceExisting) {
                    val latest = fetchLatest()
                    remoteFlow.value = latest
                    if (latest != null) {
                        stateFlow.value = CloudState.Idle
                        return@withContext SaveOutcome.NeedsDecision
                    }
                }
                val exported = scanner.exclusive { backup.exportBytes() }
                if (exported == null) {
                    stateFlow.value = CloudState.Idle
                    return@withContext SaveOutcome.Busy
                }
                val (plain, summary) = exported
                val blob = BackupCrypto.encrypt(plain, secret)
                val chunks = BackupChunks.split(blob)
                val generation = System.currentTimeMillis()
                try {
                    for (index in chunks.indices.drop(1) + 0) {
                        api.insert(
                            listOf(
                                BackupRowDto(
                                    id = ROW_ID,
                                    generation = generation,
                                    part = index,
                                    parts = chunks.size,
                                    persons = summary.persons,
                                    photos = summary.photos,
                                    faces = summary.faces,
                                    data = chunks[index]
                                )
                            )
                        )
                    }
                } catch (e: Exception) {
                    runCatching { api.delete(ROW_FILTER, "eq.$generation") }
                    throw e
                }
                prune()
                prefs.cloudSavedAt = generation
                prefs.cloudDirty = false
                prefs.cloudLinked = true
                savedAtFlow.value = generation
                linkedFlow.value = true
                remoteFlow.value = RemoteBackup(generation, summary.persons, summary.photos, summary.faces, chunks.size)
                stateFlow.value = CloudState.Idle
                SaveOutcome.Saved
            } catch (e: Exception) {
                Logger.debug(TAG, "save failed: ${e.message}")
                if (isMissingTable(e)) {
                    stateFlow.value = CloudState.NotReady
                    SaveOutcome.Failed("The people_backup table is missing in Supabase.")
                } else {
                    val message = friendly(e, "save")
                    stateFlow.value = CloudState.Failed(message)
                    SaveOutcome.Failed(message)
                }
            }
        }
    }

    suspend fun restore(): RestoreOutcome = work.withLock {
        if (!configured) return@withLock RestoreOutcome.NotConfigured
        withContext(Dispatchers.IO) {
            stateFlow.value = CloudState.Restoring
            try {
                for (meta in api.metas(ROW_FILTER)) {
                    val rows = api.parts(ROW_FILTER, "eq.${meta.generation}")
                    val complete = rows.size == meta.parts && rows.map { it.part } == (0 until meta.parts).toList()
                    if (!complete) continue
                    val plain = BackupCrypto.decrypt(BackupChunks.join(rows.map { it.data }), secret)
                    val summary = scanner.exclusive { backup.importBytes(plain) }
                    if (summary == null) {
                        stateFlow.value = CloudState.Idle
                        return@withContext RestoreOutcome.Busy
                    }
                    prefs.cloudLinked = true
                    prefs.cloudDirty = false
                    prefs.cloudSavedAt = meta.generation
                    linkedFlow.value = true
                    savedAtFlow.value = meta.generation
                    remoteFlow.value = RemoteBackup(meta.generation, meta.persons, meta.photos, meta.faces, meta.parts)
                    stateFlow.value = CloudState.Idle
                    ScanController.start(context)
                    return@withContext RestoreOutcome.Restored(summary)
                }
                stateFlow.value = CloudState.Idle
                RestoreOutcome.NoBackup
            } catch (e: Exception) {
                Logger.debug(TAG, "restore failed: ${e.message}")
                if (isMissingTable(e)) {
                    stateFlow.value = CloudState.NotReady
                    RestoreOutcome.Failed("The people_backup table is missing in Supabase.")
                } else {
                    val message = friendly(e, "restore")
                    stateFlow.value = CloudState.Failed(message)
                    RestoreOutcome.Failed(message)
                }
            }
        }
    }

    private suspend fun autoSave() {
        when (saveNow()) {
            SaveOutcome.Busy -> retryLater(BUSY_RETRY_MS)
            is SaveOutcome.Failed -> if (stateFlow.value != CloudState.NotReady) retryLater(FAIL_RETRY_MS)
            else -> Unit
        }
    }

    private fun retryLater(delayMs: Long) {
        scope.launch {
            delay(delayMs)
            dirtySignal.tryEmit(Unit)
        }
    }

    private suspend fun fetchLatest(): RemoteBackup? =
        api.metas(ROW_FILTER).firstOrNull()?.let { RemoteBackup(it.generation, it.persons, it.photos, it.faces, it.parts) }

    private suspend fun prune() {
        val generations = runCatching { api.metas(ROW_FILTER).map { it.generation } }.getOrDefault(emptyList())
        if (generations.size > KEEP_GENERATIONS) {
            runCatching { api.delete(ROW_FILTER, "lt.${generations[KEEP_GENERATIONS - 1]}") }
        }
    }

    private suspend fun hasLocalData(): Boolean =
        database.photos().count() > 0 || database.persons().all().isNotEmpty()

    private fun isMissingTable(error: Throwable): Boolean {
        val http = error as? HttpException ?: return false
        if (http.code() == 404) return true
        val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        return body.contains("PGRST205") || body.contains("42P01")
    }

    private fun friendly(error: Throwable, verb: String): String = when {
        error is WrongBackupKeyException ->
            "This backup was saved with a different PEOPLE_BACKUP_KEY, so it cannot be opened."
        error is IOException || generateSequence(error) { it.cause }.last() is IOException ->
            "No connection. Check your internet and try again."
        else -> "Could not $verb the people backup. Try again in a moment."
    }

    companion object {
        const val ROW_ID = "default"
        private const val ROW_FILTER = "eq.$ROW_ID"
        private const val TAG = "Livora.PeopleCloud"
        private const val QUIET_MS = 30_000L
        private const val BUSY_RETRY_MS = 60_000L
        private const val FAIL_RETRY_MS = 300_000L
        private const val KEEP_GENERATIONS = 2
        private val WATCHED_TABLES = arrayOf(
            "photos", "faces", "persons", "person_references", "rejections",
            "linked_copies", "virtual_folders", "person_separations"
        )
    }
}
