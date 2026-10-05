# Third Party Licenses & Compliance Matrix

This document tracks all third-party components, frameworks, libraries, and AI models utilized in **LocalAI Keyboard**.

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
| `whisper-tiny-q5_1` | OpenAI Whisper Tiny | OpenAI / ggerganov | GGML Q5_1 | ~32 MB | MIT | **Yes** | Verified upon download |
| `whisper-base-q5_1` | OpenAI Whisper Base | OpenAI / ggerganov | GGML Q5_1 | ~57 MB | MIT | **Yes** | Verified upon download |
| `whisper-small-q5_1` | OpenAI Whisper Small | OpenAI / ggerganov | GGML Q5_1 | ~180 MB | MIT | **Yes** | Verified upon download |
| `whisper-base` | OpenAI Whisper Base | OpenAI / ggerganov | GGML F16 | ~142 MB | MIT | **Yes** | Verified upon download |
| `whisper-small` | OpenAI Whisper Small | OpenAI / ggerganov | GGML F16 | ~466 MB | MIT | **Yes** | Verified upon download |

---

## 3. Privacy & Offline Compliance

- **No Remote Telemetry**: No Firebase, no Crashlytics, no remote telemetry SDKs.
- **Local Storage**: Downloaded models are stored in private internal storage (`context.filesDir/models/`).
- **No Cloud Inference**: Audio data processed through Android `MediaCodec` and native ASR remains strictly on-device in volatile memory and is never transmitted over any network interface.
