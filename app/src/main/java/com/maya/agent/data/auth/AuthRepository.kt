package com.maya.agent.data.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.maya.agent.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit

/**
 * Supabase Auth (GoTrue) over plain REST - no SDK needed.
 * The app holds ONLY the anon key + the user's own session tokens.
 * Tokens live in EncryptedSharedPreferences, never in logs or chat.
 */
class AuthRepository(
    context: Context,
    httpClient: OkHttpClient? = null,
    private val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
) {

    companion object {
        /** Must be in the Supabase project's Redirect URLs allow-list (owner-verified), else Supabase falls back to its Site URL. */
        const val RECOVERY_REDIRECT = "maya://auth"
    }

    private val appContext = context.applicationContext

    private val http = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // backend cold starts
        .build()

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "maya_auth",
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val isSignedIn: Boolean get() = accessToken != null
    val accessToken: String? get() = prefs.getString("access_token", null)
    val userEmail: String? get() = prefs.getString("email", null)

    sealed class Result {
        data object Success : Result()
        data class Failure(val message: String) : Result()
    }

    suspend fun signInWithEmail(email: String, password: String): Result =
        passwordGrant("token?grant_type=password", email, password)

    suspend fun signUpWithEmail(email: String, password: String): Result =
        passwordGrant("signup", email, password)

    /** Standard Supabase recovery email - same anon key, no new secret. */
    suspend fun sendPasswordReset(email: String): Result =
        withContext(Dispatchers.IO) {
            if (anonKey.isBlank()) {
                return@withContext Result.Failure("Supabase anon key missing - cannot send the reset email")
            }
            try {
                val json = com.google.gson.JsonObject().apply { addProperty("email", email) }
                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$supabaseUrl/auth/v1/recover?redirect_to=" + java.net.URLEncoder.encode(RECOVERY_REDIRECT, "UTF-8"))
                    .addHeader("apikey", anonKey)
                    .post(body)
                    .build()
                http.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) Result.Success
                    else Result.Failure("Could not send the reset email (${resp.code}). Try once more.")
                }
            } catch (e: Exception) {
                Result.Failure("Network error: ${e.message ?: "unknown"}")
            }
        }

    private suspend fun passwordGrant(path: String, email: String, password: String): Result =
        withContext(Dispatchers.IO) {
            if (anonKey.isBlank()) {
                return@withContext Result.Failure("Supabase anon key missing - local.properties me MAYA_SUPABASE_ANON_KEY daalo")
            }
            try {
                val body = FormBody.Builder()
                    .add("email", email)
                    .add("password", password)
                    .build()
                val request = Request.Builder()
                    .url("$supabaseUrl/auth/v1/$path")
                    .addHeader("apikey", anonKey)
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .post(body)
                    .build()
                http.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext when (resp.code) {
                            400 -> Result.Failure("Check your email or password")
                            422 -> Result.Failure("This email seems to be registered already")
                            else -> Result.Failure("Sign-in failed (${resp.code}). The server may be waking up. Try once more.")
                        }
                    }
                    val json = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val token = json.get("access_token")?.asString
                    if (token == null) return@withContext Result.Failure("No token received")
                    prefs.edit()
                        .putString("access_token", token)
                        .putString("refresh_token", json.get("refresh_token")?.asString)
                        .putString("email", email)
                        .putLong("expires_at", refresher.expiryFrom(json.get("expires_at")?.asLong, json.get("expires_in")?.asLong))
                        .apply()
                    Result.Success
                }
            } catch (e: Exception) {
                Result.Failure("Network error. Check your internet and try again")
            }
        }

    /** Google login: opens Supabase's OAuth page; result returns via maya://auth deep link. */
    fun googleSignInIntent(): Intent {
        val url = "${BuildConfig.SUPABASE_URL}/auth/v1/authorize" +
            "?provider=google&redirect_to=maya%3A%2F%2Fauth"
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    enum class Redirect { SIGNED_IN, RECOVERY, LINK_ERROR, IGNORED }

    /** In-memory only: a password-recovery token is never stored as a signed-in session. */
    private class RecoverySession(val token: String, val expiresAtMs: Long)
    @Volatile private var recovery: RecoverySession? = null
    @Volatile var linkError: String? = null; private set
    val recoveryPending: Boolean get() = recovery?.let { it.expiresAtMs > System.currentTimeMillis() } == true
    fun cancelRecovery() { recovery = null }
    fun consumeLinkError(): String? = linkError.also { linkError = null }

    /** Called from MainActivity when the maya://auth deep link comes back (Google sign-in or password recovery). */
    fun handleAuthRedirect(uri: Uri): Redirect {
        return when (val o = RecoveryLink.parse(uri.fragment, uri.query)) {
            is RecoveryLink.Outcome.Recovery -> { recovery = RecoverySession(o.accessToken, o.expiresAtMs); linkError = null; Redirect.RECOVERY }
            is RecoveryLink.Outcome.LinkError -> { recovery = null; linkError = o.message; Redirect.LINK_ERROR }
            RecoveryLink.Outcome.Ignore -> Redirect.IGNORED
            is RecoveryLink.Outcome.SignIn -> {
                val params = o.params
                prefs.edit()
                    .putString("access_token", params["access_token"])
                    .putString("refresh_token", params["refresh_token"])
                    .putLong("expires_at", refresher.expiryFrom(params["expires_at"]?.toLongOrNull(), params["expires_in"]?.toLongOrNull()))
                    .apply()
                Redirect.SIGNED_IN
            }
        }
    }

    /** Confirms the recovery token is a live session and returns whose account it is (masked), or a failure. */
    suspend fun verifyRecoverySession(): kotlin.Result<String> = withContext(Dispatchers.IO) {
        val r = recovery
        if (r == null || r.expiresAtMs <= System.currentTimeMillis()) return@withContext kotlin.Result.failure(IllegalStateException("expired"))
        try {
            val req = Request.Builder().url("$supabaseUrl/auth/v1/user").addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + r.token).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext kotlin.Result.failure(IllegalStateException("rejected"))
                val email = JsonParser.parseString(resp.body?.string() ?: "{}").asJsonObject.get("email")?.takeIf { !it.isJsonNull }?.asString
                if (email.isNullOrBlank()) kotlin.Result.failure(IllegalStateException("no email")) else kotlin.Result.success(RecoveryLink.maskEmail(email))
            }
        } catch (e: Exception) { kotlin.Result.failure(e) }
    }

    /** Sets the new password. Success only when Supabase answers 2xx; the recovery token is then dropped. */
    suspend fun setNewPassword(newPassword: String): Result = withContext(Dispatchers.IO) {
        val r = recovery
        if (r == null || r.expiresAtMs <= System.currentTimeMillis()) { recovery = null; return@withContext Result.Failure("The reset link expired. Ask for a new one.") }
        if (newPassword.length < RecoveryLink.MIN_PASSWORD) return@withContext Result.Failure("Use at least ${RecoveryLink.MIN_PASSWORD} characters.")
        try {
            val body = com.google.gson.JsonObject().apply { addProperty("password", newPassword) }.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url("$supabaseUrl/auth/v1/user").addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + r.token).put(body).build()
            http.newCall(req).execute().use { resp ->
                when {
                    resp.isSuccessful -> { recovery = null; Result.Success }
                    resp.code == 401 || resp.code == 403 -> { recovery = null; Result.Failure("The reset link expired. Ask for a new one.") }
                    resp.code == 422 -> Result.Failure("That password was not accepted. Try a different, longer one.")
                    else -> Result.Failure("Could not update the password (${resp.code}). Try once more.")
                }
            }
        } catch (e: Exception) { Result.Failure("Network error. Try once more.") }
    }

    private val store = object : TokenStore {
        override fun accessToken() = prefs.getString("access_token", null)
        override fun refreshToken() = prefs.getString("refresh_token", null)
        override fun expiresAt() = prefs.getLong("expires_at", 0L)
        override fun save(access: String, refresh: String?, expiresAt: Long) {
            prefs.edit().putString("access_token", access).putString("refresh_token", refresh)
                .putLong("expires_at", expiresAt).apply()
        }
        override fun clear() = prefs.edit().clear().apply()
    }
    private val refresher by lazy { TokenRefresher(http, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, store) }

    /** Access token valid for the next minute, refreshed when needed (see TokenRefresher). */
    suspend fun validAccessToken(): String? = withContext(Dispatchers.IO) { refresher.validAccessToken() }

    fun signOut() { recovery = null; prefs.edit().clear().apply() }
}
