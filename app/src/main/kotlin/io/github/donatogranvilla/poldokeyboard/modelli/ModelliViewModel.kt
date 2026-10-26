package io.github.donatogranvilla.poldokeyboard.modelli

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.donatogranvilla.poldokeyboard.localai.ImpostazioniTrascrizione
import io.github.donatogranvilla.poldokeyboard.localai.LocalAiManager
import io.github.donatogranvilla.poldokeyboard.localai.engine.ArchitetturaModello
import io.github.donatogranvilla.poldokeyboard.localai.engine.ModelloInstallato
import io.github.donatogranvilla.poldokeyboard.localai.model.AggiornamentoCatalogo
import io.github.donatogranvilla.poldokeyboard.localai.model.AnalisiImport
import io.github.donatogranvilla.poldokeyboard.localai.model.EsitoAggiornamentoCatalogo
import io.github.donatogranvilla.poldokeyboard.localai.model.FormatoFileModello
import io.github.donatogranvilla.poldokeyboard.localai.model.FormatoNonSupportatoException
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelCatalog
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelInfo
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelManager
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelloPersonalizzato
import io.github.donatogranvilla.poldokeyboard.localai.sherpa.ModelloIncompletoException

/**
 * Un modello installato come lo mostra la schermata dei modelli.
 * @property byteOccupati Lo spazio che occupa nella memoria del telefono.
 * @property catalogo La sua voce del catalogo, o null se l'utente l'ha importato fuori catalogo.
 */
data class ModelloSulTelefono(
    val modello: ModelloInstallato,
    val byteOccupati: Long,
    val catalogo: ModelInfo?,
)

/**
 * Le fasi dell'import di un modello: la schermata mostra una finestra per ciascuna.
 */
sealed interface StatoImport {
    /** Nessun import in corso. */
    data object Nessuno : StatoImport

    /** Copia del file e calcolo dell'impronta; l'avanzamento è null se il file non dichiara la sua dimensione. */
    data class Analisi(val avanzamento: Float?) : StatoImport

    /** Il file è fuori catalogo: servono nome, lingue e tipo scelti dall'utente. */
    data class DatiRichiesti(val analisi: AnalisiImport) : StatoImport

    /** Estrazione dell'archivio, o salvataggio del file GGML, nella memoria privata. */
    data class Installazione(val avanzamento: Float?, val archivio: Boolean) : StatoImport

    /** Modello installato e attivato; verificato se è un modello del catalogo. */
    data class Completato(val modello: ModelloInstallato, val verificato: Boolean) : StatoImport

    /** L'import si è fermato. */
    data class Errore(val problema: ProblemaImport) : StatoImport
}

/**
 * Perché un import non è riuscito, con i dati per spiegarlo all'utente.
 */
sealed interface ProblemaImport {
    /** Il file non è in un formato che l'app sa importare. */
    data class FormatoSbagliato(val formato: FormatoFileModello) : ProblemaImport

    /** Il formato non è quello che il tipo di modello richiede. */
    data class FormatoIncompatibile(val architettura: ArchitetturaModello) : ProblemaImport

    /** Nell'archivio manca un file che il tipo di modello richiede. */
    data class FileMancante(val nomeFile: String) : ProblemaImport

    /** Un altro errore, come lo spazio insufficiente, con il messaggio della libreria. */
    data class Altro(val messaggio: String?) : ProblemaImport
}

/**
 * L'aggiornamento dell'elenco dei modelli da GitHub.
 */
sealed interface StatoCatalogo {
    /** Nessun aggiornamento chiesto da quando la schermata è aperta. */
    data object Nessuno : StatoCatalogo

    /** Download in corso. */
    data object InCorso : StatoCatalogo

    /** Elenco aggiornato. */
    data class Fatto(val esito: EsitoAggiornamentoCatalogo) : StatoCatalogo

    /** L'aggiornamento non è riuscito; l'elenco resta quello di prima. */
    data class Errore(val causa: Throwable) : StatoCatalogo
}

/**
 * Stato e azioni della schermata dei modelli. Sta in un ViewModel perché l'import di un modello grande
 * dura anche minuti e non deve ripartire da capo se lo schermo ruota.
 */
