# Sauti eval: `e2b-base` (Google's model) vs `e2b-v1` (our fine-tune)

Held-out examples only (never seen in training). Up to 40 per set, greedy decoding, 4-bit on the laptop GPU. Phone speed and RAM are measured separately on the device.

## Speech to text (lower is better)

WER = share of words wrong; CER = share of letters wrong. *Guarded* = with the app's repetition guard, which stops the model when it gets stuck repeating a syllable (what the phone shows).

| Set | n | Base WER | Tuned WER | Base WER guarded | **Tuned WER guarded** | Tuned CER guarded | Loops (base→tuned) |
|---|---|---|---|---|---|---|---|
| Speech: FLEURS Kenyan Swahili (read) | 40 | 61.3 | 25.4 | 48.4 | **25.4** | 8.7 | 1→0 |
| Speech: Sartify Swahili | 40 | 80.2 | 50.9 | 80.2 | **50.9** | 26.7 | 0→0 |
| Speech: AfriVoice Swahili | 40 | 42.6 | 13.5 | 40.0 | **13.5** | 3.5 | 1→0 |
| Speech: Ndizi Swahili | 40 | 80.8 | 32.0 | 62.1 | **32.0** | 11.0 | 5→0 |
| Speech: LIVA **Sheng** conversations | 26 | 91.2 | 93.0 | 87.9 | **72.5** | 45.4 | 1→6 |
| Speech: Nairobi conversations | 37 | 37.2 | 32.6 | 37.2 | **27.3** | 9.8 | 0→2 |

## Text (chrF: higher is better, 0-100) and reply language

*Right language* = the reply is in the language it should be, judged by the same word-list detector the app uses (Sheng vs Swahili is a rough call for a word list).

| Set | n | Base chrF | Tuned chrF | Base right language | **Tuned right language** |
|---|---|---|---|---|---|
| Text: English <-> Sheng translation | 40 | 27.0 | 48.1 | 72.5% | **85.0%** |
| Text: English question, Kenyan Swahili answer | 40 | 34.1 | 38.2 | 50.0% | **60.0%** |
| Text: Swahili chat (Aya) | 40 | 26.0 | 18.1 | 85.0% | **97.5%** |
| Text: English chat (Aya) | 40 | 35.5 | 31.9 | 92.5% | **85.0%** |
| Answers in the user's language (10 EN / 10 SW / 10 Sheng) | 30 | - | - | 100.0% | **100.0%** |

### Which language came back (language-follow test)

| Asked in | Base replied in | Tuned replied in |
|---|---|---|
| en | en 10 | en 10 |
| sw | sw 10 | sw 10 |
| sheng | sheng 10 | sheng 10 |

### Sheng answers side by side (repetition guard applied, first 280 characters)

- **Q:** Niaje msee, nitatuma doh aje kwa M-Pesa?  
  *Base:* Sawa msee, doh kwa M-Pesa? Hii ni kama: /  / **"Sawa msee, doh kwa M-Pesa?"** /  / (Sawa = Okay/Good, doh = money, kwa = for/to)  
  *Tuned:* Msee, nita-weka doh kwa M-Pesa yangu, nita-weka number yako na uta-receive amount yako.
- **Q:** Manze, mtoi wangu ako na fever, nifanye aje?  
  *Base:* Haya, msee, hivi ni mambo ya kuenda nayo: /  / **1.  Toka na fever kidogo:** /  / *   **Saa ya kwanza:**  Toka na maji mengi, yaani, chupa ya maji.  Fiti, haina haja ya kucheka.  Maji ni muhimu sana. / *   **Maji na sukari:**  Sukari kidogo ya sukari ya chai (yaani, sukari) mchan  
  *Tuned:* Manze, mtoi wako ako na fever, nifanye aje? / Fiti kidogo, nawa na maji ya kidogo, nawa na ufukwe, nawa na chaga, nawa na mti wa kule njo a-cool down. / Nawa
