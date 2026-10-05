# LocalAI Keyboard — Tastiera Android con Trascrizione Vocale 100% Locale e Offline

**LocalAI Keyboard** è una tastiera Android completa, moderna e open-source basata su **FlorisBoard** (Apache-2.0) ed integrata con un motore di speech-to-text nativo on-device basato sull'architettura **Whisper** (MIT).

Il progetto è concepito specificamente per:
1. **Trascrivere istantaneamente i messaggi vocali di WhatsApp** tramite il menu di condivisione di sistema (*Share Intent* `ACTION_SEND`), visualizzando un dialog trasparente senza abbandonare la chat.
2. **Trascrivere un vocale dalla tastiera**: il pannello di trascrizione apre un file audio salvato e **inserisce il testo nel campo attivo** (`InputConnection.commitText()`). La dettatura dal microfono è rimandata a dopo la v1.
3. **Garantire il 100% di Privacy**: nessun backend remoto, nessuna API cloud, nessun account utente, zero analytics e zero telemetria. Tutto l'audio viene decodificato ed elaborato localmente nella RAM del dispositivo.
4. **Massima Libertà Commerciale**: nessuna dipendenza virale (zero GPL/AGPL). Base Apache-2.0 e componenti ASR con licenza MIT.
5. **Personalizzazione Immediata del Brand**: il nome del progetto è provvisorio e centralizzato in un unico file per essere sostituito in pochi secondi.

---

## 🏗️ Architettura del Sistema

```mermaid
graph TD
    A[WhatsApp / Note Vocale Opus] -->|Condividi / ACTION_SEND| B[TranscribeShareActivity]
    C[Azione Trascrivi della Smartbar] -->|Apre| D[VoiceTranscriptionLayout]

    subgraph "Modulo :lib:localai"
        E[AudioDecoder - MediaCodec & MediaExtractor]
        F[AudioSegmenter - Splitting pause naturali >60s]
        G[MotoreTrascrizione - Interfaccia Intercambiabile]
        H[MotoreWhisperCpp - JNI C++ Core]
        I[MotoreMock - Test e Fallback]
        J[ModelManager - Catalogo & Import verificato]
        K[TextPostProcessor - Punteggiatura & Maiuscole]
        L[BenchmarkSuite - Test Scenari Reali WhatsApp]
    end

    B --> E
    D --> E
    E --> F
    F --> G
    G --> H
    G --> I
    H --> K
    K -->|commitText| M[Campo di Testo Attivo / Chat WhatsApp]
    K -->|Copia Appunti| N[Clipboard Android]
```

---

## 🚀 Funzionalità Principali

### 1. Workflow WhatsApp Nativo
- Tieni premuto qualsiasi messaggio vocale su WhatsApp -> tocca **Condividi** -> seleziona **"Trascrivi con LocalAI"**.
- La decodifica nativa `MediaExtractor` / `MediaCodec` converte direttamente il formato WhatsApp `.opus` (Ogg container) in campioni PCM a 16 kHz Mono senza librerie pesanti come FFmpeg.
- Il testo trascritto appare in un popup non invasivo con punteggiatura corretta e opzioni rapide:
  - **"Copia e Inserisci"**: inietta direttamente il testo nella chat attiva tramite `InputConnection`.
  - **"Condividi / Invia"**: inoltra il testo a qualsiasi contatto o applicazione.

### 2. Pannello di trascrizione nella tastiera
- Si apre con l'azione **Trascrivi** della Smartbar (pannello `VoiceTranscriptionLayout`). Il tasto voce resta quello di FlorisBoard e passa alla tastiera vocale di sistema.
- Trascrive qualsiasi file audio locale (`.opus`, `.m4a`, `.wav`, `.mp3`).
- La dettatura dal microfono è rimandata a dopo la v1: l'app non chiede il permesso `RECORD_AUDIO`.
- Selettore lingua rapido: **Auto (Rileva)**, Italiano, English, Español, Français, Deutsch, Português.
- Inserimento istantaneo nel campo testo attivo senza uscire dall'app.

