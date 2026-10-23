package com.maya.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Maya's hands on the phone: tap, type, scroll, read screens - only for the
 * owner's own requests, inside the safety gates. This is the structured seam:
 * the tool executor (Gemini function calls from the backend) will dispatch
 * actions here. Skeleton intentionally conservative until on-device testing.
 */
class MayaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MayaAccessibilityService? = null
            private set
        val isConnected: Boolean get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen-reading for owner-requested actions lands here.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ---- Action seams (called by the future tool executor) ----

    fun tap(x: Float, y: Float) { /* gesture dispatch, on-device tested later */ }
    fun globalBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalHome() = performGlobalAction(GLOBAL_ACTION_HOME)
}
