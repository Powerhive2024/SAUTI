package app.sauti.edge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoopGuardTest {
    @Test fun stopsShortSyllableLoop() {
        val t = "Eh, ni- " + "ni- ".repeat(30)
        assertEquals("Eh, ni", LoopGuard.clean(t))
    }

    @Test fun stopsLoopEndingMidUnit() {
        val t = "Ndio. Eh, " + "zi, ".repeat(25) + "z"
        assertEquals("Ndio. Eh, zi", LoopGuard.clean(t))
    }

    @Test fun stopsPhraseLoop() {
        val t = "Wametengeneza. " + "Atulizie wametengeneza. ".repeat(6)
        assertEquals("Wametengeneza. Atulizie wametengeneza.", LoopGuard.clean(t))
    }

    @Test fun leavesNormalTextAlone() {
        for (t in listOf(
            "Niaje msee! Poa sana, keja iko fiti.",
            "Haha, sawa sawa. Tutaonana kesho.",
            "Twenty. Twenty three. Twenty seven.",
            "M-Pesa: 1. Fungua menyu 2. Chagua Tuma Pesa 3. Weka namba",
        )) assertNull(t, LoopGuard.cut(t))
    }
}
