package org.florisboard.lib.localai.model

/**
 * Il formato di un file scelto per l'import, riconosciuto dai primi byte e dal nome. Serve ad accettare
 * i formati giusti e a spiegare all'utente cosa scaricare quando il file non va bene.
 */
enum class FormatoFileModello {
    /** Modello Whisper per whisper.cpp: il file `ggml-*.bin` giusto. */
    GGML_WHISPER,

    /** Archivio `.tar.bz2`, il formato delle release ufficiali di sherpa-onnx. */
    ARCHIVIO_TAR_BZ2,

    /** Archivio `.zip` con i file di un modello sherpa-onnx. */
    ARCHIVIO_ZIP,

    /** File GGUF di llama.cpp: è un formato per modelli di testo, non per whisper.cpp. */
    GGUF,

    /** Pesi Hugging Face `.safetensors`: vanno convertiti, l'app non li legge. */
    SAFETENSORS,

    /** Pesi PyTorch `.pt`, `.pth` o `pytorch_model.bin`: vanno convertiti, l'app non li legge. */
    PYTORCH,

    /** Un singolo file ONNX: i modelli sherpa-onnx sono fatti di più file, vanno importati come archivio. */
    ONNX_SINGOLO,

    /** Formato non riconosciuto. */
    SCONOSCIUTO;

    /** true se l'app sa importare un file in questo formato. */
    val importabile: Boolean get() = this == GGML_WHISPER || this == ARCHIVIO_TAR_BZ2 || this == ARCHIVIO_ZIP

    /** true se il file è un archivio con la cartella di un modello sherpa-onnx. */
    val archivio: Boolean get() = this == ARCHIVIO_TAR_BZ2 || this == ARCHIVIO_ZIP
}

/**
 * I dati dell'intestazione di un file GGML di whisper.cpp che servono all'import.
 * @property numeroVocaboli Dimensione del vocabolario: distingue i modelli multilingue da quelli solo inglese.
 */
data class IntestazioneGgml(val numeroVocaboli: Int) {
    /** false per i modelli solo inglese (i `.en`), che hanno un vocabolario più piccolo. */
    val multilingue: Boolean get() = numeroVocaboli >= VOCABOLARIO_MULTILINGUE

    companion object {
        /** Vocaboli dei Whisper multilingue (51866 da large-v3); i modelli solo inglese ne hanno 51864. */
        const val VOCABOLARIO_MULTILINGUE = 51865
    }
}

/**
 * Riconosce il formato di un file dai primi byte, che vanno letti prima di copiarlo.
 */
object RiconoscitoreFormato {
    /** Byte da leggere all'inizio del file: bastano per tutti i formati riconosciuti. */
    const val BYTE_INIZIALI = 16

    // I numeri magici scritti come interi little-endian: "lmgg" per whisper.cpp, "GGUF" per llama.cpp
    private const val MAGIC_GGML = 0x67676d6c
    private const val MAGIC_GGUF = 0x46554747

    /**
     * Il formato del file.
     * @param inizio I primi byte del file, almeno [BYTE_INIZIALI] se il file è abbastanza lungo.
     * @param nomeFile Il nome del file, se il selettore di sistema lo fornisce.
     */
    fun riconosci(inizio: ByteArray, nomeFile: String?): FormatoFileModello {
        val nome = nomeFile?.lowercase().orEmpty()
        return when {
            intero(inizio, 0) == MAGIC_GGML -> FormatoFileModello.GGML_WHISPER
            intero(inizio, 0) == MAGIC_GGUF -> FormatoFileModello.GGUF
            iniziaCon(inizio, 'B'.code, 'Z'.code, 'h'.code) -> FormatoFileModello.ARCHIVIO_TAR_BZ2
            iniziaCon(inizio, 'P'.code, 'K'.code, 3, 4) -> FormatoFileModello.ARCHIVIO_ZIP
            nome.endsWith(".safetensors") -> FormatoFileModello.SAFETENSORS
            nome.endsWith(".pt") || nome.endsWith(".pth") || nome.startsWith("pytorch_model") -> FormatoFileModello.PYTORCH
            nome.endsWith(".onnx") || nome.endsWith(".ort") -> FormatoFileModello.ONNX_SINGOLO
            else -> FormatoFileModello.SCONOSCIUTO
        }
    }

    /**
     * L'intestazione GGML, se il file è un modello di whisper.cpp.
     */
    fun intestazioneGgml(inizio: ByteArray): IntestazioneGgml? {
        if (intero(inizio, 0) != MAGIC_GGML) return null
        val vocaboli = intero(inizio, 4) ?: return null
        return IntestazioneGgml(vocaboli)
    }

    /** Un intero a 32 bit little-endian a partire dalla posizione indicata, o null se i byte non bastano. */
    private fun intero(byte: ByteArray, da: Int): Int? {
        if (byte.size < da + 4) return null
        return (byte[da].toInt() and 0xff) or
            ((byte[da + 1].toInt() and 0xff) shl 8) or
            ((byte[da + 2].toInt() and 0xff) shl 16) or
            ((byte[da + 3].toInt() and 0xff) shl 24)
    }

    /** true se i primi byte coincidono con quelli indicati. */
    private fun iniziaCon(byte: ByteArray, vararg attesi: Int): Boolean {
        if (byte.size < attesi.size) return false
        return attesi.indices.all { (byte[it].toInt() and 0xff) == attesi[it] }
    }
}
