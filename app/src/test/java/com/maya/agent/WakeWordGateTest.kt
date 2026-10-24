package com.maya.agent

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import com.maya.agent.voice.WakeWordEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.Shadows

/**
 * Gate behavior of the merged wake-word engine (no device needed):
 * consent/prereq failures must surface via onFailure and never open the mic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WakeWordGateTest {

    @Test
    fun refusesWithoutMicPermission() {
        var failure: String? = null
        var woke = false
        val engine = WakeWordEngine(
            ApplicationProvider.getApplicationContext(),
            onWake = { woke = true },
            onFailure = { failure = it },
        )
        engine.start()
        assertEquals("Mic permission not granted", failure)
        assertFalse(woke)
        assertFalse(engine.isRunning)
    }

    @Test
    fun refusesWhenModelNotDownloaded() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.RECORD_AUDIO)
        var failure: String? = null
        val engine = WakeWordEngine(
            ApplicationProvider.getApplicationContext(),
            onWake = {},
            onFailure = { failure = it },
        )
        engine.start()
        assertEquals("Wake word model is not downloaded", failure)
        assertFalse(engine.isRunning)
    }
}
