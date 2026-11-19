package app.sauti.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @Test fun kenyanPhoneNumbers() {
        listOf("0712345678", "0712 345 678", "+254712345678", "+254 712 345 678", "0110123456").forEach {
            val out = Redactor.redact("nipigie $it kesho")
            assertFalse("leaked $it -> $out", out.contains(Regex("\\d{6}")))
        }
    }

    @Test fun tanzanianPhoneNumber() =
        assertEquals("piga [NAMBA] sasa", Redactor.redact("piga +255712345678 sasa"))

    @Test fun mpesaCode() =
        assertEquals("Nimetuma, code ni [MSIMBO].", Redactor.redact("Nimetuma, code ni QJK7XYZ12A."))

    @Test fun email() = assertEquals("tuma kwa [BARUA-PEPE]", Redactor.redact("tuma kwa juma.otieno@mail.co.ke"))

    @Test fun nationalId() = assertEquals("ID yangu ni [NAMBA]", Redactor.redact("ID yangu ni 23456789"))

    @Test fun ordinaryShengIsUntouched() {
        val s = "Niaje msee, form ni gani? Bei ya unga ni 200 bob leo."
        assertEquals(s, Redactor.redact(s))
    }
}
