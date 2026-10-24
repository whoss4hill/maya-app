package com.maya.agent.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Maya's hands on the phone: tap, read screens, global back/home - only for
 * the owner's own requests via ToolExecutor. Minimum-viable set for v3:
 * tap-by-coordinate, tap-by-text, read-screen, back, home.
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
        // Passive: v3 only acts on explicit owner commands, no event watching.
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // ---- Actions (called by ToolExecutor) ----

    /** Tap exact screen coordinates. */
    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Find the first visible node whose text (or content description) contains
     * [text] (case-insensitive) and click it (or its nearest clickable parent).
     */
    fun tapText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val needle = text.lowercase()
        val target = findNodeByText(root, needle) ?: return false
        var node: AccessibilityNodeInfo? = target
        while (node != null && !node.isClickable) node = node.parent
        val clicked = node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        if (!clicked) {
            // Fallback: tap the node's visible center.
            val bounds = Rect()
            target.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) {
                return tap(bounds.exactCenterX(), bounds.exactCenterY())
            }
        }
        return clicked
    }

    /** Collect visible text from the current screen, top to bottom. */
    fun readScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = LinkedHashSet<String>()
        collectText(root, out)
        return out.joinToString(". ")
    }

    private fun collectText(node: AccessibilityNodeInfo?, out: MutableSet<String>, depth: Int = 0) {
        if (node == null || depth > 40 || out.size > 60) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) collectText(node.getChild(i), out, depth + 1)
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString()?.lowercase()
        val nodeDesc = node.contentDescription?.toString()?.lowercase()
        if ((nodeText != null && nodeText.contains(needle)) ||
            (nodeDesc != null && nodeDesc.contains(needle))
        ) return node
        for (i in 0 until node.childCount) {
            findNodeByText(node.getChild(i), needle)?.let { return it }
        }
        return null
    }

    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
}
