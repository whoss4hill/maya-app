package com.maya.agent.calls

import com.google.gson.JsonParser

/**
 * Remote "call" command pushed by the owner's Telegram bot through the Maya Worker socket.
 * Pure decision logic (no Android calls) so it is unit tested on the JVM.
 *
 * Rules: the command carries a Worker-issued id and expiry; expired or already seen ids are never acted on;
 * only cmd="call" is understood; the ack says exactly what happened ("intent launched" is NOT "connected").
 * Ambiguous or unknown contact names are never dialled. Missing permission or an OS refusal is a "blocked" ack.
 */
object PhoneCommand {
    data class Ack(val id: String, val status: String, val text: String)

    class Deps(
        val hasCallPermission: () -> Boolean,
        val hasContactsPermission: () -> Boolean,
        val lookup: (String) -> List<DialCommand.Match>,
        val dial: (String) -> Boolean,
        /** True only when the call log shows the owner interacted with this number (a saved contact alone is not proof). Default true keeps the Telegram path unchanged. */
        val isKnownRecipient: (String) -> Boolean = { true },
        /** Speaks [text] with the app's own TTS; invokes onDone after the full text was spoken. Returns false when it cannot be spoken. */
        val speakReadback: (text: String, onDone: () -> Unit) -> Boolean = { _, _ -> false },
        /** Opens the dialer prefilled (owner must press Call). */
        val openDialer: (String) -> Boolean = { false },
    )

    private val seen = LinkedHashSet<String>()

    fun resetSeen() = synchronized(seen) { seen.clear() }

    /** Returns the ack to send back, or null when the message must be ignored silently (not a command / replay). */
    fun handle(raw: String, nowEpochSec: Long, deps: Deps): Ack? {
        val obj = try { JsonParser.parseString(raw).asJsonObject } catch (_: Exception) { return null }
        val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
        if (obj.get("cmd")?.asString != "call") return Ack(id, "ignored", "Unsupported command; nothing done.")
        synchronized(seen) {
            if (!seen.add(id)) return null
            if (seen.size > 50) seen.remove(seen.first())
        }
        val exp = obj.get("exp")?.asLong ?: 0L
        if (exp < nowEpochSec) return Ack(id, "expired", "Command expired before the phone received it. Nothing was dialled.")
        val target = obj.get("target")?.asString?.trim().orEmpty()
        val req = DialCommand.request("call $target")
            ?: return Ack(id, "unclear", "Could not understand '$target' as a number or contact name. Nothing was dialled.")
        if (!deps.hasCallPermission()) return Ack(id, "blocked", "Blocked: CALL_PHONE permission is not granted on the phone. Nothing was dialled.")
        val number: String = when (req) {
            is DialCommand.Request.Number -> req.number
            is DialCommand.Request.Name -> {
                if (!deps.hasContactsPermission()) return Ack(id, "blocked", "Blocked: contacts permission is not granted, so '${req.name}' cannot be looked up. Nothing was dialled.")
                val found = deps.lookup(req.name)
                when {
                    found.isEmpty() -> return Ack(id, "not_found", "No contact matches '${req.name}'. Send the exact number. Nothing was dialled.")
                    found.size > 1 -> return Ack(id, "ambiguous", "Several matches for '${req.name}': " +
                        found.joinToString("; ") { "${it.name} ${it.number}" } + ". Send the exact number. Nothing was dialled.")
                    else -> found[0].number
                }
            }
        }
        return if (deps.dial(number)) Ack(id, "intent_launched", "Call intent launched to ${mask(number)}. Android cannot confirm the call connected.")
        else Ack(id, "blocked", "Blocked: Android refused to start the call (background-launch limit or no dialer). Nothing was dialled.")
    }

    private fun mask(n: String) = if (n.length <= 4) n else n.take(3) + "*".repeat(n.length - 5) + n.takeLast(2)
}
