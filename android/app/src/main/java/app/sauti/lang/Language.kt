package app.sauti.lang

/**
 * Which language the user is speaking, so Sauti answers (and speaks) in the same one.
 * A tiny on-device word-list heuristic: no network, no model call, works on any phone.
 *
 * Sheng is Nairobi's mixed street language: Swahili grammar with its own slang, often switching
 * into English mid-sentence. So Sheng-specific words, or a real Swahili/English mix, count as Sheng.
 */
enum class Lang { ENGLISH, SWAHILI, SHENG }

object LanguageDetector {
    private val words = Regex("""[\p{L}0-9']+(?:-[\p{L}0-9']+)*""")   // "M-Pesa" stays one word

    private val ENGLISH = setOf(
        "the", "a", "an", "and", "is", "are", "was", "were", "of", "to", "you", "that", "this", "with", "for",
        "it", "what", "how", "why", "when", "where", "who", "your", "can", "could", "would", "should", "do", "does",
        "did", "i", "me", "my", "we", "they", "he", "she", "have", "has", "will", "not", "please", "tell", "about",
        "give", "help", "want", "need", "know", "there", "here", "be", "on", "in", "at", "from", "if", "or", "but",
        "so", "joke", "thanks", "thank", "hello", "hi", "yes", "no", "which", "make", "get", "good", "best",
    )

    private val SWAHILI = setOf(
        "na", "ya", "wa", "kwa", "ni", "za", "la", "kama", "lakini", "hii", "hiyo", "huu", "sana", "kuna", "ndiyo",
        "ndio", "hapana", "sawa", "yako", "yangu", "wewe", "mimi", "sisi", "nini", "gani", "vipi", "je", "habari",
        "tafadhali", "asante", "naomba", "nataka", "unaweza", "nisaidie", "jinsi", "kwanini", "lini", "wapi",
        "nani", "pia", "kwenye", "katika", "leo", "kesho", "jana", "pesa", "kazi", "shule", "mtoto", "watoto",
        "nyumbani", "chakula", "maji", "mwalimu", "daktari", "hospitali", "bei", "nzuri", "mbaya", "kubwa", "ndogo",
        "hujambo", "sijambo", "shikamoo", "karibu", "rafiki", "simu", "mzee", "mama", "baba",
    )

    /** Words that only (or mostly) appear in Sheng. */
    private val SHENG = setOf(
        "niaje", "niajeh", "poa", "fiti", "freshi", "msee", "wasee", "mazee", "maze", "manze", "buda", "budah",
        "mathe", "mbogi", "beshte", "mabeshte", "chali", "fom", "doh", "ganji", "mullah", "chapaa", "ngiri",
        "ashuu", "rada", "radaa", "keja", "mtaa", "nduthi", "manyanga", "kanjo", "sonko", "cheki", "bonga",
        "kubonga", "kudunda", "dunda", "ngori", "noma", "aje", "shash", "mresh", "kuchill", "mafala", "fala",
        "ndai", "msupa", "supuu", "venye", "bana", "buana", "kuhepa", "hepa", "nare", "mangware", "ngware",
        "wasiaje", "uko-aje", "sanse", "dinga", "mboch", "mnati", "tenje", "kuomoka", "omoka", "kudu",
        "githeri", "kukam", "nimekam", "kuchapa", "kubanjuka", "banjuka", "kunoma", "msoto",
    )

    private val swVerb = Regex("""^(ni|u|a|tu|m|wa|ha|si)(na|li|ta|me|ki)[a-z]{2,}$""")

    data class Score(val en: Int, val sw: Int, val sheng: Int)

    fun score(text: String): Score {
        var en = 0; var sw = 0; var sh = 0
        for (m in words.findAll(text.lowercase())) {
            val w = m.value
            when {
                w in SHENG -> sh++
                w in SWAHILI -> sw++
                w in ENGLISH -> en++
                // Swahili verb shape: ni-/u-/a-/tu-/m-/wa- + tense + root, e.g. ninataka, unaweza, tutaenda
                swVerb.matches(w) -> sw++
            }
        }
        return Score(en, sw, sh)
    }

    /**
     * The language to answer in. [previous] is used when a message gives no real signal
     * (e.g. "ok", "😂", a name), so the conversation keeps its language.
     */
    fun detect(text: String, previous: Lang? = null): Lang {
        val (en, sw, sh) = score(text)
        val total = en + sw + sh
        if (total == 0) return previous ?: Lang.SWAHILI
        return when {
            sh >= 2 || (sh >= 1 && (sw >= 1 || en == 0)) -> Lang.SHENG
            en >= 2 && sw + sh >= 2 && minOf(en, sw + sh) * 3 >= total -> Lang.SHENG   // real Swahili–English code-switch
            en > sw + sh -> Lang.ENGLISH
            sw + sh > 0 -> Lang.SWAHILI
            else -> previous ?: Lang.SWAHILI
        }
    }

    /** Hidden note added to the user's turn (not shown in the chat) telling the model which language to use. */
    fun replyInstruction(lang: Lang): String = when (lang) {
        Lang.ENGLISH -> "(Reply in English only.)"
        Lang.SWAHILI -> "(Jibu kwa Kiswahili tu.)"
        Lang.SHENG -> "(Jibu kwa Sheng ya Nairobi kama mtumiaji: msingi wa Kiswahili wenye maneno ya Sheng " +
            "kama niaje, poa, msee, fiti, doh, keja, na Kiingereza kidogo tu. Si slang ya Kiingereza, si Kiswahili sanifu.)"
    }
}
