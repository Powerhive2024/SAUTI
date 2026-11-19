package app.sauti.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerTextTest {
    @Test fun splitsStreamedSentences() {
        val (done, rest) = Speaker.takeSentences("Sawa! M-Pesa inakuwezesha kutuma pesa. Unahitaji")
        assertEquals(listOf("Sawa!", "M-Pesa inakuwezesha kutuma pesa."), done)
        assertEquals("Unahitaji", rest)
    }

    @Test fun keepsDecimalNumbersTogether() {
        val (done, rest) = Speaker.takeSentences("Bei ni 2.5 bob")
        assertTrue(done.isEmpty())
        assertEquals("Bei ni 2.5 bob", rest)
    }

    @Test fun cleansMarkdownEmojiAndTranslationAsides() {
        assertEquals("Hebu tuelewe! Form ni gani?",
            Speaker.forSpeech("**Hebu tuelewe!** 😊 Form ni gani? (Translation: Let's clarify!)"))
        assertEquals("Hatua ya kwanza", Speaker.forSpeech("* Hatua ya kwanza"))
    }

    @Test fun languageHeuristic() {
        assertFalse(Speaker.looksEnglish("Niaje msee, form ni gani leo na kesho?"))
        assertTrue(Speaker.looksEnglish("This is how you can send money with your phone."))
    }
}
