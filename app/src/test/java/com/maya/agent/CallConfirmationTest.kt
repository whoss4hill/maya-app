package com.maya.agent

import com.maya.agent.calls.CallConfirmation
import com.maya.agent.calls.CallConfirmation.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallConfirmationTest {
    private fun ready(): CallConfirmation = CallConfirmation().also { it.begin("9876543210", "Raj"); it.onTurnComplete(1000) }

    @Test fun `yes after readback confirms the bound number once`() {
        val c = ready(); c.onOwnerTranscript("Haan, laga do")
        val o = c.confirm(5000); assertTrue(o is Outcome.Confirmed && o.number == "9876543210")
        assertEquals(Outcome.NoPending, c.confirm(5001))
    }
    @Test fun `speech before readback finished does not count`() {
        val c = CallConfirmation(); c.begin("9876543210", ""); c.onOwnerTranscript("haan haan")
        assertEquals(Outcome.ReadbackNotFinished, c.confirm(10))
        c.onTurnComplete(1000)
        assertEquals(Outcome.NoAnswerHeard, c.confirm(1500))
    }
    @Test fun `barge in during readback cancels`() {
        val c = CallConfirmation(); c.begin("9876543210", ""); c.onInterrupted()
        assertEquals(Outcome.NoPending, c.confirm(10))
    }
    @Test fun `no, negation, and unclear answers never confirm`() {
        val a = ready(); a.onOwnerTranscript("nahi ruko"); assertEquals(Outcome.Denied, a.confirm(2000))
        val b = ready(); b.onOwnerTranscript("haan nahi wait"); assertEquals(Outcome.Denied, b.confirm(2000))
        val c = ready(); c.onOwnerTranscript("kya bol rahi ho"); assertEquals(Outcome.Unclear, c.confirm(2000))
        val d = ready(); d.onOwnerTranscript("haan " + "bla ".repeat(10)); assertEquals(Outcome.Unclear, d.confirm(2000))
    }
    @Test fun `expires after 60 seconds`() {
        val c = ready(); c.onOwnerTranscript("yes")
        assertEquals(Outcome.Expired, c.confirm(1000 + CallConfirmation.EXPIRY_MS + 1))
    }
    @Test fun `new target replaces the old pending and needs its own readback and yes`() {
        val c = ready(); c.onOwnerTranscript("yes")
        c.begin("1111111111", "Other")
        assertEquals(Outcome.ReadbackNotFinished, c.confirm(2000))
        c.onTurnComplete(3000); assertEquals(Outcome.NoAnswerHeard, c.confirm(3500))
        c.onOwnerTranscript("ok"); val o = c.confirm(4000); assertTrue(o is Outcome.Confirmed && o.number == "1111111111")
    }
    @Test fun `session reset clears pending`() {
        val c = ready(); c.onOwnerTranscript("yes"); c.reset(); assertEquals(Outcome.NoPending, c.confirm(2000))
    }
    @Test fun `hindi script answers`() {
        assertEquals(CallConfirmation.Verdict.YES, CallConfirmation.judge("हाँ कर दो"))
        assertEquals(CallConfirmation.Verdict.NO, CallConfirmation.judge("नहीं"))
    }
}
