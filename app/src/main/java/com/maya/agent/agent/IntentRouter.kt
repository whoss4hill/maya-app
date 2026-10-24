package com.maya.agent.agent

/**
 * On-device intent router for voice commands (Hinglish-first).
 * Pure Kotlin - no Android imports - so it is fully unit-testable on the JVM.
 *
 * Anything that is not a known phone action falls through to [MayaIntent.Chat]
 * and goes to the backend LLM, so the router can stay conservative: it only
 * intercepts when it is confident.
 */
sealed interface MayaIntent {
    data class SetTimer(val seconds: Int, val label: String?) : MayaIntent
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : MayaIntent
    data class CartAdd(val item: String) : MayaIntent
    data class OpenApp(val query: String) : MayaIntent
    object GoBack : MayaIntent
    object GoHome : MayaIntent
    data class TapText(val text: String) : MayaIntent
    object ReadScreen : MayaIntent
    data class Chat(val text: String) : MayaIntent
}

object IntentRouter {

    private val numberWords = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "tin" to 3, "chaar" to 4, "char" to 4,
        "paanch" to 5, "panch" to 5, "chhe" to 6, "chah" to 6, "saat" to 7,
        "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20,
        "thirty" to 30, "forty five" to 45, "fortyfive" to 45,
    )

    private val timerVerbs = Regex("\\b(timer|taaimar)\\b")
    private val alarmVerbs = Regex("\\b(alarm|alaarm|jagao|utha\\s*do|uthana)\\b")
    private val cartVerbs = Regex(
        "\\b(mangwa\\s*do|mangwa\\s*o|mangwao|mangao|manga\\s*do|order\\s*kar\\w*|add\\s*to\\s*cart|cart\\s*me\\s*daal\\w*|le\\s*aa\\w*|le\\s*aao)\\b"
    )
    private val backPattern = Regex("\\b(back\\s*(jao|karo|dabao)?|peeche\\s*(jao|chalo))\\b")
    private val homePattern = Regex("\\b(home\\s*(jao|karo)|ghar\\s*chalo)\\b")
    private val readPattern = Regex(
        "\\b(screen\\s*pe\\s*kya\\s*hai|kya\\s*dikh\\s*raha|padh\\s*ke\\s*suna\\w*|read\\s*(the\\s*)?screen|screen\\s*padho)\\b"
    )
    private val tapPattern = Regex(
        "^(?:tap\\s*(?:on\\s*)?|dabao\\s+|press\\s+karo\\s+|click\\s+karo\\s+|click\\s+on\\s+)(.+)$"
    )
    private val trailingTap = Regex("^(.+?)\\s*(?:dabao|daba\\s*do|dab\\s*do|press\\s*karo)$")
    private val openPattern = Regex(
        "^(?:open\\s+|kholo\\s+|khol\\s*do\\s+|chalu\\s*karo\\s*)(.+)$"
    )
    private val trailingOpen = Regex("^(.+?)\\s+(?:kholo|khol\\s*do|open\\s*karo)$")

    fun route(raw: String): MayaIntent {
        val text = raw.trim().lowercase().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return MayaIntent.Chat(raw)

        parseTimer(text)?.let { return it }
        parseAlarm(text)?.let { return it }

        cartVerbs.find(text)?.let { m ->
            val item = (text.substring(0, m.range.first) + " " + text.substring(m.range.last + 1))
                .replace(Regex("\\b(karo|kar\\s*do|please|plz|maya|mere\\s*liye)\\b"), " ")
                .replace(Regex("\\s+"), " ").trim()
            if (item.isNotEmpty()) return MayaIntent.CartAdd(item)
        }

        if (backPattern.containsMatchIn(text)) return MayaIntent.GoBack
        if (homePattern.containsMatchIn(text)) return MayaIntent.GoHome
        if (readPattern.containsMatchIn(text)) return MayaIntent.ReadScreen

        tapPattern.find(text)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isNotEmpty()) return MayaIntent.TapText(target)
        }
        trailingTap.find(text)?.let { m ->
            val target = m.groupValues[1].trim()
            if (target.isNotEmpty()) return MayaIntent.TapText(target)
        }

        openPattern.find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotEmpty()) return MayaIntent.OpenApp(q)
        }
        trailingOpen.find(text)?.let { m ->
            val q = m.groupValues[1].trim()
            if (q.isNotEmpty()) return MayaIntent.OpenApp(q)
        }

        return MayaIntent.Chat(raw.trim())
    }

    private fun parseTimer(text: String): MayaIntent.SetTimer? {
        if (!timerVerbs.containsMatchIn(text)) return null
        val m = Regex("(\\d+)\\s*(second|seconds|sec|sekond|minute|minutes|min|minit|hour|hours|ghanta|ghante)")
            .find(text) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val seconds = when (val u = m.groupValues[2]) {
            "second", "seconds", "sec", "sekond" -> n
            "hour", "hours", "ghanta", "ghante" -> n * 3600
            else -> n * 60
        }
        return MayaIntent.SetTimer(seconds, label = null)
    }

    private fun parseAlarm(text: String): MayaIntent.SetAlarm? {
        if (!alarmVerbs.containsMatchIn(text)) return null
        var hour: Int? = null
        var minute = 0
        Regex("\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)?").find(text)?.let { m ->
            var h = m.groupValues[1].toIntOrNull()
            minute = m.groupValues[2].toIntOrNull() ?: 0
            val meridiem = m.groupValues[3]
            if (meridiem == "pm" && (h ?: 12) < 12) h = h?.plus(12)
            if (meridiem == "am" && h == 12) h = 0
            hour = h
        }
        if (hour == null) {
            Regex("\\b(\\d{1,2})\\s*baje\\b").find(text)?.let { hour = it.groupValues[1].toIntOrNull() }
        }
        if (hour == null) {
            Regex("\\b(\\d{1,2})\\s*(am|pm)\\b").find(text)?.let { m ->
                hour = m.groupValues[1].toIntOrNull()
                if (m.groupValues[2] == "pm" && (hour ?: 12) < 12) hour = hour?.plus(12)
                if (m.groupValues[2] == "am" && hour == 12) hour = 0
            }
        }
        if (hour == null) {
            for ((word, value) in numberWords) {
                if (Regex("\\b$word\\s*baje\\b").containsMatchIn(text)) { hour = value; break }
            }
        }
        var h = hour ?: return null
        // Daypart words adjust the hour when no am/pm was explicit.
        val hasAmPm = Regex("\\b(am|pm)\\b").containsMatchIn(text)
        if (!hasAmPm) {
            when {
                Regex("\\b(subah|savere|morning)\\b").containsMatchIn(text) -> { if (h == 12) h = 0 }
                Regex("\\b(dopahar|afternoon)\\b").containsMatchIn(text) -> { if (h < 12) h += 12 }
                Regex("\\b(shaam|evening|raat|night|raatko)\\b").containsMatchIn(text) -> { if (h < 12) h += 12 }
            }
        }
        if (h !in 0..23) return null
        return MayaIntent.SetAlarm(h, minute, label = null)
    }
}
