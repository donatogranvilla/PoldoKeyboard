package org.florisboard.lib.localai.sherpa

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.engine.CoreCpu
import org.florisboard.lib.localai.engine.ModelloInstallato
import org.florisboard.lib.localai.engine.MotoreNonDisponibileException
import org.florisboard.lib.localai.engine.MotoreTrascrizione
import org.florisboard.lib.localai.engine.OpzioniTrascrizione
import org.florisboard.lib.localai.engine.RisultatoTrascrizione
import org.florisboard.lib.localai.engine.SegmentoTrascritto
import org.florisboard.lib.localai.engine.SegmentoVoce
import org.florisboard.lib.localai.postprocess.TextPostProcessor

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
                flusso.acceptWaveform(segmento.campioniAudio, FREQUENZA_CAMPIONAMENTO)
                motore.decode(flusso)
                motore.getResult(flusso)
            } finally {
                flusso.release()
            }
            if (linguaRilevata == null && risultato.lang.isNotBlank()) linguaRilevata = risultato.lang

            val pezzo = if (opzioni.formattaPunteggiatura) {
                TextPostProcessor.process(risultato.text, opzioni.lingua)
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

    override fun close() {
        riconoscitore?.release()
        riconoscitore = null
        modelloAttivo = null
    }

    private companion object {
        /** I modelli lavorano a 16 kHz, come i campioni prodotti da AudioDecoder. */
        const val FREQUENZA_CAMPIONAMENTO = 16_000
    }
}
