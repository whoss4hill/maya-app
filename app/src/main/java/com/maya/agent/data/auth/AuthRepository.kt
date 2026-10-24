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
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext

    private val http = OkHttpClient.Builder()
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
            if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                return@withContext Result.Failure("Supabase anon key missing - cannot send the reset email")
            }
            try {
                val json = com.google.gson.JsonObject().apply { addProperty("email", email) }
                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/auth/v1/recover")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
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
            if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
                return@withContext Result.Failure("Supabase anon key missing - local.properties me MAYA_SUPABASE_ANON_KEY daalo")
            }
            try {
                val body = FormBody.Builder()
                    .add("email", email)
                    .add("password", password)
                    .build()
                val request = Request.Builder()
                    .url("${BuildConfig.SUPABASE_URL}/auth/v1/$path")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
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

    /** Called from MainActivity when the maya://auth deep link comes back. */
    fun handleAuthRedirect(uri: Uri) {
        // Supabase returns tokens in the URL fragment.
        val fragment = uri.fragment ?: return
        val params = fragment.split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i > 0) it.substring(0, i) to it.substring(i + 1) else null
        }.toMap()
        val token = params["access_token"] ?: return
        prefs.edit()
            .putString("access_token", token)
            .putString("refresh_token", params["refresh_token"])
            .putLong("expires_at", refresher.expiryFrom(params["expires_at"]?.toLongOrNull(), params["expires_in"]?.toLongOrNull()))
            .apply()
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

    fun signOut() = prefs.edit().clear().apply()
}
