package com.maya.agent.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.maya.agent.MainActivity

/**
 * When the owner summons the assistant (long-press home / gesture) after picking
 * Maya as the default assistant, open the existing wake flow and finish the
 * session. No separate assistant UI.
 */
class MayaVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = MayaSession(this)

    companion object {
        /** Assistant gesture -> the app's existing wake flow. */
        fun buildAssistantLaunchIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setAction(MainActivity.ACTION_WAKE)
    }

    private class MayaSession(context: Context) : VoiceInteractionSession(context) {
        override fun onShow(args: Bundle?, showFlags: Int) {
            try {
                context.startActivity(buildAssistantLaunchIntent(context))
            } catch (_: Exception) { /* keep silent; nothing claimed */ }
            finish()
        }

        override fun onHide() {
            finish()
        }
    }
}
