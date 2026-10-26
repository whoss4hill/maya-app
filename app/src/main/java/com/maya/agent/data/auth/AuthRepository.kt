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
    /** Tests only: EncryptedSharedPreferences needs the Android keystore, which JVM tests do not have. */
    private val prefsOverride: android.content.SharedPreferences? = null,
) {

    companion object {
        /** Must be in the Supabase project's Redirect URLs allow-list (owner-verified), else Supabase falls back to its Site URL. */
        const val RECOVERY_REDIRECT = "maya://auth"
        const val PKCE_MAX_AGE_MS = 2L * 60 * 60 * 1000
    }

    private val appContext = context.applicationContext

    private val http = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS) // backend cold starts
        .build()

    private val prefs: android.content.SharedPreferences by lazy {
        prefsOverride ?: EncryptedSharedPreferences.create(
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
                val verifier = newVerifier()
                val json = com.google.gson.JsonObject().apply {
                    addProperty("email", email)
                    addProperty("code_challenge", challengeOf(verifier))
                    addProperty("code_challenge_method", "s256")
                }
                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("$supabaseUrl/auth/v1/recover?redirect_to=" + java.net.URLEncoder.encode(RECOVERY_REDIRECT, "UTF-8"))
                    .addHeader("apikey", anonKey)
                    .post(body)
                    .build()
                http.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        prefs.edit().putString("pkce_verifier", verifier).putLong("pkce_ts", System.currentTimeMillis()).apply()
                        Result.Success
                    } else Result.Failure("Could not send the reset email (${resp.code}). Try once more.")
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

    private fun newVerifier(): String {
        val b = ByteArray(48); java.security.SecureRandom().nextBytes(b)
        return android.util.Base64.encodeToString(b, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
    }
    private fun challengeOf(verifier: String): String =
        android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)

    /** In-memory only: a password-recovery token is never stored as a signed-in session. */
    private class RecoverySession(val token: String, val expiresAtMs: Long)
    @Volatile private var recovery: RecoverySession? = null
    @Volatile private var pendingCode: String? = null
    @Volatile var linkError: String? = null; private set
    val recoveryPending: Boolean get() = recovery?.let { it.expiresAtMs > System.currentTimeMillis() } == true
    val recoveryCodeWaiting: Boolean get() = pendingCode != null
    fun cancelRecovery() { recovery = null; pendingCode = null }
    fun consumeLinkError(): String? = linkError.also { linkError = null }

    private fun freshVerifier(): String? {
        val v = prefs.getString("pkce_verifier", null) ?: return null
        val ts = prefs.getLong("pkce_ts", 0L)
        return if (System.currentTimeMillis() - ts in 0..PKCE_MAX_AGE_MS) v else null
    }

    /** Called from MainActivity for maya://auth. Recovery is only accepted as a PKCE code matching a reset this app requested. */
    fun handleAuthRedirect(uri: Uri): Redirect {
        if (!RecoveryLink.isExactCallback(uri.scheme, uri.host, uri.port, uri.path, uri.userInfo)) return Redirect.IGNORED
        return when (val o = RecoveryLink.parse(uri.fragment, uri.query)) {
            is RecoveryLink.Outcome.Code -> {
                if (freshVerifier() == null) { Redirect.IGNORED }  // not a reset this app asked for
                else { recovery = null; pendingCode = o.code; linkError = null; Redirect.RECOVERY }
            }
            is RecoveryLink.Outcome.LinkError -> { recovery = null; pendingCode = null; linkError = o.message; Redirect.LINK_ERROR }
            RecoveryLink.Outcome.Ignore -> Redirect.IGNORED
            is RecoveryLink.Outcome.SignIn -> {
                prefs.edit()
                    .putString("access_token", o.accessToken)
                    .putString("refresh_token", o.refreshToken)
                    .putLong("expires_at", refresher.expiryFrom(o.expiresAt, o.expiresIn))
                    .apply()
                Redirect.SIGNED_IN
            }
        }
    }

    /** Redeems the waiting PKCE code (one-shot: verifier and code are consumed before the request) and returns the masked account. */
    suspend fun beginRecovery(): kotlin.Result<String> = withContext(Dispatchers.IO) {
        val code = pendingCode; val verifier = freshVerifier()
        pendingCode = null
        prefs.edit().remove("pkce_verifier").remove("pkce_ts").apply()
        if (code == null || verifier == null) return@withContext kotlin.Result.failure(IllegalStateException("no pending reset"))
        try {
            val body = com.google.gson.JsonObject().apply { addProperty("auth_code", code); addProperty("code_verifier", verifier) }.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url("$supabaseUrl/auth/v1/token?grant_type=pkce").addHeader("apikey", anonKey).post(body).build()
            val (token, expMs) = http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext kotlin.Result.failure(IllegalStateException("rejected"))
                val o = JsonParser.parseString(resp.body?.string() ?: "{}").asJsonObject
                val t = o.get("access_token")?.takeIf { !it.isJsonNull }?.asString
                val ein = o.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
                if (t.isNullOrBlank() || ein <= 0 || ein > 7L * 24 * 3600) return@withContext kotlin.Result.failure(IllegalStateException("bad response"))
                t to (System.currentTimeMillis() + ein * 1000)
            }
            recovery = RecoverySession(token, expMs)
            verifyRecoverySession().onFailure { recovery = null }
        } catch (e: Exception) { kotlin.Result.failure(e) }
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

    fun signOut() { recovery = null; pendingCode = null; prefs.edit().clear().apply() }
}
