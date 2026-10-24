package com.maya.agent.voice.live

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.maya.agent.agent.MayaIntent
import com.maya.agent.agent.ToolExecutor

/**
 * Maps Gemini Live function calls to the EXISTING MayaIntent/ToolExecutor
 * surface - no new capabilities, no new authority. Every existing gate stays:
 * cart stops at the confirm dialog, accessibility actions need the service,
 * nothing here can pay, book, or message anyone.
 *
 * The model speaks the result itself: the executor's spoken line is captured
 * and returned as the tool response instead of going to device TTS.
 */
object LiveToolBridge {

    /** The tool surface exposed to the Live model (functionDeclarations). */
    val DECLARATIONS_JSON: String = """[
      {"name":"set_timer","description":"Set a countdown timer on the phone clock",
       "parameters":{"type":"OBJECT","properties":{"seconds":{"type":"INTEGER"},"label":{"type":"STRING"}},"required":["seconds"]}},
      {"name":"set_alarm","description":"Set an alarm on the phone clock",
       "parameters":{"type":"OBJECT","properties":{"hour":{"type":"INTEGER"},"minute":{"type":"INTEGER"},"label":{"type":"STRING"}},"required":["hour","minute"]}},
      {"name":"add_to_cart","description":"Add an item to the shopping cart; the user always confirms in the app before anything is ordered",
       "parameters":{"type":"OBJECT","properties":{"item":{"type":"STRING"}},"required":["item"]}},
      {"name":"open_app","description":"Open an installed app by name",
       "parameters":{"type":"OBJECT","properties":{"query":{"type":"STRING"}},"required":["query"]}},
      {"name":"read_screen","description":"Read the visible text of the current screen (needs the accessibility service)",
       "parameters":{"type":"OBJECT","properties":{}}},
      {"name":"tap_text","description":"Tap a visible text/button on the current screen (needs the accessibility service)",
       "parameters":{"type":"OBJECT","properties":{"text":{"type":"STRING"}},"required":["text"]}},
      {"name":"go_back","description":"Navigate back on the phone (needs the accessibility service)",
       "parameters":{"type":"OBJECT","properties":{}}},
      {"name":"go_home","description":"Go to the phone home screen (needs the accessibility service)",
       "parameters":{"type":"OBJECT","properties":{}}}
    ]"""

    /** Pure mapping; null = unknown/unsupported call. */
    fun toIntent(call: LiveProtocol.ServerEvent.FunctionCall): MayaIntent? {
        val args = try { JsonParser.parseString(call.argsJson).asJsonObject } catch (e: Exception) { JsonObject() }
        fun str(k: String) = args.get(k)?.takeIf { it.isJsonPrimitive }?.asString
        fun int(k: String) = args.get(k)?.takeIf { it.isJsonPrimitive }?.asInt
        return when (call.name) {
            "set_timer" -> int("seconds")?.takeIf { it > 0 }?.let { MayaIntent.SetTimer(it, str("label")) }
            "set_alarm" -> {
                val h = int("hour"); val m = int("minute")
                if (h != null && m != null && h in 0..23 && m in 0..59) MayaIntent.SetAlarm(h, m, str("label")) else null
            }
            "add_to_cart" -> str("item")?.takeIf { it.isNotBlank() }?.let { MayaIntent.CartAdd(it) }
            "open_app" -> str("query")?.takeIf { it.isNotBlank() }?.let { MayaIntent.OpenApp(it) }
            "tap_text" -> str("text")?.takeIf { it.isNotBlank() }?.let { MayaIntent.TapText(it) }
            "read_screen" -> MayaIntent.ReadScreen
            "go_back" -> MayaIntent.GoBack
            "go_home" -> MayaIntent.GoHome
            else -> null
        }
    }

    /** Execute and capture the spoken line as the tool result for the model. */
    suspend fun execute(context: Context, call: LiveProtocol.ServerEvent.FunctionCall): String {
        val intent = toIntent(call)
            ?: return "{\"result\":\"Ye action voice me available nahi hai\"}"
        var spoken: String? = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            ToolExecutor(context).execute(intent) { line -> spoken = line }
        }
        val result = spoken ?: "Done"
        return JsonObject().apply { addProperty("result", result) }.toString()
    }
}
