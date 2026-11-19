package app.sauti.lang

import app.sauti.lang.LanguageDetector.detect
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {
    @Test fun english() {
        assertEquals(Lang.ENGLISH, detect("Tell me a joke"))
        assertEquals(Lang.ENGLISH, detect("Can you explain how M-Pesa works?"))
        assertEquals(Lang.ENGLISH, detect("Please fill the form for me"))
        assertEquals(Lang.ENGLISH, detect("What is M-Pesa?"))
    }

    @Test fun swahili() {
        assertEquals(Lang.SWAHILI, detect("Habari yako, uko wapi?"))
        assertEquals(Lang.SWAHILI, detect("Ninaweza kupata mkopo wapi?"))
        assertEquals(Lang.SWAHILI, detect("Nisaidie kuandika barua kwa mwalimu tafadhali"))
    }

    @Test fun sheng() {
        assertEquals(Lang.SHENG, detect("Niaje msee, form ni gani leo?"))
        assertEquals(Lang.SHENG, detect("Poa sana, nimekam keja"))
        assertEquals(Lang.SHENG, detect("Nataka kujua how to send pesa kwa simu"))   // Swahili–English code-switch
    }

    @Test fun noSignalKeepsConversationLanguage() {
        assertEquals(Lang.ENGLISH, detect("ok 😂", previous = Lang.ENGLISH))
        assertEquals(Lang.SHENG, detect("Sauti", previous = Lang.SHENG))
    }
}
