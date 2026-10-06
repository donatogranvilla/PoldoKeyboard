package org.florisboard.lib.localai.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.florisboard.lib.localai.engine.ArchitetturaModello
import org.florisboard.lib.localai.engine.ModelloInstallato
import org.florisboard.lib.localai.sherpa.FileModelloSherpa
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Gestore dei modelli offline: import da file con riconoscimento del formato e verifica SHA-256,
 * installazione nella memoria privata, modello attivo, eliminazione.
 *
 * I modelli whisper.cpp sono un file `<id>.bin`, quelli sherpa-onnx una cartella `<id>/`. Per i modelli
 * fuori catalogo un file `<id>.json` accanto conserva nome, lingue e architettura scelti dall'utente.
 * Tutto sta in [Context.getNoBackupFilesDir]/modelli, escluso dal backup perché grande e reimportabile.
 * L'app non accede alla rete: i modelli si scaricano dal browser e poi si importano.
 */
class ModelManager(private val context: Context) {
    // Il modello si sceglie nel processo dell'app ma lo usa il servizio, in un altro processo:
    // con MODE_MULTI_PROCESS ogni nuovo ModelManager rilegge il file se un altro processo l'ha cambiato
    @Suppress("DEPRECATION")
    private val prefs = context.getSharedPreferences("localai_settings", Context.MODE_MULTI_PROCESS)
    private val formatoJson = Json { ignoreUnknownKeys = true }

    private val modelsDir: File
        get() = File(context.noBackupFilesDir, "modelli").apply {
            if (!exists()) mkdirs()
        }

    /** Il file dell'import in corso: uno alla volta. */
    private val fileImport: File
        get() = File(modelsDir, "import.tmp")

    fun getHardwareProfile(): InfoHardwareDispositivo {
        return RilevatoreDispositivo.rileva(context)
    }

    fun getRecommendedModel(): ModelInfo {
        val hw = getHardwareProfile()
        return ModelCatalog.findById(hw.idModelloConsigliato) ?: ModelCatalog.DEFAULT_MODEL
    }

    /**
     * Dove sta, o starà, il modello del catalogo: un file per whisper.cpp, una cartella per sherpa-onnx.
     */
    fun getModelFile(model: ModelInfo): File {
        return percorsoModello(model.id, model.architettura)
    }

    fun isModelDownloaded(model: ModelInfo): Boolean {
        return percorsoValido(getModelFile(model), model.architettura)
    }

    /**
     * I modelli installati e pronti all'uso: quelli del catalogo e quelli importati dall'utente.
     */
    fun getInstalledModels(): List<ModelloInstallato> {
        val catalogo = ModelCatalog.ALL_MODELS
            .filter { isModelDownloaded(it) }
            .map { it.toModelloInstallato(getModelFile(it)) }
        val personalizzati = modelsDir.listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .mapNotNull { leggiPersonalizzato(it) }
        return catalogo + personalizzati
    }

    /**
     * Il modello installato con questo id, o null se non c'è.
     */
    fun trovaInstallato(id: String): ModelloInstallato? {
        return getInstalledModels().find { it.id == id }
    }

