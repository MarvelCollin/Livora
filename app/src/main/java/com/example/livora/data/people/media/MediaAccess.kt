package com.example.livora.data.people.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class AccessLevel { None, Partial, Full }

object MediaAccess {

    fun level(context: Context): AccessLevel {
        return when {
            Build.VERSION.SDK_INT >= 34 -> when {
                granted(context, Manifest.permission.READ_MEDIA_IMAGES) -> AccessLevel.Full
                granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> AccessLevel.Partial
                else -> AccessLevel.None
            }
            Build.VERSION.SDK_INT >= 33 ->
                if (granted(context, Manifest.permission.READ_MEDIA_IMAGES)) AccessLevel.Full else AccessLevel.None
            else ->
                if (granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) AccessLevel.Full else AccessLevel.None
        }
    }

    fun hasAnyAccess(context: Context): Boolean = level(context) != AccessLevel.None

    fun requestPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
