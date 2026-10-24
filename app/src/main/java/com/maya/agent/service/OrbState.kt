package com.maya.agent.service

import androidx.compose.runtime.mutableStateOf
import com.maya.agent.ui.components.OrbMood

/** Shared orb mood, written by services and read by the overlay + home orb. */
object OrbState {
    val mood = mutableStateOf(OrbMood.IDLE)
    fun setListening(listening: Boolean) {
        mood.value = if (listening) OrbMood.LISTENING else OrbMood.IDLE
    }
    fun setSpeaking() { mood.value = OrbMood.SPEAKING }
}
