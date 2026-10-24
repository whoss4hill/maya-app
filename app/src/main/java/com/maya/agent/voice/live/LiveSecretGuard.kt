package com.maya.agent.voice.live

/**
 * Best-effort detector for obvious credential payloads, mirroring the backend
 * secret_guard so the Live voice path keeps the equivalent gate: anything a
 * tool returns (most importantly read_screen text) is scanned BEFORE it is
 * sent to the model, spoken, or stored. Safety net, not perfect detection.
 */
object LiveSecretGuard {

    private val PATTERNS = listOf(
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
        Regex("\\bAIza[0-9A-Za-z_\\-]{30,}\\b"),                          // Google API key
        Regex("\\b(?:sk|pk|rk)[-_](?:live|test|proj|ant)?[-_]?[A-Za-z0-9_\\-]{20,}\\b"),
        Regex("\\bgh[pousr]_[A-Za-z0-9]{30,}\\b"),
        Regex("\\bgithub_pat_[A-Za-z0-9_]{30,}\\b"),
        Regex("\\bxox[abprs]-[A-Za-z0-9\\-]{10,}\\b"),                    // Slack
        Regex("\\bAKIA[0-9A-Z]{16}\\b"),                                  // AWS access key id
        Regex("\\b\\d{8,10}:[A-Za-z0-9_\\-]{35}\\b"),                     // Telegram bot token
        Regex("\\beyJ[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{10,}\\.[A-Za-z0-9_\\-]{5,}\\b"), // JWT
        Regex("\\bbearer\\s+[A-Za-z0-9_\\-.=]{20,}", RegexOption.IGNORE_CASE),
        Regex("\\b(?:cvv|cvc|pin)\\b\\s*(?:is|=|:|hai|h)?\\s*\\d{3,8}\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(?:mera|my)\\s+(?:password|pin|cvv)\\b\\s*[^\\s]{0,3}\\s*[\"']?[^\\s\"']{4,}", RegexOption.IGNORE_CASE),
    )

    // "password is xyz" style, with placeholders (<NAME>, ${VAR}, %VAR%) excluded like the backend.
    private val LABELED = Regex(
        "\\b(?:password|passcode|passwd|pwd|pin|cvv|cvc|otp|api[ _-]?key|secret|token)\\b\\s*(?:is|=|:|hai|h)\\s*[\"']?([^\\s\"']{4,})",
        RegexOption.IGNORE_CASE,
    )

    private val CARD = Regex("(?<![\\d])(?:\\d[ -]?){13,19}(?![\\d])")

    private fun luhnOk(digits: String): Boolean {
        var s = 0; var alt = false
        for (ch in digits.reversed()) {
            var d = ch - '0'
            if (alt) { d *= 2; if (d > 9) d -= 9 }
            s += d; alt = !alt
        }
        return s % 10 == 0
    }

    fun looksLikeSecret(text: String?): Boolean {
        val t = text ?: ""
        if (t.isBlank()) return false
        for (p in PATTERNS) if (p.containsMatchIn(t)) return true
        for (m in LABELED.findAll(t)) {
            val v = m.groupValues[1]
            if (!v.startsWith("<") && !v.startsWith("\${") && !v.startsWith("%")) return true
        }
        for (m in CARD.findAll(t)) {
            val digits = m.value.filter { it.isDigit() }
            if (digits.length in 13..19 && luhnOk(digits)) return true
        }
        return false
    }

    const val REFUSAL =
        "Screen pe kuch sensitive (password/key/card jaisa) dikh raha hai - ye main AI ko nahi bhejungi."
}