class ModelliViewModel(application: Application) : AndroidViewModel(application) {
    private val modelManager = ModelManager(application)
    private val impostazioni = ImpostazioniTrascrizione(application)
    private val aggiornamentoCatalogo = AggiornamentoCatalogo(application)
    private var lavoroImport: Job? = null

    /** RAM e core del telefono, per consigliare un modello. */
    val profilo = modelManager.getHardwareProfile()

    /**
     * Il motore per il test delle prestazioni, creato solo se l'utente lo apre. Resta in vita con la schermata:
     * chiuderlo mentre il test trascrive farebbe chiudere l'app.
     */
    val motoreTest: LocalAiManager by lazy { LocalAiManager(application) }

    /** Tutti i modelli del catalogo. */
    var catalogo by mutableStateOf<List<ModelInfo>>(emptyList())
        private set

    /** I modelli installati, con lo spazio che occupano. */
    var installati by mutableStateOf<List<ModelloSulTelefono>>(emptyList())
        private set

    /** Il modello che trascrive i vocali. */
    var idAttivo by mutableStateOf<String?>(null)
        private set

    /** La lingua imposta ai modelli multilingue, o null per il riconoscimento automatico. */
    var linguaVocali by mutableStateOf(impostazioni.linguaPredefinita)
        private set

    /** La lingua di cui mostrare i modelli, o null per mostrarli tutti. */
    var filtroLingua by mutableStateOf<String?>(null)
        private set

    /** La fase dell'import in corso. */
    var statoImport by mutableStateOf<StatoImport>(StatoImport.Nessuno)
        private set

    /** L'ultimo aggiornamento dell'elenco dei modelli. */
    var statoCatalogo by mutableStateOf<StatoCatalogo>(StatoCatalogo.Nessuno)
        private set

    /** I modelli installati che coprono la lingua del filtro. */
    val installatiFiltrati: List<ModelloSulTelefono>
        get() = installati.filter { copreFiltro(it.modello.lingue) }

    /** I modelli del catalogo non ancora installati per la lingua del filtro: per una lingua, prima gli specializzati. */
    val daScaricare: List<ModelInfo>
        get() {
            val presenti = installati.map { it.modello.id }.toSet()
            val disponibili = catalogo.filter { it.id !in presenti && copreFiltro(it.lingue) }
            return if (filtroLingua == null) disponibili else disponibili.sortedByDescending { it.specializzato }
        }

    init {
        // Un import interrotto dalla chiusura dell'app lascia la sua copia: si libera lo spazio
        modelManager.annullaImport()
        aggiorna()
    }

    /**
     * Rilegge catalogo, modelli installati e modello attivo, fuori dal thread principale.
     */
    fun aggiorna() {
        viewModelScope.launch {
            val (tuttoCatalogo, presenti, attivo) = withContext(Dispatchers.IO) {
                val lista = modelManager.getInstalledModels().map { modello ->
                    ModelloSulTelefono(
                        modello = modello,
                        byteOccupati = modelManager.spazioOccupato(modello),
                        catalogo = ModelCatalog.findById(modello.id),
                    )
                }
                Triple(ModelCatalog.ALL_MODELS, lista, modelManager.getActiveModelId())
            }
            catalogo = tuttoCatalogo
            installati = presenti
            idAttivo = attivo
        }
    }

    /**
     * Scarica da GitHub l'elenco aggiornato dei modelli. È l'unica azione dell'app che usa la rete, e parte solo
     * quando l'utente la chiede.
     */
    fun aggiornaCatalogo() {
        if (statoCatalogo == StatoCatalogo.InCorso) return
        statoCatalogo = StatoCatalogo.InCorso
        viewModelScope.launch {
            statoCatalogo = try {
                StatoCatalogo.Fatto(aggiornamentoCatalogo.aggiorna())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StatoCatalogo.Errore(e)
            }
            aggiorna()
        }
    }

    /** Mostra solo i modelli che coprono la lingua indicata; null per mostrarli tutti. */
    fun filtra(lingua: String?) {
        filtroLingua = lingua
    }

    /** Usa il modello indicato per le prossime trascrizioni. */
    fun attiva(id: String) {
        modelManager.setActiveModelId(id)
        idAttivo = id
    }

