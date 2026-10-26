package com.maya.agent.voice.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NoteToolTest {
    private val ok = { _: String -> false }

    @Test fun `owner request with matching text writes`() {
        val d = NoteTool.decide("kal doodh lana", "maya ek note bana do kal doodh lana", ok)
        assertTrue(d is NoteTool.Decision.Write)
    }
    @Test fun `no note request heard writes nothing`() {
        val d = NoteTool.decide("kal doodh lana", "kal doodh lana hai", ok) as NoteTool.Decision.Refuse
        assertEquals("no_request_heard", d.status)
    }
    @Test fun `invented text is refused as preview only`() {
        val d = NoteTool.decide("wire the payment to account tomorrow morning", "ek note bana do doodh", ok) as NoteTool.Decision.Refuse
        assertEquals("preview_only", d.status)
    }
    @Test fun `secret like text and empty and huge refused`() {
        assertEquals("refused", (NoteTool.decide("password hunter2 save", "note password hunter2 save", { true }) as NoteTool.Decision.Refuse).status)
        assertEquals("unclear", (NoteTool.decide("  ", "note", ok) as NoteTool.Decision.Refuse).status)
        assertEquals("too_long", (NoteTool.decide("a".repeat(NoteTool.MAX_CHARS + 1), "note", ok) as NoteTool.Decision.Refuse).status)
    }
    @Test fun `android 9 writes a new private file, never overwrites, says it is not in Files`() {
        val ctx = RuntimeEnvironment.getApplication()
        val t = Date(1_800_000_000_000L)
        val m1 = NoteTool.write(ctx, "one", t, sdk = 28)!!
        val m2 = NoteTool.write(ctx, "two", t, sdk = 28)!!
        assertTrue(m1, m1.contains("private") && !m1.contains("Downloads"))
        val files = File(ctx.filesDir, "notes").listFiles()!!.map { it.readText() }.sorted()
        assertEquals(listOf("one", "two"), files)
        assertTrue(m2.isNotEmpty())
    }
}
