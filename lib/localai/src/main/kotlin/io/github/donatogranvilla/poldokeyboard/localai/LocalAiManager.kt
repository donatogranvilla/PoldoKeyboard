package io.github.donatogranvilla.poldokeyboard.localai

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.donatogranvilla.poldokeyboard.localai.audio.AudioDecoder
import io.github.donatogranvilla.poldokeyboard.localai.audio.AudioSegmenter
import io.github.donatogranvilla.poldokeyboard.localai.audio.RilevatoreParlato
import io.github.donatogranvilla.poldokeyboard.localai.audio.SegmentatoreParlato
import io.github.donatogranvilla.poldokeyboard.localai.engine.*
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelManager
import io.github.donatogranvilla.poldokeyboard.localai.sherpa.MotoreSherpaOnnx
import io.github.donatogranvilla.poldokeyboard.localai.whisper.MotoreWhisperCpp
import java.io.File

/**
 * Coordinatore principale del sottosistema AI Locale.
 * Collega i motori di trascrizione ([MotoreTrascrizione]: whisper.cpp per i modelli GGML, sherpa-onnx
 * per gli altri), il gestore dei modelli ([ModelManager]), le impostazioni e il decoder audio.
 * Tiene in memoria un solo modello alla volta.
 * @param consentiMotoreFinto true per poter provare il flusso con [MotoreMock] quando manca un modello,
 *   pensato per le build di sviluppo; in produzione resta false.
 */
