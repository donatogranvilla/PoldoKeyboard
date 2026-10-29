package io.github.donatogranvilla.poldokeyboard.localai.model

import kotlinx.serialization.Serializable
import io.github.donatogranvilla.poldokeyboard.localai.engine.ArchitetturaModello
import io.github.donatogranvilla.poldokeyboard.localai.engine.ModelloInstallato
import io.github.donatogranvilla.poldokeyboard.localai.engine.TipoMotore
import java.io.File

/**
 * Un modello del catalogo, con quello che serve all'utente per scegliere: lingue, peso, velocità,
 * qualità, licenza, e il file da scaricare.
 * @property architettura Com'è fatto il modello; ne derivano il motore e i file richiesti.
 * @property lingue Codici ISO delle lingue coperte: vuota se le copre tutte, una sola se è specializzato.
 * @property downloadUrl Il file o l'archivio da scaricare, così com'è.
 * @property paginaUrl La pagina del modello, con licenza e dettagli; se manca si usa [downloadUrl].
 * @property nonCommerciale true se la licenza vieta l'uso commerciale.
 * @property sha256 Impronta del file o dell'archivio scaricato: riconosce il modello all'import.
 * @property consigliato true per i modelli da proporre per primi, scelti dalle prove sul banco; nel catalogo, così si
 *   cambiano con l'aggiornamento online. Le versioni dell'app che non conoscono il campo lo ignorano.
 */
@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val tipoMotore: TipoMotore = TipoMotore.WHISPER_CPP,
    val architettura: ArchitetturaModello = ArchitetturaModello.GGML,
    val lingue: List<String> = emptyList(),
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val paginaUrl: String? = null,
    val license: String = "MIT",
    val nonCommerciale: Boolean = false,
    val qualityLevel: String = "",
    val description: String,
    val ramConsigliataMb: Int = 400,
    val stelleAccuratezza: Int = 4,
    val fulminiVelocita: Int = 4,
    val quantizzazione: String = "Q5_1",
    val consigliato: Boolean = false,
) {
    val formattedSize: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1000) {
                "%.1f GB".format(mb / 1024.0)
            } else {
                "%.0f MB".format(mb)
            }
        }

    /** true se il modello è specializzato in una sola lingua, che va imposta in trascrizione. */
    val specializzato: Boolean get() = lingue.size == 1

    /** true se il modello copre la lingua indicata, da specializzato o da multilingue. */
    fun copre(lingua: String): Boolean = lingue.isEmpty() || lingua in lingue

    fun toModelloInstallato(file: File): ModelloInstallato {
        return ModelloInstallato(
            id = id,
            nome = name,
            percorsoFile = file,
            dimensioneByte = if (file.isFile) file.length() else sizeBytes,
            tipo = tipoMotore,
            ramConsigliataMb = ramConsigliataMb,
            descrizione = description,
            architettura = architettura,
            lingue = lingue,
        )
    }
}
