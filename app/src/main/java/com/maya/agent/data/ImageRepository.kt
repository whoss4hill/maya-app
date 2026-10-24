package com.maya.agent.data

import com.maya.agent.data.backend.BackendApi
import com.maya.agent.data.backend.BackendClient
import com.maya.agent.data.backend.ImageRequest

/**
 * Fetch for EXPLICIT user image requests only (the future approved image
 * bubble/button calls this). Never call from passive paths, never fire on
 * ordinary chat text: the prompt goes to the backend image provider.
 * Decoding is validated by ImageDecode (mime, size, JPEG magic).
 */
class ImageRepository(
    private val api: BackendApi = BackendClient.api,
    private val token: suspend () -> String?,
) {
    sealed class Result {
        data class Ok(val jpeg: ByteArray, val model: String?) : Result() {
            override fun equals(other: Any?) = other is Ok && jpeg.contentEquals(other.jpeg) && model == other.model
            override fun hashCode() = jpeg.contentHashCode()
        }
        data class Err(val reason: String) : Result()
        data object SignedOut : Result()
    }

    suspend fun fetch(prompt: String): Result {
        if (prompt.isBlank()) return Result.Err("Say what image to make")
        val t = token() ?: return Result.SignedOut
        return try {
            val r = api.image("Bearer $t", ImageRequest(prompt))
            val bytes = ImageDecode.decode(r.image_base64, r.mime)
            if (bytes != null) Result.Ok(bytes, r.model)
            else Result.Err("The image service sent nothing usable. Try again.")
        } catch (e: Exception) {
            Result.Err("Could not reach the image service. Check your internet.")
        }
    }
}
