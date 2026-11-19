package app.sauti.edge

/**
 * Stops "stuck" generations, e.g. a hard Sheng clip transcribed as "ni- ni- ni- ni- …" until the
 * token limit. Besides being wrong, a loop wastes battery and makes the phone talk nonsense.
 *
 * The check is character-periodicity on the tail of the text, so it does not care where a chunk
 * boundary falls inside the repeated unit. Same rule as scripts/loop_guard.py (used in the eval).
 */
object LoopGuard {
    private const val MAX_PERIOD = 150

    /** Repeats needed before we call it a loop: short units need more, so "haha" or "ee" survive. */
    private fun window(period: Int) = maxOf(24, period * if (period < 8) 6 else 3)

    /**
     * If [text] ends in a loop, returns the length to keep (the text up to and including the
     * first copy of the repeated unit); otherwise null.
     */
    fun cut(text: CharSequence): Int? {
        val n = text.length
        for (p in 1..MAX_PERIOD) {
            val w = window(p)
            if (w > n) break
            var periodic = true
            for (i in n - w + p until n) if (text[i] != text[i - p]) { periodic = false; break }
            if (!periodic) continue
            // grow the loop backwards to its true start, then keep exactly one copy
            var start = n - w
            while (start > 0 && text[start - 1] == text[start - 1 + p]) start--
            // finish the word we are in, so the kept copy never ends mid-word
            var keep = start + p
            while (keep < n && !text[keep].isWhitespace()) keep++
            return keep
        }
        return null
    }

    /** The text with any trailing loop reduced to one copy (for finished text, e.g. transcripts). */
    fun clean(text: String): String = cut(text)?.let { text.substring(0, it).trimEnd(' ', ',', '-') } ?: text
}
