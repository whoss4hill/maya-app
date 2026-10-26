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

    @Test fun `recovery fragment is a recovery outcome not a sign-in`() {
        val o = RecoveryLink.parse("access_token=TOK&refresh_token=R&type=recovery&expires_in=3600", null, now)
        assertTrue(o is RecoveryLink.Outcome.Recovery)
        assertEquals(now + 3_600_000, (o as RecoveryLink.Outcome.Recovery).expiresAtMs)
    }
    @Test fun `google style fragment is an ordinary sign-in`() {
        assertTrue(RecoveryLink.parse("access_token=TOK&refresh_token=R&expires_in=3600&token_type=bearer", null, now) is RecoveryLink.Outcome.SignIn)
    }
    @Test fun `expired or errored link gives a friendly error without token text`() {
        val e1 = RecoveryLink.parse("error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid", null, now) as RecoveryLink.Outcome.LinkError
        assertTrue(e1.message.contains("expired"))
        val e2 = RecoveryLink.parse("access_token=TOK&type=recovery&expires_at=${now / 1000 - 5}", null, now) as RecoveryLink.Outcome.LinkError
        assertFalse(e2.message.contains("TOK"))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse(null, null, now))
        assertEquals(RecoveryLink.Outcome.Ignore, RecoveryLink.parse("foo=bar", null, now))
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
        AuthRepository(ApplicationProvider.getApplicationContext<Context>(), client(sink, code, body), "https://proj.supabase.co", "anon")

    @Test fun `recovery link does not sign the user in and updates password only on 2xx`() = runTest {
        val sink = mutableListOf<Pair<Request, String>>()
        val r = repo(sink, 200, "{\"email\":\"owner@example.com\"}")
        val res = r.handleAuthRedirect(Uri.parse("maya://auth#access_token=TOK&type=recovery&expires_in=3600"))
        assertEquals(AuthRepository.Redirect.RECOVERY, res)
        assertFalse("recovery must not create a signed-in session", r.isSignedIn)
        assertTrue(r.recoveryPending)
        assertEquals("o***@example.com", r.verifyRecoverySession().getOrNull())
        assertEquals("Bearer TOK", sink[0].first.header("Authorization")); assertEquals("GET", sink[0].first.method)
        assertTrue(r.setNewPassword("longenough1") is AuthRepository.Result.Success)
        val (put, body) = sink[1]
        assertEquals("PUT", put.method); assertTrue(put.url.encodedPath.endsWith("/auth/v1/user"))
        assertTrue(body.contains("\"password\":\"longenough1\""))
        assertFalse("token dropped after success", r.recoveryPending)
        assertTrue(r.setNewPassword("longenough1") is AuthRepository.Result.Failure)
        assertEquals("second attempt sends nothing", 2, sink.size)
    }
    @Test fun `failed update keeps honest error and weak or short password never sent`() = runTest {
        val sink = mutableListOf<Pair<Request, String>>()
        val r = repo(sink, 422)
        r.handleAuthRedirect(Uri.parse("maya://auth#access_token=TOK&type=recovery&expires_in=3600"))
        assertTrue(r.setNewPassword("short") is AuthRepository.Result.Failure); assertEquals(0, sink.size)
        val f = r.setNewPassword("longenough1") as AuthRepository.Result.Failure
        assertTrue(f.message.contains("not accepted"))
        assertTrue("still pending after a 422 so he can retry", r.recoveryPending)
    }
    @Test fun `cancel and sign out clear the recovery token`() = runTest {
        val sink = mutableListOf<Pair<Request, String>>()
        val r = repo(sink, 200)
        r.handleAuthRedirect(Uri.parse("maya://auth#access_token=TOK&type=recovery&expires_in=3600"))
        r.cancelRecovery(); assertFalse(r.recoveryPending)
        assertTrue(r.setNewPassword("longenough1") is AuthRepository.Result.Failure); assertEquals(0, sink.size)
        r.handleAuthRedirect(Uri.parse("maya://auth#access_token=TOK&type=recovery&expires_in=3600"))
        r.signOut(); assertFalse(r.recoveryPending)
    }
    @Test fun `ordinary google redirect still signs in`() {
        val r = repo(mutableListOf(), 200)
        assertEquals(AuthRepository.Redirect.SIGNED_IN, r.handleAuthRedirect(Uri.parse("maya://auth#access_token=T2&refresh_token=R2&expires_in=3600")))
        assertTrue(r.isSignedIn)
    }
}
