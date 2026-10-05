package org.florisboard.lib.localai

import android.content.Context
import android.net.Uri
import org.florisboard.lib.localai.audio.AudioDecoder
import org.florisboard.lib.localai.engine.*
import org.florisboard.lib.localai.model.ModelCatalog
import org.florisboard.lib.localai.model.ModelInfo
import org.florisboard.lib.localai.model.ModelManager
import org.florisboard.lib.localai.whisper.MotoreWhisperCpp
import java.io.File

/**
 * Coordinatore principale del sottosistema AI Locale.
 * Collega i motori di trascrizione intercambiabili ([MotoreTrascrizione]),
 * il gestore dei modelli ([ModelManager]), il decoder audio per formati WhatsApp/Opus
 * e il registratore vocale.
 */
class LocalAiManager(val context: Context) {
    val modelManager = ModelManager(context)

    var activeEngine: MotoreTrascrizione = MotoreWhisperCpp()
        private set

    /**
     * Assicura che il modello selezionato sia caricato in RAM (operazione eseguita una sola volta).
     */
    suspend fun ensureModelLoaded(): Result<Unit> {
        if (activeEngine.isModelloCaricato) return Result.success(Unit)

        val installedModel = modelManager.getActiveInstalledModel()
        return if (installedModel != null && installedModel.percorsoFile.exists()) {
            runCatching {
                activeEngine.carica(installedModel)
            }
        } else {
            // Se nessun modello offline è stato ancora scaricato, usiamo il motore mock temporaneo
            if (activeEngine !is MotoreMock) {
                activeEngine = MotoreMock()
            }
            Result.success(Unit)
        }
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
     */
    suspend fun transcribeAudioUri(
        uri: Uri,
        options: TranscriptionOptions = TranscriptionOptions.Default,
        onProgress: ((Float) -> Unit)? = null
    ): Result<TranscriptionResult> {
        ensureModelLoaded()
        val samplesResult = AudioDecoder.decodeTo16kMono(context, uri)
        if (samplesResult.isFailure) {
            return Result.failure(samplesResult.exceptionOrNull()!!)
        }
        val samples = samplesResult.getOrThrow()
        return transcribeRecordedAudio(samples, options, onProgress)
    }

    /**
     * Trascrive un file audio locale dal filesystem.
     */
    suspend fun transcribeAudioFile(
        file: File,
        options: TranscriptionOptions = TranscriptionOptions.Default,
        onProgress: ((Float) -> Unit)? = null
    ): Result<TranscriptionResult> {
        ensureModelLoaded()
        val samplesResult = AudioDecoder.decodeTo16kMono(file)
        if (samplesResult.isFailure) {
            return Result.failure(samplesResult.exceptionOrNull()!!)
        }
        val samples = samplesResult.getOrThrow()
        return transcribeRecordedAudio(samples, options, onProgress)
    }

    /**
     * Trascrive un array di campioni float normalizzati a 16 kHz Mono.
     */
    suspend fun transcribeRecordedAudio(
        samples: FloatArray,
        options: TranscriptionOptions = TranscriptionOptions.Default,
        onProgress: ((Float) -> Unit)? = null
    ): Result<TranscriptionResult> {
        ensureModelLoaded()
        return runCatching {
            val segmenti = org.florisboard.lib.localai.audio.AudioSegmenter.segmenta(samples)
            val opzioni = options.toOpzioniTrascrizione()
            val risultato = activeEngine.trascrivi(
                segmenti = segmenti,
                opzioni = opzioni,
                avanzamento = { progress -> onProgress?.invoke(progress) }
            )
            risultato.toTranscriptionResult()
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
        ensureModelLoaded()
        return runCatching {
            activeEngine.trascrivi(segmenti, opzioni, avanzamento)
        }
    }
}
