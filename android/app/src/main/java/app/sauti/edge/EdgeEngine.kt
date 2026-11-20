package app.sauti.edge

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LoraConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.sauti.lang.Lang
import app.sauti.lang.LanguageDetector
import app.sauti.media.MediaMath as MediaLimits

const val SYSTEM_PROMPT =
    "You are Sauti, a helpful assistant who speaks English, Kiswahili and Sheng. " +
        "Always answer in the same language the user used: English gets English, Kiswahili gets Kiswahili, " +
        "and Sheng (Nairobi street language, often mixed with English) gets Sheng. " +
        "Keep answers short and clear. If the user spoke, first understand what was said, then answer. " +
        "Do not make up facts; if you do not know, say so."

/**
 * On-device Gemma 3n via LiteRT-LM. Handles text, speech (16 kHz WAV) and, on phones with room for
 * the image encoder, photos and video frames, entirely offline.
 * Accepts either a fully merged fine-tuned .litertlm, or the base .litertlm + our LoRA adapter.
 */
class EdgeEngine(private val context: Context) {
    private val lock = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    /** Earlier turns of a reopened conversation, replayed into the model's context (text only). */
    private var seed: List<Message> = emptyList()
    var loadedPath: String? = null; private set
    var backendName: String = "-"; private set
    /** True when Gemma 3n's image encoder loaded, so photos and video can be sent. */
    var visionReady: Boolean = false; private set

    val isReady get() = conversation != null

