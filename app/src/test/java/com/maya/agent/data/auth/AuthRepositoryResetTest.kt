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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Request contract for the Forgot-password flow: the app must POST the email
 * as JSON to GoTrue's /auth/v1/recover with the anon key, and must only send
 * after the user tapped the control with an email typed (UI-side guarantee).
 * The email body must be valid JSON - no form-encoding, no leakage beyond the
 * typed address. Server-side enumeration behavior is Supabase's, not ours.
 */
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryResetTest {

    private fun captureClient(code: Int, sink: MutableList<Pair<Request, String>>) =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val buf = okio.Buffer(); chain.request().body?.writeTo(buf)
            sink += chain.request() to buf.readUtf8()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("x").body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }).build()

    @Test
    fun resetPostsEmailAsJsonToRecover() = runTest {
        val captured = mutableListOf<Pair<Request, String>>()
        val repo = AuthRepository(ApplicationProvider.getApplicationContext<Context>(), captureClient(200, captured),
            "https://proj.supabase.co", "test-anon-key")
        val result = repo.sendPasswordReset("owner@example.com")
        assertTrue("200 recover must surface Success", result is AuthRepository.Result.Success)
        assertEquals(1, captured.size)
        val (req, body) = captured.single()
        assertTrue("path must be GoTrue recover, was ${req.url}", req.url.encodedPath.endsWith("/auth/v1/recover"))
        assertEquals("POST", req.method)
        assertEquals("redirect must be the app deep link", "maya://auth", req.url.queryParameter("redirect_to"))
        assertEquals("anon apikey header required", "test-anon-key", req.header("apikey"))
        assertTrue("JSON body with the typed email, was: $body", body.contains("\"email\":\"owner@example.com\""))
        assertTrue("no password or extra fields in reset body", !body.contains("password"))
    }

    @Test
    fun resetFailureSurfacesHonestError() = runTest {
        val captured = mutableListOf<Pair<Request, String>>()
        val repo = AuthRepository(ApplicationProvider.getApplicationContext<Context>(), captureClient(500, captured),
            "https://proj.supabase.co", "test-anon-key")
        val result = repo.sendPasswordReset("owner@example.com")
        assertTrue(result is AuthRepository.Result.Failure)
        assertTrue((result as AuthRepository.Result.Failure).message.contains("500"))
    }
}
