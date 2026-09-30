package com.example.livora.data.apps

import android.content.Context
import android.content.Intent
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class AppInfo(val packageName: String, val label: String)

object InstalledApps {

    private val preferred = listOf(
        "com.instagram.android",
        "com.google.android.youtube",
        "com.whatsapp",
        "com.android.chrome",
        "com.example.livora",
        "com.google.android.apps.maps",
        "com.spotify.music",
        "com.zhiliaoapp.musically"
    )

    private val cache = object : LruCache<String, ImageBitmap>(48) {}

    fun launcherApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        val apps = found
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
        val rank = preferred.withIndex().associate { it.value to it.index }
        return apps.sortedWith(compareBy<AppInfo>({ rank[it.packageName] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
    }

    fun icon(context: Context, packageName: String, sizePx: Int): ImageBitmap? {
        cache.get("$packageName:$sizePx")?.let { return it }
        return try {
            val bitmap = context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            cache.put("$packageName:$sizePx", bitmap)
            bitmap
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
