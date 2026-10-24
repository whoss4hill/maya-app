package com.maya.agent

import com.google.gson.Gson
import com.maya.agent.data.ImageRepository
import com.maya.agent.data.backend.BackendApi
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.test.runTest

/**
 * Image fetch contract: bearer header sent, prompt only in the explicit call,
 * decode validation applied, honest failures. No shipped call site exists yet
 * (display bubble awaits owner approval) - this pins the data layer.
 */
class ImageRepositoryTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 9, 9)
    private val b64 = Base64.getEncoder().encodeToString(jpeg)

    private fun apiResponding(code: Int, body: String, seenAuth: AtomicReference<String?>, seenBody: AtomicReference<String?>): BackendApi {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            seenAuth.set(chain.request().header("Authorization"))
            val buf = okio.Buffer(); chain.request().body?.writeTo(buf); seenBody.set(buf.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        return Retrofit.Builder().baseUrl("https://unit.test/")
            .client(client).addConverterFactory(GsonConverterFactory.create(Gson()))
            .build().create(BackendApi::class.java)
    }

    @Test fun explicitFetchSendsAuthAndPromptAndDecodes() = runTest {
        val auth = AtomicReference<String?>(); val body = AtomicReference<String?>()
        val repo = ImageRepository(apiResponding(200, "{\"image_base64\":\"$b64\",\"mime\":\"image/jpeg\",\"model\":\"m1\"}", auth, body)) { "tok" }
        val r = repo.fetch("a red kite") as ImageRepository.Result.Ok
        assertTrue(jpeg.contentEquals(r.jpeg)); assertEquals("m1", r.model)
        assertEquals("Bearer tok", auth.get()); assertTrue(body.get()!!.contains("a red kite"))
    }

    @Test fun signedOutNeverCallsApi() = runTest {
        val auth = AtomicReference<String?>(null); val body = AtomicReference<String?>(null)
        val repo = ImageRepository(apiResponding(200, "{}", auth, body)) { null }
        assertEquals(ImageRepository.Result.SignedOut, repo.fetch("a kite"))
        assertTrue(auth.get() == null && body.get() == null)
    }

    @Test fun unusablePayloadIsHonestError() = runTest {
        val repo = ImageRepository(apiResponding(200, "{\"image_base64\":\"%%%%\",\"mime\":\"image/jpeg\",\"model\":\"m\"}", AtomicReference(), AtomicReference())) { "tok" }
        assertTrue(repo.fetch("x") is ImageRepository.Result.Err)
    }

    @Test fun httpFailureIsHonestError() = runTest {
        val repo = ImageRepository(apiResponding(500, "{}", AtomicReference(), AtomicReference())) { "tok" }
        assertTrue(repo.fetch("x") is ImageRepository.Result.Err)
    }

    @Test fun blankPromptNeverCallsApi() = runTest {
        val auth = AtomicReference<String?>(null)
        val repo = ImageRepository(apiResponding(200, "{}", auth, AtomicReference())) { "tok" }
        assertTrue(repo.fetch("   ") is ImageRepository.Result.Err)
        assertTrue(auth.get() == null)
    }
}
