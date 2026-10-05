package org.florisboard.lib.localai.model

import org.florisboard.lib.localai.engine.EngineType

/**
 * Registro dei modelli vocali offline verificati con licenza commerciale MIT / Apache 2.0.
 * Organizzati per livello di accuratezza e requisiti hardware del dispositivo.
 */
object ModelCatalog {
    val WHISPER_TINY_Q5 = ModelInfo(
        id = "whisper-tiny-q5_1",
        name = "Whisper Tiny (Q5_1)",
        engineType = EngineType.WHISPER_CPP,
        sizeBytes = 32_152_673L,
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
        license = "MIT",
        qualityLevel = "Ultra-Leggero (Minimo consumo RAM)",
        description = "Trascrizione fulminea a bassissimo consumo di memoria (~200MB RAM). Ottimale per dispositivi entry-level o note vocali brevi.",
        ramConsigliataMb = 200,
        stelleAccuratezza = 3,
        fulminiVelocita = 5,
        quantizzazione = "Q5_1",
    )

    val WHISPER_BASE_Q5 = ModelInfo(
        id = "whisper-base-q5_1",
        name = "Whisper Base (Q5_1)",
        engineType = EngineType.WHISPER_CPP,
        sizeBytes = 59_707_625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        license = "MIT",
        qualityLevel = "Consigliato (Bilanciato)",
        description = "Trascrizione accurata multilingua (Italiano, Inglese, Spagnolo, Francese, Tedesco, Portoghese) con ottima punteggiatura e fluidità.",
        ramConsigliataMb = 400,
        stelleAccuratezza = 4,
        fulminiVelocita = 4,
        quantizzazione = "Q5_1",
    )

    val WHISPER_SMALL_Q5 = ModelInfo(
        id = "whisper-small-q5_1",
        name = "Whisper Small (Q5_1)",
        engineType = EngineType.WHISPER_CPP,
        sizeBytes = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        license = "MIT",
        qualityLevel = "Massima Accuratezza (Audio rumorosi e nomi propri)",
        description = "Massima accuratezza per messaggi vocali lunghi di WhatsApp, audio con rumore di fondo, nomi di persona e termini tecnici. Consigliato per telefoni potenti (6GB+ RAM).",
        ramConsigliataMb = 900,
        stelleAccuratezza = 5,
        fulminiVelocita = 2,
        quantizzazione = "Q5_1",
    )

    val ALL_MODELS = listOf(
        WHISPER_TINY_Q5,
        WHISPER_BASE_Q5,
        WHISPER_SMALL_Q5,
    )

    val DEFAULT_MODEL = WHISPER_BASE_Q5

    fun findById(id: String): ModelInfo? {
        return ALL_MODELS.find { it.id == id }
    }

    /**
     * Cerca nel catalogo il modello con l'impronta indicata: serve a riconoscere un file importato.
     * @param sha256 Impronta SHA-256 in esadecimale, maiuscole o minuscole.
     * @return Il modello corrispondente, oppure null se il file non è nel catalogo.
     */
    fun findBySha256(sha256: String): ModelInfo? {
        return ALL_MODELS.find { it.sha256.equals(sha256, ignoreCase = true) }
    }
}

/** Alias italiano */
typealias CatalogoModelli = ModelCatalog
