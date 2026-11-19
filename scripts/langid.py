"""Python copy of the app's on-device language detector (android/.../lang/Language.kt).

Used by the evaluation to check that Sauti answers in the user's language:
English -> English, Kiswahili -> Kiswahili, Sheng -> Sheng. Keep the word lists and rules
in step with the Kotlin version so the eval measures what the phone actually does.
"""
import re

WORDS = re.compile(r"[^\W_]+(?:'[^\W_]+)*(?:-[^\W_]+)*")

ENGLISH = set("""the a an and is are was were of to you that this with for it what how why when where who your can
could would should do does did i me my we they he she have has will not please tell about give help want need know there
here be on in at from if or but so joke thanks thank hello hi yes no which make get good best""".split())

SWAHILI = set("""na ya wa kwa ni za la kama lakini hii hiyo huu sana kuna ndiyo ndio hapana sawa yako yangu wewe mimi
sisi nini gani vipi je habari tafadhali asante naomba nataka unaweza nisaidie jinsi kwanini lini wapi nani pia kwenye
katika leo kesho jana pesa kazi shule mtoto watoto nyumbani chakula maji mwalimu daktari hospitali bei nzuri mbaya kubwa
ndogo hujambo sijambo shikamoo karibu rafiki simu mzee mama baba""".split())

SHENG = set("""niaje niajeh poa fiti freshi msee wasee mazee maze manze buda budah mathe mbogi beshte mabeshte chali fom
doh ganji mullah chapaa ngiri ashuu rada radaa keja mtaa nduthi manyanga kanjo sonko cheki bonga kubonga kudunda dunda
ngori noma aje shash mresh kuchill mafala fala ndai msupa supuu venye bana buana kuhepa hepa nare mangware ngware wasiaje
uko-aje sanse dinga mboch mnati tenje kuomoka omoka kudu githeri kukam nimekam kuchapa kubanjuka banjuka kunoma
msoto""".split())

SW_VERB = re.compile(r"^(ni|u|a|tu|m|wa|ha|si)(na|li|ta|me|ki)[a-z]{2,}$")


def score(text):
    en = sw = sh = 0
    for w in WORDS.findall((text or "").lower()):
        if w in SHENG:
            sh += 1
        elif w in SWAHILI:
            sw += 1
        elif w in ENGLISH:
            en += 1
        elif SW_VERB.match(w):
            sw += 1
    return en, sw, sh


def detect(text, previous=None):
    en, sw, sh = score(text)
    total = en + sw + sh
    if total == 0:
        return previous or "sw"
    if sh >= 2 or (sh >= 1 and (sw >= 1 or en == 0)):
        return "sheng"
    if en >= 2 and sw + sh >= 2 and min(en, sw + sh) * 3 >= total:
        return "sheng"
    if en > sw + sh:
        return "en"
    if sw + sh > 0:
        return "sw"
    return previous or "sw"


def family(lang):
    """Sheng is Swahili-based; for a looser check, Swahili and Sheng count as one family."""
    return "en" if lang == "en" else "sw"