    /** Elimina un modello installato; se era attivo, passa al primo rimasto. */
    fun elimina(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { modelManager.deleteModel(id) }
            aggiorna()
        }
    }

    /** Imposta la lingua dei vocali per i modelli multilingue; null per il riconoscimento automatico. */
    fun impostaLinguaVocali(lingua: String?) {
        impostazioni.linguaPredefinita = lingua
        linguaVocali = lingua
    }

    /**
     * Primo passo dell'import: copia e riconosce il file scelto. Un modello del catalogo si installa subito;
     * per uno fuori catalogo si chiedono i dati all'utente.
     */
    fun importa(sorgente: Uri) {
        if (lavoroImport?.isActive == true) return
        statoImport = StatoImport.Analisi(null)
        lavoroImport = viewModelScope.launch {
            val analisi = modelManager.analizzaImport(sorgente) { frazione ->
                statoImport = StatoImport.Analisi(percentuale(frazione))
            }.getOrElse { errore ->
                if (errore is CancellationException) throw errore
                statoImport = StatoImport.Errore(problemaDi(errore))
                return@launch
            }
            when {
                !analisi.formato.importabile -> {
                    modelManager.annullaImport()
                    statoImport = StatoImport.Errore(ProblemaImport.FormatoSbagliato(analisi.formato))
                }
                analisi.modelloCatalogo != null -> installa(analisi, personalizzato = null)
                else -> statoImport = StatoImport.DatiRichiesti(analisi)
            }
        }
    }

    /**
     * Secondo passo per un modello fuori catalogo: lo installa con i dati scelti dall'utente.
     */
    fun confermaDati(personalizzato: ModelloPersonalizzato) {
        val analisi = (statoImport as? StatoImport.DatiRichiesti)?.analisi ?: return
        lavoroImport = viewModelScope.launch { installa(analisi, personalizzato) }
    }

    /**
     * Interrompe l'import in qualunque fase e libera lo spazio della copia.
     */
    fun annullaImport() {
        lavoroImport?.cancel()
        lavoroImport = null
        modelManager.annullaImport()
        statoImport = StatoImport.Nessuno
    }

    /** Chiude il messaggio con l'esito dell'import. */
    fun chiudiEsito() {
        statoImport = StatoImport.Nessuno
    }

    override fun onCleared() {
        // L'utente ha lasciato la schermata: un import a metà non serve più
        lavoroImport?.cancel()
        modelManager.annullaImport()
    }

    /**
     * Installa il file analizzato e aggiorna l'elenco, con l'esito per l'utente.
     */
    private suspend fun installa(analisi: AnalisiImport, personalizzato: ModelloPersonalizzato?) {
        statoImport = StatoImport.Installazione(null, archivio = analisi.formato.archivio)
        modelManager.completaImport(analisi, personalizzato) { frazione ->
            statoImport = StatoImport.Installazione(percentuale(frazione), archivio = analisi.formato.archivio)
        }.onSuccess { modello ->
            statoImport = StatoImport.Completato(modello, verificato = analisi.modelloCatalogo != null)
        }.onFailure { errore ->
            if (errore is CancellationException) throw errore
            statoImport = StatoImport.Errore(problemaDi(errore))
        }
        aggiorna()
    }

    /** true se un modello con queste lingue va mostrato con il filtro attuale. */
    private fun copreFiltro(lingue: List<String>): Boolean {
        val lingua = filtroLingua ?: return true
        return lingue.isEmpty() || lingua in lingue
    }

    /** Il problema da mostrare per un errore della libreria. */
    private fun problemaDi(errore: Throwable): ProblemaImport = when (errore) {
        is FormatoNonSupportatoException -> ProblemaImport.FormatoIncompatibile(errore.architettura)
        is ModelloIncompletoException -> ProblemaImport.FileMancante(errore.fileMancante)
        else -> ProblemaImport.Altro(errore.message)
    }

    /**
     * L'avanzamento arrotondato al punto percentuale: lo stato cambia, e la finestra si ridisegna,
     * solo quando cambia la percentuale mostrata.
     */
    private fun percentuale(frazione: Float): Float = (frazione * 100).toInt() / 100f
}
