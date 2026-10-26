package com.maya.agent.calls

/**
 * App-owned confirmation for calls to NEW recipients (master prompt gate 3: a call to someone the owner has
 * never interacted with on this phone needs an explicit yes AFTER a read-back).
 *
 * The model cannot supply or invent the yes, and it does not do the read-back: the APP speaks the exact number
 * itself (Android TTS) and only the TTS "done" callback marks the read-back finished. Flow:
 *  1. begin(number): pending target bound to this exact number, read-back not yet done.
 *  2. onReadbackSpoken(): TTS finished speaking that number. Earlier owner speech is discarded, and speech in the
 *     first 1.5 s afterwards is ignored (echo of the app's own voice).
 *  3. Only owner speech transcribed after that counts. confirm() evaluates it (call phrase + yes/action word, no negation, short).
 *     One-shot: yes or no consumes it.
 *  4. Expires 60 s after the read-back; a new begin()/call request replaces it; session reset clears it.
 * Also records recent owner speech so a call tool call is only honoured when the owner actually asked for a call
 * out loud in the last 30 s (ownerAskedForCall). Pure logic, clock passed in, JVM tested.
 */
class CallConfirmation {
    sealed class Outcome {
        data class Confirmed(val number: String, val name: String) : Outcome()
        object NoPending : Outcome()
        object ReadbackNotFinished : Outcome()  // app TTS has not finished (or failed)
        object Expired : Outcome()
        object NoAnswerHeard : Outcome()
        object Denied : Outcome()
        object Unclear : Outcome()
    }

    private class Pending(val number: String, val name: String) {
        var readbackDoneAt: Long = -1
        val heard = StringBuilder()
    }

    private var pending: Pending? = null

    @Synchronized fun begin(number: String, name: String) { pending = Pending(number, name) }
    @Synchronized fun hasPending() = pending != null
    @Synchronized fun reset() { pending = null }

    /** The app's own TTS finished speaking the number of the current pending call. */
    @Synchronized fun onReadbackSpoken(now: Long) {
        val p = pending ?: return
        if (p.readbackDoneAt < 0) { p.readbackDoneAt = now; p.heard.setLength(0) }
    }

    private val recent = ArrayDeque<Pair<Long, String>>()

    /** Drop remembered owner speech (session end/tests). reset() only drops the pending call. */
    @Synchronized fun forgetSpeech() { recent.clear() }

    @Synchronized fun onOwnerTranscript(text: String, now: Long) {
        recent.addLast(now to text)
        while (recent.isNotEmpty() && now - recent.first().first > ASK_WINDOW_MS) recent.removeFirst()
        val p = pending ?: return
        if (p.readbackDoneAt >= 0 && now >= p.readbackDoneAt + ECHO_GUARD_MS) p.heard.append(' ').append(text)
    }

    /** True when owner speech in the last 30 s contains a call request word (transcription must be working). */
    @Synchronized fun ownerAskedForCall(now: Long): Boolean {
        val txt = recent.filter { now - it.first <= ASK_WINDOW_MS }.joinToString(" ") { it.second }.lowercase()
        return callWords.any { txt.contains(it) }
    }

    @Synchronized fun confirm(now: Long): Outcome {
        val p = pending ?: return Outcome.NoPending
        if (p.readbackDoneAt < 0) return Outcome.ReadbackNotFinished
        if (now - p.readbackDoneAt > EXPIRY_MS) { pending = null; return Outcome.Expired }
        val said = p.heard.toString().trim()
        if (said.isEmpty()) return Outcome.NoAnswerHeard
        p.heard.setLength(0)
        return when (judge(said)) {
            Verdict.YES -> { pending = null; Outcome.Confirmed(p.number, p.name) }
            Verdict.NO -> { pending = null; Outcome.Denied }
            Verdict.UNCLEAR -> Outcome.Unclear
        }
    }

    enum class Verdict { YES, NO, UNCLEAR }

    companion object {
        const val EXPIRY_MS = 60_000L
        const val ASK_WINDOW_MS = 30_000L
        const val ECHO_GUARD_MS = 1500L
        private val callWords = listOf("call", "dial", "phone", "fone", "ring ", "laga", "lagao", "कॉल", "काल", "फोन", "फ़ोन", "लगा")
        private val yes = setOf("haan", "han", "ha", "haa", "haanji", "yes", "yeah", "yep", "ok", "okay", "theek", "thik", "sahi", "bilkul",
            "confirm", "confirmed", "kardo", "lagado", "lagao", "krdo", "हाँ", "हां", "हा", "ठीक", "सही", "बिल्कुल", "ओके")
        private val yesPairs = listOf("kar do", "laga do", "kr do", "कर दो", "लगा दो")
        private val no = setOf("nahi", "nahin", "nhi", "na", "no", "nope", "mat", "ruko", "ruk", "stop", "cancel", "wait", "galat", "wrong",
            "dont", "don't", "nai", "नहीं", "नही", "मत", "रुको", "रुक", "गलत", "नाही")

        private val callWord = listOf("call", "कॉल", "काल", "phone", "fone", "dial", "laga", "लगा")
        private val action = setOf("laga", "lagao", "lagado", "lagaiye", "karo", "kar", "kardo", "krdo", "karna", "do", "place", "ahead", "लगाओ", "लगा", "करो", "कर")

        /** YES needs an explicit call phrase tied to the action ("haan call lagao", "yes call", "call karo"), not a bare "haan". */
        fun judge(text: String): Verdict {
            val norm = text.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}' ]"), " ").replace(Regex("\\s+"), " ").trim()
            val words = norm.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty() || words.size > 8) return Verdict.UNCLEAR
            if (words.any { it in no } || norm.contains("ek minute") || norm.contains("ek min")) return Verdict.NO
            val hasCall = callWord.any { norm.contains(it) }
            val hasYesOrAction = words.any { it in yes || it in action } || yesPairs.any { norm.contains(it) }
            return if (hasCall && hasYesOrAction) Verdict.YES else Verdict.UNCLEAR
        }
    }
}
