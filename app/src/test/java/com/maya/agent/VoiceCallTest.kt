package com.maya.agent

import com.maya.agent.calls.DialCommand
import com.maya.agent.calls.PhoneCommand
import com.maya.agent.calls.VoiceCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallTest {
    private val dialed = mutableListOf<String>()
    private fun deps(call: Boolean = true, contacts: Boolean = true, found: List<DialCommand.Match> = emptyList(), dialOk: Boolean = true) =
        PhoneCommand.Deps({ call }, { contacts }, { found }, { dialed.add(it); dialOk })

    @Test fun `number is dialled and not claimed connected`() {
        val r = VoiceCall.place("98765 43210", deps())
        assertEquals("intent_launched", r.status); assertEquals(listOf("9876543210"), dialed)
        assertTrue(r.text.contains("not") || r.text.contains("cannot confirm"))
    }
    @Test fun `single contact match is dialled`() {
        val r = VoiceCall.place("Ammi", deps(found = listOf(DialCommand.Match("Ammi", "+91 98765 43210"))))
        assertEquals("intent_launched", r.status); assertEquals(1, dialed.size)
    }
    @Test fun `ambiguous contact never dials`() {
        val r = VoiceCall.place("Raj", deps(found = listOf(DialCommand.Match("Raj A", "1111111"), DialCommand.Match("Raj B", "2222222"))))
        assertEquals("ambiguous", r.status); assertTrue(dialed.isEmpty())
    }
    @Test fun `unknown contact and gibberish never dial`() {
        assertEquals("not_found", VoiceCall.place("Zed", deps()).status)
        assertEquals("unclear", VoiceCall.place("", deps()).status)
        assertEquals("unclear", VoiceCall.place("me", deps()).status)
        assertTrue(dialed.isEmpty())
    }
    @Test fun `missing permissions never dial`() {
        assertEquals("blocked", VoiceCall.place("9876543210", deps(call = false)).status)
        assertEquals("blocked", VoiceCall.place("Ammi", deps(contacts = false)).status)
        assertTrue(dialed.isEmpty())
    }
    @Test fun `os refusal is reported as blocked`() {
        assertEquals("blocked", VoiceCall.place("9876543210", deps(dialOk = false)).status)
    }
    @Test fun `emergency number only opens dialer`() {
        assertTrue(DialCommand.isEmergency("112")); assertFalse(DialCommand.isEmergency("9876543210"))
        assertEquals("dialer_opened", VoiceCall.place("112", deps()).status)
    }
}
