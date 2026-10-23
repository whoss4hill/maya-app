package com.maya.agent.data.auth

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TokenRefresherTest {
    private class MemStore(var a: String?, var r: String?, var e: Long) : TokenStore {
        override fun accessToken() = a
        override fun refreshToken() = r
        override fun expiresAt() = e
        override fun save(access: String, refresh: String?, expiresAt: Long) { a = access; r = refresh; e = expiresAt }
        override fun clear() { a = null; r = null; e = 0 }
    }

    private val now = 1_000_000L
    private val calls = AtomicInteger()
    private val bodies = mutableListOf<String>()
    private val urls = mutableListOf<String>()

    private fun client(code: Int = 200, body: String = "", fail: Boolean = false) = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            calls.incrementAndGet()
            val buf = okio.Buffer(); chain.request().body?.writeTo(buf)
            synchronized(bodies) { bodies += buf.readUtf8(); urls += chain.request().url.toString() }
            if (fail) throw IOException("offline")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    private fun refresher(c: OkHttpClient, s: TokenStore) = TokenRefresher(c, "https://p.supabase.co", "anon", s) { now }

    @Test fun freshTokenIsNotRefreshed() {
        val s = MemStore("A1", "R1", now + 3600)
        assertEquals("A1", refresher(client(), s).validAccessToken())
        assertEquals(0, calls.get())
    }

    @Test fun expiredTokenRefreshesAndRotates() {
        val s = MemStore("A1", "R1", now - 5)
        val t = refresher(client(200, """{"access_token":"A2","refresh_token":"R2","expires_in":3600}"""), s).validAccessToken()
        assertEquals("A2", t); assertEquals("A2", s.a); assertEquals("R2", s.r); assertEquals(now + 3600, s.e)
        assertTrue(urls[0].endsWith("/auth/v1/token?grant_type=refresh_token"))
        assertTrue(bodies[0].contains("\"refresh_token\":\"R1\""))
    }

    @Test fun rejectedRefreshSignsOut() {
        for (code in listOf(400, 401, 403)) {
            val s = MemStore("A1", "R1", now - 5)
            assertNull(refresher(client(code, "{}"), s).validAccessToken()); assertNull(s.a)
        }
    }

    @Test fun serverErrorKeepsSession() {
        val s = MemStore("A1", "R1", now - 5)
        assertEquals("A1", refresher(client(503, "x"), s).validAccessToken()); assertEquals("R1", s.r)
    }

    @Test fun offlineKeepsSession() {
        val s = MemStore("A1", "R1", now - 5)
        assertEquals("A1", refresher(client(fail = true), s).validAccessToken()); assertEquals("R1", s.r)
    }

    @Test fun signedOutReturnsNullWithoutNetwork() {
        assertNull(refresher(client(), MemStore(null, null, 0)).validAccessToken()); assertEquals(0, calls.get())
    }

    @Test fun concurrentCallsRefreshOnce() {
        val s = MemStore("A1", "R1", now - 5)
        val r = refresher(client(200, """{"access_token":"A2","refresh_token":"R2","expires_in":3600}"""), s)
        val pool = Executors.newFixedThreadPool(8)
        val results = (1..8).map { pool.submit<String?> { r.validAccessToken() } }.map { it.get() }
        pool.shutdown()
        assertEquals(1, calls.get()); assertTrue(results.all { it == "A2" })
    }
}
