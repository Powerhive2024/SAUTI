package app.sauti.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sauti.SautiApp
import app.sauti.audio.VoiceRecorder
import app.sauti.cloud.CloudClient
import app.sauti.edge.SYSTEM_PROMPT
import app.sauti.history.ConversationSummary
import app.sauti.history.HistoryStore
import app.sauti.history.SavedConversation
import app.sauti.lang.Lang
import app.sauti.lang.LanguageDetector
import app.sauti.privacy.Redactor
import app.sauti.router.Route
import app.sauti.settings.NetworkMode
import app.sauti.settings.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Where { DEVICE, ONLINE }

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val spoken: Boolean = false,
    val where: Where? = null,
    val note: String? = null,
    /** Language Sauti answered in (mirrors the user). */
    val lang: Lang? = null,
)

enum class EngineState { NO_MODEL, LOADING, READY, ERROR }

data class PendingConsent(val question: String, val reason: String, val msgId: Long)

/** What Sauti is doing right now — each maps to its own thinking-orb animation. */
enum class Activity {
    NO_MODEL, LOADING_MODEL, IMPORTING, IDLE, LISTENING, UNDERSTANDING, THINKING, ANSWERING, SPEAKING, ONLINE
}

data class UiState(
    val activity: Activity = Activity.NO_MODEL,
    val messages: List<ChatMessage> = emptyList(),
    val engine: EngineState = EngineState.NO_MODEL,
    val engineDetail: String = "",
    val busy: Boolean = false,
    val recording: Boolean = false,
    val level: Float = 0f,
    val consent: PendingConsent? = null,
    val laterQueue: List<String> = emptyList(),
    val importProgress: Float? = null,
    /** Id of the saved conversation on screen (null = new, not saved yet). */
    val conversationId: String? = null,
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val s = app as SautiApp
    private val recorder = VoiceRecorder()
    private var nextId = 1L
    private var genJob: Job? = null
    /** The conversation's current language; follows whatever the user last spoke or typed. */
    private var lang: Lang? = null
    private var createdAt = 0L
    /** Conversations the user deleted: a reply still finishing in the background must not bring them back. */
    private val deleted = mutableSetOf<String>()

    /** Past conversations, kept only on this phone (newest first). */
    private val _history = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val history: StateFlow<List<ConversationSummary>> = _history.asStateFlow()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val settings: StateFlow<Settings> = s.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val recommendedTier get() = s.device.recommendedTier()

    init {
        refreshHistory()
        // when Sauti finishes talking, go back to "ready"
        viewModelScope.launch {
            s.speaker.speaking.collect { talking ->
                if (!talking && _ui.value.activity == Activity.SPEAKING) _ui.update { it.copy(activity = Activity.IDLE) }
            }
        }
        viewModelScope.launch {
            val st = s.settings.settings.first()
            val path = st.modelPath ?: s.models.list().firstOrNull { !s.models.isAdapter(it) }?.absolutePath
            if (path != null) loadModel(path, st.loraPath)
        }
    }

    fun loadModel(path: String, loraPath: String? = settings.value.loraPath) {
        viewModelScope.launch {
            _ui.update { it.copy(engine = EngineState.LOADING, engineDetail = "", activity = Activity.LOADING_MODEL) }
            runCatching { s.edge.load(path, loraPath) }
                .onSuccess {
                    s.settings.setModelPath(path)
                    _ui.update { it.copy(engine = EngineState.READY, engineDetail = s.edge.backendName, activity = Activity.IDLE) }
                }
                .onFailure { e -> _ui.update { it.copy(engine = EngineState.ERROR, engineDetail = e.message ?: "", activity = Activity.NO_MODEL) } }
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(importProgress = 0f, activity = Activity.IMPORTING) }
            runCatching { s.models.import(uri) { p -> _ui.update { u -> u.copy(importProgress = p) } } }
                .onSuccess { f ->
                    _ui.update { it.copy(importProgress = null) }
                    if (s.models.isAdapter(f)) {
                        s.settings.setLoraPath(f.absolutePath)
                        settings.value.modelPath?.let { loadModel(it, f.absolutePath) }
                    } else loadModel(f.absolutePath)
                }
                .onFailure { e -> _ui.update { it.copy(importProgress = null, engineDetail = e.message ?: "", activity = idleOr()) } }
        }
    }

    fun setMode(m: NetworkMode) = viewModelScope.launch { s.settings.setMode(m) }
    fun setServer(u: String) = viewModelScope.launch { s.settings.setServerUrl(u) }

    /** The Voice ⇄ Text toggle. Switching to text silences any reply being spoken. */
    fun setVoiceMode(on: Boolean) = viewModelScope.launch {
        if (!on) stopSpeaking()
        s.settings.setVoiceMode(on)
    }

    fun stopSpeaking() {
        s.speaker.stop()
        if (_ui.value.activity == Activity.SPEAKING) _ui.update { it.copy(activity = Activity.IDLE) }
    }

    private val speakReplies get() = settings.value.voiceMode

    // ---------------------------------------------------------------- voice
    fun startRecording() {
        stopSpeaking() // tapping the mic interrupts Sauti, like in a real conversation
        if (_ui.value.recording || _ui.value.busy) return
        _ui.update { it.copy(recording = true, activity = Activity.LISTENING) }
        viewModelScope.launch {
            val wav = recorder.record { lvl -> _ui.update { u -> u.copy(level = lvl) } }
            _ui.update { it.copy(recording = false, level = 0f, activity = idleOr()) }
            if (wav.size > 44 + VoiceRecorder.SR / 2) send("", wav) // ignore taps shorter than ~0.25 s
        }
    }

    fun stopRecording() = recorder.stop()

    /** Send already-recorded speech (16 kHz WAV) — same path as the mic. Waits for the model if needed. */
    fun sendVoice(wav: ByteArray) {
        viewModelScope.launch {
            while (_ui.value.engine != EngineState.READY) kotlinx.coroutines.delay(500)
            send("", wav)
        }
    }

    // ---------------------------------------------------------------- chat
    fun send(text: String, wav: ByteArray? = null) {
        if (_ui.value.engine != EngineState.READY || _ui.value.busy) return
        stopSpeaking()
        val userMsg = ChatMessage(nextId++, true, if (wav != null) "🎤 …" else text, spoken = wav != null)
        _ui.update { it.copy(messages = it.messages + userMsg, busy = true) }
        genJob = viewModelScope.launch {
            // Speech is understood on the phone first; only its transcript could ever go online.
            val question = if (wav != null) {
                _ui.update { it.copy(activity = Activity.UNDERSTANDING) }
                runCatching { s.edge.transcribe(wav) }.getOrDefault("").also { t ->
                    replace(userMsg.id) { it.copy(text = "🎤 ${t.ifBlank { "…" }}") }
                }
            } else text
            lang = LanguageDetector.detect(question, lang)
            val d = s.router.decide(question, settings.value)
            when (d.route) {
                Route.EDGE -> answerOnDevice(question, wav)
                Route.ONLINE -> answerOnline(question, d.reason)
                Route.ASK_CONSENT -> {
                    _ui.update { it.copy(busy = false, activity = Activity.IDLE, consent = PendingConsent(question, d.reason, userMsg.id)) }
                    persist()
                }
            }
        }
    }

    fun consent(accept: Boolean) {
        val c = _ui.value.consent ?: return
        _ui.update { it.copy(consent = null, busy = true) }
        genJob = viewModelScope.launch {
            if (accept) answerOnline(c.question, c.reason) else answerOnDevice(c.question, null)
        }
    }

    private suspend fun answerOnDevice(question: String, wav: ByteArray?) {
        val id = nextId++
        val replyLang = lang
        _ui.update { it.copy(messages = it.messages + ChatMessage(id, false, "", where = Where.DEVICE, lang = replyLang), activity = Activity.THINKING) }
        val sb = StringBuilder()
        var unspoken = ""
        runCatching {
            s.edge.reply(question, wav, replyLang).collect { chunk ->
                if (sb.isEmpty()) _ui.update { it.copy(activity = Activity.ANSWERING) }
                sb.append(chunk)
                replace(id) { it.copy(text = sb.toString()) }
                if (speakReplies) { // start talking sentence by sentence, before the reply is finished
                    val (sentences, rest) = app.sauti.voice.Speaker.takeSentences(unspoken + chunk)
                    sentences.forEach { s.speaker.say(it, replyLang) }
                    unspoken = rest
                }
            }
        }.onFailure { e -> replace(id) { it.copy(text = sb.toString(), note = e.message) } }
        if (speakReplies && unspoken.isNotBlank()) s.speaker.say(unspoken, replyLang)
        finishTurn()
    }

    private suspend fun answerOnline(question: String, reason: String) {
        val id = nextId++
        val replyLang = lang
        _ui.update { it.copy(messages = it.messages + ChatMessage(id, false, "", where = Where.ONLINE, note = reason, lang = replyLang), activity = Activity.ONLINE) }
        val history = _ui.value.messages.filter { it.text.isNotBlank() && it.id != id }.map {
            CloudClient.Turn(if (it.fromUser) "user" else "assistant", Redactor.redact(it.text.removePrefix("🎤 ")))
        }.let { h ->
            // same hidden language steer as on the phone, on the newest user turn
            val i = h.indexOfLast { it.role == "user" }
            if (i < 0 || replyLang == null) h
            else h.toMutableList().also { it[i] = it[i].copy(text = it[i].text + "\n\n" + LanguageDetector.replyInstruction(replyLang)) }
        }
        runCatching { s.cloud.complete(settings.value.serverUrl, history, SYSTEM_PROMPT) }
            .onSuccess { a ->
                replace(id) { it.copy(text = a) }
                if (speakReplies) app.sauti.voice.Speaker.takeSentences(a + " ").let { (ss, r) -> (ss + r).forEach { s.speaker.say(it, replyLang) } }
            }
            .onFailure {
                // Graceful degradation: answer on the phone and keep the question for later.
                _ui.update { u ->
                    u.copy(messages = u.messages.filter { m -> m.id != id }, laterQueue = u.laterQueue + question)
                }
                answerOnDevice(question, null)
                val last = _ui.value.messages.last().id
                replace(last) { m -> m.copy(note = "offline-fallback") }
                return
            }
        finishTurn()
    }

    fun cancel() {
        s.edge.cancel(); genJob?.cancel(); _ui.update { it.copy(busy = false, activity = idleOr()) }
        persist()
    }

    fun newChat(save: Boolean = true) = viewModelScope.launch {
        stopTurn(save)
        s.edge.reset(settings.value.loraPath)
        lang = null
        _ui.update { it.copy(messages = emptyList(), conversationId = null, consent = null) }
    }

    // ---------------------------------------------------------------- history (on this phone only)
    fun setSaveHistory(on: Boolean) = viewModelScope.launch {
        s.settings.setSaveHistory(on)
        if (on) persist() // start keeping the conversation that is open now
    }

    /** Reopens a saved conversation; the model gets its earlier turns back so the chat can go on. */
    fun openConversation(id: String) = viewModelScope.launch {
        if (id == _ui.value.conversationId) return@launch
        val c = s.history.load(id) ?: run { refreshHistory(); return@launch }
        stopTurn()
        s.edge.reset(settings.value.loraPath, c.messages.map { it.fromUser to it.text.removePrefix(HistoryStore.VOICE_PREFIX) })
        nextId = (c.messages.maxOfOrNull { it.id } ?: 0L) + 1
        lang = c.messages.lastOrNull { !it.fromUser && it.lang != null }?.lang
        createdAt = c.createdAt
        _ui.update { it.copy(messages = c.messages, conversationId = c.id, consent = null, activity = idleOr()) }
    }

    fun deleteConversation(id: String) = viewModelScope.launch {
        deleted += id
        s.history.delete(id)
        if (id == _ui.value.conversationId) newChat(save = false).join()
        refreshHistory()
    }

    fun deleteAllHistory() = viewModelScope.launch {
        _ui.value.conversationId?.let { deleted += it }
        newChat(save = false).join()
        s.history.deleteAll()
        refreshHistory()
    }

    private fun refreshHistory() = viewModelScope.launch { _history.value = s.history.list() }

    /** Stops whatever Sauti is doing (and saves what's on screen) before switching conversation. */
    private suspend fun stopTurn(save: Boolean = true) {
        stopSpeaking()
        if (_ui.value.recording) recorder.stop()
        if (_ui.value.busy) { s.edge.cancel(); genJob?.cancel() }
        _ui.update { it.copy(busy = false, activity = idleOr()) }
        if (save) saveNow()
    }

    /** Saves the conversation on screen, if history is on and there is something to keep. */
    private fun persist() = viewModelScope.launch { saveNow() }

    private suspend fun saveNow() = withContext(NonCancellable) {
        if (!settings.value.saveHistory) return@withContext
        val u = _ui.value
        val msgs = HistoryStore.keepable(u.messages)
        if (msgs.none { it.fromUser } || u.conversationId in deleted) return@withContext
        val id = u.conversationId ?: HistoryStore.newId().also { newId ->
            createdAt = System.currentTimeMillis()
            _ui.update { if (it.conversationId == null) it.copy(conversationId = newId) else it }
        }
        runCatching {
            s.history.save(SavedConversation(id, HistoryStore.titleFor(msgs), createdAt, System.currentTimeMillis(), msgs))
        }
        _history.value = s.history.list()
    }

    private fun finishTurn() {
        _ui.update { it.copy(busy = false, activity = if (s.speaker.speaking.value) Activity.SPEAKING else Activity.IDLE) }
        persist()
    }

    private fun idleOr() = if (_ui.value.engine == EngineState.READY) Activity.IDLE else Activity.NO_MODEL

    private fun replace(id: Long, f: (ChatMessage) -> ChatMessage) =
        _ui.update { u -> u.copy(messages = u.messages.map { if (it.id == id) f(it) else it }) }
}
