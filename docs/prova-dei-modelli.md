# Quale modello trascrive meglio i vocali italiani?

Abbiamo messo alla prova i modelli del catalogo di PoldoKeyboard su **101 vocali veri di WhatsApp in italiano**: 78 minuti di audio, circa 11.000 parole, da 6 secondi a oltre 3 minuti l'uno, con persone, accenti, rumori di fondo e modi di parlare diversi. Niente frasi lette in studio: messaggi come quelli che arrivano ogni giorno, con pause, intercalari, parolacce e frasi lasciate a metà.

*In English: we measured the word error rate (WER) of every Italian-capable model in the PoldoKeyboard catalog on 101 real Italian WhatsApp voice notes (78 minutes, ~11,000 words), with the same settings the app uses. Whisper large-v3-turbo is the best choice for a phone (11.7% WER), Parakeet TDT 0.6B v3 the fastest acceptable one (14.8%). The audio and transcripts are private and are not published.*

## Il risultato

Il numero è il **WER** (*word error rate*): su 100 parole dette, quante il modello sbaglia, salta o aggiunge. Più basso è, meglio è.

| Modello | Motore | Peso | Parole sbagliate |
|---|---|---|---|
| Whisper large-v3 | whisper.cpp | 1081 MB | **9,3%** |
| Whisper large-v3-turbo | whisper.cpp | 574 MB | **11,7%** |
| Whisper large-v3 distil italiano v0.2 | whisper.cpp | 538 MB | **12,5%** |
| Whisper large-v3 italiano (YODAS) | whisper.cpp | 1081 MB | 13,0% |
| Parakeet TDT 0.6B v3 | sherpa-onnx | 487 MB | 14,8% |
| Whisper large-v3-turbo italiano (YODAS) | whisper.cpp | 574 MB | 14,9% |
| Whisper medium | whisper.cpp | 539 MB | 17,1% |
| Whisper small | whisper.cpp | 190 MB | 25,3% |
| Omnilingual ASR 1B | sherpa-onnx | 787 MB | 29,4% |
| NeMo FastConformer multilingue | sherpa-onnx | 107 MB | 35,0% |
| Omnilingual ASR 300M | sherpa-onnx | 292 MB | 38,9% |
| Whisper base | whisper.cpp | 60 MB | 42,5% |
| Whisper tiny | whisper.cpp | 32 MB | 60,0% |

## Cosa scegliere

- **Il migliore per il telefono: Whisper large-v3-turbo.** Sbaglia poco più di una parola su dieci, la metà dei vocali ha un errore sotto il 10%, e pesa la metà di large-v3.
- **Il più veloce tra quelli buoni: Parakeet TDT 0.6B v3.** Sbaglia circa tre parole in più ogni cento, ma trascrive molto più in fretta.
- **large-v3** è il più preciso, ma su un telefono è lento: ha senso solo se non hai fretta.
- **I modelli addestrati apposta sull'italiano non battono gli originali.** Sono stati addestrati soprattutto su audio di YouTube, che somiglia poco a un vocale registrato col telefono in mano. Il distil italiano resta una buona alternativa, un po' più leggera.
- **Per l'italiano parlato lascia perdere** small, base, tiny, NeMo FastConformer e Omnilingual: sbagliano da una parola su quattro in su. Omnilingual 1B è anche il più lento di tutti.

## Come abbiamo misurato

1. **I vocali.** 101 messaggi vocali ricevuti su WhatsApp e condivisi con l'app.
2. **Il testo giusto.** Per ogni vocale serve la trascrizione esatta, parola per parola. L'abbiamo costruita così: tre modelli diversi (Whisper large-v3, Whisper turbo italiano e Parakeet) hanno trascritto ogni vocale; dove almeno due concordavano, la parola è stata presa per buona; i 393 punti in cui non c'era accordo sono stati riascoltati e corretti a orecchio da una persona, con l'audio del punto esatto a fianco. Alcuni pezzi davvero incomprensibili sono stati esclusi dal conteggio.
3. **Le stesse condizioni dell'app.** Ogni modello ha trascritto l'audio con le impostazioni che usa PoldoKeyboard: lo stesso rilevatore del parlato (Silero VAD) con gli stessi parametri, gli stessi blocchi di audio, e per Whisper la ricerca a fascio con 5 ipotesi e la frase di contesto per la punteggiatura. Per velocità le prove sono girate su un PC, con gli stessi motori dell'app (whisper.cpp e sherpa-onnx). Parakeet è stato provato anche sul telefono: 14,8% contro 15,4% sul PC, quindi i risultati del PC valgono anche per il telefono.
4. **Il confronto.** Maiuscole, punteggiatura, accenti gravi o acuti e numeri in cifre o in lettere non contano come errori: conta solo se la parola è quella giusta.

## I limiti di questa prova

- **Un piccolo vantaggio per i modelli usati come base.** Il testo giusto è partito dalle trascrizioni di large-v3, turbo italiano e Parakeet: dove tutti e tre sbagliavano allo stesso modo l'errore può essere sfuggito. Il vantaggio si vede su large-v3: la sua trascrizione usata come base sbaglia il 7,6%, la stessa rifatta con le impostazioni dell'app il 9,3%. Nella tabella c'è il secondo numero.
- **Un solo gruppo di parlanti.** 101 vocali sono abbastanza per vedere le differenze grandi tra i modelli, non per distinguere due modelli che differiscono di mezzo punto.
- **Solo la precisione.** La velocità cambia molto da un telefono all'altro e non è misurata qui.
- **Audio e testi restano privati.** Sono messaggi personali di persone reali, e non li pubblichiamo. Per questo la prova non si può rifare identica: si può rifare con i propri vocali, con lo stesso metodo.

*Prova del 27 ottobre 2026.*
