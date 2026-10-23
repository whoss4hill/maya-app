package com.maya.agent

import com.maya.agent.calls.DialCommand
import com.maya.agent.calls.PhoneCommand
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhoneCommandTest {
    private var dialed = mutableListOf<String>()
    private fun deps(call: Boolean = true, contacts: Boolean = true, found: List<DialCommand.Match> = emptyList(), ok: Boolean = true) =
        PhoneCommand.Deps({ call }, { contacts }, { found }, { dialed.add(it); ok })
    private fun cmd(id: String, target: String, exp: Long = 1000, c: String = "call") =
        """{"id":"$id","cmd":"$c","target":"$target","exp":$exp}"""

    @Before fun reset() { PhoneCommand.resetSeen(); dialed = mutableListOf() }

    @Test fun numberDialsAndAckSaysIntentOnly() {
        val a = PhoneCommand.handle(cmd("a1", "+910000000000"), 900, deps())!!
        assertEquals("intent_launched", a.status); assertEquals(listOf("+910000000000"), dialed)
        assertTrue(a.text.contains("cannot confirm"))
        assertFalse(a.text.contains("+910000000000"))
    }
    @Test fun replayAndExpiredNeverDial() {
        PhoneCommand.handle(cmd("a2", "+910000000000"), 900, deps())
        assertNull(PhoneCommand.handle(cmd("a2", "+910000000000"), 900, deps()))
        val e = PhoneCommand.handle(cmd("a3", "+910000000000", exp = 10), 900, deps())!!
        assertEquals("expired", e.status); assertEquals(1, dialed.size)
    }
    @Test fun permissionMissingIsBlocked() {
        val a = PhoneCommand.handle(cmd("a4", "+910000000000"), 900, deps(call = false))!!
        assertEquals("blocked", a.status); assertTrue(dialed.isEmpty())
    }
    @Test fun ambiguousAndUnknownNamesNeverDial() {
        val two = listOf(DialCommand.Match("Mom", "+911111111111"), DialCommand.Match("Mom Work", "+912222222222"))
        assertEquals("ambiguous", PhoneCommand.handle(cmd("a5", "Mom"), 900, deps(found = two))!!.status)
        assertEquals("not_found", PhoneCommand.handle(cmd("a6", "Zed"), 900, deps())!!.status)
        assertEquals("blocked", PhoneCommand.handle(cmd("a7", "Mom"), 900, deps(contacts = false))!!.status)
        assertTrue(dialed.isEmpty())
    }
    @Test fun singleContactDialsAndOsRefusalIsBlocked() {
        val one = listOf(DialCommand.Match("Mom", "+911111111111"))
        assertEquals("intent_launched", PhoneCommand.handle(cmd("a8", "Mom"), 900, deps(found = one))!!.status)
        assertEquals("blocked", PhoneCommand.handle(cmd("a9", "Mom"), 900, deps(found = one, ok = false))!!.status)
    }
    @Test fun garbageAndOtherCmdsIgnored() {
        assertNull(PhoneCommand.handle("not json", 900, deps()))
        assertNull(PhoneCommand.handle("""{"cmd":"call"}""", 900, deps()))
        assertEquals("ignored", PhoneCommand.handle(cmd("b1", "x", c = "wipe"), 900, deps())!!.status)
        assertTrue(dialed.isEmpty())
    }
}
