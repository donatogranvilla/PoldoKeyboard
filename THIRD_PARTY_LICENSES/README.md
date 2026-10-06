# Third Party Licenses & Compliance Matrix

This document tracks all third-party components, frameworks, libraries, and AI models utilized in **PoldoKeyboard**.

In accordance with project requirements, every dependency is audited to ensure compatibility with **commercial distribution**, private distribution, and offline operation without backend telemetry.

---

## 1. Core Framework & Libraries Matrix

| Component | Repository / Author | Version | License | Commercial Use Allowed | Attribution Required | Notes / Constraints |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **FlorisBoard** | [florisboard/florisboard](https://github.com/florisboard/florisboard) | — | Apache-2.0 | **Yes** | Yes | The project started as a fork of FlorisBoard 0.6.0-alpha02; since the `modulo-trascrizione` branch it no longer contains FlorisBoard code. |
| **AndroidX Libraries** | Google / Android Open Source Project | Various | Apache-2.0 | **Yes** | Yes | Core, Lifecycle, Compose runtime & UI. |
| **Kotlin & Coroutines** | JetBrains | 2.4.x / 1.10.x | Apache-2.0 | **Yes** | Yes | Standard Kotlin runtime and coroutines. |
| **whisper.cpp** (with **ggml**) | [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) | 1.9.4 (git submodule) | MIT | **Yes** | Yes | C/C++ ASR engine, built from source for arm64-v8a only (ARMv8.2 with dotprod and fp16). |
| **sherpa-onnx** | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | 1.13.8 | Apache-2.0 | **Yes** | Yes | Second ASR engine. `lib/localai/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so` comes unmodified from the official release asset `sherpa-onnx-v1.13.8-android-static-link-onnxruntime.tar.bz2` (SHA-256 `7583ca385ae7d981e65468455c2ea2c9f2da383921dccfc5658d0dc19d309e6f`; library SHA-256 `b4a53d915c19cb6d09b1cdc9a8084be54419b647aab9097ffdfb46f30c3c4dd8`). The Kotlin API files in `lib/localai/src/main/kotlin/com/k2fsa/sherpa/onnx/` are copied unmodified from tag v1.13.8. Model licenses checked separately. |
| **ONNX Runtime** | [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) | bundled in sherpa-onnx 1.13.8 | MIT | **Yes** | Yes | Statically linked inside `libsherpa-onnx-jni.so`. |

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
- **No Cloud Inference**: Audio is decoded with Android `MediaCodec` and transcribed by the native engine on the device; it is never transmitted over any network interface. Shared voice messages are copied to private no-backup storage (`context.noBackupFilesDir/vocali/`) and stay there, with their transcriptions, until the user deletes them.
