package com.maya.agent.data.auth

/**
 * Pure parser for the maya://auth fragment Supabase appends. A recovery link (type=recovery) is NOT an
 * ordinary sign-in: it only authorises setting a new password, so the token is kept in memory and never
 * written to the stored session. Tokens are never logged or put in error text.
 */
object RecoveryLink {
    sealed class Outcome {
        data class Recovery(val accessToken: String, val expiresAtMs: Long) : Outcome()
        data class SignIn(val params: Map<String, String>) : Outcome()
        data class LinkError(val message: String) : Outcome()
        data object Ignore : Outcome()
    }

    fun params(fragment: String?): Map<String, String> =
        (fragment ?: "").split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) java.net.URLDecoder.decode(it.substring(0, i), "UTF-8") to java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8") else null
        }.toMap()

    fun parse(fragment: String?, query: String? = null, nowMs: Long = System.currentTimeMillis()): Outcome {
        val p = params(fragment) + params(query)
        if (p.isEmpty()) return Outcome.Ignore
        if (p.containsKey("error") || p.containsKey("error_code")) {
            val code = p["error_code"] ?: p["error"] ?: ""
            val msg = if (code.contains("expired") || code.contains("access_denied"))
                "That reset link has expired or was already used. Ask for a new one." else "The reset link did not work. Ask for a new one."
            return Outcome.LinkError(msg)
        }
        val token = p["access_token"] ?: return Outcome.Ignore
        if (p["type"] == "recovery") {
            val exp = p["expires_at"]?.toLongOrNull()?.let { it * 1000 }
                ?: p["expires_in"]?.toLongOrNull()?.let { nowMs + it * 1000 }
                ?: return Outcome.LinkError("The reset link did not work. Ask for a new one.")
            if (exp <= nowMs) return Outcome.LinkError("That reset link has expired. Ask for a new one.")
            return Outcome.Recovery(token, exp)
        }
        return Outcome.SignIn(p)
    }

    fun maskEmail(email: String): String {
        val at = email.indexOf('@'); if (at < 1) return "your account"
        return email[0] + "***" + email.substring(at)
    }

    const val MIN_PASSWORD = 8
}
