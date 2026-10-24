package com.maya.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.maya.agent.service.MayaVoiceInteractionSessionService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Default-assistant support is a real registration, not a placeholder:
 * the manifest must expose the VoiceInteractionService with its session and
 * recognition stubs wired, and the assistant gesture must land in the
 * existing wake flow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultAssistantRegistrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun voiceInteractionServiceIsRegisteredWithMetadata() {
        val pm = context.packageManager
        val services = pm.queryIntentServices(
            Intent("android.service.voice.VoiceInteractionService"),
            PackageManager.GET_META_DATA,
        )
        val vis = services.firstOrNull { it.serviceInfo.name == "com.maya.agent.service.MayaVoiceInteractionService" }
        assertTrue("MayaVoiceInteractionService must be registered", vis != null)
        assertTrue("must be protected by the system bind permission",
            vis!!.serviceInfo.permission == "android.permission.BIND_VOICE_INTERACTION_SERVICE")
        val meta = vis.serviceInfo.metaData
        assertTrue("voice_interaction_service metadata must be present",
            meta != null && meta.containsKey("android.voice_interaction_service"))
    }

    @Test
    fun recognitionServiceIsRegisteredAndPermissionProtected() {
        val pm = context.packageManager
        val services = pm.queryIntentServices(
            Intent("android.speech.RecognitionService"), 0,
        )
        val rec = services.firstOrNull { it.serviceInfo.name == "com.maya.agent.service.MayaRecognitionService" }
        assertTrue("MayaRecognitionService stub must be registered", rec != null)
        assertEquals("android.permission.BIND_RECOGNITION_SERVICE", rec!!.serviceInfo.permission)
    }

    @Test
    fun assistantGestureLaunchesExistingWakeFlow() {
        val intent = MayaVoiceInteractionSessionService.buildAssistantLaunchIntent(context)
        assertEquals(MainActivity.ACTION_WAKE, intent.action)
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }
}
