package com.example.livora.data.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

object VaultClipboard {

    private const val CLEAR_DELAY_MILLIS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var lastCopied: String? = null
    private var pendingClear: Runnable? = null

    fun copySecret(context: Context, label: String, text: String) {
        val manager = context.applicationContext.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, text)
        val extras = PersistableBundle()
        val key = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
        extras.putBoolean(key, true)
        clip.description.extras = extras
        manager.setPrimaryClip(clip)
        lastCopied = text
        schedule(manager)
    }

    fun clearNow(context: Context) {
        val manager = context.applicationContext.getSystemService(ClipboardManager::class.java) ?: return
        clearIfOurs(manager)
    }

    private fun schedule(manager: ClipboardManager) {
        pendingClear?.let { handler.removeCallbacks(it) }
        val task = Runnable { clearIfOurs(manager) }
        pendingClear = task
        handler.postDelayed(task, CLEAR_DELAY_MILLIS)
    }

    private fun clearIfOurs(manager: ClipboardManager) {
        val copied = lastCopied ?: return
        val current = manager.primaryClip?.getItemAt(0)?.text?.toString()
        if (current == copied) {
            if (Build.VERSION.SDK_INT >= 28) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
        lastCopied = null
        pendingClear?.let { handler.removeCallbacks(it) }
        pendingClear = null
    }
}
