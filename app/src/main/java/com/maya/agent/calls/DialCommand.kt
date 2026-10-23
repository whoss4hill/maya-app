package com.maya.agent.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/**
 * Explicit voice/text command -> a real call.
 *
 * Consent model: the owner's own explicit "call <number>" / "call <contact name>" message is the
 * go-ahead for that one call. Nothing is dialled in the background, from a notification, from
 * backend/web content, or on any message that is not a clear call command. Ambiguous contact names
 * (several different numbers) are never dialled: Maya lists them and asks for the exact number.
 * Uses ACTION_CALL, which needs the CALL_PHONE runtime permission (Android: "initiate a phone call
 * without going through the Dialer user interface"). Emergency numbers need CALL_PRIVILEGED, which
 * normal apps cannot get, so those are only ever opened in the dialer. No call audio is touched.
 */
object DialCommand {
    sealed class Request {
        data class Number(val number: String) : Request()
        data class Name(val name: String) : Request()
    }

    private val verb = "(?:call|dial|phone|fone|kol|ring)"
    private val keyword = Regex("""\b$verb\b""", RegexOption.IGNORE_CASE)
    private val number = Regex("""(\+?\d[\d\s\-().]{5,18}\d)""")
    private val nameAfter = Regex("""^\s*$verb\s+([\p{L}][\p{L} .'-]{0,30})\s*$""", RegexOption.IGNORE_CASE)
    private val nameBefore = Regex(
        """^\s*([\p{L}][\p{L} .'-]{0,30}?)\s+ko\s+$verb\s*(?:karo|kar do|kardo|lagao|laga do|lagado|kro)?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val notNames = setOf("me", "back", "later", "now", "karo", "kar", "do", "please", "mujhe", "ko", "to", "and", "the", "a", "him", "her", "them")

    /** Cleaned number (digits and a leading +) or null if this is not a number-call request. */
    fun parse(text: String): String? {
        if (!keyword.containsMatchIn(text)) return null
        val raw = number.find(text)?.value ?: return null
        val cleaned = (if (raw.trimStart().startsWith("+")) "+" else "") + raw.filter { it.isDigit() }
        val digits = cleaned.count { it.isDigit() }
        return if (digits in 6..15) cleaned else null
    }

    /** A number or a bare contact name ("call mom", "mom ko call karo"); anything else is normal chat. */
    fun request(text: String): Request? {
        parse(text)?.let { return Request.Number(it) }
        if (text.any { it.isDigit() }) return null
        val name = (nameAfter.find(text)?.groupValues?.get(1) ?: nameBefore.find(text)?.groupValues?.get(1))
            ?.trim()?.trimEnd('.') ?: return null
        val words = name.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 3 || words.any { it.lowercase() in notNames }) return null
        return Request.Name(name)
    }

    data class Match(val name: String, val number: String)

    /** Distinct (name, normalised number) contacts whose display name contains [name]. Needs READ_CONTACTS. */
    fun lookup(context: Context, name: String): List<Match> {
        val out = LinkedHashMap<String, Match>()
        val c = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null,
        ) ?: return emptyList()
        c.use {
            while (it.moveToNext() && out.size < 6) {
                val n = it.getString(1) ?: continue
                val key = normalize(n)
                if (key.length >= 6) out.putIfAbsent(key, Match(it.getString(0) ?: name, n.trim()))
            }
        }
        return out.values.toList()
    }

    fun normalize(n: String): String {
        val d = n.filter { it.isDigit() }
        return if (d.length > 10) d.takeLast(10) else d
    }

    fun callIntent(number: String): Intent =
        Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Places the call. Caller must hold CALL_PHONE. Returns false on refusal or no handler. */
    fun call(context: Context, number: String): Boolean = try {
        context.startActivity(callIntent(number)); true
    } catch (_: Exception) { false }
}
