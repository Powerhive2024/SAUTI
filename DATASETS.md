# Swahili & Sheng datasets for fine-tuning Gemma 3n

Compiled 2026-11-18. Licenses, gating and sizes come from each dataset's Hugging Face metadata on that date. **Check each card again before training**: a dataset with no license tag has unclear reuse rights.

**Mozilla Human-Scale AI fit (see CLAUDE.md):** for anything we release, use only openly licensed data (CC0, CC-BY, CC-BY-SA, Apache, MIT) with recorded provenance. Use CC-BY-NC sets (SilencioNetwork, CLEAR-Global, alpaca-swahili) only for internal evaluation, unless the owner gives permission. Untagged sets need their licence confirmed first. Mozilla-ecosystem data (Common Voice, Mozilla Data Collective) is preferred, and new Sheng recordings should be contributed back there.

License key: **Commercial OK** = CC-BY / CC0 / Apache / MIT / CC-BY-SA. **Non-commercial** = CC-BY-NC (research or personal use only).
Gemma 3n's audio encoder expects **16 kHz mono**, and clips of **≤30 s** work best. Resample anything else.

---

## 1. Sheng: audio (scarce)

| Dataset | What it is | Size | License / access |
|---|---|---|---|
| [liva-ai/swahili-asr](https://huggingface.co/datasets/liva-ai/swahili-asr) | **Best Sheng audio found.** Natural phone conversations between friends, verbatim human transcripts with diarization, 5–10% code-switching incl. frequent **Sheng** (Nairobi). | Card describes a 1,400 h corpus; the HF repo holds a small sample (<1K rows). The full set likely needs contacting Liva AI. | CC-BY-4.0 (sample) |
| [Free-form Sheng speech, mobile-money domain](https://mozilladatacollective.com/datasets/cmt7e4pfp004nnz08j3shtpcx) (Mozilla Data Collective) | Spontaneous Sheng/Swahili/English code-switched speech, phone mics, field recordings, human transcripts. | 1 h free sample; full set planned at 50 h | Full set listed commercially (~$10,500). Check terms. |
| [SilencioNetwork/swahili-speech](https://huggingface.co/datasets/SilencioNetwork/swahili-speech) | Spontaneous Kenyan Swahili with a **Nairobi Sheng-influenced** speaker cohort, word-level alignment. | ~1.4 h, 103 clips | CC-BY-NC-4.0 |
| [AfriSwitch benchmark](https://arxiv.org/pdf/2608.26434) | In-the-wild African code-switched ASR benchmark (YouTube/podcasts) incl. Swahili–English. | Benchmark/eval only | CC-BY-4.0 (per paper) |

**Bottom line:** there is no large open Sheng speech corpus. Plan to **record your own Sheng audio** (even 5–20 h of read and spontaneous speech helps a lot), and use the sets above for evaluation.

## 2. Sheng: text

| Dataset | What it is | Size | License |
|---|---|---|---|
| [Maxyelow/kenyan-code-switch-1m](https://huggingface.co/datasets/Maxyelow/kenyan-code-switch-1m) | Kenyan Swahili–English–Sheng code-switched sentences (fintech, M-Pesa, daily life). Likely largely synthetic, so spot-check quality. | ~1M rows | Apache-2.0 |
| [Maxyelow/kenyan-code-switch-instruct-50k](https://huggingface.co/datasets/Maxyelow/kenyan-code-switch-instruct-50k) | Instruction/response pairs in Kenyan code-switched style. **Directly usable for SFT.** | ~50K | Apache-2.0 |
| [BlakHasan/sheng-augmented-collection](https://huggingface.co/datasets/BlakHasan/sheng-augmented-collection) | English↔Sheng bitext (~7.9K pairs incl. ~4K scraped from Shengilia) + ~210K monolingual Sheng rows. Has an `origin_dataset` column to filter synthetic rows. | ~218K | No license tag. Scraped content, so check before commercial use. |
| [EgesaWO/Sheng_sml](https://huggingface.co/datasets/EgesaWO/Sheng_sml) | Community Sheng seed set (also EgesaWO/Eng2sheng, ~600 EN–Sheng pairs). | ~1K | Apache-2.0 |
| [JeunesseAfricaine/sheng_nlu](https://huggingface.co/datasets/JeunesseAfricaine/sheng_nlu) | Real Sheng chatbot utterances grouped by intent (greetings, affirm, etc.). Small but authentic. | <1K | MIT |
| [Rickmwasofficial/swahili-sheng](https://huggingface.co/datasets/Rickmwasofficial/swahili-sheng) | Prompt → Sheng response pairs (tweets, youth topics). | 1K–10K | No license tag |
| [KeNairobisheng/Nairobisheng200](https://huggingface.co/datasets/KeNairobisheng/Nairobisheng200) | Sheng slang glossary pages (images). | Tiny | No license tag |
| [RideKE](https://arxiv.org/pdf/2502.06180) | 29K+ Kenyan tweets mixing English/Swahili/Sheng, labelled for sentiment/emotion. | 29K | See paper/repo |

## 3. Swahili: audio (plenty)

### Large and open (best for training)
| Dataset | What it is | Size | License / access |
|---|---|---|---|
| [google/WaxalNLP](https://huggingface.co/datasets/google/WaxalNLP) | Google's 2026 African speech corpus. Swahili included; mostly image-prompted (semi-spontaneous) speech + studio TTS recordings. | Thousands of hours across 20+ languages | CC-BY-4.0 / CC-BY-SA-4.0, open |
| [DigitalUmuganda/Afrivoice_Swahili](https://huggingface.co/datasets/DigitalUmuganda/Afrivoice_Swahili) | Large Swahili ASR set (Kenya & Tanzania speakers). | 100K–1M clips | CC-BY-4.0, **gated (auto-approve)**: click accept on the page |
| [badrex/swahili-speech-400hr](https://huggingface.co/datasets/badrex/swahili-speech-400hr) | Cleaned ~400 h subset derived from Afrivoice Swahili. Easier to start with. | ~400 h | CC-BY-4.0 |
| [Tonykip/kenyan-swahili-asr-clean](https://huggingface.co/datasets/Tonykip/kenyan-swahili-asr-clean) | ~30 h Kenyan-only subset of the above, 16 kHz, cased + punctuated transcripts. | ~30 h | CC-BY-4.0 |
| Mozilla Common Voice, Swahili | Crowdsourced read speech, many speakers. Now distributed via [Mozilla Data Collective](https://mozilladatacollective.com) (the old `mozilla-foundation/common_voice_*` HF repos are no longer maintained). | Hundreds of hours | CC0 |
| [google/fleurs](https://huggingface.co/datasets/google/fleurs) (`sw_ke` config) | Read FLORES sentences, parallel across 102 languages. Good **eval** set and for speech translation. | ~10 h Swahili | CC-BY-4.0 |
| [michsethowusu/swahili-words-speech-text-parallel](https://huggingface.co/datasets/michsethowusu/swahili-words-speech-text-parallel) | Bible-recording audio force-aligned to text (religious domain). | ~411K pairs | CC-BY-4.0 |
| [keystats/swahili_asr_data](https://huggingface.co/datasets/keystats/swahili_asr_data) | Swahili ASR pairs. | 10K–100K | Apache-2.0 |

### Conversational / real-world (closer to how people actually talk)
| Dataset | What it is | Size | License |
|---|---|---|---|
| [smutuvi/ndizi-1-sw](https://huggingface.co/datasets/smutuvi/ndizi-1-sw) | Tanzanian farmers in structured interviews (agriculture). | ~58 h | CC-BY-4.0 |
| [sartifyllc/kiswahili-asr-challenge-eval](https://huggingface.co/datasets/sartifyllc/kiswahili-asr-challenge-eval) | Zindi challenge test set, conversational conditions. **Eval only.** | 1K–10K | CC-BY-SA-4.0 |
| [InfoBayAI Swahili call-center audio](https://huggingface.co/datasets/InfoBayAI/Swahili-Call-Center-Audio-Dataset-Single-Channel) | Call-center samples (single and dual channel). | Small sample, gated | CC-BY-4.0 |
| [SpeechDataAI/swahili-conversational-speech](https://huggingface.co/datasets/SpeechDataAI/swahili-conversational-speech) | Kenyan two-person spontaneous conversations. HF hosts a sample; full 2,000 h is commercial. | Sample | "other" (commercial) |

### Kenyan-accent / special-purpose
| Dataset | What it is | Size | License |
|---|---|---|---|
| [CLEAR-Global/Kenyan-Swahili-Speech](https://huggingface.co/datasets/CLEAR-Global/Kenyan-Swahili-Speech) | Single Kenyan male speaker, read prompts (good for TTS). | ~6 h | CC-BY-NC-4.0 |
| [cdli/kenyan_swahili_nonstandard_speech_v1.0](https://huggingface.co/datasets/cdli/kenyan_swahili_nonstandard_speech_v1.0) | Kenyan Swahili from speakers with speech impairments (accessibility). | 1K–10K | CC-BY-4.0, gated (manual approval) |
| [Bateesa/kiswahili-tts-dataset](https://huggingface.co/datasets/Bateesa/kiswahili-tts-dataset) | Read speech for TTS (KE/TZ). | 10K–100K | CC-BY-4.0 |
| [stem-content-ai-project/swahili-speech](https://huggingface.co/datasets/stem-content-ai-project/swahili-speech) | Read STEM-content sentences, 44.1 kHz FLAC (resample to 16 kHz). | 1K–10K | MIT |
| [intronhealth/afrivox-transcribe](https://huggingface.co/datasets/intronhealth/afrivox-transcribe) | Multi-domain African ASR **benchmark** incl. Swahili. | Eval | Gated (manual) |
| [Kencorpus](https://arxiv.org/pdf/2208.12081) | Kenyan Swahili/Dholuo/Luhya text + ~177 h speech. | — | See paper |
| [Thiomi dataset](https://arxiv.org/html/2603.29244v1) | Multimodal corpus, 10 African languages incl. Swahili. Release on HF announced. | — | CC-BY-4.0 (paper) |

## 4. Swahili: text / instruction data

| Dataset | What it is | Size | License |
|---|---|---|---|
| [CohereForAI/aya_dataset](https://huggingface.co/datasets/CohereForAI/aya_dataset) | Human-written multilingual instructions. Filter `language == "Swahili"`. | Swahili subset | Apache-2.0 |
| [lelapa/Inkuba-instruct](https://huggingface.co/datasets/lelapa/Inkuba-instruct) | Large African-language instruction set (Swahili is the biggest part). | Very large | Gated (auto). Check terms on the card |
| [saillab/alpaca_swahili_taco](https://huggingface.co/datasets/saillab/alpaca_swahili_taco) | Alpaca-style Swahili instructions. | 10K–100K | No license tag |
| [ebartile/alpaca-swahili](https://huggingface.co/datasets/ebartile/alpaca-swahili) | Translated Alpaca, 52K instructions. | 52K | CC-BY-NC-4.0 |
| [princeton-nlp/fineweb_edu-swahili-translated](https://huggingface.co/datasets/princeton-nlp/fineweb_edu-swahili-translated) | Educational web text translated to Swahili (continued pre-training). | 100K–1M | No license tag |
| [masakhane/afriqa](https://huggingface.co/datasets/masakhane/afriqa) | Open-retrieval QA in African languages incl. Swahili. | 10K–100K | CC-BY-SA-4.0 |
| [facebook/flores](https://huggingface.co/datasets/facebook/flores) (`swh_Latn`) | High-quality parallel sentences. **Eval** for translation. | ~2K sentences | CC-BY-SA-4.0, gated (auto) |

---

## Suggested starting mix

1. **Swahili speech understanding:** `badrex/swahili-speech-400hr` (or a subset) + WAXAL Swahili + Common Voice Swahili. Evaluate on FLEURS `sw_ke` and the Zindi eval set.
2. **Real conversational / code-switched audio:** `liva-ai/swahili-asr` sample + `SilencioNetwork/swahili-speech` (non-commercial) + `smutuvi/ndizi-1-sw`.
3. **Sheng text:** `Maxyelow/kenyan-code-switch-instruct-50k` + the clean (non-synthetic) rows of `BlakHasan/sheng-augmented-collection` + `JeunesseAfricaine/sheng_nlu`.
4. **Swahili instruction following:** Aya (Swahili) + Inkuba-instruct subset.
5. **Fill the Sheng audio gap:** record your own, e.g. Sheng speakers reading the Sheng text above plus free conversation, at 16 kHz mono.

If you plan to sell or ship the model commercially, drop the **CC-BY-NC** and untagged datasets, or get permission first.
