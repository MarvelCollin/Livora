package com.example.livora.util

import java.io.IOException

object UserMessages {

    fun saveFailure(error: Throwable, what: String): String {
        val root = generateSequence(error) { it.cause }.last()
        val message = error.message.orEmpty()
        return when {
            root is IOException -> "No connection. Check your internet and try again."
            message.startsWith("Cloud sync is not set up") -> message
            else -> "Could not save $what. Try again in a moment."
        }
    }
}
