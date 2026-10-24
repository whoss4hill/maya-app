package com.maya.agent.voice.live

import com.google.gson.Gson
import com.maya.agent.data.backend.LiveTokenResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTokenResponseTest {
    @Test
    fun `parses backend mint response shape`() {
        // Shape deployed 19:13 on maya-backend-staging (commit 5abef0e).
        val json = """{"token":"auth_tokens/abc","expire_time":"2026-10-24T14:00:00Z","model":"models/gemini-3.8-live","ws_url":"wss://generativelanguage.googleapis.com/ws/x?access_token=auth_tokens/abc","note":"fresh token per session"}"""
        val r = Gson().fromJson(json, LiveTokenResponse::class.java)
        assertEquals("auth_tokens/abc", r.token)
        assertEquals("models/gemini-3.8-live", r.model)
        assertEquals("wss://generativelanguage.googleapis.com/ws/x?access_token=auth_tokens/abc", r.ws_url)
        assertEquals("2026-10-24T14:00:00Z", r.expire_time)
    }
}
