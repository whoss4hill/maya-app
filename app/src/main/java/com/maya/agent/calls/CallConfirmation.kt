package com.maya.agent.calls

/**
 * App-owned confirmation for calls to NEW recipients (master prompt gate 3: a call to someone the owner has
 * never interacted with on this phone needs an explicit yes AFTER a read-back).
 *
 * The model cannot supply or invent the yes. Flow:
 *  1. begin(number): pending target bound to this exact number, readback not yet done.
 *  2. The model reads the number back; the next TurnComplete marks the readback finished. A barge-in
 *     (Interrupted) before that cancels the pending call: the owner may have answered before hearing it all.
 *  3. Only owner speech transcribed AFTER the readback finished counts. On confirm() the app itself evaluates
 *     that transcript (yes words, no negation, short). One-shot: success or an explicit no consumes it.
 *  4. Expires 60 s after the readback; a new begin() (any target) replaces the old pending; session reset clears it.
 * Pure logic, clock passed in, JVM tested.
 */
class CallConfirmation {
    sealed class Outcome {
        data class Confirmed(val number: String, val name: String) : Outcome()
        object NoPending : Outcome()
        object ReadbackNotFinished : Outcome()
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

    @Synchronized fun onTurnComplete(now: Long) {
        val p = pending ?: return
        if (p.readbackDoneAt < 0) { p.readbackDoneAt = now; p.heard.setLength(0) }
    }

    @Synchronized fun onInterrupted() {
        val p = pending ?: return
        if (p.readbackDoneAt < 0) pending = null
    }

    @Synchronized fun onOwnerTranscript(text: String) {
        val p = pending ?: return
        if (p.readbackDoneAt >= 0) p.heard.append(' ').append(text)
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
        private val yes = setOf("haan", "han", "ha", "haa", "haanji", "yes", "yeah", "yep", "ok", "okay", "theek", "thik", "sahi", "bilkul",
            "confirm", "confirmed", "kardo", "lagado", "lagao", "krdo", "हाँ", "हां", "हा", "ठीक", "सही", "बिल्कुल", "ओके")
        private val yesPairs = listOf("kar do", "laga do", "kr do", "कर दो", "लगा दो")
        private val no = setOf("nahi", "nahin", "nhi", "na", "no", "nope", "mat", "ruko", "ruk", "stop", "cancel", "wait", "galat", "wrong",
            "dont", "don't", "nai", "नहीं", "नही", "मत", "रुको", "रुक", "गलत", "नाही")

        fun judge(text: String): Verdict {
            val norm = text.lowercase().replace(Regex("[^\\p{L}\\p{M}\\p{N}' ]"), " ").replace(Regex("\\s+"), " ").trim()
            val words = norm.split(' ').filter { it.isNotEmpty() }
            if (words.isEmpty() || words.size > 8) return Verdict.UNCLEAR
            if (words.any { it in no } || norm.contains("ek minute") || norm.contains("ek min")) return Verdict.NO
            if (words.any { it in yes } || yesPairs.any { norm.contains(it) }) return Verdict.YES
            return Verdict.UNCLEAR
        }
    }
}
