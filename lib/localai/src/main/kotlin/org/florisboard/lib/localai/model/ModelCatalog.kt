package org.florisboard.lib.localai.model

import org.florisboard.lib.localai.engine.TipoMotore

/**
 * Catalogo dei modelli vocali importabili, con licenza MIT e con impronte SHA-256 e dimensioni
 * prese dall'API di Hugging Face. Il modello predefinito definitivo lo sceglierà il benchmark
 * sui vocali reali; fino ad allora vale la raccomandazione in base alla RAM del dispositivo.
 */
object ModelCatalog {
    val WHISPER_TINY_Q5 = ModelInfo(
        id = "whisper-tiny-q5_1",
        name = "Whisper Tiny (Q5_1)",
        tipoMotore = TipoMotore.WHISPER_CPP,
        sizeBytes = 32_152_673L,
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
        license = "MIT",
        qualityLevel = "Ultra-Leggero (Minimo consumo RAM)",
        description = "Trascrizione fulminea a bassissimo consumo di memoria (~200MB RAM). Ottimale per dispositivi entry-level o note vocali brevi.",
        ramConsigliataMb = 200,
        stelleAccuratezza = 2,
        fulminiVelocita = 5,
        quantizzazione = "Q5_1",
    )

    val WHISPER_BASE_Q5 = ModelInfo(
        id = "whisper-base-q5_1",
        name = "Whisper Base (Q5_1)",
        tipoMotore = TipoMotore.WHISPER_CPP,
        sizeBytes = 59_707_625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
        license = "MIT",
        qualityLevel = "Leggero",
        description = "Leggero e rapido, adatto a vocali brevi e puliti; sbaglia più spesso nomi propri e parole poco comuni.",
        ramConsigliataMb = 400,
        stelleAccuratezza = 3,
        fulminiVelocita = 4,
        quantizzazione = "Q5_1",
    )

    val WHISPER_SMALL_Q5 = ModelInfo(
        id = "whisper-small-q5_1",
        name = "Whisper Small (Q5_1)",
        tipoMotore = TipoMotore.WHISPER_CPP,
        sizeBytes = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
        license = "MIT",
        qualityLevel = "Buona accuratezza",
        description = "Buon compromesso tra qualità e velocità; con audio rumorosi e nomi propri resta sotto i modelli large.",
        ramConsigliataMb = 900,
        stelleAccuratezza = 4,
        fulminiVelocita = 3,
        quantizzazione = "Q5_1",
    )

    // Da medium in su la RAM consigliata è una stima, da misurare sul telefono in F2
    val WHISPER_MEDIUM_Q5 = ModelInfo(
        id = "whisper-medium-q5_0",
        name = "Whisper Medium (Q5_0)",
        tipoMotore = TipoMotore.WHISPER_CPP,
        sizeBytes = 539_212_467L,
        sha256 = "19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
        license = "MIT",
        qualityLevel = "Alta accuratezza",
        description = "Qualità vicina ai modelli large con un file più piccolo, ma più lento di small.",
        ramConsigliataMb = 1000,
        stelleAccuratezza = 4,
        fulminiVelocita = 2,
        quantizzazione = "Q5_0",
    )

    val WHISPER_LARGE_V3_TURBO_Q5 = ModelInfo(
        id = "whisper-large-v3-turbo-q5_0",
        name = "Whisper Large v3 Turbo (Q5_0)",
        tipoMotore = TipoMotore.WHISPER_CPP,
        sizeBytes = 574_041_195L,
        sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        license = "MIT",
        qualityLevel = "Molto alta",
        description = "Large v3 con il decoder ridotto da 32 a 4 strati: qualità molto alta con file e tempi più contenuti. Candidato principale per un telefono da 8 GB.",
        ramConsigliataMb = 1000,
        stelleAccuratezza = 5,
        fulminiVelocita = 2,
        quantizzazione = "Q5_0",
    )

    val WHISPER_LARGE_V3_TURBO_Q8 = ModelInfo(
        id = "whisper-large-v3-turbo-q8_0",
        name = "Whisper Large v3 Turbo (Q8_0)",
        tipoMotore = TipoMotore.WHISPER_CPP,
        sizeBytes = 874_188_075L,
        sha256 = "317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q8_0.bin",
        license = "MIT",
        qualityLevel = "Molto alta",
        description = "Come la versione Q5_0 ma con una quantizzazione più fedele ai pesi originali: file e memoria maggiori.",
        ramConsigliataMb = 1300,
        stelleAccuratezza = 5,
        fulminiVelocita = 2,
        quantizzazione = "Q8_0",
    )

    val WHISPER_LARGE_V3_Q5 = ModelInfo(
        id = "whisper-large-v3-q5_0",
        name = "Whisper Large v3 (Q5_0)",
        tipoMotore = TipoMotore.WHISPER_CPP,
        sizeBytes = 1_081_140_203L,
        sha256 = "d75795ecff3f83b5faa89d1900604ad8c780abd5739fae406de19f23ecd98ad1",
        downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin",
        license = "MIT",
        qualityLevel = "Massima",
        description = "Il Whisper più accurato, con il decoder completo: il più lento e il più pesante. Da verificare sul telefono da 8 GB.",
        ramConsigliataMb = 2000,
        stelleAccuratezza = 5,
        fulminiVelocita = 1,
        quantizzazione = "Q5_0",
    )

    // Parakeet v3 entrerà con il motore sherpa-onnx (F3): richiede un archivio di più file
    val ALL_MODELS = listOf(
        WHISPER_TINY_Q5,
        WHISPER_BASE_Q5,
        WHISPER_SMALL_Q5,
        WHISPER_MEDIUM_Q5,
        WHISPER_LARGE_V3_TURBO_Q5,
        WHISPER_LARGE_V3_TURBO_Q8,
        WHISPER_LARGE_V3_Q5,
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
