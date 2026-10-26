package com.maya.agent.calls

/**
 * Voice-triggered call ("Maya, Ammi ko call karo") from the Live model's call_contact tool.
 *
 * Consent model (same as DialCommand): the owner's own spoken command in an open voice session is the
 * go-ahead for that one call. Pure decision logic so it is JVM tested. The result text goes back to the model
 * so it can tell the owner the truth: "intent launched" is NOT "connected". Unknown/ambiguous contacts and
 * missing permissions never dial; the model is told to ask for the exact number or point to Permissions.
 * No permission prompt is raised from here (no new UI): missing permission is reported honestly.
 */
object VoiceCall {
    data class Result(val status: String, val text: String)

    fun place(target: String, deps: PhoneCommand.Deps): Result {
        val t = target.trim()
        if (DialCommand.isEmergency(t) && t.none { it.isLetter() })
            return if (deps.dial(t.filter { it.isDigit() })) Result("dialer_opened", "That is an emergency number. Only the dialer was opened; the owner must press Call. Nothing was auto-dialled.")
            else Result("blocked", "Could not open the dialer. Nothing was dialled.")
        val req = DialCommand.request("call $t")
            ?: return Result("unclear", "Could not understand '$t' as a number or contact name. Nothing was dialled. Ask the owner for a clear name or the full number.")
        if (!deps.hasCallPermission())
            return Result("blocked", "Phone (call) permission is not granted on this phone, so nothing was dialled. Tell the owner to allow Phone permission in Maya's Permissions screen.")
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
                    else -> found[0].number
                }
            }
        }
        if (DialCommand.isEmergency(number))
            return if (deps.dial(number)) Result("dialer_opened", "That is an emergency number. Only the dialer was opened; the owner must press Call. Nothing was auto-dialled.")
            else Result("blocked", "Could not open the dialer. Nothing was dialled.")
        return if (deps.dial(number)) Result("intent_launched", "Call intent launched to the number; Android cannot confirm the call connected. Do not say the person picked up.")
        else Result("blocked", "Android refused to start the call (no dialer or background limit). Nothing was dialled.")
    }
}
