package org.florisboard.lib.localai.model

import kotlinx.serialization.Serializable
import org.florisboard.lib.localai.engine.EngineType
import org.florisboard.lib.localai.engine.ModelloInstallato
import org.florisboard.lib.localai.engine.TipoMotore
import java.io.File

/**
 * Metadata descriptor for an offline ASR model.
 * Contiene i dati sulle prestazioni, requisiti hardware e punteggio di velocità/accuratezza
 * per permettere all'utente di scegliere il modello più adatto al proprio dispositivo.
 */
@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val engineType: EngineType = EngineType.WHISPER_CPP,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val license: String = "MIT",
    val qualityLevel: String,
    val description: String,
    val ramConsigliataMb: Int = 400,
    val stelleAccuratezza: Int = 4,
    val fulminiVelocita: Int = 4,
    val quantizzazione: String = "Q5_1",
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

    fun toModelloInstallato(file: File): ModelloInstallato {
        return ModelloInstallato(
            id = id,
            nome = name,
            percorsoFile = file,
            dimensioneByte = if (file.exists()) file.length() else sizeBytes,
            tipo = when (engineType) {
                EngineType.WHISPER_CPP -> TipoMotore.WHISPER_CPP
                EngineType.SHERPA_ONNX -> TipoMotore.SHERPA_ONNX
                EngineType.MOCK_TEST -> TipoMotore.MOCK
            },
            ramConsigliataMb = ramConsigliataMb,
            descrizione = description,
        )
    }
}
