package com.example.livora.util

import java.io.IOException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMessagesTest {

    @Test
    fun networkErrorsAskForAConnection() {
        val message = UserMessages.saveFailure(IOException("timeout"), "this word")
        assertTrue(message.startsWith("No connection"))
    }

    @Test
    fun wrappedNetworkErrorsAreDetected() {
        val wrapped = IllegalStateException("boom", UnknownHostException("host"))
        assertTrue(UserMessages.saveFailure(wrapped, "this task").startsWith("No connection"))
    }

    @Test
    fun missingSetupMessageIsShownAsIs() {
        val text = "Cloud sync is not set up. Add secrets.properties and rebuild the app."
        assertEquals(text, UserMessages.saveFailure(IllegalStateException(text), "this word"))
    }

    @Test
    fun serverErrorsNeverLeakRawJson() {
        val raw = IllegalStateException("{\"code\":\"23502\",\"message\":\"null value in column\"}")
        val message = UserMessages.saveFailure(raw, "this word")
        assertEquals("Could not save this word. Try again in a moment.", message)
    }
}
