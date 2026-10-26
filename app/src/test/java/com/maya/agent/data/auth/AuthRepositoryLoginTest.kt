package com.maya.agent.data.auth

import android.content.Context
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

/** The real request the app sends must be JSON, because GoTrue answers a form body with 400 bad_json. */
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryLoginTest {
    private fun repo(sink: MutableList<Pair<Request, String>>, code: Int, body: String) =
        AuthRepository(ApplicationProvider.getApplicationContext<Context>(),
            OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                val buf = okio.Buffer(); chain.request().body?.writeTo(buf)
                sink += chain.request() to buf.readUtf8()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }).build(), "https://proj.supabase.co", "anon")

    @Test fun signInSendsJsonToPasswordGrant() = runTest {
        val sink = mutableListOf<Pair<Request, String>>()
        repo(sink, 400, """{"error_code":"invalid_credentials"}""").signInWithEmail("  a@b.co ", "p\"w&x=1")
        val (req, body) = sink.single()
        assertTrue(req.url.toString().endsWith("/auth/v1/token?grant_type=password"))
        assertEquals("POST", req.method)
        assertTrue("content-type was ${req.body?.contentType()}", req.body?.contentType().toString().startsWith("application/json"))
        val o = com.google.gson.JsonParser.parseString(body).asJsonObject
        assertEquals("a@b.co", o.get("email").asString)
        assertEquals("p\"w&x=1", o.get("password").asString)
    }
    @Test fun invalidCredentialsMapsToWrongPasswordMessage() = runTest {
        val r = repo(mutableListOf(), 400, """{"error_code":"invalid_credentials","msg":"x"}""").signInWithEmail("a@b.co", "pw")
        assertEquals("Check your email or password", (r as AuthRepository.Result.Failure).message)
    }
    @Test fun badJsonIsNotReportedAsWrongPassword() = runTest {
        val r = repo(mutableListOf(), 400, """{"error_code":"bad_json"}""").signInWithEmail("a@b.co", "pw")
        val m = (r as AuthRepository.Result.Failure).message
        assertFalse(m.startsWith("Check your email or password"))
        assertTrue(m.contains("bad_json"))
    }
    @Test fun noPasswordInErrorText() = runTest {
        val r = repo(mutableListOf(), 400, """{"error_code":"x"}""").signInWithEmail("a@b.co", "SecretPw123")
        assertFalse((r as AuthRepository.Result.Failure).message.contains("SecretPw123"))
    }
}
