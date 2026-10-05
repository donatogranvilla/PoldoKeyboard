package dev.patrickgold.florisboard.ime.voice.archivio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Callable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.engine.RisultatoTrascrizione

/**
 * Archivio delle trascrizioni: le righe del database e le copie private dei vocali.
 * È l'unica fonte sia per la coda del servizio sia per la scheda Trascrizioni della tastiera,
 * e si usa allo stesso modo in ogni processo dell'app.
 */
class ArchivioTrascrizioni private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database = DatabaseTrascrizioni.nuovo(appContext)
    private val dao = database.trascrizioniDao()

    /** Cartella delle copie dei vocali, esclusa dal backup come quella dei modelli. */
    private val cartellaAudio: File
        get() = File(appContext.noBackupFilesDir, CARTELLA_AUDIO).apply { mkdirs() }

    /**
     * Le trascrizioni più recenti, per la scheda della tastiera.
     */
    fun osservaRecenti(limite: Int = NUMERO_RECENTI): Flow<List<Trascrizione>> = dao.osservaRecenti(limite)

    /**
     * La copia privata del vocale di questa trascrizione.
     */
    fun fileAudio(trascrizione: Trascrizione): File = File(cartellaAudio, trascrizione.nomeFileAudio)

    /**
     * Copia il vocale condiviso in memoria privata e lo mette in coda. Va chiamata subito: il permesso
     * di leggere l'URI dura quanto l'Activity che ha ricevuto la condivisione.
     * @throws IOException se il vocale non si può leggere o copiare.
     */
    suspend fun accoda(sorgente: Uri): Trascrizione = withContext(Dispatchers.IO) {
        val nomeOriginale = leggiNomeOriginale(sorgente)
        val destinazione = File(cartellaAudio, UUID.randomUUID().toString() + estensioneDi(nomeOriginale))
        try {
            val ingresso = appContext.contentResolver.openInputStream(sorgente)
                ?: throw IOException("Impossibile leggere il vocale condiviso.")
            ingresso.use { input ->
                destinazione.outputStream().use { output -> input.copyTo(output) }
            }
            val nuova = Trascrizione(
                creataIl = System.currentTimeMillis(),
                nomeFileAudio = destinazione.name,
                nomeOriginale = nomeOriginale,
            )
            nuova.copy(id = dao.inserisci(nuova))
        } catch (e: Exception) {
            // Senza la riga nel database la copia resterebbe orfana
            destinazione.delete()
            throw e
        }
    }

    /**
     * Prende la prima trascrizione in coda e la segna in corso nella stessa transazione,
     * così lo stesso vocale non viene preso due volte.
     * @return null se la coda è vuota.
     */
    suspend fun prendiProssima(): Trascrizione? = withContext(Dispatchers.IO) {
        database.runInTransaction(Callable<Trascrizione?> {
            val prossima = dao.conStato(StatoTrascrizione.IN_CODA).firstOrNull() ?: return@Callable null
            val presa = prossima.copy(
                stato = StatoTrascrizione.IN_CORSO,
                avanzamento = 0f,
                tentativi = prossima.tentativi + 1,
            )
            dao.aggiorna(presa)
            presa
        })
    }

    /**
     * Aggiorna la frazione elaborata, mostrata nella scheda mentre il servizio lavora.
     */
    suspend fun aggiornaAvanzamento(id: Long, avanzamento: Float) {
        withContext(Dispatchers.IO) {
            dao.aggiornaAvanzamento(id, avanzamento.coerceIn(0f, 1f))
        }
    }

    /**
     * Salva il testo trascritto. La trascrizione torna da vedere, così la tastiera riapre la scheda.
     * @param idModello Il modello usato, per sapere poi con quale qualità è stata fatta.
     */
    suspend fun completa(id: Long, risultato: RisultatoTrascrizione, idModello: String?) {
        withContext(Dispatchers.IO) {
            modifica(id) {
                it.copy(
                    stato = StatoTrascrizione.COMPLETATA,
                    avanzamento = 1f,
                    testo = risultato.testo,
                    errore = null,
                    idModello = idModello,
                    durataAudioMs = risultato.durataAudioMs,
                    tempoInferenzaMs = risultato.tempoInferenzaMs,
                    conclusaIl = System.currentTimeMillis(),
                    vista = false,
                )
            }
        }
    }

    /**
     * Registra l'errore che ha fermato la trascrizione, da mostrare nella scheda.
     */
    suspend fun segnaErrore(id: Long, messaggio: String) {
        withContext(Dispatchers.IO) {
            modifica(id) {
                it.copy(
                    stato = StatoTrascrizione.ERRORE,
                    errore = messaggio,
                    conclusaIl = System.currentTimeMillis(),
                    vista = false,
                )
            }
        }
    }

    /**
     * Rimette in coda una trascrizione, per riprovare dopo un errore o con un altro modello.
     * Il testo precedente resta visibile finché non arriva quello nuovo.
     */
    suspend fun rimettiInCoda(id: Long) {
        withContext(Dispatchers.IO) {
            modifica(id) {
                it.copy(
                    stato = StatoTrascrizione.IN_CODA,
                    avanzamento = 0f,
                    tentativi = 0,
                    errore = null,
                    conclusaIl = null,
                )
            }
        }
    }

    /**
     * Applica [dopoInterruzione] alle trascrizioni rimaste in corso. Va chiamata all'avvio del servizio,
     * prima di prendere la prossima dalla coda.
     * @return Quante trascrizioni interrotte sono state trovate.
     */
    suspend fun recuperaInterrotte(): Int = withContext(Dispatchers.IO) {
        database.runInTransaction(Callable<Int> {
            val interrotte = dao.conStato(StatoTrascrizione.IN_CORSO)
            interrotte.forEach { dao.aggiorna(it.dopoInterruzione()) }
            interrotte.size
        })
    }

    /**
     * true se c'è un aggiornamento che l'utente non ha ancora visto: la tastiera apre da sola la scheda.
     */
    suspend fun ciSonoNovita(): Boolean = withContext(Dispatchers.IO) {
        dao.contaNonViste() > 0
    }

    /**
     * Segna come visti tutti gli aggiornamenti, quando la scheda li mostra.
     */
    suspend fun segnaTutteViste() {
        withContext(Dispatchers.IO) {
            dao.segnaTutteViste()
        }
    }

    /**
     * Elimina una trascrizione e la copia del suo vocale. Prima la riga, poi il file:
     * un'interruzione a metà lascia al massimo un file orfano, mai una riga senza audio.
     */
    suspend fun elimina(trascrizione: Trascrizione) {
        withContext(Dispatchers.IO) {
            dao.elimina(trascrizione.id)
            fileAudio(trascrizione).delete()
        }
    }

    /**
     * Pulizia manuale dello storico: elimina tutte le trascrizioni e le copie dei loro vocali.
     */
    suspend fun eliminaTutte() {
        withContext(Dispatchers.IO) {
            val nomiFile = database.runInTransaction(Callable<List<String>> {
                dao.nomiFileAudio().also { dao.eliminaTutte() }
            })
            nomiFile.forEach { File(cartellaAudio, it).delete() }
        }
    }

    /**
     * Legge, trasforma e riscrive una riga nella stessa transazione.
     * Se nel frattempo la trascrizione è stata eliminata non fa nulla.
     */
    private fun modifica(id: Long, trasforma: (Trascrizione) -> Trascrizione) {
        database.runInTransaction(Runnable {
            val attuale = dao.trova(id) ?: return@Runnable
            dao.aggiorna(trasforma(attuale))
        })
    }

    /**
     * Il nome del file condiviso, se l'app di provenienza lo espone.
     */
    private fun leggiNomeOriginale(sorgente: Uri): String? = runCatching {
        appContext.contentResolver.query(sorgente, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursore ->
            val colonna = cursore.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursore.moveToFirst() && colonna >= 0) cursore.getString(colonna) else null
        }
    }.getOrNull()

    companion object {
        /** Quante trascrizioni mostra la scheda della tastiera. */
        const val NUMERO_RECENTI = 5

        private const val CARTELLA_AUDIO = "vocali"

        @Volatile
        private var istanzaCorrente: ArchivioTrascrizioni? = null

        /**
         * L'archivio di questo processo: Room vuole una sola istanza del database per processo.
         */
        fun istanza(context: Context): ArchivioTrascrizioni {
            return istanzaCorrente ?: synchronized(this) {
                istanzaCorrente ?: ArchivioTrascrizioni(context).also { istanzaCorrente = it }
            }
        }

        /**
         * L'estensione del file originale, tenuta solo se breve e alfanumerica.
         */
        private fun estensioneDi(nomeFile: String?): String {
            val estensione = nomeFile?.substringAfterLast('.', "").orEmpty()
            return if (estensione.length in 1..5 && estensione.all { it.isLetterOrDigit() }) ".$estensione" else ""
        }
    }
}
