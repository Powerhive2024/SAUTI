package app.sauti.privacy

/**
 * Strips personal identifiers from text *before* it can leave the phone.
 * Conservative by design: false positives only cost a little context; false negatives leak data.
 */
object Redactor {
    private val rules = listOf(
        // Kenyan / Tanzanian / Ugandan phone numbers: +254 7xx…, 07xx…, 01xx…, +255…, +256…
        Regex("""(\+?25[456][\s-]?|0)[17]\d{2}[\s-]?\d{3}[\s-]?\d{3}""") to "[NAMBA]",
        // M-Pesa style transaction codes (10 upper-case letters/digits, at least one digit)
        Regex("""\b(?=[A-Z0-9]{10}\b)(?=\w*\d)[A-Z0-9]{10}\b""") to "[MSIMBO]",
        Regex("""[\w.+-]+@[\w-]+\.[\w.]+""") to "[BARUA-PEPE]",
        // National ID / long account numbers (7+ digits)
        Regex("""\b\d{7,}\b""") to "[NAMBA]",
    )

    fun redact(text: String): String = rules.fold(text) { t, (re, tag) -> re.replace(t, tag) }
}
