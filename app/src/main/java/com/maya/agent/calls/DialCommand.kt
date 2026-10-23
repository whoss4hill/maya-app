package com.maya.agent.calls

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * "Call <number>" opens the phone dialer with the number filled in (ACTION_DIAL).
 * Maya never places the call herself: the owner presses Call in the dialer.
 * No CALL_PHONE use, nothing is dialled in the background, no call audio is touched.
 */
object DialCommand {
    private val keyword = Regex("""\b(call|dial|phone|fone|kol|ring)\b""", RegexOption.IGNORE_CASE)
    private val number = Regex("""(\+?\d[\d\s\-().]{5,18}\d)""")

    /** Returns the cleaned number (digits and a leading +) or null if this is not a dial request. */
    fun parse(text: String): String? {
        if (!keyword.containsMatchIn(text)) return null
        val raw = number.find(text)?.value ?: return null
        val cleaned = (if (raw.trimStart().startsWith("+")) "+" else "") + raw.filter { it.isDigit() }
        val digits = cleaned.count { it.isDigit() }
        return if (digits in 6..15) cleaned else null
    }

    fun intentFor(number: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Opens the dialer; returns false if no dialer app can handle it. */
    fun open(context: Context, number: String): Boolean = try {
        context.startActivity(intentFor(number)); true
    } catch (_: Exception) { false }
}
