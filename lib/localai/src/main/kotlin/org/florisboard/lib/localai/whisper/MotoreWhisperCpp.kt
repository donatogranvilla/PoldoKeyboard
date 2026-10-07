package org.florisboard.lib.localai.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.engine.*
import org.florisboard.lib.localai.postprocess.TextPostProcessor

/**
 * Implementazione di [MotoreTrascrizione] basata su whisper.cpp.
 * Carica in memoria il file GGML quantizzato e processa i campioni audio
 * ad alta efficienza energetica sfruttando i core ARM NEON del processore.
 */
class MotoreWhisperCpp : MotoreTrascrizione {
    private var handleContesto: Long = 0L
    private var modelloAttivo: ModelloInstallato? = null

    override val isModelloCaricato: Boolean
        get() = handleContesto != 0L

    override val modelloCorrente: ModelloInstallato?
        get() = modelloAttivo

    /**
     * Carica in memoria il modello indicato: operazione lenta, da fare una volta.
     */
    override suspend fun carica(modello: ModelloInstallato) = withContext(Dispatchers.IO) {
        if (!modello.percorsoFile.exists()) {
            throw IllegalArgumentException("Il file del modello non esiste in: ${modello.percorsoFile.absolutePath}")
        }

        // Senza la libreria nativa non c'è nulla da caricare: meglio un errore che un motore che finge
        if (!WhisperNative.isAvailable) {
            throw MotoreNonDisponibileException("whisper.cpp")
        }

        // Se lo stesso identico modello è già caricato, evitiamo allocazioni ridondanti
        if (modelloAttivo?.id == modello.id && handleContesto != 0L) {
            return@withContext
        }

        close() // Rilascia l'eventuale modello precedente per liberare RAM

        val handle = WhisperNative.initContext(modello.percorsoFile.absolutePath)
        if (handle == 0L) {
            throw IllegalStateException("whisper.cpp non è riuscito a inizializzare il modello: ${modello.nome}")
        }
        handleContesto = handle
        modelloAttivo = modello
    }

    /**
     * Trascrive segmenti di voce a 16 kHz, segnalando l'avanzamento da 0 a 1.
     */
    override suspend fun trascrivi(
        segmenti: List<SegmentoVoce>,
        opzioni: OpzioniTrascrizione,
        avanzamento: (Float) -> Unit,
    ): RisultatoTrascrizione = withContext(Dispatchers.Default) {
        check(handleContesto != 0L) { "Nessun modello caricato in MotoreWhisperCpp." }
        if (segmenti.isEmpty()) {
            return@withContext RisultatoTrascrizione(testo = "", durataAudioMs = 0L, tempoInferenzaMs = 0L)
        }

        val tempoInizio = System.currentTimeMillis()
        val durataAudioMs = segmenti.sumOf { it.durataMs }
        val numSegmenti = segmenti.size
        val segmentiTrascritte = mutableListOf<SegmentoTrascritto>()
        val builder = StringBuilder()

        // Con la lingua automatica whisper.cpp la stimerebbe segmento per segmento, anche sulle code di pochi
        // secondi dove sbaglia di più: la si stima una volta sul segmento più lungo e la si usa per tutti
        val lingua = opzioni.lingua ?: if (segmenti.size > 1) {
            val piuLungo = segmenti.maxBy { it.campioniAudio.size }
            WhisperNative.rilevaLingua(handleContesto, piuLungo.campioniAudio, opzioni.numThread)
        } else {
            null
        }

        for ((idx, seg) in segmenti.withIndex()) {
            val baseProgresso = idx.toFloat() / numSegmenti
            val pesoProgresso = 1.0f / numSegmenti
            avanzamento((baseProgresso + 0.1f * pesoProgresso).coerceIn(0.0f, 1.0f))

            val chunkGrezzo: String = WhisperNative.fullTranscribe(
                contextHandle = handleContesto,
                samples = seg.campioniAudio,
                language = lingua,
                threads = opzioni.numThread,
                translate = opzioni.traduciInInglese
            ) ?: ""

            val chunkProcessato = if (opzioni.formattaPunteggiatura) {
                TextPostProcessor.process(chunkGrezzo, lingua)
            } else {
                chunkGrezzo.trim()
            }

            if (chunkProcessato.isNotBlank()) {
                if (builder.isNotEmpty() && !builder.endsWith(" ") && !builder.endsWith("\n")) {
                    builder.append(" ")
                }
                builder.append(chunkProcessato)
                segmentiTrascritte.add(
                    SegmentoTrascritto(
                        inizioMs = seg.timestampInizioMs,
                        fineMs = seg.timestampFineMs,
                        testo = chunkProcessato
                    )
                )
            }

            avanzamento(((idx + 1).toFloat() / numSegmenti).coerceIn(0.0f, 1.0f))
        }

        val testoFinale = builder.toString()
        val tempoInferenzaMs = System.currentTimeMillis() - tempoInizio
        avanzamento(1.0f)

        RisultatoTrascrizione(
            testo = testoFinale,
            linguaRilevata = lingua,
            durataAudioMs = durataAudioMs,
            tempoInferenzaMs = tempoInferenzaMs,
            segmenti = segmentiTrascritte
        )
    }

    override fun close() {
        if (handleContesto != 0L) {
            WhisperNative.freeContext(handleContesto)
            handleContesto = 0L
        }
        modelloAttivo = null
    }
}