    /**
     * [wantVision] also loads the image encoder (extra memory, so only on phones with room for it).
     * If the phone can't run it, Sauti still loads for text and voice.
     */
    suspend fun load(modelPath: String, loraPath: String?, wantVision: Boolean = false) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (loadedPath == modelPath && conversation != null && visionReady == wantVision) return@withLock
            closeLocked()
            // Prefer GPU for text decoding where the phone supports it; fall back to CPU.
            // LiteRT-LM runs Gemma 3n's audio encoder on CPU. The image encoder prefers the GPU.
            data class Try(val name: String, val backend: Backend, val vision: Backend?)
            // Text always runs on the CPU: it works on every phone, while some GPU drivers crash the
            // native runtime. The GPU is only tried for the image encoder, which requires it, and only
            // when the user switched on "Photos and video".
            val withVision = if (wantVision) listOf(Try("CPU", Backend.CPU(), Backend.GPU())) else emptyList()
            val all = withVision + listOf(Try("CPU", Backend.CPU(), null))
            // Crash guard: some GPU drivers crash inside the native runtime (no exception to catch).
            // Each attempt is written down first; one still written down at the next start crashed,
            // so it is skipped from then on. Text on CPU without vision is never skipped.
            val marker = java.io.File(context.filesDir, "engine-attempt")
            val crashedFile = java.io.File(context.filesDir, "engine-crashed")
            if (marker.exists()) { crashedFile.appendText(marker.readText().trim() + "\n"); marker.delete() }
            val crashed = if (crashedFile.exists()) crashedFile.readLines().map { it.trim() }.toSet() else emptySet()
            fun key(a: Try) = "${a.name}/${if (a.vision == null) "none" else if (a.vision is Backend.GPU) "gpu" else "cpu"}"
            val attempts = all.filter { key(it) !in crashed || key(it) == "CPU/none" }
            if (crashed.isNotEmpty()) Log.w(TAG, "skipping options that crashed before: $crashed")
            var lastError: Throwable? = null
            for (a in attempts) {
                var e: Engine? = null
                marker.writeText(key(a))
                try {
                    e = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = a.backend,
                            audioBackend = Backend.CPU(),
                            // Without vision the image encoder isn't loaded at all (saves RAM on small phones).
                            visionBackend = a.vision,
                            maxNumTokens = 2048,
                            maxNumImages = if (a.vision != null) MediaLimits.MAX_IMAGES else null,
                            cacheDir = context.cacheDir.path,
                        )
                    )
                    e.initialize()
                    // Some backends (e.g. the image encoder on a GPU without support) only fail here,
                    // so the first conversation is part of the attempt; on failure try the next option.
                    val c = e.createConversation(conversationConfig(loraPath))
                    engine = e
                    conversation = c
                    backendName = a.name
                    visionReady = a.vision != null
                    Log.i(TAG, "Engine ready: text on ${a.name}, vision ${if (a.vision != null) "on" else "off"}")
                    marker.delete()
                    break
                } catch (t: Throwable) {
                    Log.w(TAG, "Engine init (${a.name}, vision=${a.vision != null}) failed", t)
                    runCatching { e?.close() }
                    marker.delete() // failed cleanly (no crash): may be tried again next time
                    lastError = t
                }
            }
            if (conversation == null) throw IllegalStateException("Could not load model", lastError)
            loadedPath = modelPath
        }
    }

    private fun conversationConfig(loraPath: String?) = ConversationConfig(
        systemInstruction = Contents.of(SYSTEM_PROMPT),
        initialMessages = seed,
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6),
        loraConfig = loraPath?.let { LoraConfig(loraPath = it, audioLoraPath = it) },
    )

    /**
     * Streams the reply as text chunks. [wav] is optional speech (16 kHz mono PCM16 WAV);
     * [images] are optional JPEG photos or video frames (only when [visionReady]).
     */
    fun reply(text: String, wav: ByteArray?, lang: Lang? = null, images: List<ByteArray> = emptyList()): Flow<String> {
        val conv = conversation ?: throw IllegalStateException("Model not loaded")
        val parts = buildList {
            if (visionReady) images.take(MediaLimits.MAX_IMAGES).forEach { add(Content.ImageBytes(it)) }
            if (wav != null) add(Content.AudioBytes(wav))
            val ask = text.ifBlank {
                if (images.isNotEmpty() && wav == null) "Eleza unachokiona kwenye picha hii, kwa ufupi."
                else "Jibu kile kilichosemwa kwenye sauti hii."
            }
            // hidden per-turn steer so the reply stays in the user's language
            add(Content.Text(if (lang != null) ask + "\n\n" + LanguageDetector.replyInstruction(lang) else ask))
        }
        return flow {
            // Hold back a short tail so a repetition loop is caught before it reaches the screen
            // or the voice; once a loop is found, generation is stopped (saves battery too).
            val acc = StringBuilder()
            var sent = 0
            var looped = false
            conv.sendMessageAsync(Contents.of(parts)).map { it.toString() }.takeWhile { chunk ->
                acc.append(chunk)
                val cut = LoopGuard.cut(acc)
                if (cut != null) {
                    Log.i(TAG, "repetition loop stopped at $cut chars")
                    acc.setLength(maxOf(cut, sent))
                    looped = true
                }
                if (acc.length - HOLD_BACK > sent) { emit(acc.substring(sent, acc.length - HOLD_BACK)); sent = acc.length - HOLD_BACK }
                !looped
            }.collect()
            if (looped) conv.cancelProcess()
            if (acc.length > sent) emit(acc.substring(sent).let { if (looped) it.trimEnd(' ', ',', '-') else it })
        }.flowOn(Dispatchers.IO)
    }

    /** Speech -> text only (used before sending a *transcript* to the online tier, never audio). */
    suspend fun transcribe(wav: ByteArray): String = withContext(Dispatchers.IO) {
        val e = engine ?: throw IllegalStateException("Model not loaded")
        e.createConversation(ConversationConfig(samplerConfig = SamplerConfig(1, 1.0, 0.0))).use { c ->
            c.sendMessage(
                Contents.of(Content.AudioBytes(wav), Content.Text("Andika maneno yaliyosemwa kwenye sauti hii."))
            ).toString().trim().let(LoopGuard::clean)
        }
    }

    fun cancel() = conversation?.cancelProcess()

    /**
     * Starts a fresh chat (clears on-device context). [history] = earlier (fromUser, text) turns of a
     * reopened conversation, so the model remembers it; only the most recent ones that fit are kept.
     */
    suspend fun reset(loraPath: String?, history: List<Pair<Boolean, String>> = emptyList()) = withContext(Dispatchers.IO) {
        lock.withLock {
            seed = seedFrom(history)
            conversation?.close()
            conversation = engine?.createConversation(conversationConfig(loraPath))
        }
    }

    suspend fun close() = lock.withLock { closeLocked() }

    private fun closeLocked() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        loadedPath = null
        visionReady = false
    }

    companion object {
        private const val TAG = "EdgeEngine"
        /** Context budget for a replayed conversation (~1k tokens of the 2048), so the new reply still fits. */
        private const val SEED_CHARS = 3000
        private const val SEED_TURN_CHARS = 600

        private fun seedFrom(history: List<Pair<Boolean, String>>): List<Message> {
            val picked = ArrayDeque<Pair<Boolean, String>>()
            var used = 0
            for ((user, text) in history.asReversed()) {
                val t = text.trim().take(SEED_TURN_CHARS)
                if (t.isEmpty()) continue
                if (used + t.length > SEED_CHARS) break
                picked.addFirst(user to t); used += t.length
            }
            while (picked.isNotEmpty() && !picked.first().first) picked.removeFirst() // start on a user turn
            return picked.map { (user, t) -> if (user) Message.user(t) else Message.model(t) }
        }
        private const val HOLD_BACK = 64
    }
}
