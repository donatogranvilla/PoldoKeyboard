# PoldoKeyboard — trascrivi i vocali di WhatsApp sul telefono, senza mandarli su internet

> Ti è arrivato un vocale di quattro minuti e sei in riunione? PoldoKeyboard lo ascolta al posto tuo e te lo scrive. Tutto succede sul telefono: il vocale non va da nessuna parte.

**PoldoKeyboard** è un'app Android gratuita e open source che **trascrive i messaggi vocali** di **WhatsApp**, **Telegram** e di altre app **direttamente sul telefono**, con l'intelligenza artificiale e **senza inviare l'audio a nessun server**. Condividi il vocale con l'app, aspetti la notifica e il testo è pronto: puoi leggerlo, copiarlo o inserirlo nella risposta con una tastiera dedicata, senza uscire dalla chat.

*In English: PoldoKeyboard is a free, open-source Android app that transcribes WhatsApp and Telegram voice messages on your phone, fully offline, with on-device speech-to-text (OpenAI Whisper and NVIDIA Parakeet). Share a voice note, get a notification with the text, and insert it into your reply from a transcription-only keyboard. No cloud, no account, no ads: the audio never leaves the phone. The app is in English and Italian, with models for 14 languages.*

## Perché ti piacerà

- 🔒 **Privacy vera.** L'audio e le trascrizioni non lasciano mai il telefono. Niente account, niente pubblicità, niente raccolta di dati.
- 📴 **Funziona offline.** Una volta scaricato il modello, trascrive anche in aereo o in montagna.
- ⌨️ **Una tastiera che fa una cosa sola.** Non sostituisce la tua tastiera (Gboard, SwiftKey o un'altra): ci passi un attimo, tocchi la trascrizione, premi **Inserisci** e torni a scrivere come prima.
- 🧠 **Il modello lo scegli tu.** Ci sono 36 modelli per 14 lingue, alcuni specializzati in italiano. Se un testo non ti convince, puoi rifarlo con un modello più preciso.
- 🤫 **Niente frasi inventate sui silenzi.** Prima di trascrivere, l'app toglie silenzi e rumori di fondo, dove i modelli tendono a inventare parole.
- 📚 **Lavora in background**, anche a schermo spento e con più vocali in coda.

## Per chi è

- Per chi riceve vocali lunghi e non può ascoltarli: in ufficio, in treno, in biblioteca, con un bambino che dorme accanto.
- Per chi preferisce leggere invece di ascoltare, o ci sente poco.
- Per chi non vuole che i propri vocali passino dai server di qualcun altro.

## Cosa serve

- Un telefono **Android 8.0 o successivo con processore a 64 bit** (arm64): sono quasi tutti i telefoni degli ultimi anni.
- Lo spazio per un modello: da circa 500 MB a 1 GB per quelli consigliati.
- Più il telefono è recente, più la trascrizione è veloce. Su un telefono di fascia media del 2024, un vocale di due minuti e mezzo si trascrive in circa 17 secondi con Parakeet.

L'app è in italiano e in inglese.

## Come installarla

Il progetto è giovane e **non c'è ancora una versione pubblicata sugli store**. L'APK si scarica dalla pagina [Releases](https://github.com/donatogranvilla/PoldoKeyboard/releases) di GitHub: apri l'ultima versione e tocca il file `PoldoKeyboard-….apk` tra gli allegati. Non serve un account. Le versioni segnate come *Pre-release* sono anteprime da provare.

Poi apri il file sul telefono e consenti l'installazione da fonti sconosciute quando Android lo chiede. Le versioni successive si installano sopra la precedente, senza perdere modelli e trascrizioni.

Accanto a ogni APK c'è il file `.sha256` con la sua impronta, per controllare che il file scaricato sia quello pubblicato. Tutte le versioni sono firmate con lo stesso certificato, la cui impronta SHA-256 è `23:3E:D0:BF:82:D4:0C:A5:19:70:24:99:8A:58:2B:17:8A:BC:4E:57:50:E3:A2:11:6A:64:CB:3A:41:BB:A3:C5`.

## Primi passi

1. **Scarica un modello.** Apri l'app, vai in **Modelli** e scegline uno dal catalogo: **Scarica** apre il file nel browser, poi **Importa** lo installa. Per l'italiano parti da **Parakeet Ultra** (veloce e preciso) o **Whisper Large v3 Turbo** (il più preciso tra quelli adatti al telefono, ma più lento).
2. **Attiva la tastiera.** Nelle impostazioni di Android, tra le tastiere, attiva *PoldoKeyboard Trascrizioni*. La tua tastiera di sempre resta quella principale.

## Come si usa

1. **Condividi il vocale.** In WhatsApp tieni premuto un vocale, tocca **Condividi** e scegli **Trascrivi con PoldoKeyboard**. Puoi condividere anche più vocali insieme. Non si apre nessuna finestra: resti nella chat e il vocale va in coda.
2. **Aspetta la notifica.** La trascrizione avviene in background, anche a schermo spento, e una notifica ne mostra l'avanzamento.
3. **Leggi il testo.** Quando è pronto, arriva una notifica con il testo completo, leggibile dalla tendina, e il pulsante **Copia**.
4. **Inseriscilo nella risposta.** Passa alla tastiera *PoldoKeyboard Trascrizioni* con il tasto di cambio tastiera, tocca una trascrizione per leggerla tutta e premi **Inserisci**: il testo finisce nel campo di scrittura e torni alla tua tastiera.
5. **Se il testo non ti convince**, tocca **Ritrascrivi con…**, nello storico dell'app o nella tastiera, e scegli un altro modello installato. Il testo vecchio resta finché non arriva quello nuovo.

Nell'app trovi anche lo storico di tutte le trascrizioni.

## Quale modello scegliere

Per l'italiano li abbiamo messi alla prova su 101 vocali veri di WhatsApp:

| Modello | Parole sbagliate | Velocità |
| :--- | :--- | :--- |
| Whisper Large v3 Turbo | 11,7% | media |
| Whisper Distil italiano | 12,5% | media |
| Parakeet Ultra 0.6B | 12,8% | alta |
| Parakeet TDT 0.6B v3 | 14,8% | alta |

La classifica completa, con tutti i modelli e il metodo, è in [Quale modello trascrive meglio i vocali italiani?](docs/prova-dei-modelli.md). I modelli più piccoli, come Whisper Tiny, Base e Small, in italiano sbagliano troppo: l'app li segnala come sconsigliati.

Nella schermata **Modelli** ogni scheda indica lingue, precisione, velocità, peso e licenza. Puoi filtrare per lingua, scegliere la lingua dei vocali o lasciarla riconoscere all'app, e leggere una guida su quale file scaricare.

**Aggiorna l'elenco** scarica da GitHub il catalogo più recente: così arrivano i modelli nuovi senza aggiornare l'app. È l'unico momento in cui l'app usa internet.

### Le lingue

| Lingua | Modelli specializzati | Modelli multilingue che la coprono |
| :--- | :--- | :--- |
| Italiano | Whisper Turbo, Distil e Large v3 italiani | Parakeet Ultra e v3, NeMo multilingue, Whisper, Omnilingual |
| Inglese | Parakeet v2, Moonshine | Parakeet Ultra e v3, NeMo multilingue, Whisper, Omnilingual |
| Spagnolo | Whisper Turbo latinoamericano, NeMo FastConformer, Moonshine (non commerciale) | Parakeet Ultra e v3, NeMo multilingue, Whisper, Omnilingual |
| Francese | tre Whisper Large v3 distillati | Parakeet Ultra e v3, NeMo multilingue, Whisper, Omnilingual |
| Tedesco | Whisper Turbo, NeMo FastConformer | Parakeet Ultra e v3, NeMo multilingue, Whisper, Omnilingual |
| Portoghese | Whisper Distil e NeMo FastConformer (non commerciale), entrambi brasiliani | Parakeet Ultra e v3, Whisper, Omnilingual |
| Catalano | nessuno adatto al telefono | Whisper, Omnilingual |
| Russo | GigaAM v3, con e senza punteggiatura; Whisper Podlodka Turbo | Parakeet Ultra e v3, NeMo multilingue, Whisper, Omnilingual |
| Croato | Whisper Turbo ParlaSpeech | Parakeet Ultra e v3, NeMo multilingue, Whisper, Omnilingual |
| Sloveno | Whisper Small | Parakeet Ultra e v3, Whisper, Omnilingual |
| Albanese | Whisper Small FUTO | Whisper, Omnilingual |
| Greco | Whisper Small FUTO | Parakeet Ultra e v3, Whisper, Omnilingual |
| Turco | Whisper Large v3 | Whisper, Omnilingual |
| Arabo marocchino | Whisper Turbo arabo dialettale | Whisper, Omnilingual |

"Whisper" indica i sette modelli ufficiali, da Tiny a Large v3. Alcuni modelli sono conversioni fatte da terzi, indicate nella descrizione: l'impronta SHA-256 garantisce che il file sia quello del catalogo, non chi l'ha convertito.

## Privacy

- **Niente server.** L'app non ha un servizio online, non usa API nel cloud e non raccoglie statistiche. I vocali e le trascrizioni non lasciano mai il telefono.
- **Internet solo per il catalogo.** L'app ha il permesso di accedere a internet per un solo motivo: scaricare l'elenco dei modelli da GitHub quando tocchi **Aggiorna l'elenco**. Il codice è pubblico e un controllo automatico verifica che non usi la rete altrove.
- **I tuoi dati restano tuoi.** I vocali condivisi e le trascrizioni restano nella memoria privata dell'app finché non li elimini, e sono esclusi dal backup. Il testo copiato è segnato come sensibile, così Android non lo mostra nelle anteprime degli appunti.

## Licenze

Il codice è sotto licenza Apache-2.0 (vedi [`LICENSE`](LICENSE)). I motori di trascrizione e le librerie usate hanno licenze aperte (MIT e Apache-2.0), senza dipendenze GPL. I modelli hanno licenze proprie (MIT, Apache-2.0, CC-BY-4.0): i pochi non utilizzabili a scopo commerciale sono segnalati nell'app. I dettagli sono in [`THIRD_PARTY_LICENSES/README.md`](THIRD_PARTY_LICENSES/README.md).

Il progetto è nato come fork di FlorisBoard (Apache-2.0) e oggi non ne contiene più il codice: restano solo parti della configurazione di build, indicate nella `LICENSE`.

## Per chi sviluppa

Architettura, catalogo dei modelli e compilazione sono descritti in [docs/sviluppo.md](docs/sviluppo.md).
