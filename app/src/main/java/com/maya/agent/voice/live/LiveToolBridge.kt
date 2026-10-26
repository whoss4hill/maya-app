package com.maya.agent.voice.live

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.maya.agent.agent.MayaIntent
import com.maya.agent.agent.ToolExecutor
import com.maya.agent.calls.DialCommand
import com.maya.agent.calls.PhoneCommand
import com.maya.agent.calls.VoiceCall

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
      {"name":"tap_text","description":"Tap a visible text/button on the current screen (needs the accessibility service)",
       "parameters":{"type":"OBJECT","properties":{"text":{"type":"STRING"}},"required":["text"]}},
      {"name":"go_back","description":"Navigate back on the phone (needs the accessibility service)",
       "parameters":{"type":"OBJECT","properties":{}}},
      {"name":"go_home","description":"Go to the phone home screen (needs the accessibility service)",
       "parameters":{"type":"OBJECT","properties":{}}},
      {"name":"call_contact","description":"Place a phone call ONLY when the owner has just clearly asked to call someone. target is a contact name or a phone number exactly as the owner said it. If the result says ambiguous, unclear or not found, ask the owner; never guess a number. A launched call is not a connected call.",
       "parameters":{"type":"OBJECT","properties":{"target":{"type":"STRING"}},"required":["target"]}}
    ]"""

    private fun timeAndWeather(context: Context): String {
        val fmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm:ss a", java.util.Locale("en", "IN"))
        val ist = fmt.format(java.util.Date()) // device timezone, not forced IST
        var weather = "Weather data unavailable (location permission might be needed)."
        try {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val loc = runCatching {
                    lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                        ?: lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                }.getOrNull()
                if (loc != null) {
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=" + loc.latitude +
                        "&longitude=" + loc.longitude +
                        "&current_weather=true&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=auto"
                    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 8000; readTimeout = 8000
                    }
                    weather = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            weather = "Could not fetch weather data"
        }
        return "Current date & time: " + ist + ". Weather Data: " + weather
    }

    private fun callDeps(context: Context): PhoneCommand.Deps {
        fun granted(p: String) = androidx.core.content.ContextCompat.checkSelfPermission(context, p) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        return PhoneCommand.Deps(
            hasCallPermission = { granted(android.Manifest.permission.CALL_PHONE) },
            hasContactsPermission = { granted(android.Manifest.permission.READ_CONTACTS) },
            lookup = { DialCommand.lookup(context, it) },
            dial = { DialCommand.call(context, it) },
        )
    }

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
            // read_screen deliberately NOT bridged: in Live the result would be
            // transmitted to the model (new audience for screen contents). Re-enable
            // only with the user's explicit screen-context sharing grant.
            "go_back" -> MayaIntent.GoBack
            "go_home" -> MayaIntent.GoHome
            else -> null
        }
    }

    /** Execute and capture the spoken line as the tool result for the model. */
    suspend fun execute(context: Context, call: LiveProtocol.ServerEvent.FunctionCall): String {
        // Time/weather mirrors Zoya's getDateTimeAndWeather: free Open-Meteo (no key)
        // + device location; network off the main thread.
        if (call.name == "get_date_time_and_weather") {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                timeAndWeather(context)
            }
            return JsonObject().apply { addProperty("result", result) }.toString()
        }
        if (call.name == "call_contact") {
            val target = try { JsonParser.parseString(call.argsJson).asJsonObject.get("target")?.asString } catch (e: Exception) { null }
            val res = if (target.isNullOrBlank()) VoiceCall.Result("unclear", "No target given. Nothing was dialled.")
                else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { VoiceCall.place(target, callDeps(context)) }
            return JsonObject().apply { addProperty("result", res.text) }.toString()
        }
        val intent = toIntent(call)
            ?: return "{\"result\":\"Ye action voice me available nahi hai\"}"
        var spoken: String? = null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            ToolExecutor(context).execute(intent) { line -> spoken = line }
        }
        var result = spoken ?: "Done"
        // Secret guard: nothing that looks like a credential/card leaves the device
        // in a tool response (the backend /v1/chat path has the equivalent gate).
        if (LiveSecretGuard.looksLikeSecret(result)) result = LiveSecretGuard.REFUSAL
        return JsonObject().apply { addProperty("result", result) }.toString()
    }
}
