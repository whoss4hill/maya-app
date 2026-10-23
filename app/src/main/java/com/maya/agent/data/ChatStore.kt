package com.maya.agent.data

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val time: String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
)

/** Session chat log (long-term log lives on the backend via /v1/chat). */
object ChatStore {
    val messages = mutableStateListOf<ChatMessage>()

    fun addUser(text: String) { messages.add(ChatMessage(true, text)) }
    fun addMaya(text: String) { messages.add(ChatMessage(false, text)) }
}