    /**
     * Lo spazio che il modello occupa nella memoria del telefono: il suo file, o tutti i file della sua cartella.
     */
    fun spazioOccupato(modello: ModelloInstallato): Long {
        return modello.percorsoFile.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Restituisce l'ID del modello attualmente selezionato dall'utente. Se quello salvato non c'è più,
     * il primo installato, oppure quello consigliato per il dispositivo.
     */
    fun getActiveModelId(): String {
        val installati = getInstalledModels()
        val salvato = prefs.getString(CHIAVE_ATTIVO, null)
        if (salvato != null && installati.any { it.id == salvato }) return salvato
        return installati.firstOrNull()?.id ?: getRecommendedModel().id
    }

    /**
     * Imposta il modello attivo da usare per la trascrizione.
     */
    fun setActiveModelId(modelId: String) {
        prefs.edit().putString(CHIAVE_ATTIVO, modelId).apply()
    }

    /**
     * Restituisce il [ModelloInstallato] attualmente attivo, o null se non ancora importato.
     */
    fun getActiveInstalledModel(): ModelloInstallato? {
        return trovaInstallato(getActiveModelId())
    }

    /**
     * Primo passo dell'import: copia il file scelto in memoria privata calcolandone l'impronta, e ne riconosce
     * il formato. Il file resta in attesa di [completaImport] o [annullaImport]. Se la coroutine viene annullata
     * la copia si ferma e il file si cancella.
     * @param avanzamento Frazione copiata tra 0 e 1, notificata solo se il provider espone la dimensione.
     */
    suspend fun analizzaImport(sorgente: Uri, avanzamento: (Float) -> Unit = {}): Result<AnalisiImport> =
        withContext(Dispatchers.IO) {
            runCatching {
                val nomeFile = leggiNome(sorgente)
                val dimensione = leggiDimensione(sorgente)
                // Un archivio si estrae accanto alla sua copia: serve il doppio dello spazio
                verificaSpazioLibero(dimensione?.times(2))
                val inizio = ByteArray(RiconoscitoreFormato.BYTE_INIZIALI)
                val impronta = copiaCalcolandoImpronta(sorgente, fileImport, dimensione, inizio, avanzamento)
                AnalisiImport(
                    nomeFile = nomeFile,
                    formato = RiconoscitoreFormato.riconosci(inizio, nomeFile),
                    impronta = impronta,
                    dimensione = fileImport.length(),
                    modelloCatalogo = ModelCatalog.findBySha256(impronta),
                    intestazioneGgml = RiconoscitoreFormato.intestazioneGgml(inizio),
                )
            }.onFailure { fileImport.delete() }
        }

    /**
     * Secondo passo dell'import: installa il file analizzato e lo rende attivo. I modelli del catalogo
     * si installano da soli; per gli altri servono i dati scelti dall'utente. Se la coroutine viene annullata
     * l'estrazione si ferma e non resta nulla di installato a metà.
     * @param personalizzato Nome, lingue e architettura di un modello fuori catalogo; ignorato per il catalogo.
     * @param avanzamento Frazione dell'archivio estratta, per i modelli sherpa-onnx.
     */
    suspend fun completaImport(
        analisi: AnalisiImport,
        personalizzato: ModelloPersonalizzato?,
        avanzamento: (Float) -> Unit = {},
    ): Result<ModelloInstallato> = withContext(Dispatchers.IO) {
        // L'estrazione non sospende mai: l'annullamento si controlla a ogni avanzamento
        val contesto = currentCoroutineContext()
        val avanzamentoAnnullabile: (Float) -> Unit = { frazione ->
            contesto.ensureActive()
            avanzamento(frazione)
        }
        runCatching {
            check(fileImport.exists()) { "Nessun import in corso." }
            val catalogo = analisi.modelloCatalogo
            val modello = if (catalogo != null) {
                verificaCompatibilita(analisi.formato, catalogo.architettura)
                installa(catalogo.id, catalogo.architettura, analisi.formato, avanzamentoAnnullabile)
                catalogo.toModelloInstallato(getModelFile(catalogo))
            } else {
                val dati = requireNotNull(personalizzato) {
                    "Per un modello fuori catalogo servono nome, lingue e architettura."
                }.copy(id = PREFISSO_PERSONALIZZATO + analisi.impronta.take(12))
                verificaCompatibilita(analisi.formato, dati.architettura)
                installa(dati.id, dati.architettura, analisi.formato, avanzamentoAnnullabile)
                File(modelsDir, "${dati.id}.json").writeText(formatoJson.encodeToString(dati))
                dati.toModelloInstallato(percorsoModello(dati.id, dati.architettura))
            }
            setActiveModelId(modello.id)
            modello
        }.also { fileImport.delete() }
    }

    /**
     * Rinuncia all'import analizzato e libera lo spazio della copia.
     */
    fun annullaImport() {
        fileImport.delete()
    }

    /**
     * Elimina un modello installato per liberare spazio su disco.
     */
    fun deleteModel(modelId: String): Boolean {
        val installato = trovaInstallato(modelId)
        val successo = installato?.percorsoFile?.deleteRecursively() ?: true
        File(modelsDir, "$modelId.json").delete()

        // Se era il modello attivo, si passa al primo rimasto
        if (prefs.getString(CHIAVE_ATTIVO, null) == modelId) {
            val rimasto = getInstalledModels().firstOrNull()
            if (rimasto != null) setActiveModelId(rimasto.id) else prefs.edit().remove(CHIAVE_ATTIVO).apply()
        }
        return successo
    }

    /**
     * Sposta il file analizzato nella posizione definitiva: rinominato se è un file GGML, estratto in una
     * cartella e controllato se è l'archivio di un modello sherpa-onnx.
     */
    private fun installa(
        id: String,
        architettura: ArchitetturaModello,
        formato: FormatoFileModello,
        avanzamento: (Float) -> Unit,
    ) {
        val destinazione = percorsoModello(id, architettura)
        if (architettura.singoloFile) {
            destinazione.delete()
            check(fileImport.renameTo(destinazione)) { "Impossibile salvare il modello nella memoria privata." }
            return
        }
        val temporanea = File(modelsDir, "$id.estrazione")
        temporanea.deleteRecursively()
        try {
            EstrattoreArchivi.estrai(fileImport, formato, temporanea, avanzamento)
            // Prima di renderlo disponibile si controlla che ci siano i file che l'architettura richiede
            FileModelloSherpa.configurazione(temporanea, architettura, numeroThread = 1, lingua = null)
            // Lo stesso modello già installato si sostituisce solo ora che il nuovo è completo
            destinazione.deleteRecursively()
            check(temporanea.renameTo(destinazione)) { "Impossibile salvare il modello nella memoria privata." }
        } finally {
            temporanea.deleteRecursively()
        }
    }

    /**
     * Rifiuta i formati che l'app non legge o che non corrispondono all'architettura del modello.
     */
    private fun verificaCompatibilita(formato: FormatoFileModello, architettura: ArchitetturaModello) {
        val compatibile = if (architettura.singoloFile) {
            formato == FormatoFileModello.GGML_WHISPER
        } else {
            formato.archivio
        }
        if (!compatibile) throw FormatoNonSupportatoException(formato, architettura)
    }

    /**
     * Dove sta il modello: `<id>.bin` per whisper.cpp, la cartella `<id>` per sherpa-onnx.
     */
    private fun percorsoModello(id: String, architettura: ArchitetturaModello): File {
        return if (architettura.singoloFile) File(modelsDir, "$id.bin") else File(modelsDir, id)
    }

    /** true se il file o la cartella del modello esiste e non è vuota. */
    private fun percorsoValido(percorso: File, architettura: ArchitetturaModello): Boolean {
        return if (architettura.singoloFile) {
            percorso.isFile && percorso.length() > 0
        } else {
            percorso.isDirectory && !percorso.listFiles().isNullOrEmpty()
        }
    }

    /**
     * Rilegge un modello importato dall'utente dal suo file di dati, se è ancora installato.
     */
    private fun leggiPersonalizzato(file: File): ModelloInstallato? {
        val dati = runCatching { formatoJson.decodeFromString<ModelloPersonalizzato>(file.readText()) }.getOrNull()
            ?: return null
        val percorso = percorsoModello(dati.id, dati.architettura)
        return if (percorsoValido(percorso, dati.architettura)) dati.toModelloInstallato(percorso) else null
    }

    /**
     * Legge il nome del file scelto, se il provider lo espone.
     */
    private fun leggiNome(sorgente: Uri): String? {
        return context.contentResolver.query(sorgente, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursore ->
            val colonna = cursore.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursore.moveToFirst() && colonna >= 0) cursore.getString(colonna) else null
        }
    }

