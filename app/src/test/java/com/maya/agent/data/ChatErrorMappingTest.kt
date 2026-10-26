package com.maya.agent.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ChatErrorMappingTest {
    private fun http(code: Int, body: String) =
        HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))

    @Test fun serverErrorIsNotBlamedOnInternet() {
        val m = MemoryRepository.mapChatError(http(401, """{"error":"invalid_token"}""")).reason
        assertTrue(m, m.contains("401") && m.contains("invalid_token"))
        assertFalse(m.contains("Check your internet"))
        assertTrue(MemoryRepository.mapChatError(http(500, """{"error":"upstream"}""")).reason.contains("500, upstream"))
    }
    @Test fun nonJsonErrorBodyStillMapped() {
        val m = MemoryRepository.mapChatError(http(502, "<html>bad gateway</html>")).reason
        assertTrue(m.contains("502")); assertFalse(m.contains("html"))
    }
    @Test fun errorTagIsSanitized() {
        val m = MemoryRepository.mapChatError(http(400, """{"error":"Bearer abc def secret text here"}""")).reason
        assertFalse(m.contains("secret"))
    }
    @Test fun realNetworkFailureKeepsInternetMessage() {
        assertTrue(MemoryRepository.mapChatError(java.net.UnknownHostException("x")).reason.contains("Check your internet"))
        assertTrue(MemoryRepository.mapChatError(java.net.SocketTimeoutException()).reason.contains("waking up"))
    }
    @Test fun parseFailureIsLabelled() {
        assertTrue(MemoryRepository.mapChatError(com.google.gson.JsonSyntaxException("x")).reason.contains("could not be read"))
    }
}
