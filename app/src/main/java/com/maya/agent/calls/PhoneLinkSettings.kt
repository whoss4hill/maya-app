package com.maya.agent.calls

/** Pure rules for the "Let Telegram start calls" switch (kept free of Android types so they are unit-testable). */
object PhoneLinkSettings {
    enum class Status { OFF, NEEDS_PERMISSION, WAITING, CONNECTED }

    /** The switch can be turned ON only with the Phone permission. Contacts is NOT required (only name lookup needs it). */
    fun canEnable(callPhoneGranted: Boolean) = callPhoneGranted

    fun status(enabled: Boolean, callPhoneGranted: Boolean, connected: Boolean): Status = when {
        !enabled -> Status.OFF
        !callPhoneGranted -> Status.NEEDS_PERMISSION
        connected -> Status.CONNECTED
        else -> Status.WAITING
    }

    fun label(s: Status) = when (s) {
        Status.OFF -> "Off"
        Status.NEEDS_PERMISSION -> "Needs Phone permission"
        Status.WAITING -> "Not connected"
        Status.CONNECTED -> "Connected"
    }
}
