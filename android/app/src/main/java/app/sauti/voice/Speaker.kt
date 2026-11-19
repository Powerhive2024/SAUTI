package app.sauti.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import app.sauti.lang.Lang
import java.util.concurrent.atomic.AtomicInteger

/**
 * Speaks Sauti's replies with the phone's own text-to-speech engine (offline once the voice is
 * installed; Google TTS ships Kiswahili). Replies are spoken sentence by sentence while the model
 * is still writing, so Sauti starts talking quickly even on slow phones.
 */
class Speaker(context: Context) {
    private val pending = AtomicInteger(0)
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    @Volatile var swahiliVoice: Locale? = null; private set
    @Volatile var englishVoice: Locale = Locale.UK; private set
    @Volatile var ready = false; private set
    private var counter = 0L

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            swahiliVoice = listOf(Locale("sw", "KE"), Locale("sw", "TZ"), Locale("sw")).firstOrNull {
                tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
            }
            englishVoice = listOf(Locale("en", "KE"), Locale.UK, Locale.US).firstOrNull {
                tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
            } ?: Locale.UK
            ready = true
            Log.i(TAG, "TTS ready; Swahili voice = $swahiliVoice")
        } else Log.w(TAG, "TTS init failed: $status")
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { _speaking.value = true }
            override fun onDone(id: String?) { finishOne() }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { finishOne() }
            override fun onError(id: String?, errorCode: Int) { finishOne() }
            override fun onStop(id: String?, interrupted: Boolean) { finishOne() }
        })
    }

    private fun finishOne() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            _speaking.value = false
        }
    }

    /**
     * Queue one chunk of a reply, in the voice of the user's language: English → English voice;
     * Kiswahili and Sheng → the Swahili voice (Sheng is read with Swahili sounds). A clearly English
     * sentence inside a Kiswahili reply still gets the English voice.
     */
    fun say(text: String, lang: Lang? = null) {
        val clean = forSpeech(text)
        if (!ready || clean.isBlank()) return
        val english = when (lang) {
            Lang.ENGLISH -> true
            Lang.SHENG -> false
            Lang.SWAHILI -> looksEnglish(clean)
            null -> looksEnglish(clean)
        }
        val locale = if (english) englishVoice else (swahiliVoice ?: englishVoice)
        tts.language = locale
        tts.setSpeechRate(0.95f)
        pending.incrementAndGet()
        _speaking.value = true
        tts.speak(clean, TextToSpeech.QUEUE_ADD, null, "sauti-${counter++}")
    }

    fun stop() {
        pending.set(0)
        tts.stop()
        _speaking.value = false
    }

    fun shutdown() = tts.shutdown()

    companion object {
        private const val TAG = "Speaker"

        /** Drop markdown, emoji and bracketed asides that read badly aloud. */
        fun forSpeech(s: String): String = s
            .replace(Regex("""\*\*|__|`|#+\s"""), "")
            .replace(Regex("""^\s*[*\-•]\s+""", RegexOption.MULTILINE), "")
            .replace(Regex("""\([^)]*(Translation|Tafsiri)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[\p{So}\p{Cs}\x{FE0F}]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        private val SW = Regex("""\b(na|ya|wa|kwa|ni|za|la|kama|lakini|hii|hiyo|sana|kuna|ndio|hapana|sasa|poa|msee|niaje|mambo|sawa|yako|wewe|mimi)\b""", RegexOption.IGNORE_CASE)
        private val EN = Regex("""\b(the|and|is|are|of|to|you|that|this|with|for|it|what|how|your|can)\b""", RegexOption.IGNORE_CASE)

        fun looksEnglish(s: String): Boolean = EN.findAll(s).count() > SW.findAll(s).count() + 1

        /**
         * Splits streamed text into speakable sentences. Returns (complete sentences, remainder).
         * A sentence ends at . ! ? or a newline, followed by whitespace.
         */
        fun takeSentences(buf: String): Pair<List<String>, String> {
            val out = ArrayList<String>()
            var start = 0
            val re = Regex("""[.!?…]+["')\]]?\s+|\n+""")
            for (m in re.findAll(buf)) {
                val end = m.range.last + 1
                val piece = buf.substring(start, end).trim()
                if (piece.isNotEmpty()) out += piece
                start = end
            }
            return out to buf.substring(start)
        }
    }
}
