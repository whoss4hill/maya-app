package com.maya.agent.data.telegram

import com.maya.agent.data.auth.AuthRepository
import com.maya.agent.data.backend.BackendClient
import com.maya.agent.data.backend.PairStartRequest
import com.maya.agent.data.backend.TelegramStatus
import com.maya.agent.data.backend.ToggleRequest

/** What the Settings card shows. Never holds a token; only the masked chat suffix the backend returns. */
data class TelegramUi(val bound: Boolean, val enabled: Boolean, val maskedChat: String?)

sealed class TgResult<out T> {
    data class Ok<T>(val value: T) : TgResult<T>()
    data class Err(val message: String) : TgResult<Nothing>()
}

object ChatIdInput {
    /** Chat ID is only a pairing target. Accept digits only (positive Telegram user id); anything else is rejected before any request. */
    fun parse(raw: String): Long? {
        val t = raw.trim()
        if (t.isEmpty() || t.length > 15 || !t.all { it in '0'..'9' }) return null
        val v = t.toLongOrNull() ?: return null
        return if (v > 0) v else null
    }
}

fun TelegramStatus.toUi() = TelegramUi(bound, enabled, bound_suffix?.let { "\u2022\u2022\u2022\u2022$it" })

/** Owner-authenticated calls to the Maya backend. The bot token never reaches the app. */
class TelegramControl(private val auth: AuthRepository) {
    private suspend fun bearer(): String? = auth.validAccessToken()?.let { "Bearer $it" }

    suspend fun status(): TgResult<TelegramUi> = call { b -> BackendClient.api.telegramStatus(b).toUi() }

    /** Returns the one-time code the owner must send to the bot FROM that chat. Pairing completes only on the Telegram side. */
    suspend fun startPair(chatId: Long?): TgResult<String> = call { b -> BackendClient.api.telegramPairStart(b, PairStartRequest(chatId)).code }

    suspend fun setEnabled(on: Boolean): TgResult<Unit> = call { b ->
        val r = BackendClient.api.telegramToggle(b, ToggleRequest(on)); if (!r.isSuccessful) error("http ${r.code()}")
    }

    suspend fun unpair(): TgResult<Unit> = call { b ->
        val r = BackendClient.api.telegramRevoke(b); if (!r.isSuccessful) error("http ${r.code()}")
    }

    private suspend fun <T> call(block: suspend (String) -> T): TgResult<T> {
        val b = bearer() ?: return TgResult.Err("Sign in first")
        return try { TgResult.Ok(block(b)) } catch (e: Exception) { TgResult.Err("Could not reach Maya. Try again.") }
    }
}
