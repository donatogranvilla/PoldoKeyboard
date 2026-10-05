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
        sizeBytes = 31_000_000L,
        sha256 = "3239e2499ddc0384a6c6e7f7ab2c7b52da0e2e9cff545b7367e9b04f76269df6",
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
        sizeBytes = 57_000_000L,
        sha256 = "c24ec494a8f9f743c4a04e578c772e44d3ba928a30cf7e2f50c0c6b12a83e0c0",
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
        sizeBytes = 182_000_000L,
        sha256 = "91a5e1e07ca4dc2d67a6d860e6f212fb9471131792bc44360e22709e99a80e1b",
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
}

/** Alias italiano */
typealias CatalogoModelli = ModelCatalog
