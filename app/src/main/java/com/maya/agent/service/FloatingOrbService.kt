package com.maya.agent.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import com.maya.agent.ui.components.OrbMood
import com.maya.agent.ui.components.VoiceOrb
import com.maya.agent.ui.theme.MayaTheme
import com.maya.agent.MainActivity
import androidx.compose.ui.unit.dp

/**
 * The floating voice orb: a small translucent bubble over other apps.
 * Drag to move; tap to open Maya. Requires the overlay permission granted
 * in onboarding. Mic-active state is always visible on the orb panel.
 */
class FloatingOrbService : Service() {

    private var windowManager: WindowManager? = null
    private var orbView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (orbView != null) return START_STICKY
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 32; y = 120
        }

        val container = FrameLayout(this)
        val composeView = ComposeView(this).apply {
            setContent {
                MayaTheme {
                    VoiceOrb(mood = OrbState.mood.value, size = 72.dp)
                }
            }
        }
        container.addView(composeView)

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX - (event.rawX - downX).toInt()
                    params.y = startY - (event.rawY - downY).toInt()
                    windowManager?.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - downX) + kotlin.math.abs(event.rawY - downY)
                    if (moved < 24) {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    true
                }
                else -> false
            }
        }

        orbView = container
        windowManager?.addView(container, params)
        return START_STICKY
    }

    override fun onDestroy() {
        orbView?.let { windowManager?.removeView(it) }
        orbView = null
        super.onDestroy()
    }
}
