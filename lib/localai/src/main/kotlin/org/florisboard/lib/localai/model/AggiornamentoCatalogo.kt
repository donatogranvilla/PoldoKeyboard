package org.florisboard.lib.localai.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * L'esito di un aggiornamento del catalogo.
 * @property nuovi I modelli che prima non c'erano.
 * @property aggiornati I modelli già presenti con dati cambiati, come un link nuovo o una descrizione corretta.
 * @property vociScartate Le voci che questa versione dell'app non sa leggere: servirà una versione più recente.
 */
data class EsitoAggiornamentoCatalogo(
    val nuovi: List<ModelInfo>,
    val aggiornati: List<ModelInfo>,
    val vociScartate: Int,
)

/**
 * Aggiorna il catalogo dei modelli con quello pubblicato su GitHub, solo quando l'utente lo chiede. Il catalogo
 * scaricato si salva nella memoria dell'app e vale finché l'app non viene aggiornata: la nuova versione ne include
 * uno almeno altrettanto recente.
 */
class AggiornamentoCatalogo(context: Context) {
    private val file = fileCatalogo(context)

    /**
     * Scarica il catalogo, lo controlla e, se è valido, lo salva e lo fa usare a [ModelCatalog].
     * @throws ConnessioneNonRiuscitaException se la connessione non riesce.
     * @throws RispostaServerException se GitHub risponde con un errore.
     * @throws CatalogoNonValidoException se il file scaricato non è un catalogo valido.
     */
    suspend fun aggiorna(): EsitoAggiornamentoCatalogo = withContext(Dispatchers.IO) {
        val testo = ScaricatoreCatalogo.scarica()
        val letto = LetturaCatalogo.leggi(testo)
        val prima = ModelCatalog.ALL_MODELS.associateBy { it.id }
        val dopo = LetturaCatalogo.unisci(ModelCatalog.inclusi, letto.modelli)
        salva(testo)
        ModelCatalog.usaCatalogoScaricato(file)
        ModelCatalog.ricarica()
        EsitoAggiornamentoCatalogo(
            nuovi = dopo.filter { it.id !in prima },
            aggiornati = dopo.filter { modello -> prima[modello.id].let { it != null && it != modello } },
            vociScartate = letto.vociScartate,
        )
    }

    /** Scrive prima in un file temporaneo, così un salvataggio interrotto non lascia un catalogo a metà. */
    private fun salva(testo: String) {
        file.parentFile?.mkdirs()
        val temporaneo = File(file.parentFile, "${file.name}.tmp")
        temporaneo.writeText(testo)
        check(temporaneo.renameTo(file)) { "Impossibile salvare il catalogo scaricato." }
    }

    companion object {
        private fun fileCatalogo(context: Context) = File(context.filesDir, "catalogo/catalogo_modelli.json")

        /**
         * Fa usare a [ModelCatalog] il catalogo scaricato, se c'è. Uno scaricato prima dell'ultimo aggiornamento
         * dell'app si elimina: il catalogo incluso nella nuova versione è almeno altrettanto recente.
         */
        fun registra(context: Context) {
            val file = fileCatalogo(context)
            @Suppress("DEPRECATION")
            val aggiornataIl = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            if (file.exists() && file.lastModified() < aggiornataIl) file.delete()
            ModelCatalog.usaCatalogoScaricato(file.takeIf { it.exists() })
        }
    }
}
