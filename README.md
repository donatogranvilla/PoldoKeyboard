# PoldoKeyboard — Trascrizione in locale dei vocali

**PoldoKeyboard** trascrive sul telefono, senza rete, i messaggi vocali ricevuti su WhatsApp, Telegram e app simili. Usa modelli aperti eseguiti in locale: **Whisper** con **whisper.cpp**, e **Parakeet**, **GigaAM**, **Moonshine** e **Omnilingual** con **sherpa-onnx**. Privilegia la qualità del testo rispetto alla velocità.

Non è una tastiera completa: per scrivere si continua a usare la propria tastiera (per esempio SwiftKey). PoldoKeyboard aggiunge una **tastiera di sola trascrizione**, come la digitazione vocale di Google, da cui inserire il testo dei vocali nella chat.

> **Stato:** il flusso completo funziona sul telefono di test (Motorola Edge 50 Fusion, Android 16) con Whisper Small: un vocale breve è pronto in meno di 20 secondi. I motori sono compilati solo per arm64-v8a; altrove risultano non disponibili. I modelli sherpa-onnx e i Whisper specializzati del catalogo sono ancora da provare sul telefono.

---

## Come si usa

1. **Condividi il vocale.** In WhatsApp tieni premuto un vocale → Condividi → **Trascrivi con PoldoKeyboard**. Si possono condividere anche più vocali insieme. Non si apre nessuna finestra: il vocale viene copiato nella memoria privata dell'app, messo in coda, e resti nella chat.
2. **La trascrizione avviene in background**, in un servizio con la notifica di avanzamento, anche a schermo spento.
3. **Arriva una notifica con il testo completo**, leggibile dalla tendina, con il pulsante **Copia**.
4. **Per inserirlo nella risposta** passa alla tastiera *PoldoKeyboard Trascrizioni* con il tasto di cambio tastiera: tocca una trascrizione per leggerla tutta, poi **Inserisci** la scrive nel campo e ti riporta alla tua tastiera.
5. **Se il testo non convince**, **Ritrascrivi con…**, nello storico dell'app o nella tastiera, rifà la trascrizione con un altro modello installato, per esempio uno più grande. Il testo precedente resta finché non arriva quello nuovo.
6. **Nell'app** trovi lo storico completo e la schermata **Modelli**, con il test delle prestazioni.

---

## Architettura

```mermaid
graph TD
    A[WhatsApp / vocale Opus] -->|Condividi| B[CondivisioneActivity]
    B -->|Copia e accoda| Q[ArchivioTrascrizioni - coda e storico Room]
    B -->|Avvia| S[ServizioTrascrizione - processo :trascrizione]
    S -->|Prende dalla coda e salva l'esito| Q
    S -->|Testo pronto| N[Notifica con testo e Copia]
    T[TastieraTrascrizioni - sottotipo voice] -->|Osserva le ultime 5, ritrascrive| Q
    T -->|Inserisci| M[Campo di testo della chat]
    P[MainActivity - storico] -->|Osserva tutto, ritrascrive| Q
    MA[ModelliActivity - catalogo, import, lingua] --> J

    subgraph "Modulo :lib:localai"
        E[AudioDecoder - MediaCodec e MediaExtractor]
        F[AudioSegmenter - segmenti fino a 28 s, tagliati nelle pause]
        L[LocalAiManager - sceglie motore, modello e lingua]
        H[MotoreWhisperCpp - whisper.cpp via JNI]
        K[MotoreSherpaOnnx - sherpa-onnx]
        J[ModelManager - catalogo JSON e import guidato]
    end

    S --> E --> F --> L
    L --> H
    L --> K
    L --> J
```

- **`:app`** (`io.github.donatogranvilla.poldokeyboard`): condivisione, archivio Room condiviso tra i processi, servizio in primo piano nel processo `:trascrizione`, notifiche, tastiera di trascrizione, storico e schermata dei modelli (Compose, Material 3 con i colori del telefono).
- **`:lib:localai`**: decodifica dell'audio, segmentazione, motori di trascrizione, catalogo e import dei modelli, benchmark. whisper.cpp 1.9.4 è un submodule git in `lib/localai/src/main/cpp/whisper.cpp`, compilato per arm64-v8a in sette varianti del backend CPU, da ARMv8.0 ad ARMv9.2 con SVE2 e SME: all'avvio si carica la più veloce che il processore supporta, così l'app gira sui telefoni vecchi e sfrutta le istruzioni di quelli nuovi. sherpa-onnx 1.13.8 arriva come libreria già compilata della release ufficiale, con ONNX Runtime incluso, insieme alla sua API Kotlin.
- **Isolamento**: il motore gira in un processo separato; se esaurisce la memoria si chiude quel processo, non l'app.

---

## Modelli

Due motori: **whisper.cpp** per i modelli Whisper in formato GGML, un solo file `ggml-*.bin`, e **sherpa-onnx** per Parakeet, GigaAM, Moonshine, Omnilingual e gli altri modelli ONNX, un archivio `.tar.bz2` con più file. L'app sceglie il motore in base al modello e ne tiene in memoria uno solo.

La schermata **Modelli** si apre dall'app e dalla tastiera di trascrizione:

