package com.maya.agent

import androidx.test.core.app.ApplicationProvider
import com.maya.agent.voice.VoiceController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The in-app mic bug fix: callbacks on the app-level VoiceController must be
 * replaceable (Chat screen wires/unwires them) and a turn must surface errors
 * honestly instead of being dropped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceControllerCallbackTest {

    @Test
    fun callbacksAreReplaceableAndDefaultsSafe() {
        val vc = VoiceController(ApplicationProvider.getApplicationContext())
        vc.onResult("ignored by default no-op") // must not throw
        var got: String? = null
        vc.onResult = { got = it }
        vc.onResult("hello")
        assertEquals("hello", got)
        vc.onResult = {} // dispose path restores a no-op
        vc.onResult("dropped safely")
        vc.shutdown()
    }

    @Test
    fun startListeningWithoutRecognizerServiceSurfacesError() {
        var err: String? = null
        val vc = VoiceController(
            ApplicationProvider.getApplicationContext(),
            onError = { err = it },
        )
        vc.startListening()
        // Robolectric has no speech recognition service installed: the turn must
        // end in an honest error message, not silence.
        assertEquals("Speech recognition is not available on this phone", err)
        assertFalse(vc.isListening)
        vc.shutdown()
    }
}
