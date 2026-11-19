# Sauti: Android app (working name)

An offline-first voice and chat assistant for Swahili and Sheng speakers. Its fine-tuned Gemma 3n runs **on the phone**, and it can reach an **online community server** when the user allows it and the task needs it.

## How it works

```
 mic (16 kHz WAV, ≤30 s) ─┐
 typed text ──────────────┼─► Router ──EDGE──────► EdgeEngine (LiteRT-LM, Gemma 3n on the phone)
                          │      │                   • speech + text, fully offline
                          │      ├─ASK_CONSENT─► dialog ─yes─┐
                          │      └─ONLINE (Wi-Fi + Auto)─────┤
                          │                                  ▼
                          │                    Redactor (strips phone numbers, M-Pesa codes, IDs, emails)
                          │                                  ▼
                          │                    CloudClient ─► our own server (/v1/chat/completions)
                          │                                  │  text only · store:false · never a frontier API
                          └──── no network / server error ◄──┘  → answer on the phone + keep the question for later
```

| Package | What it does |
|---|---|
| `edge/EdgeEngine` | Loads a `.litertlm` model and streams replies. It tries GPU first and falls back to CPU; audio always runs on CPU. It can load a merged fine-tuned model, or the base model plus a LoRA adapter (`LoraConfig`). |
| `edge/ModelStore` | Imports model files from **SD card, Bluetooth, USB or a download** into private storage, so one copy can be shared around a community without spending mobile data. |
| `audio/VoiceRecorder` | Records 16 kHz mono PCM16 WAV, up to 30 s, which is exactly what Gemma 3n's audio encoder expects. The recording never leaves the phone. |
| `router/Router` | Edge is the default. It only considers going online for questions that need fresh information (prices, news, weather, "leo") or for long documents. It respects the network mode and checks for a working connection, and whether it's on Wi-Fi or mobile data. |
| `privacy/Redactor` | Removes personal identifiers before any text goes online. Covered by unit tests. |
| `cloud/CloudClient` | Talks to an OpenAI-compatible endpoint that **we** host, running the bigger fine-tuned Sauti model. Sends only redacted text and the last 8 turns. |
| `device/DeviceProfile` | Recommends E2B for phones with less than 7 GB of RAM and E4B for 7 GB or more. Also reports connectivity. |
| `ui/` | Jetpack Compose screens. Each answer is labelled "answered on this phone" or "answered online". The UI is in Swahili by default, with English available. |

**Network modes** (Settings): *Phone only* never goes online. *Ask me* is the default. *Auto* goes online on Wi-Fi and still asks on mobile data.

## Building

```bash
cd android
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk (~44 MB, no model inside)
./gradlew testDebugUnitTest
```
This needs JDK 17+ (Android Studio's bundled JBR works) and Android SDK 36. The stack is AGP 8.13.2, Kotlin 2.4.0, and LiteRT-LM 0.19.1, which requires Kotlin 2.4 or newer. It targets arm64 phones on Android 8.0+ (minSdk 26).

## Getting a model onto the phone

The APK doesn't include a model, which keeps it at 44 MB.

1. **Today, as a baseline:** Google's `gemma-3n-E2B-it-int4.litertlm` (3.7 GB) or `gemma-3n-E4B-it-int4.litertlm` (4.9 GB) from `google/gemma-3n-E2B-it-litert-lm` and `google/gemma-3n-E4B-it-litert-lm`. Both repos are gated, so accept the licence on Hugging Face first. Copy the file to the phone, then use **Settings → Import model file**.
2. **Our fine-tuned Sauti model:** merge the LoRA into Gemma 3n and export it with `litert-torch` (`export_hf`, which supports Gemma 3n since Oct 2026), then package it with `litert-lm-builder`. The other option is to ship only the LoRA as a small file and import it next to the base model; a file name containing `lora` is treated as an adapter.

## Online tier (server)

Any OpenAI-compatible server running the fine-tuned model works. For example, `vllm serve <merged-sauti-e4b> --served-model-name sauti`. Enter its URL in Settings. The server code and routing are open source, so any community or NGO can run its own.
