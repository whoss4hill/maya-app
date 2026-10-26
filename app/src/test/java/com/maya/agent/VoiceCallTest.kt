package com.maya.agent

import com.maya.agent.calls.CallConfirmation
import com.maya.agent.calls.DialCommand
import com.maya.agent.calls.PhoneCommand
import com.maya.agent.calls.VoiceCall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCallTest {
    private val dialed = mutableListOf<String>()
    private fun deps(call: Boolean = true, contacts: Boolean = true, found: List<DialCommand.Match> = emptyList(), dialOk: Boolean = true, known: Boolean = true, tts: Boolean = true) =
        PhoneCommand.Deps({ call }, { contacts }, { found }, { dialed.add(it); dialOk }, { known },
            { text, done -> spoken.add(text); if (tts) pendingDone = done; tts }, { dialerOpened.add(it); true })
    private val spoken = mutableListOf<String>()
    private val dialerOpened = mutableListOf<String>()
    private var pendingDone: (() -> Unit)? = null
    /** conf where the owner just asked for a call out loud */
    private fun asked() = CallConfirmation().also { it.onOwnerTranscript("Maya call karo", 0) }

    @Test fun `no voice call dials directly, not even a number or single contact match`() {
        val r = VoiceCall.place("98765 43210", deps(), asked(), { 1000 })
        assertEquals("needs_confirmation", r.status); assertTrue(dialed.isEmpty())
        val r2 = VoiceCall.place("Ammi", deps(found = listOf(DialCommand.Match("Ammi", "+91 98765 43210"))), asked(), { 1000 })
        assertEquals("needs_confirmation", r2.status); assertTrue(dialed.isEmpty())
        assertTrue(spoken[1].startsWith("Ammi.") && spoken[1].contains("9 1 9 8 7 6 5 4 3 2 1 0"))
    }
    @Test fun `confirmed call is launched and never claimed connected`() {
        val d = deps(); val c = asked(); VoiceCall.place("9876543210", d, c, { 1000 }); pendingDone!!.invoke()
        c.onOwnerTranscript("haan call lagao", 3000)
        val r = VoiceCall.confirmPending(d, c, 3500)
        assertEquals("intent_launched", r.status); assertTrue(r.text.contains("cannot confirm"))
    }
    @Test fun `ambiguous contact never dials`() {
        val r = VoiceCall.place("Raj", deps(found = listOf(DialCommand.Match("Raj A", "1111111"), DialCommand.Match("Raj B", "2222222"))), asked(), { 1000 })
        assertEquals("ambiguous", r.status); assertTrue(dialed.isEmpty())
    }
    @Test fun `unknown contact and gibberish never dial`() {
        assertEquals("not_found", VoiceCall.place("Zed", deps(), asked(), { 1000 }).status)
        assertEquals("unclear", VoiceCall.place("", deps(), asked(), { 1000 }).status)
        assertEquals("unclear", VoiceCall.place("me", deps(), asked(), { 1000 }).status)
        assertTrue(dialed.isEmpty())
    }
    @Test fun `missing permissions never dial`() {
        assertEquals("blocked", VoiceCall.place("9876543210", deps(call = false), asked(), { 1000 }).status)
        assertEquals("blocked", VoiceCall.place("Ammi", deps(contacts = false), asked(), { 1000 }).status)
        assertTrue(dialed.isEmpty())
    }
    @Test fun `os refusal is reported as blocked`() {
        val d = deps(dialOk = false); val c = asked(); VoiceCall.place("9876543210", d, c, { 1000 }); pendingDone!!.invoke()
        c.onOwnerTranscript("haan call lagao", 3000)
        assertEquals("blocked", VoiceCall.confirmPending(d, c, 3500).status)
    }
    @Test fun `emergency number only opens dialer`() {
        assertTrue(DialCommand.isEmergency("112")); assertFalse(DialCommand.isEmergency("9876543210"))
        assertEquals("dialer_opened", VoiceCall.place("112", deps(), asked(), { 1000 }).status)
    }

    @Test fun `unknown number is read back by the app and held until the owner answers after it`() {
        val d = deps(known = false); val c = asked()
        assertEquals("needs_confirmation", VoiceCall.place("9876543210", d, c, { 1000 }).status)
        assertTrue(spoken[0].contains("9 8 7 6 5 4 3 2 1 0"))                   // app speaks the exact digits itself
        assertEquals("wait", VoiceCall.confirmPending(d, c, 1100).status)       // model confirms before TTS finished
        pendingDone!!.invoke()                                                  // TTS done callback -> readback finished at t=1000
        assertEquals("no_answer", VoiceCall.confirmPending(d, c, 3000).status)  // model invents a yes: nothing heard
        assertTrue(dialed.isEmpty())
        c.onOwnerTranscript("haan call kar do", 3000 + CallConfirmation.ECHO_GUARD_MS)
        assertEquals("intent_launched", VoiceCall.confirmPending(d, c, 5000).status)
        assertEquals(listOf("9876543210"), dialed)
        assertEquals("no_pending", VoiceCall.confirmPending(d, c, 6000).status) // one-shot, replay refused
        assertEquals(1, dialed.size)
    }
    @Test fun `tts failure fails closed to dialer only`() {
        val r = VoiceCall.place("9876543210", deps(known = false, tts = false), asked(), { 1000 })
        assertEquals("dialer_opened", r.status); assertTrue(dialed.isEmpty()); assertEquals(1, dialerOpened.size)
    }
    @Test fun `no spoken call request heard means dialer only, never a call`() {
        assertEquals("dialer_opened", VoiceCall.place("9876543210", deps(), CallConfirmation(), { 1000 }).status)
        assertTrue(dialed.isEmpty())
    }
    @Test fun `a fresh call request replaces an older pending and its yes`() {
        val d = deps(); val c = asked()
        VoiceCall.place("1111111111", d, c, { 1000 }); pendingDone!!.invoke()
        c.onOwnerTranscript("haan call lagao", 3000)
        VoiceCall.place("2222222222", d, c, { 4000 })        // different target: old yes is gone
        assertEquals("wait", VoiceCall.confirmPending(d, c, 4100).status)
        assertTrue(dialed.isEmpty())
    }
    @Test fun `ambiguous or silent owner never reaches ACTION_CALL`() {
        val d = deps(); val c = asked(); VoiceCall.place("9876543210", d, c, { 1000 }); pendingDone!!.invoke()
        assertEquals("no_answer", VoiceCall.confirmPending(d, c, 2500).status)
        c.onOwnerTranscript("hmm", 2600 + CallConfirmation.ECHO_GUARD_MS)
        assertEquals("unclear_answer", VoiceCall.confirmPending(d, c, 4200).status)
        assertTrue(dialed.isEmpty())
    }
}
