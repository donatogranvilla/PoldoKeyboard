package org.florisboard.lib.localai.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

/**
 * Un catalogo letto: i modelli validi e quante voci sono state scartate.
 * @property vociScartate Le voci che questa versione dell'app non sa leggere, come un modello con un'architettura
 *   aggiunta in una versione successiva, oppure con dati non validi o con un id ripetuto.
 */
data class CatalogoLetto(val modelli: List<ModelInfo>, val vociScartate: Int)

/** Il testo non è un catalogo: non è un elenco JSON, è troppo grande o non contiene nessun modello valido. */
class CatalogoNonValidoException(messaggio: String) : IllegalArgumentException(messaggio)

/**
 * Legge e unisce i cataloghi dei modelli, nel formato di `catalogo_modelli.json`: un elenco JSON di [ModelInfo].
 */
object LetturaCatalogo {
    private val formato = Json { ignoreUnknownKeys = true }
    private val IMPRONTA = Regex("[0-9a-fA-F]{64}")

    /**
     * Legge un catalogo voce per voce. Una voce che non si sa leggere viene scartata invece di far fallire tutto:
     * così una versione vecchia dell'app usa le voci che capisce di un catalogo più nuovo.
     * @throws CatalogoNonValidoException se il testo non è un elenco JSON o non contiene nessun modello valido.
     */
    fun leggi(testo: String): CatalogoLetto {
        val voci = try {
            formato.parseToJsonElement(testo).jsonArray
        } catch (_: IllegalArgumentException) {
            // Comprende sia il JSON malformato sia un JSON che non è un elenco
            throw CatalogoNonValidoException("Il file non è un elenco JSON di modelli.")
        }
        val modelli = voci
            .mapNotNull { voce -> runCatching { formato.decodeFromJsonElement<ModelInfo>(voce) }.getOrNull() }
            .filter(::valido)
            // Lo stesso id due volte: vale la prima voce
            .distinctBy { it.id }
        if (modelli.isEmpty()) throw CatalogoNonValidoException("Il catalogo non contiene modelli validi.")
        return CatalogoLetto(modelli, voci.size - modelli.size)
    }

    /**
     * I modelli inclusi nell'app aggiornati con quelli scaricati: una voce scaricata sostituisce quella con lo stesso
     * id e tiene il suo posto, le voci nuove vanno in fondo. Un modello che manca dal catalogo scaricato resta.
     */
    fun unisci(inclusi: List<ModelInfo>, scaricati: List<ModelInfo>): List<ModelInfo> {
        val perId = LinkedHashMap<String, ModelInfo>()
        for (modello in inclusi) perId[modello.id] = modello
        for (modello in scaricati) perId[modello.id] = modello
        return perId.values.toList()
    }

    /**
     * true se la voce si può mostrare e usare: dati essenziali presenti, impronta SHA-256 ben formata, motore coerente
     * con l'architettura e indirizzi solo HTTPS.
     */
    private fun valido(modello: ModelInfo): Boolean = with(modello) {
        id.isNotBlank() && name.isNotBlank() && sizeBytes > 0 &&
            IMPRONTA.matches(sha256) &&
            architettura.motore == tipoMotore &&
            downloadUrl.startsWith("https://") &&
            (paginaUrl == null || paginaUrl.startsWith("https://"))
    }
}
