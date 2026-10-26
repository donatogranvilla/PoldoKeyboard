package io.github.donatogranvilla.poldokeyboard.localai.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Catalogo dei modelli importabili, letto dal file di dati `catalogo_modelli.json` del modulo.
 * Ogni voce ha impronta SHA-256 e dimensione prese dalla fonte ufficiale (API di Hugging Face o di GitHub):
 * così un file importato si riconosce con certezza. Il catalogo incluso nell'app si può aggiornare su richiesta
 * dell'utente con quello pubblicato su GitHub ([AggiornamentoCatalogo]); i modelli che non ci sono si importano
 * come personalizzati.
 */
object ModelCatalog {
    private const val RISORSA = "/catalogo_modelli.json"
    private val formato = Json { ignoreUnknownKeys = true }

    @Volatile private var fileScaricato: File? = null
    @Volatile private var modelli: List<ModelInfo>? = null

    /** I modelli del catalogo incluso nell'app, letti una sola volta. */
    val inclusi: List<ModelInfo> by lazy {
        val flusso = checkNotNull(ModelCatalog::class.java.getResourceAsStream(RISORSA)) {
            "Catalogo dei modelli mancante: $RISORSA"
        }
        flusso.bufferedReader().use { lettore -> formato.decodeFromString<List<ModelInfo>>(lettore.readText()) }
    }

    /** Tutti i modelli: quelli inclusi nell'app, aggiornati e completati dal catalogo scaricato se c'è. */
    val ALL_MODELS: List<ModelInfo>
        get() = modelli ?: carica().also { modelli = it }

    /**
     * Usa il catalogo scaricato nel file indicato, o nessuno con null. Se il file è lo stesso di prima non rilegge
     * nulla: per un file appena riscritto serve [ricarica].
     */
    fun usaCatalogoScaricato(file: File?) {
        if (file == fileScaricato) return
        fileScaricato = file
        modelli = null
    }

    /** Rilegge il catalogo scaricato al prossimo accesso. */
    fun ricarica() {
        modelli = null
    }

    /** Il catalogo incluso unito a quello scaricato; uno scaricato illeggibile si ignora. */
    private fun carica(): List<ModelInfo> {
        val scaricati = fileScaricato
            ?.let { file -> runCatching { LetturaCatalogo.leggi(file.readText()).modelli }.getOrNull() }
            .orEmpty()
        return LetturaCatalogo.unisci(inclusi, scaricati)
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
