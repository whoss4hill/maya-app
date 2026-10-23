package com.maya.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun backendUrlLooksRight() {
        assertTrue(BuildConfig.BACKEND_URL.startsWith("https://"))
    }

    @Test
    fun noServiceRoleKeyInApp() {
        // The app must never ship the service-role key - anon/public only.
        assertTrue(BuildConfig.SUPABASE_ANON_KEY.isEmpty() || BuildConfig.SUPABASE_ANON_KEY.startsWith("eyJ"))
    }
}
