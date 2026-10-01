package com.example.livora.data.people

import android.content.Context
import com.example.livora.data.people.cluster.ClusterParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PeoplePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("people_prefs", Context.MODE_PRIVATE)

    private val minPhotosState = MutableStateFlow(prefs.getInt(KEY_MIN_PHOTOS, DEFAULT_MIN_PHOTOS))
    val minPhotos: StateFlow<Int> = minPhotosState.asStateFlow()

    var initialScanDone: Boolean
        get() = prefs.getBoolean(KEY_INITIAL_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_INITIAL_DONE, value).apply()

    var scanTotal: Int
        get() = prefs.getInt(KEY_SCAN_TOTAL, 0)
        set(value) = prefs.edit().putInt(KEY_SCAN_TOTAL, value).apply()

    var lastScanFinishedAt: Long
        get() = prefs.getLong(KEY_LAST_FINISHED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_FINISHED, value).apply()

    var lastScanNewCount: Int
        get() = prefs.getInt(KEY_LAST_NEW, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_NEW, value).apply()

    var lastGeneration: Long
        get() = prefs.getLong(KEY_LAST_GENERATION, -1L)
        set(value) = prefs.edit().putLong(KEY_LAST_GENERATION, value).apply()

    var lastMediaCount: Int
        get() = prefs.getInt(KEY_LAST_MEDIA_COUNT, -1)
        set(value) = prefs.edit().putInt(KEY_LAST_MEDIA_COUNT, value).apply()

    var groupingVersion: Int
        get() = prefs.getInt(KEY_GROUPING_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_GROUPING_VERSION, value).apply()

    var groupingPending: Boolean
        get() = prefs.getBoolean(KEY_GROUPING_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_GROUPING_PENDING, value).apply()

    var strictness: Float
        get() = prefs.getFloat(KEY_STRICTNESS, ClusterParams.DEFAULT_STRICTNESS)
        set(value) = prefs.edit().putFloat(KEY_STRICTNESS, value).apply()

    var skipScreenshots: Boolean
        get() = prefs.getBoolean(KEY_SKIP_SCREENSHOTS, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_SCREENSHOTS, value).apply()

    var notificationAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_ASKED, value).apply()

    var cloudLinked: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_LINKED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_LINKED, value).apply()

    var cloudDirty: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_DIRTY, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_DIRTY, value).apply()

    var cloudSavedAt: Long
        get() = prefs.getLong(KEY_CLOUD_SAVED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CLOUD_SAVED_AT, value).apply()

    private val moveMatchesState = MutableStateFlow(prefs.getBoolean(KEY_MOVE_MATCHES, true))
    val moveMatches: StateFlow<Boolean> = moveMatchesState.asStateFlow()

    fun setMoveMatches(value: Boolean) {
        prefs.edit().putBoolean(KEY_MOVE_MATCHES, value).apply()
        moveMatchesState.value = value
    }

    fun setMinPhotos(value: Int) {
        prefs.edit().putInt(KEY_MIN_PHOTOS, value).apply()
        minPhotosState.value = value
    }

    companion object {
        const val DEFAULT_MIN_PHOTOS = 3
        private const val KEY_CLOUD_LINKED = "cloud_linked"
        private const val KEY_CLOUD_DIRTY = "cloud_dirty"
        private const val KEY_CLOUD_SAVED_AT = "cloud_saved_at"
        private const val KEY_MIN_PHOTOS = "min_photos"
        private const val KEY_INITIAL_DONE = "initial_scan_done"
        private const val KEY_SCAN_TOTAL = "scan_total"
        private const val KEY_GROUPING_PENDING = "grouping_pending"
        private const val KEY_LAST_NEW = "last_new"
        private const val KEY_LAST_GENERATION = "last_generation"
        private const val KEY_LAST_MEDIA_COUNT = "last_media_count"
        private const val KEY_GROUPING_VERSION = "grouping_version"
        private const val KEY_STRICTNESS = "strictness"
        private const val KEY_LAST_FINISHED = "last_finished"
        private const val KEY_SKIP_SCREENSHOTS = "skip_screenshots"
        private const val KEY_NOTIFICATION_ASKED = "notification_asked"
        private const val KEY_MOVE_MATCHES = "move_matches"
    }
}
