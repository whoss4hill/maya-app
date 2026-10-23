package com.maya.agent.data.auth

import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Minimal token storage so the refresh logic can be unit-tested without Android. */
interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun expiresAt(): Long
    fun save(access: String, refresh: String?, expiresAt: Long)
    fun clear()
}

/**
 * Keeps the access token fresh using the GoTrue refresh grant
 * (POST /auth/v1/token?grant_type=refresh_token, JSON {"refresh_token": ...}).
 * The server rotates the refresh token, so the new one is always saved.
 */
class TokenRefresher(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val store: TokenStore,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val lock = Any()

    fun expiryFrom(expiresAt: Long?, expiresIn: Long?): Long = when {
        expiresAt != null && expiresAt > 0 -> expiresAt
        expiresIn != null && expiresIn > 0 -> nowSeconds() + expiresIn
        else -> 0L
    }

    /** Valid token for >= 60s, refreshing if needed. Null = signed out / session ended. */
    fun validAccessToken(): String? = synchronized(lock) {
        val current = store.accessToken() ?: return null
        if (store.expiresAt() > nowSeconds() + 60) return current
        val refresh = store.refreshToken() ?: return current
        try {
            val body = com.google.gson.JsonObject().apply { addProperty("refresh_token", refresh) }.toString()
            val request = Request.Builder()
                .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                .addHeader("apikey", apiKey)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { resp ->
                if (resp.code == 400 || resp.code == 401 || resp.code == 403) {
                    store.clear() // refresh token revoked/expired: session is over
                    return null
                }
                if (!resp.isSuccessful) return current // server trouble: keep session
                val json = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val token = json.get("access_token")?.asString ?: return current
                store.save(
                    token,
                    json.get("refresh_token")?.asString ?: refresh,
                    expiryFrom(json.get("expires_at")?.asLong, json.get("expires_in")?.asLong),
                )
                token
            }
        } catch (e: Exception) {
            current // offline: keep tokens, do not log out
        }
    }
}
