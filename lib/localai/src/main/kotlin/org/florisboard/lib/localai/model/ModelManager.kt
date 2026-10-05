package org.florisboard.lib.localai.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.engine.ModelloInstallato
import org.florisboard.lib.localai.engine.TipoMotore
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Gestore dei modelli vocali offline: import da file con verifica SHA-256,
 * storage locale protetto e cambio del modello attivo.
 *
 * L'app non accede alla rete: i modelli si scaricano dal browser e poi si importano.
 * Risiedono in [Context.getNoBackupFilesDir]/modelli/, esclusi dal backup automatico
 * perché grandi e reimportabili.
 */
class ModelManager(private val context: Context) {
    // Il modello si sceglie nel processo della tastiera ma lo usa il servizio, in un altro processo:
    // con MODE_MULTI_PROCESS ogni nuovo ModelManager rilegge il file se un altro processo l'ha cambiato
    @Suppress("DEPRECATION")
    private val prefs = context.getSharedPreferences("localai_settings", Context.MODE_MULTI_PROCESS)

    private val modelsDir: File
        get() = File(context.noBackupFilesDir, "modelli").apply {
            if (!exists()) mkdirs()
        }

    fun getHardwareProfile(): InfoHardwareDispositivo {
        return RilevatoreDispositivo.rileva(context)
    }

    fun getRecommendedModel(): ModelInfo {
        val hw = getHardwareProfile()
        return ModelCatalog.findById(hw.idModelloConsigliato) ?: ModelCatalog.DEFAULT_MODEL
    }

    fun getModelFile(model: ModelInfo): File {
        return File(modelsDir, "${model.id}.bin")
    }

    fun isModelDownloaded(model: ModelInfo): Boolean {
        val file = getModelFile(model)
        return file.exists() && file.length() > 0
    }

    /**
     * Restituisce la lista di tutti i modelli attualmente installati e pronti all'uso offline.
     */
    fun getInstalledModels(): List<ModelloInstallato> {
        val list = mutableListOf<ModelloInstallato>()

        // Modelli noti dal catalogo
        for (catalogModel in ModelCatalog.ALL_MODELS) {
            val file = getModelFile(catalogModel)
            if (file.exists() && file.length() > 0) {
                list.add(catalogModel.toModelloInstallato(file))
            }
        }

        // Modelli custom inseriti dall'utente nella directory
        val customFiles = modelsDir.listFiles { f ->
            f.isFile && f.name.endsWith(".bin") && ModelCatalog.ALL_MODELS.none { it.id == f.nameWithoutExtension }
        } ?: emptyArray()

        for (file in customFiles) {
            list.add(creaModelloPersonalizzato(file))
        }

        return list
    }

    /**
     * Restituisce l'ID del modello attualmente selezionato dall'utente.
     */
    fun getActiveModelId(): String {
        val saved = prefs.getString("active_model_id", null)
        if (saved != null) {
            val file = File(modelsDir, "$saved.bin")
            if (file.exists() && file.length() > 0) {
                return saved
            }
        }

        // Se nessun modello è stato impostato o quello salvato non esiste più,
        // scegli il primo modello scaricato, oppure il default del catalogo
        val downloaded = getInstalledModels()
        return if (downloaded.isNotEmpty()) {
            downloaded.first().id
        } else {
            getRecommendedModel().id
        }
    }

    /**
     * Imposta il modello attivo da usare per la trascrizione.
     */
    fun setActiveModelId(modelId: String) {
        prefs.edit().putString("active_model_id", modelId).apply()
    }

    /**
     * Restituisce il [ModelloInstallato] attualmente attivo, o null se non ancora scaricato.
     */
    fun getActiveInstalledModel(): ModelloInstallato? {
        val activeId = getActiveModelId()
        return getInstalledModels().find { it.id == activeId }
    }

