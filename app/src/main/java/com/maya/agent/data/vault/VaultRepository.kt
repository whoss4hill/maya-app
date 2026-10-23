package com.maya.agent.data.vault

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The vault: secrets encrypted on-device with keys in the Android Keystore,
 * unlocked by biometric/PIN (see BiometricGate). Secrets never leave the
 * device - never to Supabase, the backend, logs, chat, or Gemini.
 */
class VaultRepository(context: Context) {

    private val appContext = context.applicationContext
    private val gson = Gson()

    data class VaultItem(
        val id: String,
        val kind: Kind,
        val label: String,
        val username: String = "",
        val secret: String,
        val site: String = "",      // package name or domain this item belongs to
        val lastUsedAt: String = "",
    ) {
        enum class Kind { LOGIN, CARD, KEY, OTHER }
    }

    data class AuditEntry(
        val ts: String,
        val itemLabel: String,   // label only - NEVER the secret
        val action: String,      // "added", "filled", "viewed", "deleted"
        val where: String,       // app/site it was used on
    )

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "maya_vault",
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val auditFile: File get() = File(appContext.filesDir, "vault_audit.json")

    // ---- Items ----

    fun list(): List<VaultItem> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        val type = object : TypeToken<List<VaultItem>>() {}.type
        return gson.fromJson(raw, type) ?: emptyList()
    }

    private fun saveAll(items: List<VaultItem>) {
        prefs.edit().putString("items", gson.toJson(items)).apply()
    }

    fun add(item: VaultItem) {
        saveAll(list() + item)
        audit(item.label, "added", item.site)
    }

    fun delete(id: String) {
        val item = list().firstOrNull { it.id == id } ?: return
        saveAll(list().filterNot { it.id == id })
        audit(item.label, "deleted", item.site)
    }

    /** Returns the secret for a fill, and records the use. Caller must have passed BiometricGate. */
    fun secretForFill(id: String, targetAppOrSite: String): String? {
        val item = list().firstOrNull { it.id == id } ?: return null
        audit(item.label, "filled", targetAppOrSite)
        return item.secret
    }

    /** New-site approval rule: an item saved for one site needs owner OK for another. */
    fun needsApprovalFor(item: VaultItem, targetAppOrSite: String): Boolean =
        item.site.isNotBlank() && !targetAppOrSite.contains(item.site, ignoreCase = true)

    // ---- Audit log (on-device, no secrets) ----

    fun auditLog(): List<AuditEntry> {
        if (!auditFile.exists()) return emptyList()
        val type = object : TypeToken<List<AuditEntry>>() {}.type
        return gson.fromJson(auditFile.readText(), type) ?: emptyList()
    }

    private fun audit(label: String, action: String, where: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val entry = AuditEntry(ts, label, action, where)
        auditFile.writeText(gson.toJson(auditLog() + entry))
    }
}
