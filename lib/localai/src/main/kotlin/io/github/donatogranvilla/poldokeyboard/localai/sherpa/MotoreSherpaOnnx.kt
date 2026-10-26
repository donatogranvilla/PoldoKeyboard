package io.github.donatogranvilla.poldokeyboard.localai.sherpa

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.donatogranvilla.poldokeyboard.localai.engine.CoreCpu
import io.github.donatogranvilla.poldokeyboard.localai.engine.ModelloInstallato
import io.github.donatogranvilla.poldokeyboard.localai.engine.MotoreNonDisponibileException
import io.github.donatogranvilla.poldokeyboard.localai.engine.MotoreTrascrizione
import io.github.donatogranvilla.poldokeyboard.localai.engine.OpzioniTrascrizione
import io.github.donatogranvilla.poldokeyboard.localai.engine.RisultatoTrascrizione
import io.github.donatogranvilla.poldokeyboard.localai.engine.SegmentoTrascritto
import io.github.donatogranvilla.poldokeyboard.localai.engine.SegmentoVoce
import io.github.donatogranvilla.poldokeyboard.localai.postprocess.TextPostProcessor

/**
 * Motore basato su sherpa-onnx: esegue i modelli ONNX, come Parakeet, GigaAM, Moonshine, SenseVoice,
 * Omnilingual ASR o Whisper esportato, da una cartella con i loro file.
 */
class MotoreSherpaOnnx : MotoreTrascrizione {
    private var riconoscitore: OfflineRecognizer? = null
    private var modelloAttivo: ModelloInstallato? = null

    override val isModelloCaricato: Boolean
        get() = riconoscitore != null

    override val modelloCorrente: ModelloInstallato?
        get() = modelloAttivo

    /**
     * Carica il modello dalla sua cartella. La lingua dei modelli specializzati si fissa qui, perché
     * per sherpa-onnx fa parte della configurazione del riconoscitore.
     */
    override suspend fun carica(modello: ModelloInstallato) = withContext(Dispatchers.IO) {
        require(modello.percorsoFile.isDirectory) {
            "La cartella del modello non esiste in: ${modello.percorsoFile.absolutePath}"
        }
        if (modelloAttivo?.id == modello.id && riconoscitore != null) {
            return@withContext
        }
        close()

        val configurazione = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = FREQUENZA_CAMPIONAMENTO, featureDim = 80),
            modelConfig = FileModelloSherpa.configurazione(
                cartella = modello.percorsoFile,
                architettura = modello.architettura,
                numeroThread = CoreCpu.threadConsigliati(),
                lingua = modello.linguaSpecializzata,
            ),
        )
        riconoscitore = try {
            OfflineRecognizer(assetManager = null, config = configurazione)
        } catch (e: LinkageError) {
            // Su un telefono non arm64 la libreria nativa non c'è: al primo tentativo arriva UnsatisfiedLinkError,
            // ai successivi NoClassDefFoundError, entrambi LinkageError
            throw MotoreNonDisponibileException("sherpa-onnx")
        }
        modelloAttivo = modello
    }

    /**
     * Trascrive i segmenti uno alla volta, segnalando l'avanzamento a ogni segmento finito.
     */
    override suspend fun trascrivi(
        segmenti: List<SegmentoVoce>,
        opzioni: OpzioniTrascrizione,
        avanzamento: (Float) -> Unit,
    ): RisultatoTrascrizione = withContext(Dispatchers.Default) {
        val motore = checkNotNull(riconoscitore) { "Nessun modello caricato in MotoreSherpaOnnx." }
        val inizio = System.currentTimeMillis()
        val testo = StringBuilder()
        val segmentiTrascritti = mutableListOf<SegmentoTrascritto>()
        var linguaRilevata: String? = null

        for ((indice, segmento) in segmenti.withIndex()) {
            val flusso = motore.createStream()
            val risultato = try {
                flusso.acceptWaveform(conSilenzioAttorno(segmento.campioniAudio), FREQUENZA_CAMPIONAMENTO)
                motore.decode(flusso)
                motore.getResult(flusso)
            } finally {
                flusso.release()
            }
            if (linguaRilevata == null && risultato.lang.isNotBlank()) linguaRilevata = risultato.lang

            val pezzo = if (opzioni.formattaPunteggiatura) {
                TextPostProcessor.process(risultato.text, opzioni.lingua, TextPostProcessor.continuaFrase(testo))
            } else {
                risultato.text.trim()
            }
            if (pezzo.isNotBlank()) {
                if (testo.isNotEmpty()) testo.append(' ')
                testo.append(pezzo)
                segmentiTrascritti.add(SegmentoTrascritto(segmento.timestampInizioMs, segmento.timestampFineMs, pezzo))
            }
            avanzamento((indice + 1).toFloat() / segmenti.size)
        }

        RisultatoTrascrizione(
            testo = testo.toString(),
            segmenti = segmentiTrascritti,
            linguaRilevata = opzioni.lingua ?: linguaRilevata,
            durataAudioMs = segmenti.sumOf { it.durataMs },
            tempoInferenzaMs = System.currentTimeMillis() - inizio,
        )
    }

    /**
     * Il blocco con un po' di silenzio prima e dopo. I transducer come Parakeet perdono la prima o l'ultima
     * parola quando l'audio comincia o finisce proprio sul parlato, come capita a un vocale chiuso di scatto.
     */
    private fun conSilenzioAttorno(campioni: FloatArray): FloatArray {
        val risultato = FloatArray(SILENZIO_PRIMA + campioni.size + SILENZIO_DOPO)
        System.arraycopy(campioni, 0, risultato, SILENZIO_PRIMA, campioni.size)
        return risultato
    }

    override fun close() {
        riconoscitore?.release()
        riconoscitore = null
        modelloAttivo = null
    }

    private companion object {
        /** I modelli lavorano a 16 kHz, come i campioni prodotti da AudioDecoder. */
        const val FREQUENZA_CAMPIONAMENTO = 16_000

        /** 0,3 s di silenzio prima di ogni blocco. */
        const val SILENZIO_PRIMA = FREQUENZA_CAMPIONAMENTO * 3 / 10

        /** 0,5 s di silenzio dopo ogni blocco. */
        const val SILENZIO_DOPO = FREQUENZA_CAMPIONAMENTO / 2
    }
}
