package org.florisboard.lib.localai

import android.content.Context
import android.net.Uri
import org.florisboard.lib.localai.audio.AudioDecoder
import org.florisboard.lib.localai.audio.AudioSegmenter
import org.florisboard.lib.localai.engine.*
import org.florisboard.lib.localai.model.ModelCatalog
import org.florisboard.lib.localai.model.ModelInfo
import org.florisboard.lib.localai.model.ModelManager
import org.florisboard.lib.localai.whisper.MotoreWhisperCpp
import java.io.File

/**
 * Coordinatore principale del sottosistema AI Locale.
 * Collega i motori di trascrizione intercambiabili ([MotoreTrascrizione]),
 * il gestore dei modelli ([ModelManager]) e il decoder audio per i formati WhatsApp/Opus.
 * @param consentiMotoreFinto true per poter provare il flusso con [MotoreMock] quando manca un modello,
 *   pensato per le build di sviluppo; in produzione resta false.
 */
class LocalAiManager(
    val context: Context,
    private val consentiMotoreFinto: Boolean = false,
) {
    val modelManager = ModelManager(context)

    var activeEngine: MotoreTrascrizione = MotoreWhisperCpp()
        private set

    /**
     * Assicura che il modello selezionato sia caricato in RAM (operazione eseguita una sola volta).
     * @return Successo se il motore attivo è pronto, altrimenti l'errore che spiega perché non lo è.
     */
    suspend fun ensureModelLoaded(): Result<Unit> {
        if (activeEngine.isModelloCaricato) return Result.success(Unit)

        val installedModel = modelManager.getActiveInstalledModel()
        if (installedModel != null && installedModel.percorsoFile.exists()) {
            // Un modello vero non va mai caricato nel motore finto
            if (activeEngine is MotoreMock) {
                activeEngine.close()
                activeEngine = MotoreWhisperCpp()
            }
            return runCatching { activeEngine.carica(installedModel) }
        }

        // Nessun modello installato: errore esplicito, a meno che il chiamante non abbia chiesto il motore finto
        if (!consentiMotoreFinto) {
            return Result.failure(NessunModelloException())
        }
        if (activeEngine !is MotoreMock) {
            activeEngine.close()
            activeEngine = MotoreMock()
        }
        return Result.success(Unit)
    }

    /**
     * Cambia il modello attivo e ricarica il motore.
     */
    suspend fun switchModel(modelId: String): Result<Unit> {
        modelManager.setActiveModelId(modelId)
        val installedModel = modelManager.getActiveInstalledModel()
        return if (installedModel != null && installedModel.percorsoFile.exists()) {
            if (activeEngine !is MotoreWhisperCpp) {
                activeEngine.close()
                activeEngine = MotoreWhisperCpp()
            }
            runCatching {
                activeEngine.carica(installedModel)
            }
        } else {
            Result.failure(IllegalStateException("Il modello selezionato non è ancora scaricato."))
        }
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
        return trascriviCampioni(campioni, opzioni, avanzamento)
    }

    /**
     * Trascrive un file audio locale dal filesystem.
     * @param avanzamento Frazione completata tra 0 e 1.
     */
    suspend fun transcribeAudioFile(
        file: File,
        opzioni: OpzioniTrascrizione = OpzioniTrascrizione.Predefinite,
        avanzamento: (Float) -> Unit = {},
    ): Result<RisultatoTrascrizione> {
        val campioni = AudioDecoder.decodeTo16kMono(file).getOrElse { return Result.failure(it) }
        return trascriviCampioni(campioni, opzioni, avanzamento)
    }

    /**
     * Divide i campioni a 16 kHz mono in segmenti e li trascrive con il motore attivo.
     */
    private suspend fun trascriviCampioni(
        campioni: FloatArray,
        opzioni: OpzioniTrascrizione,
        avanzamento: (Float) -> Unit,
    ): Result<RisultatoTrascrizione> {
        ensureModelLoaded().onFailure { return Result.failure(it) }
        return runCatching {
            activeEngine.trascrivi(AudioSegmenter.segmenta(campioni), opzioni, avanzamento)
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
        return runCatching {
            activeEngine.trascrivi(segmenti, opzioni, avanzamento)
        }
    }
}