    /**
     * Importa un file modello scelto con il selettore di sistema. Il file viene copiato a blocchi
     * in memoria privata calcolandone lo SHA-256 nello stesso passaggio, poi riconosciuto
     * confrontando l'impronta con il catalogo.
     * @param sorgente URI restituito dal selettore di sistema.
     * @param consentiFuoriCatalogo true per accettare anche file assenti dal catalogo (opzioni sviluppatore).
     * @param avanzamento Frazione copiata tra 0 e 1, notificata solo se il provider espone la dimensione.
     * @return Il modello installato e reso attivo, oppure l'errore che ha fermato l'import.
     */
    suspend fun importaModello(
        sorgente: Uri,
        consentiFuoriCatalogo: Boolean,
        avanzamento: (Float) -> Unit = {},
    ): Result<ModelloInstallato> = withContext(Dispatchers.IO) {
        runCatching {
            val dimensioneSorgente = leggiDimensione(sorgente)
            verificaSpazioLibero(dimensioneSorgente)

            val fileTemporaneo = File(modelsDir, "import.tmp")
            try {
                val impronta = copiaCalcolandoImpronta(sorgente, fileTemporaneo, dimensioneSorgente, avanzamento)
                val modelloCatalogo = ModelCatalog.findBySha256(impronta)
                when {
                    modelloCatalogo != null -> installa(fileTemporaneo, getModelFile(modelloCatalogo)) { file ->
                        modelloCatalogo.toModelloInstallato(file)
                    }
                    consentiFuoriCatalogo -> installa(fileTemporaneo, File(modelsDir, "personalizzato-${impronta.take(12)}.bin")) { file ->
                        creaModelloPersonalizzato(file)
                    }
                    else -> throw ModelloNonRiconosciutoException(impronta)
                }
            } finally {
                // Dopo un'installazione riuscita il file è già stato spostato e qui non resta nulla da cancellare
                fileTemporaneo.delete()
            }
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
     * Copia la sorgente nel file di destinazione calcolandone lo SHA-256 nello stesso passaggio.
     * @return L'impronta SHA-256 in esadecimale minuscolo.
     */
    private fun copiaCalcolandoImpronta(
        sorgente: Uri,
        destinazione: File,
        dimensioneAttesa: Long?,
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
                    val letti = ingresso.read(buffer)
                    if (letti < 0) break
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

    /**
     * Sposta il file verificato nella posizione definitiva e rende attivo il modello.
     */
    private fun installa(
        fileVerificato: File,
        destinazione: File,
        creaModello: (File) -> ModelloInstallato,
    ): ModelloInstallato {
        if (destinazione.exists()) destinazione.delete()
        if (!fileVerificato.renameTo(destinazione)) {
            throw IllegalStateException("Impossibile salvare il modello nella memoria privata.")
        }
        val modello = creaModello(destinazione)
        setActiveModelId(modello.id)
        return modello
    }

    /**
     * Descrive un file fuori catalogo come modello whisper.cpp personalizzato.
     */
    private fun creaModelloPersonalizzato(file: File): ModelloInstallato {
        return ModelloInstallato(
            id = file.nameWithoutExtension,
            nome = file.nameWithoutExtension.replace("_", " ").replace("-", " ").capitalizeWords(),
            percorsoFile = file,
            dimensioneByte = file.length(),
            tipo = TipoMotore.WHISPER_CPP,
            ramConsigliataMb = 500,
            descrizione = "Modello personalizzato importato dall'utente.",
        )
    }

    /**
     * Elimina un modello scaricato per liberare spazio su disco.
     */
    fun deleteModel(modelId: String): Boolean {
        val file = File(modelsDir, "$modelId.bin")
        val success = if (file.exists()) file.delete() else true

        // Se abbiamo eliminato il modello attivo, resettiamo la preferenza
        if (getActiveModelId() == modelId) {
            val remaining = getInstalledModels()
            if (remaining.isNotEmpty()) {
                setActiveModelId(remaining.first().id)
            } else {
                prefs.edit().remove("active_model_id").apply()
            }
        }

        return success
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private companion object {
        /** Spazio da lasciare libero oltre al modello, per non saturare la memoria del telefono. */
        const val MARGINE_SPAZIO_BYTE = 50L * 1024 * 1024

        /** Dimensione dei blocchi letti durante la copia. */
        const val DIMENSIONE_BLOCCO = 1024 * 1024

        const val BYTE_PER_MB = 1024L * 1024
    }
}

/**
 * Il file importato non corrisponde a nessun modello del catalogo.
 * @property impronta SHA-256 calcolato sul file, utile per riconoscerlo in seguito.
 */
class ModelloNonRiconosciutoException(val impronta: String) :
    IllegalArgumentException("Il file non corrisponde a nessun modello del catalogo (SHA-256 $impronta).")

/** Alias italiano */
typealias GestoreModelli = ModelManager
