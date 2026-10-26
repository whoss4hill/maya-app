package com.maya.agent.voice.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Weather for the owner's CURRENT location only, on the owner's request.
 * - Location is request-scoped: a fix older than MAX_FIX_AGE_MS (by its platform
 *   elapsed-realtime timestamp) is never used; a fresh one is requested under the
 *   already granted permission. No fix -> an honest "could not get location".
 * - Named places are unsupported (no geocoder) and are never replaced by the
 *   current location.
 * - Provider: Open-Meteo free API (non-commercial, CC BY 4.0). Coordinates are
 *   rounded to 2 decimals (about 1 km): less precise, NOT anonymous.
 * - The model gets a short summary with times and source, never raw JSON.
 */
object WeatherTool {
    const val MAX_FIX_AGE_MS = 15 * 60 * 1000L
    private const val FRESH_FIX_TIMEOUT_MS = 10_000L

    /** Strictly younger than 15 min; negative ages (clock/boot mismatch) are invalid. */
    /** Spoken by the APP's own TTS after a successful weather result, independent of what the model says. */
    const val SPOKEN_CREDIT = "Weather data by Open-Meteo dot com."

    fun isFresh(ageMs: Long): Boolean = ageMs in 0 until MAX_FIX_AGE_MS

    fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    fun url(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=" + round2(lat) + "&longitude=" + round2(lon) +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&forecast_days=1&timezone=auto"

    fun codeText(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "mostly clear / partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67 -> "rain"
        in 71..77 -> "snow"
        in 80..82 -> "rain showers"
        85, 86 -> "snow showers"
        95 -> "thunderstorm"
        96, 99 -> "thunderstorm with hail"
        else -> "unknown conditions"
    }

    /** Short model-facing summary; null if the provider payload is unusable. */
    fun summarize(providerJson: String, checkedAt: String): String? = runCatching {
        val root = JsonParser.parseString(providerJson).asJsonObject
        val cur = root.getAsJsonObject("current")
        val daily = root.getAsJsonObject("daily")
        fun d(o: JsonObject, k: String) = o.get(k).takeIf { it != null && !it.isJsonNull }?.asDouble
        val t = d(cur, "temperature_2m") ?: return null
        val sb = StringBuilder()
        sb.append("Weather at the phone's current location: ")
        sb.append(codeText(cur.get("weather_code").asInt)).append(", ")
        sb.append(t).append(" C")
        d(cur, "apparent_temperature")?.let { sb.append(" (feels like ").append(it).append(" C)") }
        d(cur, "relative_humidity_2m")?.let { sb.append(", humidity ").append(it.toInt()).append("%") }
        d(cur, "wind_speed_10m")?.let { sb.append(", wind ").append(it).append(" km/h") }
        val tz = root.get("timezone_abbreviation")?.takeIf { !it.isJsonNull }?.asString ?: root.get("timezone")?.takeIf { !it.isJsonNull }?.asString
        sb.append(". Observed at (provider time, local to that location").append(if (tz != null) ", " + tz else "").append("): ").append(cur.get("time").asString).append(".")
        val hi = daily.getAsJsonArray("temperature_2m_max")?.get(0)?.takeIf { !it.isJsonNull }?.asDouble
        val lo = daily.getAsJsonArray("temperature_2m_min")?.get(0)?.takeIf { !it.isJsonNull }?.asDouble
        if (hi != null && lo != null) sb.append(" Forecast for today (that location, calendar day): ").append(lo).append(" to ").append(hi).append(" C")
        daily.getAsJsonArray("precipitation_probability_max")?.get(0)?.takeIf { !it.isJsonNull }
            ?.let { sb.append(", rain chance up to ").append(it.asInt).append("%") }
        sb.append(". Checked by Maya at ").append(checkedAt).append(". Source: Open-Meteo.com (CC BY 4.0). Tell the owner when it was checked.")
        sb.toString()
    }.getOrNull()

    enum class Gate { ALLOWED, NO_REQUEST_HEARD, OTHER_PLACE }

    private val weatherWords = listOf("weather", "mausam", "temperature", "tapman", "barish", "baarish", "rain", "forecast", "garmi", "thand", "umbrella", "chhata", "मौसम", "तापमान", "बारिश", "गर्मी", "ठंड")
    private val hereWords = setOf("aaj", "abhi", "yahan", "yaha", "yahaan", "mera", "meri", "kal", "is", "ye", "today", "current", "here", "my", "now", "tomorrow", "the",
        "आज", "अभी", "यहाँ", "यहां", "मेरा", "कल", "यह")
    private val placePatterns = listOf(
        Regex("(\\S+)\\s+(?:ka|ki|ke|का|की|के)\\s+(?:mausam|weather|मौसम|temperature|tapman|तापमान)"),
        Regex("(?:weather|temperature|forecast|rain)\\s+(?:in|at|of|for|near)\\s+(\\S+)"))

    /** App-side scope check on what the owner actually said (not on the model's claim). */
    fun gate(ownerSpeech: String): Gate {
        val t = ownerSpeech.lowercase()
        if (weatherWords.none { t.contains(it) }) return Gate.NO_REQUEST_HEARD
        for (re in placePatterns) for (m in re.findAll(t)) {
            val w = m.groupValues[1].trim(',', '.', '?', '!')
            if (w.isNotEmpty() && w !in hereWords) return Gate.OTHER_PLACE
        }
        return Gate.ALLOWED
    }

    private fun result(msg: String) = JsonObject().apply { addProperty("result", msg) }.toString()

    private suspend fun freshLocation(context: Context): Location? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        for (p in providers) {
            val last = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (last != null) {
                val age = (SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos) / 1_000_000
                if (isFresh(age)) return last
            }
        }
        for (p in providers) {
            val got = kotlinx.coroutines.withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val sig = CancellationSignal()
                    cont.invokeOnCancellation { sig.cancel() }
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(lm, p, sig, ContextCompat.getMainExecutor(context)) {
                            if (cont.isActive) cont.resume(it)
                        }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
            if (got != null) return got
        }
        return null
    }

