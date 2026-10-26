package com.maya.agent.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsDraftTest {
    private fun deps(matches: List<DialCommand.Match>) = PhoneCommand.Deps(
        hasCallPermission = { false }, hasContactsPermission = { true }, lookup = { matches }, dial = { false })
    private val ammi = listOf(DialCommand.Match("Ammi", "9876543210"))
    private var opened: Pair<String, String>? = null
    private fun run(target: String, body: String, speech: String, m: List<DialCommand.Match> = ammi, secret: Boolean = false) =
        SmsDraft.prepare(target, body, speech, deps(m), { secret }, { n, b -> opened = n to b; true })

    @Test fun `heard request opens composer only, never sends`() {
        opened = null
        val r = run("Ammi", "main late aaunga dinner par", "maya ammi ko message bhej main late aaunga dinner par")
        assertEquals("composer_opened", r.status); assertEquals("9876543210" to "main late aaunga dinner par", opened)
        assertTrue(r.text.contains("NOT been sent"))
    }
    @Test fun `no message request heard means nothing`() {
        opened = null
        assertEquals("no_request_heard", run("Ammi", "hello there", "ammi kaisi ho").status); assertEquals(null, opened)
    }
    @Test fun `model invented body is preview only`() {
        opened = null
        assertEquals("preview_only", run("Ammi", "please transfer the money tomorrow morning", "ammi ko message bhej do").status); assertEquals(null, opened)
    }
    @Test fun `recipient not said by owner is preview only`() {
        opened = null
        assertEquals("preview_only", run("Ammi", "main late aaunga", "message bhej main late aaunga").status); assertEquals(null, opened)
    }
    @Test fun `ambiguous or unknown contact is never guessed`() {
        opened = null
        assertEquals("ambiguous", run("Ammi", "x y z", "ammi message bhej", listOf(DialCommand.Match("Ammi", "1"), DialCommand.Match("Ammi Home", "2"))).status)
        assertEquals("not_found", run("Ammi", "x y z", "ammi message bhej", emptyList()).status); assertEquals(null, opened)
    }
    @Test fun `secret like body and emergency refused`() {
        opened = null
        assertEquals("refused", run("Ammi", "my password is hunter2 ok", "ammi message bhej password hunter2", secret = true).status)
        assertEquals("refused", run("112", "help me now please", "112 ko message bhej help me now please", emptyList()).status); assertEquals(null, opened)
    }
    @Test fun `overlap math`() { assertFalse(SmsDraft.bodyOverlap("abc def ghi", "xyz") > 0.1) }
}
