package com.maya.agent.service

import android.service.voice.VoiceInteractionService

/**
 * Presence-only VoiceInteractionService so Android offers Maya in the system
 * default-assistant picker (Settings > Apps > Default apps > Digital assistant).
 * Choosing the role is always the owner's action in the system UI; Maya never
 * sets it herself. Invocation is handled by [MayaVoiceInteractionSessionService],
 * which simply opens the app's existing wake flow.
 */
class MayaVoiceInteractionService : VoiceInteractionService()
