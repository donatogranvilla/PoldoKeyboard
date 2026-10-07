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
 * @property percorsoFile Il file del modello per whisper.cpp, la cartella con i suoi file per sherpa-onnx.
 * @property lingue Le lingue del modello: vuota se le copre tutte, una sola se è specializzato.
 */
data class ModelloInstallato(
    val id: String,
    val nome: String,
    val percorsoFile: File,
    val dimensioneByte: Long = if (percorsoFile.exists()) percorsoFile.length() else 0L,
    val tipo: TipoMotore = TipoMotore.WHISPER_CPP,
    val ramConsigliataMb: Int = 400,
    val descrizione: String = "",
    val architettura: ArchitetturaModello = ArchitetturaModello.GGML,
    val lingue: List<String> = emptyList(),
) {
    /** La lingua da imporre in trascrizione, se il modello è specializzato in una sola. */
    val linguaSpecializzata: String? get() = lingue.singleOrNull()
}

/**
 * Tipologia di motore ASR sottostante.
 */
enum class TipoMotore {
    WHISPER_CPP,
    SHERPA_ONNX,
    MOCK,
}

/**
 * Com'è fatto un modello, e quindi quale motore lo esegue e quali file servono.
 * @property motore Il motore che sa eseguirlo.
 * @property singoloFile true se il modello è un solo file; altrimenti è una cartella di file.
 * @property durataMassimaBloccoS La durata massima, in secondi, di un blocco di parlato da trascrivere in una
 *   volta: Whisper lavora su finestre di 30 s, GigaAM regge fino a 25 s, Omnilingual fino a 40 s.
 */
enum class ArchitetturaModello(
    val motore: TipoMotore,
    val singoloFile: Boolean,
    val durataMassimaBloccoS: Int = 28,
) {
    /** Whisper in formato GGML per whisper.cpp: un solo file `ggml-*.bin`. */
    GGML(TipoMotore.WHISPER_CPP, singoloFile = true),

    /** Transducer NeMo, come Parakeet TDT e GigaAM: encoder, decoder, joiner e tokens.txt. */
    NEMO_TRANSDUCER(TipoMotore.SHERPA_ONNX, singoloFile = false, durataMassimaBloccoS = 24),

    /** Transducer Zipformer di icefall: encoder, decoder, joiner e tokens.txt. */
    ZIPFORMER_TRANSDUCER(TipoMotore.SHERPA_ONNX, singoloFile = false),

    /** CTC NeMo: un solo file model.onnx più tokens.txt. */
    NEMO_CTC(TipoMotore.SHERPA_ONNX, singoloFile = false, durataMassimaBloccoS = 24),

    /** Omnilingual ASR di Meta (CTC): model.onnx più tokens.txt. */
    OMNILINGUAL_CTC(TipoMotore.SHERPA_ONNX, singoloFile = false, durataMassimaBloccoS = 35),

    /** Moonshine: preprocess, encode e decoder (v1) oppure encoder e decoder unito (v2), più tokens.txt. */
    MOONSHINE(TipoMotore.SHERPA_ONNX, singoloFile = false),

    /** SenseVoice: model.onnx più tokens.txt. */
    SENSE_VOICE(TipoMotore.SHERPA_ONNX, singoloFile = false),

    /** Whisper esportato in ONNX per sherpa-onnx: encoder, decoder e tokens. */
    WHISPER_ONNX(TipoMotore.SHERPA_ONNX, singoloFile = false),
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
    val numThread: Int = CoreCpu.threadConsigliati(),
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
