package com.maya.agent.calls

/**
 * Draft-only SMS from the Live tool draft_sms. It NEVER sends: at most it opens the stock SMS composer
 * pre-filled (ACTION_SENDTO, no SEND_SMS use), and the owner reads it and presses Send there.
 * Opening the composer is not a consent step for any gate; the owner's Send press is the only send.
 * The composer opens only when the owner's own recent speech (app-side transcript) contains a message
 * request word, the recipient they named, and most of the draft's wording. Anything less is preview only.
 * Best-effort: the secret guard is a filter, not a promise of perfect masking.
 */
object SmsDraft {
    data class Result(val status: String, val text: String)

    private val msgWords = listOf("sms", "message", "msg", "text", "bhej", "likh", "send", "मैसेज", "संदेश", "भेज", "लिख")

    private fun words(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ")
        .split(Regex("\\s+")).filter { it.length >= 3 }

    /** Share of the draft's content words that the owner actually said. */
    fun bodyOverlap(body: String, speech: String): Double {
        val b = words(body); if (b.isEmpty()) return 0.0
        val sp = words(speech).toSet()
        return b.count { it in sp }.toDouble() / b.size
    }

    fun prepare(
        target: String, body: String, ownerSpeech: String,
        deps: PhoneCommand.Deps,
        looksSecret: (String) -> Boolean,
        openComposer: (number: String, body: String) -> Boolean,
    ): Result {
        val t = target.trim(); val b = body.trim()
        if (t.isEmpty() || b.isEmpty()) return Result("unclear", "Need both who the message is for and what it says. Nothing was drafted or sent.")
        if (t.none { it.isLetter() } && DialCommand.isEmergency(t)) return Result("refused", "Not drafting messages to emergency numbers. Nothing was sent.")
        if (looksSecret(b)) return Result("refused", "The text looks like it contains a secret or credential, so no draft was made. Nothing was sent.")
        val sp = ownerSpeech.lowercase()
        if (msgWords.none { sp.contains(it) })
            return Result("no_request_heard", "The app did not hear the owner ask for a message just now. Nothing was drafted or sent.")
        val req = DialCommand.request("call $t")
            ?: return Result("unclear", "Could not understand '$t' as a contact or number. Nothing was drafted or sent. Ask the owner who it is for.")
        var name = ""
        val number: String = when (req) {
            is DialCommand.Request.Number -> req.number
            is DialCommand.Request.Name -> {
                if (!deps.hasContactsPermission())
                    return Result("blocked", "Contacts permission is not granted, so '${req.name}' cannot be looked up. Nothing was drafted or sent.")
                val found = deps.lookup(req.name)
                when {
                    found.isEmpty() -> return Result("not_found", "No contact matches '${req.name}'. Nothing was drafted or sent. Ask the owner for the exact number.")
                    found.size > 1 -> return Result("ambiguous", "Several contacts match '${req.name}': " + found.joinToString("; ") { it.name } + ". Nothing was drafted. Ask which one; do not guess.")
                    else -> { name = found[0].name; found[0].number }
                }
            }
        }
        if (DialCommand.isEmergency(number)) return Result("refused", "Not drafting messages to emergency numbers. Nothing was sent.")
        val recipientHeard = when (req) {
            is DialCommand.Request.Name -> words(req.name).ifEmpty { listOf(req.name.lowercase()) }.any { sp.contains(it) }
            is DialCommand.Request.Number -> sp.filter { it.isDigit() }.contains(number.filter { it.isDigit() }.takeLast(10))
        }
        if (!recipientHeard || bodyOverlap(b, ownerSpeech) < 0.5)
            return Result("preview_only", "The app could not match the recipient or wording to what the owner just said, so no composer was opened and nothing was sent. Read the draft back to the owner, ask them to restate who and what, and do not choose a recipient yourself.")
        return if (openComposer(number, b))
            Result("composer_opened", "The SMS composer is open with the draft" + (if (name.isNotBlank()) " for $name" else "") + ". It has NOT been sent; the owner must read it and press Send themselves. Do not say it was sent.")
        else Result("blocked", "Could not open the SMS composer. Nothing was sent.")
    }
}
