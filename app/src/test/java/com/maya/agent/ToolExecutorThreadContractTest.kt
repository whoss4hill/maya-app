package com.maya.agent

import androidx.test.core.app.ApplicationProvider
import com.maya.agent.agent.MayaIntent
import com.maya.agent.agent.ToolExecutor
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Threading contract for ToolExecutor acks (regression pin):
 * the spoken callback must never land on a background thread - ChatStore
 * compose state is written from it.
 * - Sync local-tool paths ack on the caller thread.
 * (The signed-out chat path needs EncryptedSharedPreferences, which has no
 *  Robolectric 4.13 keystore shadow - covered by review, not this suite.)
 * - The async chat path must resume on Dispatchers.Main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolExecutorThreadContractTest {

    @Test
    fun localToolAcksSynchronously() {
        val executor = ToolExecutor(ApplicationProvider.getApplicationContext())
        var ack: String? = null
        executor.execute(MayaIntent.OpenApp("no-such-app-zzz-12345")) { ack = it }
        assertNotNull(ack)
        assertTrue(ack!!.contains("nahi mili"))
    }

    @Test
    fun asyncChatScopeIsMainDispatcher() {
        val executor = ToolExecutor(ApplicationProvider.getApplicationContext())
        val f = ToolExecutor::class.java.getDeclaredField("scope")
        f.isAccessible = true
        val scope = f.get(executor) as CoroutineScope
        assertEquals(Dispatchers.Main, scope.coroutineContext[ContinuationInterceptor])
    }
}
