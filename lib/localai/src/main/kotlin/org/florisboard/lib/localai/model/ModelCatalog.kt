package org.florisboard.lib.localai.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Catalogo dei modelli importabili, letto dal file di dati `catalogo_modelli.json` del modulo.
 * Ogni voce ha impronta SHA-256 e dimensione prese dalla fonte ufficiale (API di Hugging Face o di GitHub):
 * così un file importato si riconosce con certezza. L'app non usa la rete, quindi il catalogo si aggiorna
 * con le nuove versioni dell'app; i modelli che non ci sono si importano come personalizzati.
 */
object ModelCatalog {
    private const val RISORSA = "/catalogo_modelli.json"
    private val formato = Json { ignoreUnknownKeys = true }

    /** Tutti i modelli del catalogo, letti una sola volta. */
    val ALL_MODELS: List<ModelInfo> by lazy {
        val flusso = checkNotNull(ModelCatalog::class.java.getResourceAsStream(RISORSA)) {
            "Catalogo dei modelli mancante: $RISORSA"
        }
        flusso.bufferedReader().use { lettore -> formato.decodeFromString<List<ModelInfo>>(lettore.readText()) }
    }

    val WHISPER_TINY_Q5: ModelInfo get() = trova("whisper-tiny-q5_1")
    val WHISPER_BASE_Q5: ModelInfo get() = trova("whisper-base-q5_1")
    val WHISPER_SMALL_Q5: ModelInfo get() = trova("whisper-small-q5_1")

    /** Il modello proposto quando non ce n'è un altro consigliato per il dispositivo. */
    val DEFAULT_MODEL: ModelInfo get() = WHISPER_BASE_Q5

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

    /**
     * I modelli che coprono una lingua: prima gli specializzati, poi i multilingue.
     */
    fun perLingua(lingua: String): List<ModelInfo> {
        return ALL_MODELS.filter { it.copre(lingua) }.sortedByDescending { it.specializzato }
    }

    private fun trova(id: String): ModelInfo = checkNotNull(findById(id)) { "Modello $id assente dal catalogo" }
}

/** Alias italiano */
typealias CatalogoModelli = ModelCatalog
