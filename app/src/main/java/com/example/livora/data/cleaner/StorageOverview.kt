package com.example.livora.data.cleaner

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import com.example.livora.data.usage.UsageAccess

class StorageSlice(val key: String, val label: String, val bytes: Long, val slot: Int?)

class StorageOverview(val total: Long, val free: Long, val slices: List<StorageSlice>) {
    val used: Long get() = (total - free).coerceAtLeast(0)
}

object StorageReader {

    fun read(context: Context, files: List<CleanerFile>): StorageOverview {
        var total = 0L
        var free = 0L
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        } else {
            null
        }
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                total = manager.getTotalBytes(StorageManager.UUID_DEFAULT)
                free = manager.getFreeBytes(StorageManager.UUID_DEFAULT)
            } catch (e: Exception) {
                total = 0L
            }
        }
        if (total <= 0L) {
            @Suppress("DEPRECATION")
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            total = stat.blockCountLong * stat.blockSizeLong
            free = stat.availableBlocksLong * stat.blockSizeLong
        }

        val photos = files.filter { !it.video }.sumOf { it.size }
        val videos = files.filter { it.video }.sumOf { it.size }
        var apps: Long? = null
        var audio: Long? = null
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && UsageAccess.isGranted(context)) {
            try {
                val user = manager.queryStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle())
                apps = user.appBytes + user.dataBytes + user.cacheBytes
                audio = manager.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, Process.myUserHandle()).audioBytes
            } catch (e: Exception) {
                apps = null
                audio = null
            }
        }

        val used = (total - free).coerceAtLeast(0)
        val known = photos + videos + (apps ?: 0L) + (audio ?: 0L)
        val other = (used - known).coerceAtLeast(0)
        val slices = ArrayList<StorageSlice>()
        if (apps != null) slices.add(StorageSlice("apps", "Apps", apps, 0))
        slices.add(StorageSlice("photos", "Photos", photos, 1))
        slices.add(StorageSlice("videos", "Videos", videos, 2))
        if (audio != null) slices.add(StorageSlice("audio", "Audio", audio, 3))
        slices.add(StorageSlice("other", "Everything else", other, null))
        return StorageOverview(total, free, slices)
    }
}