    suspend fun run(
        context: Context, argsJson: String, ownerSpeech: String,
        speakCredit: (String) -> Boolean = { ReadbackSpeaker.speak(context.applicationContext, it, {}, {}) },
    ): String {
        when (gate(ownerSpeech)) {
            Gate.NO_REQUEST_HEARD -> return result("Maine abhi owner se mausam ki request nahi suni, isliye location ya mausam nahi li. Owner ke poochne par hi dobara try karo.")
            Gate.OTHER_PLACE -> return result("Abhi sirf phone ki current location ka mausam available hai; kisi aur jagah ka nahi. Owner ko yehi batao, current location ka mausam us jagah ka mat batana.")
            Gate.ALLOWED -> {}
        }
        val place = runCatching {
            JsonParser.parseString(argsJson).asJsonObject.get("place")?.takeIf { !it.isJsonNull }?.asString
        }.getOrNull()
        if (!place.isNullOrBlank()) {
            return result("Abhi sirf phone ki current location ka mausam available hai; kisi aur jagah ($place) ka nahi. Owner ko yehi batao, current location ka mausam us jagah ka mat batana.")
        }
        val loc = freshLocation(context)
            ?: return result("Location nahi mil paayi (permission ya location off ho sakti hai), isliye mausam nahi bata sakti. Owner ko honestly batao.")
        val body = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val conn = (java.net.URL(url(loc.latitude, loc.longitude)).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 8000; readTimeout = 8000
                }
                try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
            }.getOrNull()
        } ?: return result("Mausam service se data nahi aa paaya (network issue). Owner ko honestly batao.")
        val checkedAt = java.text.SimpleDateFormat("d MMM yyyy, h:mm a z", java.util.Locale("en", "IN")).format(java.util.Date())
        val summary = summarize(body, checkedAt) ?: return result("Mausam ka data samajh nahi aaya. Owner ko honestly batao.")
        speakCredit(SPOKEN_CREDIT)
        return result(summary)
    }
}
