package com.maya.agent

import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * The credential-free render hook used by the hosted emulator smoke. It exists
 * so CI can screenshot pages without a real account. It only changes which
 * screen the app starts on - it never signs anyone in, never mints a token,
 * and every backend call still carries no JWT and fails honestly. A release
 * (non-debuggable) build ignores the extra entirely.
 */
object SmokeDemoGate {
    const val EXTRA = "maya_smoke_demo"
    const val PREF = "smoke_demo_mode"

    fun allowed(appInfo: ApplicationInfo, intent: Intent?): Boolean =
        (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            intent?.getBooleanExtra(EXTRA, false) == true
}
