# Sauti eval: v1 vs v2 (both our fine-tunes; "Base" columns = v1, "Tuned" = v2)


**Verdict (20 Nov 2026):** v2 is better at Sheng and Nairobi speech (LIVA Sheng 72.5% -> 64.8% WER, Nairobi 27.3% -> 25.9%, no repetition loops on those sets), but worse at following the user's language (100% -> 86.7%; Sheng questions got Swahili answers 3/10) and at English chat. **v1 stays the phone model for now;** v2's Sheng speech gains go into v3 together with a stronger language-following mix.
Held-out examples only (never seen in training). Up to 40 per set, greedy decoding, 4-bit on the laptop GPU. Phone speed and RAM are measured separately on the device.

## Speech to text (lower is better)

WER = share of words wrong; CER = share of letters wrong. *Guarded* = with the app's repetition guard, which stops the model when it gets stuck repeating a syllable (what the phone shows).

| Set | n | Base WER | Tuned WER | Base WER guarded | **Tuned WER guarded** | Tuned CER guarded | Loops (base→tuned) |
|---|---|---|---|---|---|---|---|
| Speech: FLEURS Kenyan Swahili (read) | 40 | 25.4 | 27.4 | 25.4 | **27.4** | 10.1 | 0→0 |
| Speech: Sartify Swahili | 40 | 50.9 | 113.4 | 50.9 | **51.2** | 28.1 | 0→2 |
| Speech: AfriVoice Swahili | 40 | 13.5 | 14.4 | 13.5 | **14.4** | 4.0 | 0→0 |
| Speech: Ndizi Swahili | 40 | 32.0 | 30.6 | 32.0 | **30.6** | 10.8 | 0→0 |
| Speech: LIVA **Sheng** conversations | 26 | 93.0 | 64.8 | 72.5 | **64.8** | 32.3 | 6→0 |
| Speech: Nairobi conversations | 37 | 32.6 | 25.9 | 27.3 | **25.9** | 7.7 | 2→0 |

## Text (chrF: higher is better, 0-100) and reply language

*Right language* = the reply is in the language it should be, judged by the same word-list detector the app uses (Sheng vs Swahili is a rough call for a word list).

| Set | n | Base chrF | Tuned chrF | Base right language | **Tuned right language** |
|---|---|---|---|---|---|
| Text: English <-> Sheng translation | 40 | 48.1 | 49.5 | 85.0% | **77.5%** |
| Text: English question, Kenyan Swahili answer | 40 | 38.2 | 37.3 | 60.0% | **50.0%** |
| Text: Swahili chat (Aya) | 40 | 18.1 | 17.0 | 97.5% | **100.0%** |
| Text: English chat (Aya) | 40 | 31.9 | 25.7 | 85.0% | **72.5%** |
| Answers in the user's language (10 EN / 10 SW / 10 Sheng) | 30 | - | - | 100.0% | **86.7%** |

### Which language came back (language-follow test)

| Asked in | Base replied in | Tuned replied in |
|---|---|---|
| en | en 10 | en 9, sw 1 |
| sw | sw 10 | sw 10 |
| sheng | sheng 10 | sw 3, sheng 7 |

### Sheng answers side by side (repetition guard applied, first 280 characters)

- **Q:** Niaje msee, nitatuma doh aje kwa M-Pesa?  
  *Base:* Msee, nita-weka doh kwa M-Pesa yangu, nita-weka number yako na uta-receive amount yako.  
  *Tuned:* Msee, nitatuma doh aje kwa M-Pesa?
- **Q:** Manze, mtoi wangu ako na fever, nifanye aje?  
  *Base:* Manze, mtoi wako ako na fever, nifanye aje? / Fiti kidogo, nawa na maji ya kidogo, nawa na ufukwe, nawa na chaga, nawa na mti wa kule njo a-cool down. / Nawa  
  *Tuned:* Dunga maji kidogo, naweka chuchu chache, naweka sukari kidogo, naweka limu kidogo, naweka mchana kidogo, naweka mbebe kidogo, naweka maziwa kidogo, naweka mtiwe
- **Q:** Buda, nipe tips za kupanda mahindi kama hakuna mvua.  
  *Base:* Buda, hivo tu: / 1. **Prepa jua:** Kwa hiyo, nenda pale siku ya jua kali, na uweze ku-irrigate vizuri. / 2. **Mchanga wa fiti:** Chagua fiti ya  
  *Tuned:* Buda, tip zangu kupanda mahindi kama hakuna mvua ni hizi: / 1. Chagua spishi yenye ukiweka dhishi. / 2. Naomba mtoi wake awe wa kutosha. / 3. Naomba mtoi wake awe wa kutosha. / 4. Naomba mtoi wake awe wa kutosha. / 5. Naomba mtoi wake awe wa kutosha. / 6. Naomba mtoi wake awe wa 
- **Q:** Msee nisaidie kuandikia landlord message juu ya rent nimechelewa.  
  *Base:* Msee, landlord wangu nimechelewa na rent yangu. Nimechelewa kiasi lakini niko ready ku-pay leo. Nime-call yako asubuhi niku-update.  
  *Tuned:* Msee, nimechelewa na rent yangu. Nitawekeza kurejelea leo.
- **Q:** Niaje, loan na grant ziko tofauti aje?  
  *Base:* Loan na grant ziko tofauti sana, msee. Loan ni cash ya kurejea na kutoa interest, yaani unatoa doh ya interest kwa loan. Grant ni cash ya kutoa bila interest, yaani haukutakiwa kuchezea interest.  
  *Tuned:* Loan ni mshikili wa kwanza, unatumia kwa zuri chochote, lakini unampelejea kwa mshikili wa kwanza. Grant ni mshikili wa kwanza, unatumia kwa zuri chochote, lakini unampelejea kwa mshikili wa kwanza.

## What this means

- **Speech is much better.** Swahili word errors drop by half or more on every Swahili set (e.g. AfriVoice 42.6% -> 13.5%, FLEURS Kenya 61.3% -> 25.4%), and Nairobi conversations improve too. Voice is the main way people will use Sauti, so this is the biggest win.
- **Sheng speech is still hard** (72.5% WER with the guard, from 87.9%). Only 26 test clips, from fast, overlapping conversations; it needs more real Sheng recordings.
- **English <-> Sheng translation improved a lot** (chrF 27.0 -> 48.1).
- **Free chat is mixed.** Swahili and English chat scores dropped against the reference answers. In Sheng the tuned model is shorter and more direct (it answers the loan and landlord questions in natural Sheng), while the base model is longer and often explains the Sheng words instead of answering; but the tuned model sometimes loops or echoes the question. Cause: the v1 training mix was mostly speech and translation, with little real conversation. The v2 mix (more English, Swahili and Sheng chat, plus the language note the app sends) is built to fix this in the next run.
- **Language following works for both** (30/30): with the app's hidden language note, English gets English, Swahili gets Swahili and Sheng gets Sheng-flavoured replies.
- **On the phone, expect:** good Swahili voice transcription, OK-but-imperfect Sheng, and chat answers that are sometimes too short or odd. Please note examples; they feed the next training round.

## Notes

- Laptop speed (not the phone): ~4.2 tokens/s, peak VRAM 8.56 GB.
- The Sheng training text is Bible-style (EgesaWO/Sheng_sml); everyday Sheng from real users is the biggest missing piece and should come from community recordings with consent.
- This model (E2B v1) was trained on the v1 mix. The v2 mix (language follows the user) goes into the next run.
