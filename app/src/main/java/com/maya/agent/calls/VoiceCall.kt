package com.maya.agent.calls

/**
 * Voice-triggered call ("Maya, Ammi ko call karo") from the Live model's call_contact tool.
 *
 * Consent model: the owner's spoken command opens the request. Master prompt gate 3: a number with no earlier call
 * in the call log is a NEW recipient and needs the owner's own spoken yes after a read-back, enforced by
 * CallConfirmation (app-side), never by a model-supplied flag. Pure decision logic so it is JVM tested. The result text goes back to the model
 * so it can tell the owner the truth: "intent launched" is NOT "connected". Unknown/ambiguous contacts and
 * missing permissions never dial; the model is told to ask for the exact number or point to Permissions.
 * No permission prompt is raised from here (no new UI): missing permission is reported honestly.
 */
object VoiceCall {
    data class Result(val status: String, val text: String)

    val confirmation = CallConfirmation()

    fun confirmPending(deps: PhoneCommand.Deps, conf: CallConfirmation = confirmation, now: Long = System.currentTimeMillis()): Result =
        when (val o = conf.confirm(now)) {
            is CallConfirmation.Outcome.Confirmed -> {
                if (!deps.hasCallPermission()) Result("blocked", "Phone (call) permission is not granted, so nothing was dialled.")
                else if (deps.dial(o.number)) Result("intent_launched", "The owner confirmed. Call intent launched; Android cannot confirm the call connected. Do not say the person picked up.")
                else Result("blocked", "Android refused to start the call. Nothing was dialled.")
            }
            CallConfirmation.Outcome.NoPending -> Result("no_pending", "There is no call waiting for confirmation. Nothing was dialled. Do not claim a call is being placed.")
            CallConfirmation.Outcome.ReadbackNotFinished -> Result("wait", "The read-back is not finished. Nothing was dialled. Finish reading the number back, then wait for the owner's answer.")
            CallConfirmation.Outcome.Expired -> Result("expired", "The confirmation expired. Nothing was dialled. If the owner still wants the call, start again.")
            CallConfirmation.Outcome.NoAnswerHeard -> Result("no_answer", "The app has not heard an answer from the owner after the read-back. Nothing was dialled. Ask again and wait; do not assume yes.")
            CallConfirmation.Outcome.Denied -> Result("denied", "The owner said no. The call is cancelled and nothing was dialled.")
            CallConfirmation.Outcome.Unclear -> Result("unclear_answer", "The owner's answer was not a clear yes. Nothing was dialled. Ask again: should I call, yes or no?")
        }

    fun place(target: String, deps: PhoneCommand.Deps, conf: CallConfirmation = confirmation, now: () -> Long = { System.currentTimeMillis() }): Result {
        conf.reset() // any new call request discards an older pending confirmation
        val t = target.trim()
        if (DialCommand.isEmergency(t) && t.none { it.isLetter() })
            return if (deps.dial(t.filter { it.isDigit() })) Result("dialer_opened", "That is an emergency number. Only the dialer was opened; the owner must press Call. Nothing was auto-dialled.")
            else Result("blocked", "Could not open the dialer. Nothing was dialled.")
        val req = DialCommand.request("call $t")
            ?: return Result("unclear", "Could not understand '$t' as a number or contact name. Nothing was dialled. Ask the owner for a clear name or the full number.")
        if (!deps.hasCallPermission())
            return Result("blocked", "Phone (call) permission is not granted on this phone, so nothing was dialled. Tell the owner to allow Phone permission in Maya's Permissions screen.")
        var name = ""
        val number: String = when (req) {
            is DialCommand.Request.Number -> req.number
            is DialCommand.Request.Name -> {
                if (!deps.hasContactsPermission())
                    return Result("blocked", "Contacts permission is not granted, so '${req.name}' cannot be looked up. Nothing was dialled. Ask the owner for the full number or to allow Contacts permission.")
                val found = deps.lookup(req.name)
                when {
                    found.isEmpty() -> return Result("not_found", "No contact matches '${req.name}'. Nothing was dialled. Ask the owner for the exact number.")
                    found.size > 1 -> return Result("ambiguous", "Several contacts match '${req.name}': " +
                        found.joinToString("; ") { it.name } + ". Nothing was dialled. Ask the owner which one; do not guess.")
                    else -> { name = found[0].name; found[0].number }
                }
            }
        }
        if (DialCommand.isEmergency(number))
            return if (deps.dial(number)) Result("dialer_opened", "That is an emergency number. Only the dialer was opened; the owner must press Call. Nothing was auto-dialled.")
            else Result("blocked", "Could not open the dialer. Nothing was dialled.")
        // The model emitting the tool is not enough: the app must have heard the owner ask for a call out loud.
        if (!conf.ownerAskedForCall(now())) {
            return if (deps.openDialer(number)) Result("dialer_opened", "The app did not hear the owner ask for a call, so it did not place one. It opened the dialer with the number; the owner must press Call. Tell them so.")
            else Result("blocked", "The app did not hear the owner ask for a call. Nothing was dialled.")
        }
        if (!deps.isKnownRecipient(number)) {
            conf.begin(number, name)
            val spoken = number.filter { it.isDigit() || it == '+' }.map { if (it == '+') "plus" else it.toString() }.joinToString(" ")
            val lead = if (name.isBlank()) "Naya number" else "$name, naya number"
            val ok = deps.speakReadback("$lead. $spoken. Bolo, call lagaun?") { conf.onReadbackSpoken(now()) }
            if (!ok) {
                conf.reset()
                return if (deps.openDialer(number)) Result("dialer_opened", "The app could not read the new number back aloud, so it did not place the call. It opened the dialer with the number; the owner must press Call.")
                else Result("blocked", "The app could not read the number back aloud. Nothing was dialled.")
            }
            return Result("needs_confirmation", "New recipient (no earlier call in the call log; a contact entry alone does not count). Nothing was dialled. The APP is now reading the number aloud itself: say nothing about the number and do not read it yourself. Wait silently for the owner's own answer, then call confirm_pending_call (no arguments). The app checks their actual words; you cannot confirm for them. If they name a different person or number, start over with call_contact.")
        }
        return if (deps.dial(number)) Result("intent_launched", "Call intent launched to the number; Android cannot confirm the call connected. Do not say the person picked up.")
        else Result("blocked", "Android refused to start the call (no dialer or background limit). Nothing was dialled.")
    }
}
