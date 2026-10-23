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
        } catch (e: java.net.SocketTimeoutException) {
            Reply.Failed("Server jaag raha hai, thoda time lagega. Ek baar aur bhejo.")
        } catch (e: Exception) {
            Reply.Failed("Server tak pahunch nahi paayi. Internet check karo; offline kaam chalta rahega.")
        }
    }

    suspend fun memories(): List<MemoryDto> = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext emptyList()
        try { api.memories("Bearer $token").memories } catch (e: Exception) { emptyList() }
    }

    suspend fun addMemory(kind: String, content: String) = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext
        try { api.addMemory("Bearer $token", MemoryCreate(kind, content)) } catch (_: Exception) {}
    }

    suspend fun deleteMemory(id: String) = withContext(Dispatchers.IO) {
        val token = auth.validAccessToken() ?: return@withContext
        try { api.deleteMemory("Bearer $token", id) } catch (_: Exception) {}
    }
}
