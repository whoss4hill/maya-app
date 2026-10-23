package com.maya.agent

import com.maya.agent.voice.WakeWordEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordMatchTest {
    @Test fun matchesWholeWordOnly() {
        assertTrue(WakeWordEngine.matches("hey Maya"))
        assertTrue(WakeWordEngine.matches("maya"))
        assertFalse(WakeWordEngine.matches("mayan calendar"))
        assertFalse(WakeWordEngine.matches(""))
    }
}
