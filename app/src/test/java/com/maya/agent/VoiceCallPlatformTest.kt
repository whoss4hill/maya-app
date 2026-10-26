package com.maya.agent

import android.Manifest
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.maya.agent.calls.VoiceCall
import com.maya.agent.voice.live.LiveToolBridge
import com.maya.agent.voice.live.ReadbackSpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import android.os.Looper

/**
 * No-dial platform harness: real Context, real LiveToolBridge/VoiceCall/DialCommand/ReadbackSpeaker code, Robolectric's
 * shadow TTS and shadow activity launcher. Robolectric cannot place a phone call: ACTION_CALL is only captured as an
 * Intent. Proves ordering (no CALL intent before the app-owned readback + phrase), permission refusal, one-shot and reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoiceCallPlatformTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val args = "{\"target\":\"9876543210\"}"

    @Before fun clean() { ReadbackSpeaker.resetForTest(); VoiceCall.confirmation.reset(); VoiceCall.confirmation.forgetSpeech(); ShadowTextToSpeech.clearLastSpokenText() }

    private fun listenerAndId(): Pair<android.speech.tts.UtteranceProgressListener, String> {
        val f = ReadbackSpeaker::class.java.getDeclaredField("listener").apply { isAccessible = true }
        val a = ReadbackSpeaker::class.java.getDeclaredField("active").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val ids = (a.get(ReadbackSpeaker) as HashMap<String, *>).keys
        assertEquals(1, ids.size)
        return (f.get(ReadbackSpeaker) as android.speech.tts.UtteranceProgressListener) to ids.first()
    }

    private fun nextCallIntent(): Intent? = Shadows.shadowOf(app).nextStartedActivity

    @Test fun `no call intent until app readback finished and owner says the phrase, one-shot`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        val now = System.currentTimeMillis()
        VoiceCall.confirmation.onOwnerTranscript("Maya call karo", now)
        val out = LiveToolBridge.callContactResult(args, LiveToolBridge.callDeps(ctx))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(out, out.contains("Nothing was dialled yet"))
        assertNull(nextCallIntent())                                                   // tool alone: no call
        val spoken = ShadowTextToSpeech.getLastSpokenText()
        assertTrue(spoken, spoken.contains("9 8 7 6 5 4 3 2 1 0"))                     // app speaks exact digits itself
        assertTrue(VoiceCall.confirmPending(LiveToolBridge.callDeps(ctx), now = now + 100).text.contains("not finished"))
        assertNull(nextCallIntent())
        val (l, id) = listenerAndId(); l.onDone(id)                                    // TTS finished
        val done = System.currentTimeMillis()
        VoiceCall.confirmation.onOwnerTranscript("haan", done + 2000)                   // bare haan
        assertTrue(VoiceCall.confirmPending(LiveToolBridge.callDeps(ctx), now = done + 2100).text.contains("not a clear yes"))
        assertNull(nextCallIntent())
        VoiceCall.confirmation.onOwnerTranscript("haan call lagao", done + 2200)
        val r = VoiceCall.confirmPending(LiveToolBridge.callDeps(ctx), now = done + 2300)
        assertEquals("intent_launched", r.status)
        val i = nextCallIntent(); assertNotNull(i)
        assertEquals(Intent.ACTION_CALL, i!!.action); assertEquals("tel:9876543210", i.data.toString())
        assertEquals("no_pending", VoiceCall.confirmPending(LiveToolBridge.callDeps(ctx), now = done + 2400).status)
        assertNull(nextCallIntent())                                                   // replay: no second intent
    }

    @Test fun `without CALL_PHONE nothing is spoken or launched`() {
        VoiceCall.confirmation.onOwnerTranscript("Maya call karo", System.currentTimeMillis())
        val out = LiveToolBridge.callContactResult(args, LiveToolBridge.callDeps(ctx))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue(out, out.contains("permission is not granted"))
        assertNull(nextCallIntent()); assertEquals("", ShadowTextToSpeech.getLastSpokenText() ?: "")
    }

    @Test fun `no spoken call request means dialer only, never ACTION_CALL`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        val out = LiveToolBridge.callContactResult(args, LiveToolBridge.callDeps(ctx))
        assertTrue(out, out.contains("opened the dialer"))
        val i = nextCallIntent(); assertNotNull(i); assertEquals(Intent.ACTION_DIAL, i!!.action)
    }

    @Test fun `reset clears a pending confirmation`() {
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        VoiceCall.confirmation.onOwnerTranscript("Maya call karo", System.currentTimeMillis())
        LiveToolBridge.callContactResult(args, LiveToolBridge.callDeps(ctx))
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        VoiceCall.confirmation.reset()
        assertEquals("no_pending", VoiceCall.confirmPending(LiveToolBridge.callDeps(ctx)).status)
        assertNull(nextCallIntent())
    }
}
