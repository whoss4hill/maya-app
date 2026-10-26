package com.maya.agent.voice.live

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * create_note: writes a NEW text file the owner asked for. It never reads, edits, overwrites, moves or
 * deletes any existing file. The note text must match what the owner actually just said (app-side
 * transcript check, not the model's claim); otherwise nothing is written (preview only).
 * Location: Android 10+ -> Downloads/Maya/ (MediaStore, no permission, never replaces a file; Android
 * renames on a name clash). Android 8/9 -> Maya's private app storage, which the normal Files app
 * cannot show; the result text says so. The secret guard is best-effort, not a guarantee.
 */
object NoteTool {
    const val MAX_CHARS = 20_000
    private val noteWords = listOf("note", "memo", "likh", "yaad", "लिख", "नोट", "मेमो")

    sealed class Decision {
        data class Write(val body: String) : Decision()
        data class Refuse(val status: String, val text: String) : Decision()
    }

    private fun words(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N} ]"), " ")
        .split(Regex("\\s+")).filter { it.length >= 3 }

    /** Share of the note's content words the owner actually said. */
    fun overlap(body: String, speech: String): Double {
        val b = words(body); if (b.isEmpty()) return 0.0
        val sp = words(speech).toSet()
        return b.count { it in sp }.toDouble() / b.size
    }

    fun decide(body: String, ownerSpeech: String, looksSecret: (String) -> Boolean): Decision {
        val b = body.trim()
        if (b.isEmpty()) return Decision.Refuse("unclear", "No note text was given. Nothing was saved.")
        if (b.length > MAX_CHARS) return Decision.Refuse("too_long", "The note is too long. Nothing was saved.")
        val sp = ownerSpeech.lowercase()
        if (noteWords.none { sp.contains(it) })
            return Decision.Refuse("no_request_heard", "The app did not hear the owner ask for a note just now. Nothing was saved.")
        if (looksSecret(b))
            return Decision.Refuse("refused", "The text looks like it contains a secret or credential, so it was not saved. Nothing was written.")
        if (overlap(b, ownerSpeech) < 0.6)
            return Decision.Refuse("preview_only", "The note text does not match what the owner just said, so nothing was saved. Read the text back, ask the owner to say the note again, and do not invent content.")
        return Decision.Write(b)
    }

    fun fileName(now: Date = Date()): String = "Maya note " + SimpleDateFormat("yyyy-MM-dd HHmmss", Locale.US).format(now) + ".txt"

    /** Returns a human sentence about where the file went, or null on failure. */
    fun write(context: Context, body: String, now: Date = Date(), sdk: Int = Build.VERSION.SDK_INT): String? = runCatching {
        val name = fileName(now)
        if (sdk >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Maya")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val r = context.contentResolver
            val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return@runCatching null
            r.openOutputStream(uri, "w")?.use { it.write(body.toByteArray(Charsets.UTF_8)) } ?: return@runCatching null
            r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            "Saved a new note in the Downloads/Maya folder (Files app)."
        } else {
            val dir = File(context.filesDir, "notes").apply { mkdirs() }
            var f = File(dir, name); var i = 1
            while (f.exists()) { f = File(dir, name.removeSuffix(".txt") + " (" + i++ + ").txt") }
            f.writeText(body, Charsets.UTF_8)
            "Saved a new note inside Maya's own private storage. The normal Files app cannot show it on this Android version."
        }
    }.getOrNull()

    fun run(context: Context, argsJson: String, ownerSpeech: String): String {
        val body = runCatching { JsonParser.parseString(argsJson).asJsonObject.get("body")?.takeIf { it.isJsonPrimitive }?.asString }.getOrNull() ?: ""
        val msg = when (val d = decide(body, ownerSpeech) { LiveSecretGuard.looksLikeSecret(it) }) {
            is Decision.Refuse -> d.text
            is Decision.Write -> (write(context, d.body) ?: "Could not save the note. Nothing was saved.") +
                " Only a new file was created; no existing file was touched. Tell the owner exactly where it went."
        }
        return JsonObject().apply { addProperty("result", msg) }.toString()
    }
}