- **Q:** Buda, nipe tips za kupanda mahindi kama hakuna mvua.  
  *Base:* Haya, msee, sikiliza vizuri, kupanda mahindi bila mvua ni changamoto, lakini siwezi kusema hayana fiti! Hapa ni tips za kupanda mahindi, ni kama ku-adapt kwa hali ya hepo, ya kumjenga mwilio: /  / **1.  Kutumia Maji kwa Ufanisi (Water Management):** /  / *   **Dripping Irrigation  
  *Tuned:* Buda, hivo tu: / 1. **Prepa jua:** Kwa hiyo, nenda pale siku ya jua kali, na uweze ku-irrigate vizuri. / 2. **Mchanga wa fiti:** Chagua fiti ya
- **Q:** Msee nisaidie kuandikia landlord message juu ya rent nimechelewa.  
  *Base:* Sawa msee, hapa kuna mwezo wa barua ya landlord, ukiuzwa kwa Sheng ya Nairobi: /  / **Option 1 (Casual):** /  / "Yo landlord, msee, sorry sana for the rent. Nimechelewa sikitii.  Nita-pay full amount soon, doh.  Let's talk soon, okay?" /  / **Option 2 (Slightly more formal, but s  
  *Tuned:* Msee, landlord wangu nimechelewa na rent yangu. Nimechelewa kiasi lakini niko ready ku-pay leo. Nime-call yako asubuhi niku-update.
- **Q:** Niaje, loan na grant ziko tofauti aje?  
  *Base:* Hii ni sawa sana! Hii ni jinsi inavyoonekana kwa mzungumzaji wa Nairobi: /  / **Niaje, loan na grant ziko tofauti aje?** /  / Sawa, loan na grant ziko tofauti sana, msee. **Loan** ni kama **doh** ya pesa, unazitengeza na unazituma **back** (kurejea) baadaye, **fiti** kama unavyo-  
  *Tuned:* Loan na grant ziko tofauti sana, msee. Loan ni cash ya kurejea na kutoa interest, yaani unatoa doh ya interest kwa loan. Grant ni cash ya kutoa bila interest, yaani haukutakiwa kuchezea interest.

## What this means

- **Speech is much better.** Swahili word errors drop by half or more on every Swahili set (e.g. AfriVoice 42.6% -> 13.5%, FLEURS Kenya 61.3% -> 25.4%), and Nairobi conversations improve too. Voice is the main way people will use Sauti, so this is the biggest win.
- **Sheng speech is still hard** (72.5% WER with the guard, from 87.9%). Only 26 test clips, from fast, overlapping conversations; it needs more real Sheng recordings.
- **English <-> Sheng translation improved a lot** (chrF 27.0 -> 48.1).
- **Free chat is mixed.** Swahili and English chat scores dropped against the reference answers. In Sheng the tuned model is shorter and more direct (it answers the loan and landlord questions in natural Sheng), while the base model is longer and often explains the Sheng words instead of answering; but the tuned model sometimes loops or echoes the question. Cause: the v1 training mix was mostly speech and translation, with little real conversation. The v2 mix (more English, Swahili and Sheng chat, plus the language note the app sends) is built to fix this in the next run.
- **Language following works for both** (30/30): with the app's hidden language note, English gets English, Swahili gets Swahili and Sheng gets Sheng-flavoured replies.
- **On the phone, expect:** good Swahili voice transcription, OK-but-imperfect Sheng, and chat answers that are sometimes too short or odd. Please note examples; they feed the next training round.

## Notes

- Laptop speed (not the phone): ~7.3 tokens/s, peak VRAM 8.56 GB.
- The Sheng training text is Bible-style (EgesaWO/Sheng_sml); everyday Sheng from real users is the biggest missing piece and should come from community recordings with consent.
- This model (E2B v1) was trained on the v1 mix. The v2 mix (language follows the user) goes into the next run.