    /**
     * Legge la dimensione del file scelto, se il provider la espone.
     */
    private fun leggiDimensione(sorgente: Uri): Long? {
        return context.contentResolver.query(sorgente, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursore ->
            val colonna = cursore.getColumnIndex(OpenableColumns.SIZE)
            if (cursore.moveToFirst() && colonna >= 0 && !cursore.isNull(colonna)) cursore.getLong(colonna) else null
        }
    }

    /**
     * Interrompe l'import se in memoria privata non c'è posto per il file più un margine di sicurezza.
     */
    private fun verificaSpazioLibero(dimensioneRichiesta: Long?) {
        if (dimensioneRichiesta == null) return
        val spazioNecessario = dimensioneRichiesta + MARGINE_SPAZIO_BYTE
        val spazioLibero = modelsDir.usableSpace
        if (spazioLibero < spazioNecessario) {
            throw IllegalStateException(
                "Spazio insufficiente: servono ${spazioNecessario / BYTE_PER_MB} MB, liberi ${spazioLibero / BYTE_PER_MB} MB."
            )
        }
    }

    /**
     * Copia la sorgente nel file di destinazione calcolandone lo SHA-256 nello stesso passaggio, e conserva
     * i primi byte per riconoscerne il formato. Si ferma tra un blocco e l'altro se la coroutine viene annullata.
     * @param inizio Riceve i primi byte del file.
     * @return L'impronta SHA-256 in esadecimale minuscolo.
     */
    private suspend fun copiaCalcolandoImpronta(
        sorgente: Uri,
        destinazione: File,
        dimensioneAttesa: Long?,
        inizio: ByteArray,
        avanzamento: (Float) -> Unit,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val flussoIngresso = context.contentResolver.openInputStream(sorgente)
            ?: throw IllegalStateException("Impossibile leggere il file selezionato.")
        flussoIngresso.use { ingresso ->
            FileOutputStream(destinazione).use { uscita ->
                val buffer = ByteArray(DIMENSIONE_BLOCCO)
                var byteCopiati = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val letti = ingresso.read(buffer)
                    if (letti < 0) break
                    // I primi byte servono a riconoscere il formato
                    if (byteCopiati < inizio.size) {
                        val daCopiare = minOf(letti.toLong(), inizio.size - byteCopiati).toInt()
                        buffer.copyInto(inizio, destinationOffset = byteCopiati.toInt(), startIndex = 0, endIndex = daCopiare)
                    }
                    uscita.write(buffer, 0, letti)
                    digest.update(buffer, 0, letti)
                    byteCopiati += letti
                    if (dimensioneAttesa != null && dimensioneAttesa > 0) {
                        avanzamento((byteCopiati.toFloat() / dimensioneAttesa).coerceIn(0f, 1f))
                    }
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val CHIAVE_ATTIVO = "active_model_id"

        /** I modelli importati dall'utente hanno questo prefisso seguito dall'inizio dell'impronta. */
        const val PREFISSO_PERSONALIZZATO = "personalizzato-"

        /** Spazio da lasciare libero oltre al modello, per non saturare la memoria del telefono. */
        const val MARGINE_SPAZIO_BYTE = 50L * 1024 * 1024

        /** Dimensione dei blocchi letti durante la copia. */
        const val DIMENSIONE_BLOCCO = 1024 * 1024

        const val BYTE_PER_MB = 1024L * 1024
    }
}

/**
 * Il risultato del primo passo dell'import.
 * @property modelloCatalogo Il modello del catalogo con la stessa impronta, o null se il file è fuori catalogo.
 * @property intestazioneGgml Per i file GGML: se il modello è multilingue o solo inglese.
 */
data class AnalisiImport(
    val nomeFile: String?,
    val formato: FormatoFileModello,
    val impronta: String,
    val dimensione: Long,
    val modelloCatalogo: ModelInfo?,
    val intestazioneGgml: IntestazioneGgml?,
)

/**
 * I dati di un modello importato fuori catalogo, scelti dall'utente e conservati accanto al modello.
 * @property lingue Le lingue del modello: vuota se le copre tutte, una sola se è specializzato.
 */
@Serializable
data class ModelloPersonalizzato(
    val id: String = "",
    val nome: String,
    val architettura: ArchitetturaModello,
    val lingue: List<String> = emptyList(),
) {
    /** Il modello pronto per il motore, con il percorso del suo file o della sua cartella. */
    fun toModelloInstallato(percorso: File): ModelloInstallato {
        return ModelloInstallato(
            id = id,
            nome = nome,
            percorsoFile = percorso,
            tipo = architettura.motore,
            descrizione = "Modello importato dall'utente.",
            architettura = architettura,
            lingue = lingue,
        )
    }
}

/**
 * Il file scelto non è in un formato che l'app sa usare per quel tipo di modello.
 * @property formato Il formato riconosciuto, per spiegare all'utente cosa scaricare.
 */
class FormatoNonSupportatoException(val formato: FormatoFileModello, val architettura: ArchitetturaModello) :
    IllegalArgumentException("Il file ($formato) non è adatto a un modello $architettura.")

/** Alias italiano */
typealias GestoreModelli = ModelManager
