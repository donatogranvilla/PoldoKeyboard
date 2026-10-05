# Third Party Licenses & Compliance Matrix

This document tracks all third-party components, frameworks, libraries, and AI models utilized in **PoldoKeyboard**.

In accordance with project requirements, every dependency is audited to ensure compatibility with **commercial distribution**, private distribution, and offline operation without backend telemetry.

---

## 1. Core Framework & Libraries Matrix

| Component | Repository / Author | Version | License | Commercial Use Allowed | Attribution Required | Notes / Constraints |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FlorisBoard** | [florisboard/florisboard](https://github.com/florisboard/florisboard) | 0.6.0-alpha02 | Apache-2.0 | **Yes** | Yes | Full source fork; NOTICE file maintained. |
| **AndroidX Libraries** | Google / Android Open Source Project | Various | Apache-2.0 | **Yes** | Yes | Core, Lifecycle, Compose runtime & UI. |
| **Kotlin & Coroutines** | JetBrains | 2.4.x / 1.10.x | Apache-2.0 | **Yes** | Yes | Standard Kotlin runtime and coroutines. |
| **whisper.cpp** | [ggerganov/whisper.cpp](https://github.com/ggerganov/whisper.cpp) | 1.7.x | MIT | **Yes** | Yes | Highly optimized C/C++ ASR engine with ARM NEON support. |
| **sherpa-onnx** | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Optional/Alternative | Apache-2.0 | **Yes** | Yes | Next-gen Kaldi ONNX runtime bridge. Model licenses checked separately. |

---

## 2. ASR Speech Models Matrix

> **CRITICAL RULE**: Framework license (e.g. Apache-2.0 in sherpa-onnx or MIT in whisper.cpp) does NOT automatically grant rights for underlying weights. Every model must be verified individually before being packaged or downloaded.

| Model Identifier | Base Architecture | Author / Source | Format | Size (approx.) | License | Commercial Use | Checksum (SHA256) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `whisper-tiny-q5_1` | OpenAI Whisper Tiny | OpenAI / ggerganov | GGML Q5_1 | 32 MB | MIT | **Yes** | Verified on import |
| `whisper-base-q5_1` | OpenAI Whisper Base | OpenAI / ggerganov | GGML Q5_1 | 60 MB | MIT | **Yes** | Verified on import |
| `whisper-small-q5_1` | OpenAI Whisper Small | OpenAI / ggerganov | GGML Q5_1 | 190 MB | MIT | **Yes** | Verified on import |
| `whisper-medium-q5_0` | OpenAI Whisper Medium | OpenAI / ggerganov | GGML Q5_0 | 539 MB | MIT | **Yes** | Verified on import |
| `whisper-large-v3-turbo-q5_0` | OpenAI Whisper Large v3 Turbo | OpenAI / ggerganov | GGML Q5_0 | 574 MB | MIT | **Yes** | Verified on import |
| `whisper-large-v3-turbo-q8_0` | OpenAI Whisper Large v3 Turbo | OpenAI / ggerganov | GGML Q8_0 | 874 MB | MIT | **Yes** | Verified on import |
| `whisper-large-v3-q5_0` | OpenAI Whisper Large v3 | OpenAI / ggerganov | GGML Q5_0 | 1.08 GB | MIT | **Yes** | Verified on import |
| `parakeet-tdt-0.6b-v3-int8` (planned) | NVIDIA Parakeet TDT 0.6B v3 | NVIDIA / k2-fsa | ONNX int8 | 640 MB | CC-BY-4.0 | **Yes**, with attribution | Verified on import |

---

## 3. Privacy & Offline Compliance

- **No Remote Telemetry**: No Firebase, no Crashlytics, no remote telemetry SDKs.
- **Local Storage**: Models are downloaded by the user with a browser, imported through the system file picker, verified against their SHA-256 and stored in private no-backup storage (`context.noBackupFilesDir/modelli/`). The app has no INTERNET permission.
- **No Cloud Inference**: Audio data processed through Android `MediaCodec` and native ASR remains strictly on-device in volatile memory and is never transmitted over any network interface.
