package app.sauti.history

import app.sauti.lang.Lang
import app.sauti.ui.ChatMessage
import app.sauti.ui.Where
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class HistoryStoreTest {
    private val msgs = listOf(
        ChatMessage(1, true, "🎤 Niaje msee, keja iko wapi?", spoken = true),
        ChatMessage(2, false, "Poa! Keja iko **Kilimani**.", where = Where.DEVICE, lang = Lang.SHENG),
        ChatMessage(3, true, "🎤 …", spoken = true),            // unfinished voice turn
        ChatMessage(4, false, "", where = Where.DEVICE),          // empty reply
    )

    @Test fun keepsOnlyFinishedTurns() {
        assertEquals(listOf(1L, 2L), HistoryStore.keepable(msgs).map { it.id })
    }

    @Test fun titleFromFirstUserTurnWithoutMicPrefix() {
        assertEquals("Niaje msee, keja iko wapi?", HistoryStore.titleFor(msgs))
        val long = HistoryStore.titleFor(listOf(ChatMessage(1, true, "a ".repeat(80))))
        assertTrue(long.length <= 60 && long.endsWith("…"))
    }

    @Test fun saveListLoadDelete() = runBlocking {
        val dir = Files.createTempDirectory("hist").toFile()
        val store = HistoryStore(dir)
        val c = SavedConversation("abc-1234", "t", 10, 20, HistoryStore.keepable(msgs))
        store.save(c)
        val listed = store.list().single()
        assertEquals("abc-1234", listed.id); assertEquals(2, listed.messageCount)
        val back = store.load("abc-1234")!!
        assertEquals(c.messages.map { it.text }, back.messages.map { it.text })
        assertEquals(Lang.SHENG, back.messages[1].lang)
        assertEquals(Where.DEVICE, back.messages[1].where)
        assertTrue(back.messages[0].spoken)
        store.delete("abc-1234")
        assertTrue(store.list().isEmpty())
        store.save(c); store.deleteAll()
        assertFalse(dir.exists())
    }
}
