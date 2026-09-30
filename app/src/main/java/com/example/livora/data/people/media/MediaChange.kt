package com.example.livora.data.people.media

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.livora.data.people.PeoplePrefs

object MediaChange {

    fun generation(context: Context): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return -1L
        return try {
            MediaStore.getGeneration(context, MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } catch (e: Exception) {
            -1L
        }
    }

    fun hasChanged(context: Context, prefs: PeoplePrefs): Boolean {
        if (!prefs.initialScanDone) return true
        if (prefs.groupingPending) return true
        val generation = generation(context)
        if (generation >= 0 && prefs.lastGeneration >= 0) return generation != prefs.lastGeneration
        return MediaImages.count(context) != prefs.lastMediaCount
    }

    fun remember(context: Context, prefs: PeoplePrefs, generation: Long, mediaCount: Int) {
        prefs.lastGeneration = generation
        prefs.lastMediaCount = mediaCount
    }
}