### 3. Gestione Vocali Lunghi (> 60 secondi)
- I modelli Whisper operano internamente su finestre di 30 secondi.
- Il modulo [`AudioSegmenter`](file:///c:/Dev/PoldoKeyboard/lib/localai/src/main/kotlin/org/florisboard/lib/localai/audio/AudioSegmenter.kt) rileva le pause di respirazione e i punti a minore energia sonora (RMS) tra 10s e 28s.
- Suddivide vocali di 60s, 90s, 3 minuti o superiori in blocchi continui senza tagliare parole a metà.
- Ogni blocco viene elaborato con avanzamento percentuale fluido e ricongiunto con punteggiatura naturale.

### 4. Modelli Intercambiabili e Riconoscimento Hardware
Il catalogo ([`ModelCatalog`](lib/localai/src/main/kotlin/org/florisboard/lib/localai/model/ModelCatalog.kt)) comprende tutti i modelli Whisper da tiny a large-v3; il predefinito lo sceglierà il benchmark sui vocali reali. Fino ad allora la tastiera ne consiglia uno in base alla RAM ([`ProfiloDispositivo`](lib/localai/src/main/kotlin/org/florisboard/lib/localai/model/ProfiloDispositivo.kt)). La RAM dei modelli da medium in su è una stima da misurare.

| Modello | File | Dimensione | RAM stimata |
| :--- | :--- | ---: | ---: |
| Whisper Tiny (Q5_1) | `ggml-tiny-q5_1.bin` | 32 MB | ~200 MB |
| Whisper Base (Q5_1) | `ggml-base-q5_1.bin` | 60 MB | ~400 MB |
| Whisper Small (Q5_1) | `ggml-small-q5_1.bin` | 190 MB | ~900 MB |
| Whisper Medium (Q5_0) | `ggml-medium-q5_0.bin` | 539 MB | ~1 GB |
| Whisper Large v3 Turbo (Q5_0) | `ggml-large-v3-turbo-q5_0.bin` | 574 MB | ~1 GB |
| Whisper Large v3 Turbo (Q8_0) | `ggml-large-v3-turbo-q8_0.bin` | 874 MB | ~1,3 GB |
| Whisper Large v3 (Q5_0) | `ggml-large-v3-q5_0.bin` | 1,08 GB | ~2 GB |

Parakeet TDT 0.6B v3 (sherpa-onnx) entrerà nel catalogo insieme al suo motore. Con le opzioni sviluppatore attive si può importare anche un file GGML fuori catalogo.

### 5. Suite di Benchmark & Test Prestazioni
- Accessibile dal pulsante **"🚀 Test Prestazioni"** nella gestione modelli.
- Esegue 4 scenari realistici WhatsApp:
  1. *Vocale Breve* (8s)
  2. *Vocale Standard* (30s)
  3. *Vocale Lungo* (75s con verifica splitting)
  4. *Vocale Rumoroso* (20s con basso SNR)
- Calcola in tempo reale il **Real-Time Factor (RTF)** (`tempoInferenza / durataAudio`) e il delta di memoria RAM.

---

## 🏷️ Guida al Rebranding (Personalizzazione Nome in 30 Secondi)

Il nome "LocalAI Keyboard" è volutamente provvisorio e centralizzato. Per sostituirlo con il tuo brand commerciale:

1. **File Kotlin**: apri [`lib/localai/src/main/kotlin/org/florisboard/lib/localai/LocalAiBrandConfig.kt`](file:///c:/Dev/PoldoKeyboard/lib/localai/src/main/kotlin/org/florisboard/lib/localai/LocalAiBrandConfig.kt) e modifica le costanti:
   ```kotlin
   object LocalAiBrandConfig {
       const val BRAND_NAME = "MioBrand"
       const val APP_FULL_NAME = "MioBrand Keyboard"
       const val TRANSCRIBER_NAME = "MioBrand Transcriber"
       const val SHARE_ACTION_LABEL = "Trascrivi con MioBrand"
   }
   ```
2. **File Stringhe Android**: apri [`app/src/main/res/values/strings.xml`](file:///c:/Dev/PoldoKeyboard/app/src/main/res/values/strings.xml) e modifica la seconda riga:
   ```xml
   <string name="app_name">MioBrand Keyboard</string>
   <string name="localai_brand_name">MioBrand</string>
   <string name="localai_app_name">MioBrand Keyboard</string>
   <string name="localai_share_action_label">Trascrivi con MioBrand</string>
   ```

Tutte le viste (Smartbar, activity WhatsApp, impostazioni, manifest, icone del launcher) si aggiorneranno automaticamente.

---

## ⚖️ Conformità Licenze e Distribuzione Commerciale

Il progetto rispetta rigorosamente i vincoli per la distribuzione commerciale senza obblighi di copyleft virale:

- **Codice Base (FlorisBoard)**: Licenza **Apache-2.0** (permette l'uso commerciale, modifica e redistribuzione con mantenimento del file NOTICE).
- **Motore ASR (whisper.cpp)**: Licenza **MIT** (permette l'uso commerciale senza restrizioni).
- **Modelli GGML Whisper (OpenAI / ggerganov)**: Licenza **MIT** (pesi aperti a distribuzione e uso commerciale).
- **Librerie di Supporto**: AndroidX, Jetpack Compose, Kotlinx Serialization, Coroutines (tutte **Apache-2.0**).
- **Zero Dipendenze GPL/LGPL**: nessun modulo introduce licenze GPL (ad es. AnySoftKeyboard usa GPL-3.0 ed è stato scartato; FFmpeg con licenze GPL è stato evitato a favore di `MediaCodec` nativo Android).

La matrice dettagliata è consultabile in [`THIRD_PARTY_LICENSES/README.md`](file:///c:/Dev/PoldoKeyboard/THIRD_PARTY_LICENSES/README.md).

---

## 🛠️ Istruzioni di Compilazione

### Prerequisiti
- **JDK**: Java 21 (es. OpenJDK 21)
- **Android SDK**: Compile/Target SDK 37 (API 37 preview)
- **Android NDK**: r27c (`27.2.12479018`)
- **CMake & Ninja**: CMake 3.22+ e Ninja

### Compilazione APK Debug
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;C:\Program Files\Microsoft Visual Studio\18\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;C:\Program Files\Microsoft Visual Studio\18\Professional\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja;" + $env:PATH
.\gradlew.bat :app:assembleDebug --max-workers=2
```
L'APK generato si troverà in:
`app/build/outputs/apk/debug/app-debug.apk`

### Esecuzione Test Unitari
```powershell
.\gradlew.bat :lib:localai:test --max-workers=2
```

### Compilazione APK Release per Distribuzione Commerciale
1. Genera il keystore di firma:
   ```bash
   keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias localai
   ```
2. Configura le credenziali di firma in `local.properties`:
   ```properties
   signing.storeFile=/percorso/release.jks
   signing.storePassword=tua_password
   signing.keyAlias=localai
   signing.keyPassword=tua_password
   ```
3. Avvia la build di release:
   ```powershell
   .\gradlew.bat :app:assembleRelease --max-workers=2
   ```
   L'APK firmato e ottimizzato con Proguard/R8 sarà disponibile in:
   `app/build/outputs/apk/release/app-release.apk`
