package com.example.livora.util

import android.util.Log

object Logger {
    fun debug(tag: String, message: String) {
        Log.d(tag, "[DEBUG] $message")
    }
}
