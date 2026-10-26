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
    private fun deps(call: Boolean = true, contacts: Boolean = true, found: List<DialCommand.Match> = emptyList(), dialOk: Boolean = true, known: Boolean = true) =
        PhoneCommand.Deps({ call }, { contacts }, { found }, { dialed.add(it); dialOk }, { known })

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

    @Test fun `unknown number is held until the app hears the owner after readback`() {
        val d = deps(known = false); val c = CallConfirmation()
        assertEquals("needs_confirmation", VoiceCall.place("9876543210", d, c).status)
        assertEquals("wait", VoiceCall.confirmPending(d, c, 0).status)             // model confirms before readback finished
        c.onTurnComplete(1000)
        assertEquals("no_answer", VoiceCall.confirmPending(d, c, 2000).status)     // model invents a yes: nothing heard
        assertTrue(dialed.isEmpty())
        c.onOwnerTranscript("haan kar do")
        assertEquals("intent_launched", VoiceCall.confirmPending(d, c, 3000).status)
        assertEquals(listOf("9876543210"), dialed)
        assertEquals("no_pending", VoiceCall.confirmPending(d, c, 4000).status)    // one-shot, replay refused
        assertEquals(1, dialed.size)
    }
    @Test fun `contact entry alone is not prior interaction`() {
        val c = CallConfirmation()
        val r = VoiceCall.place("Ammi", deps(known = false, found = listOf(DialCommand.Match("Ammi", "9876543210"))), c)
        assertEquals("needs_confirmation", r.status); assertTrue(dialed.isEmpty())
    }
    @Test fun `known call history dials directly`() {
        assertEquals("intent_launched", VoiceCall.place("9876543210", deps(known = true), CallConfirmation()).status)
    }
}
