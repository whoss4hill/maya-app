package com.maya.agent.data.auth

/**
 * Pure, strict parser for what arrives on maya://auth. Everything in the URL is untrusted.
 * - Password recovery is accepted ONLY as a PKCE "code" (query) that the app can redeem with the verifier it
 *   generated when it asked for the reset email (see AuthRepository). A token in a URL fragment that carries any
 *   "type" (recovery, magiclink, signup, ...) is ignored: presence of type=recovery proves nothing.
 * - An ordinary sign-in fragment (Google OAuth) must have no "type", a JWT-shaped access token, and a sane expiry.
 * - Duplicate or malformed parameters are rejected. error_description and tokens are never copied into messages.
 */
object RecoveryLink {
    sealed class Outcome {
        data class Code(val code: String) : Outcome()
        data class SignIn(val accessToken: String, val refreshToken: String?, val expiresAt: Long?, val expiresIn: Long?) : Outcome()
        data class LinkError(val message: String) : Outcome()
        data object Ignore : Outcome()
    }

    private val JWT = Regex("^[A-Za-z0-9_-]{8,4096}\\.[A-Za-z0-9_-]{8,4096}\\.[A-Za-z0-9_-]{4,4096}$")
    private val CODE = Regex("^[A-Za-z0-9._~-]{8,512}$")
    private const val MAX_EXPIRES_S = 7L * 24 * 3600

    /** null = malformed or duplicate keys. */
    fun params(raw: String?): Map<String, String>? {
        if (raw.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in raw.split("&")) {
            if (part.isEmpty()) continue
            val i = part.indexOf('=')
            if (i <= 0) return null
            val k = try { java.net.URLDecoder.decode(part.substring(0, i), "UTF-8") } catch (e: Exception) { return null }
            val v = try { java.net.URLDecoder.decode(part.substring(i + 1), "UTF-8") } catch (e: Exception) { return null }
            if (out.put(k, v) != null) return null
        }
        return out
    }

    fun parse(fragment: String?, query: String? = null, nowMs: Long = System.currentTimeMillis()): Outcome {
        val f = params(fragment); val q = params(query)
        if (f == null || q == null) return Outcome.Ignore
        if (f.isEmpty() && q.isEmpty()) return Outcome.Ignore
        if (f.containsKey("error") || f.containsKey("error_code") || q.containsKey("error") || q.containsKey("error_code")) {
            val code = (q["error_code"] ?: q["error"] ?: f["error_code"] ?: f["error"] ?: "")
            return Outcome.LinkError(
                if (code.contains("expired") || code.contains("access_denied")) "That reset link has expired or was already used. Ask for a new one."
                else "The reset link did not work. Ask for a new one."
            )
        }
        val code = q["code"]
        if (code != null) {
            if (f.isNotEmpty() || !CODE.matches(code)) return Outcome.Ignore
            return Outcome.Code(code)
        }
        val token = f["access_token"] ?: return Outcome.Ignore
        if (f.containsKey("type") || !JWT.matches(token)) return Outcome.Ignore
        val ein = f["expires_in"]?.toLongOrNull(); val eat = f["expires_at"]?.toLongOrNull()
        if (ein != null && (ein <= 0 || ein > MAX_EXPIRES_S)) return Outcome.Ignore
        if (eat != null && (eat * 1000 <= nowMs || eat * 1000 > nowMs + MAX_EXPIRES_S * 1000)) return Outcome.Ignore
        if (ein == null && eat == null) return Outcome.Ignore
        val refresh = f["refresh_token"]?.takeIf { it.length in 4..512 }
        return Outcome.SignIn(token, refresh, eat, ein)
    }

    /** Exact app callback only: maya://auth (optional single slash), no userinfo, port or extra path. */
    fun isExactCallback(scheme: String?, host: String?, port: Int, path: String?, userInfo: String?): Boolean =
        scheme == "maya" && host == "auth" && port == -1 && userInfo == null && (path.isNullOrEmpty() || path == "/")

    fun maskEmail(email: String): String {
        val at = email.indexOf('@'); if (at < 1) return "your account"
        return email[0] + "***" + email.substring(at)
    }

    const val MIN_PASSWORD = 8
}