class LocalAiManager(
    val context: Context,
    private val consentiMotoreFinto: Boolean = false,
) {
    val modelManager = ModelManager(context)
    val impostazioni = ImpostazioniTrascrizione(context)

    var activeEngine: MotoreTrascrizione = MotoreWhisperCpp()
        private set

    /** Il rilevatore di parlato, caricato al primo vocale e tenuto per i successivi: pesa circa 1 MB. */
    private val rilevatore: RilevatoreParlato? by lazy { RilevatoreParlato.crea(context) }

    /**
     * Assicura che il modello sia caricato in RAM nel motore giusto: quello indicato oppure, senza id,
     * quello attivo. Se è già caricato non fa nulla.
     * @return Successo se il motore è pronto, altrimenti l'errore che spiega perché non lo è.
     */
    suspend fun ensureModelLoaded(idModello: String? = null): Result<Unit> {
        val modello = if (idModello != null) {
            modelManager.trovaInstallato(idModello)
                ?: return Result.failure(IllegalStateException("Il modello $idModello non è più installato."))
        } else {
            modelManager.getActiveInstalledModel()
        }

        if (modello == null) {
            // Nessun modello installato: errore esplicito, a meno che il chiamante non abbia chiesto il motore finto
            if (!consentiMotoreFinto) return Result.failure(NessunModelloException())
            if (activeEngine !is MotoreMock) sostituisciMotore(MotoreMock())
            return Result.success(Unit)
        }

        if (activeEngine.isModelloCaricato && activeEngine.modelloCorrente?.id == modello.id) {
            return Result.success(Unit)
        }
        // Il motore dipende dal modello; un modello vero non va mai caricato nel motore finto
        if (!motoreAdatto(activeEngine, modello)) sostituisciMotore(nuovoMotore(modello))
        return runCatching { activeEngine.carica(modello) }
    }

    /**
     * Cambia il modello attivo e ricarica il motore.
     */
    suspend fun switchModel(modelId: String): Result<Unit> {
        modelManager.setActiveModelId(modelId)
        return ensureModelLoaded(modelId)
    }

    /**
     * Trascrive un file audio da URI Android (es. ricevuto tramite intent WhatsApp o SAF).
     * @param avanzamento Frazione completata tra 0 e 1.
     */
    suspend fun transcribeAudioUri(
        uri: Uri,
        opzioni: OpzioniTrascrizione = OpzioniTrascrizione.Predefinite,
        avanzamento: (Float) -> Unit = {},
    ): Result<RisultatoTrascrizione> {
        val campioni = AudioDecoder.decodeTo16kMono(context, uri).getOrElse { return Result.failure(it) }
        return trascriviCampioni(campioni, opzioni, idModello = null, avanzamento)
    }

    /**
     * Trascrive un file audio locale dal filesystem.
     * @param idModello Il modello da usare per questa trascrizione, per esempio per ritrascrivere con uno
     *   più accurato; null per quello attivo.
     * @param avanzamento Frazione completata tra 0 e 1.
     */
    suspend fun transcribeAudioFile(
        file: File,
        opzioni: OpzioniTrascrizione = OpzioniTrascrizione.Predefinite,
        idModello: String? = null,
        avanzamento: (Float) -> Unit = {},
    ): Result<RisultatoTrascrizione> {
        val campioni = AudioDecoder.decodeTo16kMono(file).getOrElse { return Result.failure(it) }
        return trascriviCampioni(campioni, opzioni, idModello, avanzamento)
    }

    /**
     * Divide i campioni a 16 kHz mono in blocchi di parlato e li trascrive con il motore del modello richiesto.
     */
    private suspend fun trascriviCampioni(
        campioni: FloatArray,
        opzioni: OpzioniTrascrizione,
        idModello: String?,
        avanzamento: (Float) -> Unit,
    ): Result<RisultatoTrascrizione> {
        ensureModelLoaded(idModello).onFailure { return Result.failure(it) }
        val opzioniEffettive = opzioni.copy(lingua = linguaPer(activeEngine.modelloCorrente, opzioni))
        return runCatching {
            val segmenti = segmentiDiParlato(campioni, activeEngine.modelloCorrente)
            if (segmenti.isEmpty()) throw NessunParlatoException()
            activeEngine.trascrivi(segmenti, opzioniEffettive, avanzamento)
                // Il rilevatore toglie il non-parlato: la somma dei segmenti è più corta del vocale
                .copy(durataAudioMs = campioni.size * 1000L / FREQUENZA_CAMPIONAMENTO)
        }
    }

    /**
     * I blocchi da trascrivere: il parlato trovato dal rilevatore, unito fino alla durata massima del motore.
     * Se il rilevatore non c'è o non riesce, l'audio intero diviso nelle pause, come prima del VAD.
     * @return Vuoto se nel vocale non c'è parlato.
     */
    private suspend fun segmentiDiParlato(
        campioni: FloatArray,
        modello: ModelloInstallato?,
    ): List<SegmentoVoce> = withContext(Dispatchers.Default) {
        val durataMassimaS = (modello?.architettura ?: ArchitetturaModello.GGML).durataMassimaBloccoS
        val tratti = rilevatore?.trova(campioni, durataMassimaS)
        if (tratti == null) {
            AudioSegmenter.segmenta(campioni)
        } else {
            SegmentatoreParlato.segmenta(campioni, tratti, durataMassimaS)
        }
    }

    /**
     * Trascrizione diretta secondo la nuova interfaccia [MotoreTrascrizione].
     */
    suspend fun trascriviSegmenti(
        segmenti: List<SegmentoVoce>,
        opzioni: OpzioniTrascrizione = OpzioniTrascrizione.Predefinite,
        avanzamento: (Float) -> Unit = {}
    ): Result<RisultatoTrascrizione> {
        ensureModelLoaded().onFailure { return Result.failure(it) }
        val opzioniEffettive = opzioni.copy(lingua = linguaPer(activeEngine.modelloCorrente, opzioni))
        return runCatching {
            activeEngine.trascrivi(segmenti, opzioniEffettive, avanzamento)
        }
    }

    /**
     * La lingua con cui trascrivere: quella chiesta esplicitamente, altrimenti quella del modello se è
     * specializzato, altrimenti quella delle impostazioni; null perché il modello la riconosca da sé.
     */
    private fun linguaPer(modello: ModelloInstallato?, opzioni: OpzioniTrascrizione): String? {
        return opzioni.lingua ?: modello?.linguaSpecializzata ?: impostazioni.linguaPredefinita
    }

    /** true se il motore sa eseguire il modello. */
    private fun motoreAdatto(motore: MotoreTrascrizione, modello: ModelloInstallato): Boolean = when (modello.tipo) {
        TipoMotore.SHERPA_ONNX -> motore is MotoreSherpaOnnx
        TipoMotore.WHISPER_CPP -> motore is MotoreWhisperCpp
        TipoMotore.MOCK -> motore is MotoreMock
    }

    /** Un motore nuovo, adatto al modello. */
    private fun nuovoMotore(modello: ModelloInstallato): MotoreTrascrizione = when (modello.tipo) {
        TipoMotore.SHERPA_ONNX -> MotoreSherpaOnnx()
        TipoMotore.WHISPER_CPP -> MotoreWhisperCpp()
        TipoMotore.MOCK -> MotoreMock()
    }

    /** Libera il modello del motore attuale e passa al nuovo. */
    private fun sostituisciMotore(nuovo: MotoreTrascrizione) {
        activeEngine.close()
        activeEngine = nuovo
    }

    private companion object {
        /** AudioDecoder consegna sempre campioni a 16 kHz. */
        const val FREQUENZA_CAMPIONAMENTO = 16_000
    }
}