- **Catalogo** di 35 modelli per 14 lingue, filtrabile per lingua: prima gli specializzati, poi i multilingue. Ogni scheda indica lingue, accuratezza, velocità, peso, RAM consigliata, file da scaricare, licenza e pagina del modello. Il catalogo è un file di dati, `lib/localai/src/main/resources/catalogo_modelli.json`.
- **Scarica** apre il file nel browser, perché l'app non ha il permesso INTERNET; **Importa** lo installa dal selettore di sistema. Il file si riconosce dall'impronta SHA-256 e dai primi byte. I modelli del catalogo si installano da soli; per gli altri l'app chiede nome, lingue e tipo. Per i formati che non vanno (GGUF, safetensors, PyTorch, un ONNX singolo) spiega cosa scaricare al loro posto.
- **Lingua dei vocali**: i modelli specializzati usano sempre la loro lingua; per i multilingue si sceglie una lingua fissa oppure il riconoscimento automatico.
- Una **guida** spiega quale file scaricare (quantizzazioni q5, q8 e f16; versioni int8) e dove cercare altri modelli.

| Lingua | Specializzati nel catalogo | Multilingue che la coprono |
| :--- | :--- | :--- |
| Italiano | Whisper Turbo, Distil e Large v3 italiani | Parakeet v3, NeMo multilingue, Whisper, Omnilingual |
| Inglese | Parakeet v2, Moonshine | Parakeet v3, NeMo multilingue, Whisper, Omnilingual |
| Spagnolo | Whisper Turbo latinoamericano, NeMo FastConformer, Moonshine (non commerciale) | Parakeet v3, NeMo multilingue, Whisper, Omnilingual |
| Francese | tre Whisper Large v3 distillati | Parakeet v3, NeMo multilingue, Whisper, Omnilingual |
| Tedesco | Whisper Turbo, NeMo FastConformer | Parakeet v3, NeMo multilingue, Whisper, Omnilingual |
| Portoghese | Whisper Distil e NeMo FastConformer (non commerciale), entrambi brasiliani | Parakeet v3, Whisper, Omnilingual |
| Catalano | nessuno pronto per il telefono | Whisper, Omnilingual |
| Russo | GigaAM v3, con e senza punteggiatura; Whisper Podlodka Turbo | Parakeet v3, NeMo multilingue, Whisper, Omnilingual |
| Croato | Whisper Turbo ParlaSpeech | Parakeet v3, NeMo multilingue, Whisper, Omnilingual |
| Sloveno | Whisper Small | Parakeet v3, Whisper, Omnilingual |
| Albanese | Whisper Small FUTO | Whisper, Omnilingual |
| Greco | Whisper Small FUTO | Parakeet v3, Whisper, Omnilingual |
| Turco | Whisper Large v3 | Whisper, Omnilingual |
| Arabo marocchino | Whisper Turbo arabo dialettale | Whisper, Omnilingual |

"Whisper" sono i sette modelli ufficiali, da Tiny a Large v3. Il NeMo multilingue è segnato come non commerciale perché la sua licenza NGC non dichiara l'uso commerciale. Alcuni Whisper specializzati sono conversioni GGML di terze parti, indicate nella descrizione: l'impronta garantisce che il file sia quello del catalogo, non chi l'ha convertito.

Il modello predefinito lo sceglierà il benchmark sui vocali reali (fase F4 del piano). Intanto il **Test delle prestazioni**, nella schermata Modelli, misura la velocità (RTF) su quattro vocali sintetici.

---

## Privacy e licenze

- Nessun backend, nessuna API cloud, nessuna telemetria; l'app non ha il permesso INTERNET.
- I vocali condivisi e le trascrizioni restano nella memoria privata dell'app finché non li elimini, e sono esclusi dal backup. Il testo copiato è segnato come sensibile negli appunti.
- Licenze del codice: whisper.cpp e ggml MIT, sherpa-onnx Apache-2.0 con ONNX Runtime MIT, Apache Commons Compress, AndroidX e Kotlin Apache-2.0. Nessuna dipendenza GPL. I modelli hanno licenze proprie (MIT, Apache-2.0, CC-BY-4.0); i pochi non commerciali sono segnalati nell'app. Dettagli in [`THIRD_PARTY_LICENSES/README.md`](THIRD_PARTY_LICENSES/README.md).
- Il progetto è nato come fork di FlorisBoard (Apache-2.0); dal branch `modulo-trascrizione` non ne contiene più il codice.

---

## Compilazione

Prerequisiti: JDK 17 o successivo, Android SDK con compileSdk 37, NDK e CMake (le versioni sono in `gradle/tools.versions.toml`; sul PC di sviluppo si possono sostituire con `poldo.ndkVersion` e `poldo.cmakeVersion` nel `gradle.properties` dell'utente).

```bash
git submodule update --init
./gradlew :app:assembleDebug
```

L'APK è in `app/build/outputs/apk/debug/app-debug.apk`. Le build di debug, locali e della CI, sono firmate con la chiave del progetto `app/debug.keystore`: un APK nuovo si installa sopra il precedente senza perdere modelli e storico.

La CI (GitHub Actions) compila a ogni push, pubblica l'APK tra gli artifact della run ed esegue i test unitari senza bloccare l'APK.
