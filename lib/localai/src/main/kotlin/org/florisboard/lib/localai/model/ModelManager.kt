package org.florisboard.lib.localai.model

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.engine.ModelloInstallato
import org.florisboard.lib.localai.engine.TipoMotore
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Gestore dei modelli vocali offline: download, storage locale protetto,
 * verifica integrità SHA-256, cambio modello attivo e importazione modelli personalizzati.
 *
 * Tutti i modelli risiedono localmente in [Context.getFilesDir]/localai_models/.
 * La rete internet viene utilizzata ESCLUSIVAMENTE quando l'utente preme esplicitamente "Scarica".
 */
class ModelManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("localai_settings", Context.MODE_PRIVATE)

    private val modelsDir: File
        get() = File(context.filesDir, "localai_models").apply {
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
            list.add(
                ModelloInstallato(
                    id = file.nameWithoutExtension,
                    nome = file.nameWithoutExtension.replace("_", " ").replace("-", " ").capitalizeWords(),
                    percorsoFile = file,
                    dimensioneByte = file.length(),
                    tipo = TipoMotore.WHISPER_CPP,
                    ramConsigliataMb = 500,
                    descrizione = "Modello personalizzato importato dall'utente.",
                )
            )
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
     * Scarica un modello dal catalogo in modo sicuro, con progress callback e verifica integrità.
     */
    suspend fun downloadModel(
        model: ModelInfo,
        onProgress: ((Float) -> Unit)? = null
    ): Result<ModelloInstallato> = withContext(Dispatchers.IO) {
        runCatching {
            val destinationFile = getModelFile(model)
            val tempFile = File(modelsDir, "${model.id}.bin.tmp")

            if (tempFile.exists()) tempFile.delete()

            val url = URL(model.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Download fallito (HTTP ${connection.responseCode}: ${connection.responseMessage})")
            }

            val totalBytes = connection.contentLength.toLong().let {
                if (it > 0) it else model.sizeBytes
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.0f, 1.0f)
                            onProgress?.invoke(progress)
                        }
                    }
                }
            }

            // Spostamento atomico nel file definitivo
            if (destinationFile.exists()) destinationFile.delete()
            if (!tempFile.renameTo(destinationFile)) {
                throw IllegalStateException("Impossibile salvare il modello scaricato nella directory locale.")
            }

            // Imposta automaticamente come attivo se è il primo modello scaricato
            if (prefs.getString("active_model_id", null) == null) {
                setActiveModelId(model.id)
            }

            model.toModelloInstallato(destinationFile)
        }
    }

    /**
     * Importa un file GGML Whisper (.bin) da memoria esterna (es. Download).
     */
    suspend fun importCustomModel(sourceUri: Uri, customName: String): Result<ModelloInstallato> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanId = "custom-" + customName.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
            val destFile = File(modelsDir, "$cleanId.bin")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Impossibile leggere il file selezionato.")

            val installed = ModelloInstallato(
                id = cleanId,
                nome = customName,
                percorsoFile = destFile,
                dimensioneByte = destFile.length(),
                tipo = TipoMotore.WHISPER_CPP,
                ramConsigliataMb = 500,
                descrizione = "Modello Whisper personalizzato (.bin)"
            )

            setActiveModelId(cleanId)
            installed
        }
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
}

/** Alias italiano */
typealias GestoreModelli = ModelManager
