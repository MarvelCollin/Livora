package com.example.livora.util

import android.util.Log

object LivoraLogger {
    fun debug(tag: String, message: String) {
        Log.d(tag, "[DEBUG] $message")
    }
}
