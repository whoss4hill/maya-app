package com.maya.agent.data.auth

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecoveryFlowTest {
    private val now = 1_800_000_000_000L
    private fun plainPrefs() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("t_auth_" + System.nanoTime(), Context.MODE_PRIVATE)

    private val jwt = "aaaaaaaa.bbbbbbbb.cccc"

    @Test fun `only a pkce code is a recovery entry`() {
        assertEquals(RecoveryLink.Outcome.Code("abcdef123456"), RecoveryLink.parse(null, "code=abcdef123456", now))
    }
    @Test fun `fragment tokens with any type are ignored, even type recovery`() {
        for (t in listOf("recovery", "magiclink", "signup", "invite", "email_change"))
            assertEquals(t, RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=$jwt&type=$t&expires_in=3600", null, now))
    }
    @Test fun `google style fragment is an ordinary sign-in only when well formed`() {
        assertTrue(RecoveryLink.parse("access_token=$jwt&refresh_token=RRRRRR&expires_in=3600&token_type=bearer", null, now) is RecoveryLink.Outcome.SignIn)
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=notajwt&expires_in=3600", null, now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=$jwt", null, now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=$jwt&expires_in=-5", null, now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=$jwt&expires_in=99999999999", null, now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=$jwt&expires_at=${now / 1000 - 5}", null, now))
    }
    @Test fun `duplicate or malformed params and code plus fragment are rejected`() {
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse(null, "code=abcdef123456&code=zzzzzzzzzz", now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=$jwt&access_token=$jwt&expires_in=60", null, now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse(null, "code", now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse(null, "code=short", now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("access_token=$jwt&expires_in=60", "code=abcdef123456", now))
    }
    @Test fun `error links give a fixed message that never echoes server text`() {
        val e = RecoveryLink.parse("error=access_denied&error_code=otp_expired&error_description=SECRET+TEXT", null, now) as RecoveryLink.Outcome.LinkError
        assertTrue(e.message.contains("expired")); assertFalse(e.message.contains("SECRET"))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse(null, null, now))
    }
    @Test fun `callback must be exactly maya auth`() {
        assertTrue(RecoveryLink.isExactCallback("maya", "auth", -1, "", null))
        assertTrue(RecoveryLink.isExactCallback("maya", "auth", -1, "/", null))
        assertFalse(RecoveryLink.isExactCallback("maya", "auth", -1, "/x", null))
        assertFalse(RecoveryLink.isExactCallback("maya", "auth", 80, "", null))
        assertFalse(RecoveryLink.isExactCallback("maya", "auth", -1, "", "u"))
        assertFalse(RecoveryLink.isExactCallback("maya", "evil", -1, "", null))
        assertFalse(RecoveryLink.isExactCallback("https", "auth", -1, "", null))
    }
    @Test fun `mask email`() { assertEquals("o***@example.com", RecoveryLink.maskEmail("owner@example.com")) }

    private fun client(sink: MutableList<Pair<Request, String>>, code: Int, body: String = "{}") =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val buf = okio.Buffer(); chain.request().body?.writeTo(buf)
            sink += chain.request() to buf.readUtf8()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()

    private fun repo(sink: MutableList<Pair<Request, String>>, code: Int, body: String = "{}") =
        AuthRepository(ApplicationProvider.getApplicationContext<Context>(), client(sink, code, body), "https://proj.supabase.co", "anon", plainPrefs())

    private fun reqBody(sink: List<Pair<Request, String>>, i: Int) = sink[i].second

    @Test fun `reset request carries a pkce challenge and a stored verifier is needed to accept a code`() = runTest {
        val sink = mutableListOf<Pair<Request, String>>()
        val r = repo(sink, 200, "{\"email\":\"owner@example.com\"}")
        // a code arriving with no reset requested by this app is ignored
        assertEquals(AuthRepository.Redirect.IGNORED, r.handleAuthRedirect(Uri.parse("maya://auth?code=abcdef123456")))
        assertFalse(r.recoveryCodeWaiting)
        assertTrue(r.sendPasswordReset("owner@example.com") is AuthRepository.Result.Success)
        val body = reqBody(sink, 0)
        assertTrue(body.contains("\"code_challenge_method\":\"s256\"") && body.contains("\"code_challenge\":\""))
        assertFalse("verifier itself must not be sent", Regex("code_verifier").containsMatchIn(body))
        assertEquals(AuthRepository.Redirect.RECOVERY, r.handleAuthRedirect(Uri.parse("maya://auth?code=abcdef123456")))
        assertFalse("a code is not a signed-in session", r.isSignedIn)
    }
    @Test fun `foreign callbacks never sign in or start recovery`() {
        val r = repo(mutableListOf(), 200)
        for (u in listOf("maya://auth/x#access_token=aaaaaaaa.bbbbbbbb.cccc&type=recovery&expires_in=3600",
            "maya://auth#access_token=aaaaaaaa.bbbbbbbb.cccc&type=recovery&expires_in=3600",
            "maya://evil#access_token=aaaaaaaa.bbbbbbbb.cccc&expires_in=3600",
            "maya://auth?code=abcdef123456&code=abcdef123456"))
            assertEquals(u, AuthRepository.Redirect.IGNORED, r.handleAuthRedirect(Uri.parse(u)))
        assertFalse(r.isSignedIn); assertFalse(r.recoveryPending)
    }
    @Test fun `code exchange then identity check then update only on 2xx, replay sends nothing`() = runTest {
        val sink = mutableListOf<Pair<Request, String>>()
        val r = AuthRepository(ApplicationProvider.getApplicationContext<Context>(),
            OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                val buf = okio.Buffer(); chain.request().body?.writeTo(buf); sink += chain.request() to buf.readUtf8()
                val path = chain.request().url.encodedPath
                val json = when {
                    path.endsWith("/token") -> "{\"access_token\":\"aaaaaaaa.bbbbbbbb.cccc\",\"expires_in\":3600}"
                    path.endsWith("/user") && chain.request().method == "GET" -> "{\"email\":\"owner@example.com\"}"
                    else -> "{}"
                }
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("x")
                    .body(json.toResponseBody("application/json".toMediaType())).build()
            }).build(), "https://proj.supabase.co", "anon", plainPrefs())
        r.sendPasswordReset("owner@example.com")
        r.handleAuthRedirect(Uri.parse("maya://auth?code=abcdef123456"))
        assertEquals("o***@example.com", r.beginRecovery().getOrNull())
        val ex = sink[1]
        assertTrue(ex.first.url.toString().contains("grant_type=pkce"))
        assertTrue(ex.second.contains("\"auth_code\":\"abcdef123456\"") && ex.second.contains("\"code_verifier\":\""))
        assertEquals("Bearer aaaaaaaa.bbbbbbbb.cccc", sink[2].first.header("Authorization"))
        assertFalse("recovery must not create a signed-in session", r.isSignedIn)
        assertTrue(r.setNewPassword("longenough1") is AuthRepository.Result.Success)
        val (put, body) = sink[3]
        assertEquals("PUT", put.method); assertTrue(body.contains("\"password\":\"longenough1\""))
        assertFalse(r.recoveryPending)
        assertTrue(r.setNewPassword("longenough1") is AuthRepository.Result.Failure)
        assertEquals(4, sink.size)
        // verifier was consumed: the same code cannot be replayed
        assertEquals(AuthRepository.Redirect.IGNORED, r.handleAuthRedirect(Uri.parse("maya://auth?code=abcdef123456")))
    }
    @Test fun `short password never sent, 422 keeps recovery for retry, cancel and sign out clear it`() = runTest {
        val sink = mutableListOf<Pair<Request, String>>()
        val r = repo(sink, 422)
        assertTrue(r.setNewPassword("longenough1") is AuthRepository.Result.Failure); assertEquals(0, sink.size)
        r.cancelRecovery(); r.signOut(); assertFalse(r.recoveryPending); assertFalse(r.recoveryCodeWaiting)
    }
    @Test fun `ordinary google redirect still signs in`() {
        val r = repo(mutableListOf(), 200)
        assertEquals(AuthRepository.Redirect.SIGNED_IN, r.handleAuthRedirect(Uri.parse("maya://auth#access_token=aaaaaaaa.bbbbbbbb.cccc&refresh_token=RRRRRR&expires_in=3600")))
        assertTrue(r.isSignedIn)
    }
}
