package com.maya.agent

import com.maya.agent.calls.CallConfirmation
import com.maya.agent.calls.CallConfirmation.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallConfirmationTest {
    private fun ready(): CallConfirmation = CallConfirmation().also { it.begin("9876543210", "Raj"); it.onReadbackSpoken(1000) }
    private val after = 1000 + CallConfirmation.ECHO_GUARD_MS + 100

    @Test fun `yes after app readback confirms the bound number once`() {
        val c = ready(); c.onOwnerTranscript("Haan, call laga do", after)
        val o = c.confirm(5000); assertTrue(o is Outcome.Confirmed && o.number == "9876543210")
        assertEquals(Outcome.NoPending, c.confirm(5001))
    }
    @Test fun `speech before readback finished or in the echo window does not count`() {
        val c = CallConfirmation(); c.begin("9876543210", ""); c.onOwnerTranscript("haan call lagao", 10)
        assertEquals(Outcome.ReadbackNotFinished, c.confirm(20))
        c.onReadbackSpoken(1000); c.onOwnerTranscript("haan call lagao", 1200)
        assertEquals(Outcome.NoAnswerHeard, c.confirm(1500))
    }
    @Test fun `no, negation, and unclear answers never confirm`() {
        val a = ready(); a.onOwnerTranscript("nahi ruko call mat karo", after); assertEquals(Outcome.Denied, a.confirm(2000))
        val b = ready(); b.onOwnerTranscript("haan call nahi wait", after); assertEquals(Outcome.Denied, b.confirm(2000))
        val c = ready(); c.onOwnerTranscript("kya bol rahi ho", after); assertEquals(Outcome.Unclear, c.confirm(2000))
        val d = ready(); d.onOwnerTranscript("haan call " + "bla ".repeat(10), after); assertEquals(Outcome.Unclear, d.confirm(2000))
    }
    @Test fun `expires after 60 seconds`() {
        val c = ready(); c.onOwnerTranscript("yes call", after)
        assertEquals(Outcome.Expired, c.confirm(1000 + CallConfirmation.EXPIRY_MS + 1))
    }
    @Test fun `new target replaces the old pending and needs its own readback and yes`() {
        val c = ready(); c.onOwnerTranscript("yes call", after)
        c.begin("1111111111", "Other")
        assertEquals(Outcome.ReadbackNotFinished, c.confirm(2000))
        c.onReadbackSpoken(3000); assertEquals(Outcome.NoAnswerHeard, c.confirm(3500))
        c.onOwnerTranscript("ok call karo", 4600); val o = c.confirm(4700); assertTrue(o is Outcome.Confirmed && o.number == "1111111111")
    }
    @Test fun `session reset clears pending`() {
        val c = ready(); c.onOwnerTranscript("yes call", after); c.reset(); assertEquals(Outcome.NoPending, c.confirm(2000))
    }
    @Test fun `bare haan or ambient yes without a call phrase is not enough`() {
        val a = ready(); a.onOwnerTranscript("haan", after); assertEquals(Outcome.Unclear, a.confirm(2000))
        assertEquals(CallConfirmation.Verdict.UNCLEAR, CallConfirmation.judge("yes ok theek hai"))
        assertEquals(CallConfirmation.Verdict.YES, CallConfirmation.judge("yes call"))
        assertEquals(CallConfirmation.Verdict.YES, CallConfirmation.judge("call lagao"))
        assertEquals(CallConfirmation.Verdict.NO, CallConfirmation.judge("call mat lagao"))
    }
    @Test fun `hindi script answers`() {
        assertEquals(CallConfirmation.Verdict.YES, CallConfirmation.judge("हाँ कॉल लगाओ"))
        assertEquals(CallConfirmation.Verdict.NO, CallConfirmation.judge("नहीं"))
    }
    @Test fun `owner must have asked for a call out loud recently`() {
        val c = CallConfirmation()
        assertFalse(c.ownerAskedForCall(1000))
        c.onOwnerTranscript("Maya Ammi ko call karo", 1000)
        assertTrue(c.ownerAskedForCall(2000))
        assertFalse(c.ownerAskedForCall(1000 + CallConfirmation.ASK_WINDOW_MS + 1))
        val d = CallConfirmation(); d.onOwnerTranscript("aaj mausam kaisa hai", 1000)
        assertFalse(d.ownerAskedForCall(2000))
    }
}
