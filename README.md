# Sauti: offline Swahili & Sheng voice AI on the phones people already own

Sauti ("voice" in Kiswahili) is an Android app that runs a fine-tuned **Gemma 3n** model **fully on the phone**. It works with no internet and no GPU. It understands spoken and typed Swahili, Sheng (Nairobi's street language) and English, including code-switching, and it answers in the language you used.

- **Edge-first.** Every core feature works offline. Optional online help goes only to our own open model, never to a frontier API, and only with the user's consent. It sends redacted text, never audio.
- **Small.** The base is Gemma 3n E2B (about 2B effective parameters), quantized to int4 for 3–6 GB Android phones. E4B is the tier for 6 GB+ phones.
- **Voice-first,** for people who can't read or type comfortably. Audio uses Gemma 3n's native 16 kHz audio encoder.

## What's in this repo

| Path | What it is |
|---|---|
| `android/` | The Sauti app (Kotlin, Jetpack Compose, LiteRT-LM). See [android/README.md](android/README.md) |
| `scripts/prepare_data.py`, `scripts/build_v2_mix.py` | Build the training mixes from open datasets |
| `scripts/train_lora.py` | QLoRA fine-tune of Gemma 3n on a single 12 GB laptop GPU |
| `scripts/evaluate.py`, `scripts/make_report.py` | Held-out evaluation: WER/CER for speech, chrF and reply language for text, plus speed and memory |
| `scripts/merge_lora.py`, `cloud/Sauti_convert.ipynb`, `scripts/splice_audio.py` | Merge, convert to a phone `.litertlm` (int4), and re-attach Google's unchanged audio encoder |
| `results/` | Evaluation reports and summary numbers |
| `DATASETS.md` | Every dataset considered, with licence and provenance |

## Results so far (E2B v1, held-out data)

| Speech to text (word error rate, lower is better) | Google's model | Sauti v1 |
|---|---|---|
| FLEURS Kenyan Swahili | 48.4% | **25.4%** |
| AfriVoice Swahili | 40.0% | **13.5%** |
| Ndizi Swahili | 62.1% | **32.0%** |
| Nairobi conversations | 37.2% | **27.3%** |
| LIVA Sheng conversations | 87.9% | **72.5%** (still hard; needs real Sheng recordings) |

English↔Sheng translation chrF went from 27.0 to 48.1. The model answers in the user's language in 30 of 30 tests. Full details are in [results/EVAL-REPORT-e2b-v1.md](results/EVAL-REPORT-e2b-v1.md). Measurements on real phones (size, RAM, tokens/s, battery) are next.

## Data and licences

- **Code** in this repo: Apache-2.0 (see `LICENSE`). Fonts are under the SIL OFL, and the thinking-orb animation is under MIT (licence files in `android/`).
- **Training data:** only openly licensed sets (Apache-2.0 and CC-BY-4.0). Per-source rows are in `results/TRAINING-DATA-v1.md` and `-v2.md`, and provenance is in `DATASETS.md`. Non-commercial sets (for example SilencioNetwork, CC-BY-NC) were used **for evaluation only**. The processed data and per-example eval outputs aren't committed; the scripts rebuild them.
- **Model weights:** Sauti is a fine-tune of Google's Gemma 3n, so the weights and any merged model are subject to the [Gemma Terms of Use](https://ai.google.dev/gemma/terms).

## Help wanted

The biggest gap is **everyday Sheng speech**. There's no large open Sheng speech corpus, so we plan consented community recording drives. The recordings will be contributed back to Mozilla Common Voice and the Mozilla Data Collective.
