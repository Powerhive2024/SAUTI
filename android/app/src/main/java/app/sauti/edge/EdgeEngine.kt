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

const val SYSTEM_PROMPT =
    "You are Sauti, a helpful assistant who speaks English, Kiswahili and Sheng. " +
        "Always answer in the same language the user used: English gets English, Kiswahili gets Kiswahili, " +
        "and Sheng (Nairobi street language, often mixed with English) gets Sheng. " +
        "Keep answers short and clear. If the user spoke, first understand what was said, then answer. " +
        "Do not make up facts; if you do not know, say so."

/**
 * On-device Gemma 3n via LiteRT-LM. Handles text and speech (16 kHz WAV) entirely offline.
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

    val isReady get() = conversation != null

    suspend fun load(modelPath: String, loraPath: String?) = withContext(Dispatchers.IO) {
        lock.withLock {
            if (loadedPath == modelPath && conversation != null) return@withLock
            closeLocked()
            // Prefer GPU for text decoding where the phone supports it; fall back to CPU.
            // LiteRT-LM currently runs Gemma 3n's audio encoder on CPU only.
            val attempts = listOf<Pair<String, Backend>>("GPU" to Backend.GPU(), "CPU" to Backend.CPU())
            var lastError: Throwable? = null
            for ((name, backend) in attempts) {
                try {
                    val e = Engine(
                        EngineConfig(
                            modelPath = modelPath,
                            backend = backend,
                            audioBackend = Backend.CPU(),
                            // Vision is not used yet; leaving it unset skips loading the image
                            // encoder (saves RAM on low-end phones; Google's file needs GPU for it).
                            visionBackend = null,
                            maxNumTokens = 2048,
                            cacheDir = context.cacheDir.path,
                        )
                    )
                    e.initialize()
                    engine = e
                    backendName = name
                    break
                } catch (t: Throwable) {
                    Log.w(TAG, "Engine init on $name failed", t)
                    lastError = t
                }
            }
            val e = engine ?: throw IllegalStateException("Could not load model", lastError)
            conversation = e.createConversation(conversationConfig(loraPath))
            loadedPath = modelPath
        }
    }

    private fun conversationConfig(loraPath: String?) = ConversationConfig(
        systemInstruction = Contents.of(SYSTEM_PROMPT),
        initialMessages = seed,
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.6),
        loraConfig = loraPath?.let { LoraConfig(loraPath = it, audioLoraPath = it) },
    )

    /** Streams the reply as text chunks. [wav] is optional speech (16 kHz mono PCM16 WAV). */
    fun reply(text: String, wav: ByteArray?, lang: Lang? = null): Flow<String> {
        val conv = conversation ?: throw IllegalStateException("Model not loaded")
        val parts = buildList {
            if (wav != null) add(Content.AudioBytes(wav))
            val ask = text.ifBlank { "Jibu kile kilichosemwa kwenye sauti hii." }
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
