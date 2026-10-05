package org.florisboard.lib.localai.engine

import java.io.File

/**
 * Motore di riconoscimento vocale locale, intercambiabile.
 * Progettato per consentire lo swap dinamico del backend di inferenza
 * (Whisper C++, Sherpa-ONNX, o Mock per test) senza impattare la UI
 * o la gestione della tastiera.
 */
interface MotoreTrascrizione : AutoCloseable {
    /**
     * Carica in memoria il modello indicato: operazione lenta, da fare una volta.
     * @param modello Il modello installato localmente sul filesystem del dispositivo.
     */
    suspend fun carica(modello: ModelloInstallato)

    /**
     * Trascrive segmenti di voce a 16 kHz, segnalando l'avanzamento da 0 a 1.
     * @param segmenti Lista di segmenti audio a 16.000 Hz Mono FloatArray.
     * @param opzioni Parametri di configurazione (lingua, punteggiatura, thread, ecc.).
     * @param avanzamento Callback richiamata periodicamente con valori tra 0.0f e 1.0f.
     * @return [RisultatoTrascrizione] contenente il testo trascritto e metadati.
     */
    suspend fun trascrivi(
        segmenti: List<SegmentoVoce>,
        opzioni: OpzioniTrascrizione,
        avanzamento: (Float) -> Unit,
    ): RisultatoTrascrizione

    /**
     * Indica se un modello è attualmente caricato in memoria e pronto all'uso.
     */
    val isModelloCaricato: Boolean get() = false

    /**
     * Il modello attualmente caricato in RAM, o null se non caricato.
     */
    val modelloCorrente: ModelloInstallato? get() = null
}

/**
 * Rappresenta un modello speech-to-text scaricato e verificato sul dispositivo.
 */
data class ModelloInstallato(
    val id: String,
    val nome: String,
    val percorsoFile: File,
    val dimensioneByte: Long = if (percorsoFile.exists()) percorsoFile.length() else 0L,
    val tipo: TipoMotore = TipoMotore.WHISPER_CPP,
    val ramConsigliataMb: Int = 400,
    val descrizione: String = "",
)

/**
 * Tipologia di motore ASR sottostante.
 */
enum class TipoMotore {
    WHISPER_CPP,
    SHERPA_ONNX,
    MOCK,
}

/**
 * Rappresenta un segmento audio vocale a 16.000 Hz, 16-bit Mono (float normalizzati [-1.0f, 1.0f]).
 */
data class SegmentoVoce(
    val campioniAudio: FloatArray,
    val timestampInizioMs: Long = 0L,
    val timestampFineMs: Long = (campioniAudio.size / 16.0).toLong(),
) {
    val durataMs: Long get() = (timestampFineMs - timestampInizioMs).coerceAtLeast(0L)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SegmentoVoce
        return campioniAudio.contentEquals(other.campioniAudio) &&
                timestampInizioMs == other.timestampInizioMs &&
                timestampFineMs == other.timestampFineMs
    }

    override fun hashCode(): Int {
        var result = campioniAudio.contentHashCode()
        result = 31 * result + timestampInizioMs.hashCode()
        result = 31 * result + timestampFineMs.hashCode()
        return result
    }
}

/**
 * Parametri di configurazione per la trascrizione.
 */
data class OpzioniTrascrizione(
    val lingua: String? = null,
    val formattaPunteggiatura: Boolean = true,
    val numThread: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
    val temperatura: Float = 0.0f,
    val traduciInInglese: Boolean = false,
) {
    companion object {
        val Predefinite = OpzioniTrascrizione()
    }
}

/**
 * Risultato completo dell'inferenza vocale locale.
 */
data class RisultatoTrascrizione(
    val testo: String,
    val segmenti: List<SegmentoTrascritto> = emptyList(),
    val linguaRilevata: String? = null,
    val durataAudioMs: Long = 0L,
    val tempoInferenzaMs: Long = 0L,
)

/**
 * Singolo segmento testuale con marcatura temporale.
 */
data class SegmentoTrascritto(
    val inizioMs: Long,
    val fineMs: Long,
    val testo: String,
)
