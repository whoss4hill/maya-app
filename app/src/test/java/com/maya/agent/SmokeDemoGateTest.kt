package com.maya.agent

import android.content.Intent
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The render hook must work on the debuggable CI build and be inert on a
 * release (non-debuggable) install - tested against the actual gate logic,
 * not a build-flag assumption.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmokeDemoGateTest {

    private fun info(debuggable: Boolean) = ApplicationInfo().apply {
        flags = if (debuggable) ApplicationInfo.FLAG_DEBUGGABLE else 0
    }

    private fun intentWithExtra() = Intent().putExtra(SmokeDemoGate.EXTRA, true)

    @Test
    fun debuggableBuildWithExtraAllowsDemoEntry() {
        assertTrue(SmokeDemoGate.allowed(info(debuggable = true), intentWithExtra()))
    }

    @Test
    fun releaseBuildIgnoresTheExtra() {
        assertFalse(SmokeDemoGate.allowed(info(debuggable = false), intentWithExtra()))
    }

    @Test
    fun debuggableBuildWithoutExtraDoesNotEnterDemo() {
        assertFalse(SmokeDemoGate.allowed(info(debuggable = true), Intent()))
        assertFalse(SmokeDemoGate.allowed(info(debuggable = true), null))
    }
}
