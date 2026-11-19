# v1 training data manifest

Training rows: 11109 (audio ≈ 26.8 h after upsampling)

| Source | Licence | Rows in train |
|---|---|---|
| Maxyelow/kenyan-code-switch-instruct-50k | Apache-2.0 | 3500 |
| badrex/swahili-speech-400hr | CC-BY-4.0 | 1916 |
| EgesaWO/Sheng_sml | Apache-2.0 | 1830 |
| google/fleurs sw_ke | CC-BY-4.0 | 1500 |
| google/WaxalNLP (swa_tts) | CC-BY-4.0 | 963 |
| smutuvi/ndizi-1-sw | CC-BY-4.0 | 654 |
| liva-ai/swahili-asr (sample) | CC-BY-4.0 | 480 |
| CohereForAI/aya_dataset | Apache-2.0 | 266 |

Eval-only (never trained on): FLEURS sw_ke test, Sartify Zindi eval, SilencioNetwork (CC-BY-NC), held-out speakers/conversations from Afrivoice, ndizi, liva; held-out Sheng translation pairs, code-switch test split, Aya Swahili.
