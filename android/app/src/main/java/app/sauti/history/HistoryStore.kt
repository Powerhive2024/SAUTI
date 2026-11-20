package app.sauti.history

import app.sauti.lang.Lang
import app.sauti.ui.ChatMessage
import app.sauti.ui.Where
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One row in the history list (no message bodies, so the list stays cheap to load). */
data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val messageCount: Int,
    val bytes: Long,
)

data class SavedConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messages: List<ChatMessage>,
)

/**
 * Conversation history kept **only on this phone**: one small JSON file per conversation in the
 * app's private storage (`files/history/`). No database library, no network, no sync, no telemetry;
 * the app has `allowBackup="false"`, so it is not copied to cloud backups either.
 * Voice turns are stored as their text transcript; recorded audio is never written here.
 * Uninstalling the app, or "Delete all history" in Settings, removes everything.
 */
class HistoryStore(private val dir: File) {
    private val lock = Mutex()

    suspend fun list(): List<ConversationSummary> = withContext(Dispatchers.IO) {
        lock.withLock {
            files().mapNotNull { f ->
                runCatching {
                    val o = JSONObject(f.readText())
                    ConversationSummary(
                        id = o.getString("id"),
                        title = o.optString("title"),
                        updatedAt = o.optLong("updatedAt"),
                        messageCount = o.optJSONArray("messages")?.length() ?: 0,
                        bytes = f.length(),
                    )
                }.getOrNull()
            }.sortedByDescending { it.updatedAt }
        }
    }

    suspend fun load(id: String): SavedConversation? = withContext(Dispatchers.IO) {
        lock.withLock { runCatching { decode(JSONObject(fileFor(id).readText())) }.getOrNull() }
    }

    /** Writes the conversation atomically (temp file + rename) so a crash never leaves half a file. */
    suspend fun save(c: SavedConversation) = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.mkdirs()
            val tmp = File(dir, "${c.id}.json.tmp")
            tmp.writeText(encode(c).toString())
            val target = fileFor(c.id)
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { lock.withLock { fileFor(id).delete() } }

    suspend fun deleteAll() = withContext(Dispatchers.IO) { lock.withLock { dir.deleteRecursively() } }

    private fun files(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.toList().orEmpty()

    private fun fileFor(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "bad id" }
        return File(dir, "$id.json")
    }

    companion object {
        const val VOICE_PREFIX = "🎤 "

        fun newId(now: Long = System.currentTimeMillis()): String = now.toString(36) + "-" + (1000..9999).random()

        /** A short title from the first thing the user said or typed. */
        fun titleFor(messages: List<ChatMessage>): String {
            val first = messages.firstOrNull { it.fromUser && it.text.removePrefix(VOICE_PREFIX).trim().let { t -> t.isNotEmpty() && t != "…" } }
                ?.text?.removePrefix(VOICE_PREFIX)?.trim()?.replace(Regex("\\s+"), " ") ?: return ""
            return if (first.length <= 60) first else first.take(57).trimEnd() + "…"
        }

        /** Only finished, non-empty turns are kept (never the "🎤 …" placeholder of an unfinished voice turn). */
        fun keepable(messages: List<ChatMessage>): List<ChatMessage> = messages.filter {
            val t = it.text.removePrefix(VOICE_PREFIX).trim()
            t.isNotEmpty() && t != "…"
        }

        fun encode(c: SavedConversation): JSONObject = JSONObject()
            .put("v", 1)
            .put("id", c.id)
            .put("title", c.title)
            .put("createdAt", c.createdAt)
            .put("updatedAt", c.updatedAt)
            .put("messages", JSONArray().apply {
                c.messages.forEach { m ->
                    put(JSONObject().apply {
                        put("user", m.fromUser)
                        put("text", m.text)
                        if (m.spoken) put("spoken", true)
                        m.where?.let { put("where", it.name) }
                        m.note?.let { put("note", it) }
                        m.lang?.let { put("lang", it.name) }
                    })
                }
            })

        fun decode(o: JSONObject): SavedConversation {
            val arr = o.optJSONArray("messages") ?: JSONArray()
            val msgs = (0 until arr.length()).map { i ->
                val m = arr.getJSONObject(i)
                ChatMessage(
                    id = i + 1L,
                    fromUser = m.optBoolean("user"),
                    text = m.optString("text"),
                    spoken = m.optBoolean("spoken"),
                    where = m.optString("where").takeIf { it.isNotEmpty() }?.let { runCatching { Where.valueOf(it) }.getOrNull() },
                    note = if (m.has("note")) m.optString("note") else null,
                    lang = m.optString("lang").takeIf { it.isNotEmpty() }?.let { runCatching { Lang.valueOf(it) }.getOrNull() },
                )
            }
            return SavedConversation(
                id = o.getString("id"),
                title = o.optString("title"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
                messages = msgs,
            )
        }
    }
}
