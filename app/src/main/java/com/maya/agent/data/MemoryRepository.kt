package com.maya.agent.data

import com.maya.agent.data.auth.AuthRepository
import com.maya.agent.data.backend.BackendClient
import com.maya.agent.data.backend.ChatRequest
import com.maya.agent.data.backend.MemoryCreate
import com.maya.agent.data.backend.MemoryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chat + long-term memory, all via the Maya backend (never direct to DB/Gemini). */
class MemoryRepository(private val auth: AuthRepository) {

    private val api = BackendClient.api

    sealed class Reply {
        data class Ok(val text: String) : Reply()
        /** Backend sleeping / down / quota - say which honestly. */
        data class Failed(val reason: String) : Reply()
        data object SignedOut : Reply()
    }

    suspend fun chat(message: String): Reply = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext Reply.SignedOut
        try {
            val res = api.chat("Bearer $token", ChatRequest(message))
            Reply.Ok(res.reply ?: "...")
        } catch (e: Exception) {
            mapChatError(e)
        }
    }

    companion object {
        /** Tells apart no-network, timeout, and a server that answered with an error. Never includes tokens or message text. */
        fun mapChatError(e: Throwable): Reply.Failed = when (e) {
            is java.net.SocketTimeoutException ->
                Reply.Failed("The server is waking up and will take a moment. Please send again.")
            is retrofit2.HttpException -> {
                val code = e.code()
                val tag = try {
                    val raw = e.response()?.errorBody()?.string().orEmpty()
                    val v = com.google.gson.JsonParser.parseString(raw).asJsonObject.get("error")
                    if (v != null && v.isJsonPrimitive) v.asString.takeIf { Regex("^[A-Za-z0-9_.-]{1,40}$").matches(it) } else null
                } catch (_: Exception) { null }
                val label = "$code" + (tag?.let { ", $it" } ?: "")
                if (code == 401 || code == 403) Reply.Failed("The server refused this request ($label). The server was reached, so this is not your internet.")
                else Reply.Failed("The server answered with an error ($label). The server was reached, so this is not your internet.")
            }
            is java.net.UnknownHostException, is java.net.ConnectException, is javax.net.ssl.SSLException ->
                Reply.Failed("Could not reach the server. Check your internet; offline tasks keep working.")
            is com.google.gson.JsonParseException, is IllegalStateException ->
                Reply.Failed("The server's reply could not be read (unexpected format). The server was reached.")
            else -> Reply.Failed("Chat failed (${e.javaClass.simpleName}). Try again.")
        }
    }

    suspend fun memories(): List<MemoryDto> = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext emptyList()
        try { api.memories("Bearer $token").memories } catch (e: Exception) { emptyList() }
    }

    /** Returns null on success/failure-without-detail, or a user message when the server refuses the content as a secret. */
    suspend fun addMemory(kind: String, content: String): String? = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext null
        try { api.addMemory("Bearer $token", MemoryCreate(kind, content)); null }
        catch (e: retrofit2.HttpException) {
            if (e.code() == 422) "That looks like a password or secret, so it was not saved. Keep secrets in the Vault." else null
        } catch (_: Exception) { null }
    }

    suspend fun deleteMemory(id: String) = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext
        try { api.deleteMemory("Bearer $token", id) } catch (_: Exception) {}
    }
}
