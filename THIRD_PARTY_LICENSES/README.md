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
| **whisper.cpp** (with **ggml**) | [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) | 1.9.4 (git submodule) | MIT | **Yes** | Yes | C/C++ ASR engine, built from source for arm64-v8a only, as shared libraries with seven CPU backend variants (ARMv8.0 to ARMv9.2 with SVE2 and SME); the app loads the fastest one the processor supports. |
| **sherpa-onnx** | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | 1.13.8 | Apache-2.0 | **Yes** | Yes | Second ASR engine. `lib/localai/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so` comes unmodified from the official release asset `sherpa-onnx-v1.13.8-android-static-link-onnxruntime.tar.bz2` (SHA-256 `7583ca385ae7d981e65468455c2ea2c9f2da383921dccfc5658d0dc19d309e6f`; library SHA-256 `b4a53d915c19cb6d09b1cdc9a8084be54419b647aab9097ffdfb46f30c3c4dd8`). The Kotlin API files in `lib/localai/src/main/kotlin/com/k2fsa/sherpa/onnx/` are copied unmodified from tag v1.13.8. Model licenses checked separately. |
| **ONNX Runtime** | [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) | bundled in sherpa-onnx 1.13.8 | MIT | **Yes** | Yes | Statically linked inside `libsherpa-onnx-jni.so`. |
| **Apache Commons Compress** | [apache/commons-compress](https://github.com/apache/commons-compress) | 1.27.1 | Apache-2.0 | **Yes** | Yes | Extracts the .tar.bz2 archives of sherpa-onnx models. |
| **kotlinx.serialization** | JetBrains | 1.11.0 | Apache-2.0 | **Yes** | Yes | Reads the model catalog and the data of imported models. |
| **Silero VAD** | [snakers4/silero-vad](https://github.com/snakers4/silero-vad) | v6.2.0 | MIT | **Yes** | Yes | Speech detector run by whisper.cpp before every transcription. `lib/localai/src/main/assets/vad/ggml-silero-v6.2.0.bin` (885 098 bytes, SHA-256 `2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987`) is the ggml conversion shipped unmodified in the whisper.cpp submodule as `models/for-tests-silero-v6.2.0-ggml.bin`. The only model bundled in the app. |

---

## 2. ASR Speech Models Matrix

> **CRITICAL RULE**: Framework license (e.g. Apache-2.0 in sherpa-onnx or MIT in whisper.cpp) does NOT automatically grant rights for underlying weights. Every model must be verified individually before being packaged or downloaded.

The app ships no model: users download them with a browser and import them. The catalog (`lib/localai/src/main/resources/catalogo_modelli.json`) records the SHA-256 and size of every file, taken from the Hugging Face API and from the sherpa-onnx `asr-models` release (cross-checked with its `checksum.txt`); the app recognizes catalog models by that hash. Models imported outside the catalog are not verified.

| Model Identifier | Engine | Languages | Source | Format | Size | License | Commercial Use | Notes |
| :--- | :--- | :--- | :--- | :--- | ---: | :--- | :--- | :--- |
| `whisper-tiny-q5_1` | WHISPER_CPP | all | [ggerganov](https://huggingface.co/ggerganov/whisper.cpp) | GGML Q5_1 | 32 MB | MIT | **Yes** |  |
| `whisper-base-q5_1` | WHISPER_CPP | all | [ggerganov](https://huggingface.co/ggerganov/whisper.cpp) | GGML Q5_1 | 60 MB | MIT | **Yes** |  |
| `whisper-small-q5_1` | WHISPER_CPP | all | [ggerganov](https://huggingface.co/ggerganov/whisper.cpp) | GGML Q5_1 | 190 MB | MIT | **Yes** |  |
| `whisper-medium-q5_0` | WHISPER_CPP | all | [ggerganov](https://huggingface.co/ggerganov/whisper.cpp) | GGML Q5_0 | 539 MB | MIT | **Yes** |  |
| `whisper-large-v3-turbo-q5_0` | WHISPER_CPP | all | [ggerganov](https://huggingface.co/ggerganov/whisper.cpp) | GGML Q5_0 | 574 MB | MIT | **Yes** |  |
| `whisper-large-v3-turbo-q8_0` | WHISPER_CPP | all | [ggerganov](https://huggingface.co/ggerganov/whisper.cpp) | GGML Q8_0 | 874 MB | MIT | **Yes** |  |
| `whisper-large-v3-q5_0` | WHISPER_CPP | all | [ggerganov](https://huggingface.co/ggerganov/whisper.cpp) | GGML Q5_0 | 1.08 GB | MIT | **Yes** |  |
| `parakeet-tdt-0.6b-v3-int8` | SHERPA_ONNX | it, en, es, fr, de, pt, ru, hr, sl, el | [k2-fsa.github.io](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html) | sherpa-onnx archive, int8 | 487 MB | CC-BY-4.0 | **Yes**, with attribution |  |
| `nemo-fastconformer-multi-20k-int8` | SHERPA_ONNX | it, en, es, fr, de, ru, hr | [catalog.ngc.nvidia.com](https://catalog.ngc.nvidia.com/orgs/nvidia/teams/nemo/models/stt_multilingual_fastconformer_hybrid_large_pc) | sherpa-onnx archive, int8 | 107 MB | NVIDIA NGC Terms of Use | **No** | NGC terms do not state commercial use; flagged non-commercial in the app. |
| `omnilingual-300m-ctc-v2-int8` | SHERPA_ONNX | all | [k2-fsa.github.io](https://k2-fsa.github.io/sherpa/onnx/omnilingual-asr/models.html) | sherpa-onnx archive, int8 | 292 MB | Apache-2.0 | **Yes** |  |
| `omnilingual-1b-ctc-v2-int8` | SHERPA_ONNX | all | [csukuangfj2](https://huggingface.co/csukuangfj2/sherpa-onnx-omnilingual-asr-1600-languages-1B-ctc-v2-int8-2026-02-05) | sherpa-onnx archive, int8 | 787 MB | Apache-2.0 | **Yes** |  |
| `whisper-it-turbo-multi-yodas-q5_0` | WHISPER_CPP | it | [LocalAI-io](https://huggingface.co/LocalAI-io/whisper-large-v3-turbo-it-multi-yodas-asr_only-nocv-ggml) | GGML Q5_0 | 574 MB | MIT | **Yes** |  |
| `whisper-it-distil-v0.2-q5_0` | WHISPER_CPP | it | [bofenghuang](https://huggingface.co/bofenghuang/whisper-large-v3-distil-it-v0.2) | GGML Q5_0 | 538 MB | MIT | **Yes** |  |
| `whisper-it-large-v3-yodas-q5_0` | WHISPER_CPP | it | [LocalAI-io](https://huggingface.co/LocalAI-io/whisper-large-v3-it-yodas-only-ggml) | GGML Q5_0 | 1.08 GB | MIT | **Yes** |  |
| `moonshine-v2-base-en` | SHERPA_ONNX | en | [k2-fsa.github.io](https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html) | sherpa-onnx archive | 111 MB | MIT | **Yes** |  |
| `parakeet-tdt-0.6b-v2-int8` | SHERPA_ONNX | en | [k2-fsa.github.io](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/offline-transducer/nemo-transducer-models.html) | sherpa-onnx archive, int8 | 482 MB | CC-BY-4.0 | **Yes**, with attribution |  |
| `moonshine-v2-base-es` | SHERPA_ONNX | es | [k2-fsa.github.io](https://k2-fsa.github.io/sherpa/onnx/moonshine/models-v2.html) | sherpa-onnx archive | 51 MB | Moonshine AI Community License | **No** | Moonshine AI Community License: free commercial use only below USD 1M yearly revenue and with registration; flagged non-commercial. |
| `nemo-stt-es-fastconformer-int8` | SHERPA_ONNX | es | [github.com](https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/nemo/fast-conformer-hybrid-transducer-ctc/run-transducer-non-streaming.sh) | sherpa-onnx archive, int8 | 107 MB | CC-BY-4.0 | **Yes**, with attribution |  |
| `whisper-es-turbo-latam-q5_0` | WHISPER_CPP | es | [JoaoZaokk](https://huggingface.co/JoaoZaokk/whisper-large-v3-turbo-latam-ggml) | GGML Q5_0 | 574 MB | MIT | **Yes** | Third-party GGML conversion. |
| `whisper-fr-distil-v0.2-q5_0` | WHISPER_CPP | fr | [bofenghuang](https://huggingface.co/bofenghuang/whisper-large-v3-distil-fr-v0.2) | GGML Q5_0 | 538 MB | MIT | **Yes** |  |
| `whisper-fr-distil-dec8-q5_0` | WHISPER_CPP | fr | [bofenghuang](https://huggingface.co/bofenghuang/whisper-large-v3-french-distil-dec8) | GGML Q5_0 | 646 MB | MIT | **Yes** |  |
| `whisper-fr-distil-dec16-q5_0` | WHISPER_CPP | fr | [bofenghuang](https://huggingface.co/bofenghuang/whisper-large-v3-french-distil-dec16) | GGML Q5_0 | 791 MB | MIT | **Yes** |  |
| `nemo-stt-de-fastconformer-int8` | SHERPA_ONNX | de | [csukuangfj](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-stt_de_fastconformer_hybrid_large_pc-int8) | sherpa-onnx archive, int8 | 108 MB | CC-BY-4.0 | **Yes**, with attribution |  |
| `whisper-de-turbo-primeline-q5_0` | WHISPER_CPP | de | [JoaoZaokk](https://huggingface.co/JoaoZaokk/whisper-large-v3-turbo-german-ggml) | GGML Q5_0 | 574 MB | Apache-2.0 | **Yes** | Third-party GGML conversion. |
| `nemo-stt-pt-fastconformer-int8` | SHERPA_ONNX | pt | [csukuangfj](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-stt_pt_fastconformer_hybrid_large_pc-int8) | sherpa-onnx archive, int8 | 107 MB | CC-BY-NC-4.0 | **No** | CC-BY-NC-4.0. |
| `whisper-pt-distil-ptbr-q5_0` | WHISPER_CPP | pt | [JoaoZaokk](https://huggingface.co/JoaoZaokk/distil-whisper-large-v3-ptbr-ggml) | GGML Q5_0 | 538 MB | MIT | **Yes** | Third-party GGML conversion. |
| `gigaam-v3-e2e-rnnt-punct` | SHERPA_ONNX | ru | [csukuangfj](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16) | sherpa-onnx archive, int8 (encoder) | 170 MB | MIT | **Yes** |  |
| `gigaam-v3-rnnt` | SHERPA_ONNX | ru | [csukuangfj](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-transducer-giga-am-v3-russian-2025-12-16) | sherpa-onnx archive, int8 (encoder) | 167 MB | MIT | **Yes** |  |
| `whisper-ru-podlodka-turbo-q5_0` | WHISPER_CPP | ru | [JoaoZaokk](https://huggingface.co/JoaoZaokk/whisper-podlodka-turbo-ggml) | GGML Q5_0 | 574 MB | Apache-2.0 | **Yes** | Third-party GGML conversion. |
| `whisper-hr-turbo-parla-q5_0` | WHISPER_CPP | hr | [JoaoZaokk](https://huggingface.co/JoaoZaokk/whisper-large-v3-turbo-hr-parla-ggml) | GGML Q5_0 | 574 MB | Apache-2.0 | **Yes** | Third-party GGML conversion. |
| `whisper-sl-small-samolego-q8_0` | WHISPER_CPP | sl | [samolego](https://huggingface.co/samolego/whisper-small-slovenian) | GGML Q8_0 | 264 MB | Apache-2.0 | **Yes** |  |
| `whisper-sq-small-futo-q8_0` | WHISPER_CPP | sq | [mandododo](https://huggingface.co/mandododo/whisper-small-albanian-futo) | GGML Q8_0 | 264 MB | MIT (con riserve dell'autore) | **Yes** | The author notes reservations about the teacher model and dataset licenses. |
| `whisper-el-small-futo-q8_0` | WHISPER_CPP | el | [mandododo](https://huggingface.co/mandododo/whisper-small-greek-futo) | GGML Q8_0 | 264 MB | MIT | **Yes** |  |
| `whisper-tr-large-v3-general-q5_0` | WHISPER_CPP | tr | [JoaoZaokk](https://huggingface.co/JoaoZaokk/whisper-large-v3-turkish-general-ggml) | GGML Q5_0 | 1.08 GB | Apache-2.0 | **Yes** | Third-party GGML conversion. |
| `whisper-ar-dialectal-turbo-v2-q5_0` | WHISPER_CPP | ar | [JoaoZaokk](https://huggingface.co/JoaoZaokk/whisper-large-v3-turbo-arabic-dialectal-v2-ggml) | GGML Q5_0 | 574 MB | Apache-2.0 | **Yes** | Third-party GGML conversion. |

---

## 3. Privacy & Offline Compliance

- **No Remote Telemetry**: No Firebase, no Crashlytics, no remote telemetry SDKs.
- **Local Storage**: Models are downloaded by the user with a browser, imported through the system file picker, recognized by their SHA-256 and stored in private no-backup storage (`context.noBackupFilesDir/modelli/`: a file for whisper.cpp models, an extracted folder for sherpa-onnx models).
- **Single Network Use**: The INTERNET permission serves one purpose: downloading the model catalog (`catalogo_modelli.json`) from `raw.githubusercontent.com` when the user taps "Update the list". The request carries no user data. All network code lives in `ScaricatoreCatalogo.kt`, and CI fails if network classes appear in any other Kotlin or Java file.
- **No Cloud Inference**: Audio is decoded with Android `MediaCodec` and transcribed by the native engine on the device; it is never transmitted over any network interface. Shared voice messages are copied to private no-backup storage (`context.noBackupFilesDir/vocali/`) and stay there, with their transcriptions, until the user deletes them.
