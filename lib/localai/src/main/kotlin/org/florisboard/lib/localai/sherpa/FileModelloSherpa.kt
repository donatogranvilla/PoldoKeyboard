package org.florisboard.lib.localai.sherpa

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineOmnilingualAsrCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import org.florisboard.lib.localai.engine.ArchitetturaModello
import java.io.File

/**
 * Il modello sherpa-onnx è incompleto: manca un file che l'architettura richiede.
 * @property fileMancante Il file cercato, descritto come lo nomina la documentazione di sherpa-onnx.
 */
class ModelloIncompletoException(val fileMancante: String) :
    IllegalArgumentException("Nella cartella del modello manca $fileMancante: l'archivio non è quello giusto per questa architettura.")

/**
 * Trova nella cartella di un modello sherpa-onnx i file che la sua architettura richiede, con i nomi
 * usati dagli archivi ufficiali, e prepara la configurazione del riconoscitore. Se ci sono sia la versione
 * piena sia quella int8 di un file, sceglie la int8: meno memoria e più velocità sul telefono.
 */
object FileModelloSherpa {

    /**
     * La configurazione del modello per sherpa-onnx.
     * @param lingua La lingua da imporre, per i modelli che la accettano; null per riconoscerla da sé.
     * @throws ModelloIncompletoException se manca un file richiesto.
     */
    fun configurazione(
        cartella: File,
        architettura: ArchitetturaModello,
        numeroThread: Int,
        lingua: String?,
    ): OfflineModelConfig {
        val file = cartella.listFiles().orEmpty().filter { it.isFile }
        val tokens = file.firstOrNull { it.name.endsWith("tokens.txt") }?.absolutePath
            ?: throw ModelloIncompletoException("tokens.txt")

        /** Il file di rete neurale il cui nome contiene la parte indicata, preferendo la versione int8. */
        fun rete(nome: String, filtro: (String) -> Boolean): String {
            return file
                .filter { (it.extension == "onnx" || it.extension == "ort") && filtro(it.name.lowercase()) }
                .sortedByDescending { it.name.contains("int8") }
                .firstOrNull()?.absolutePath
                ?: throw ModelloIncompletoException(nome)
        }

        return when (architettura) {
            ArchitetturaModello.NEMO_TRANSDUCER, ArchitetturaModello.ZIPFORMER_TRANSDUCER -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = rete("encoder.onnx") { "encoder" in it },
                    decoder = rete("decoder.onnx") { "decoder" in it },
                    joiner = rete("joiner.onnx") { "joiner" in it },
                ),
                // Parakeet e GigaAM vogliono il tipo esplicito; per gli Zipformer sherpa-onnx lo ricava da sé
                modelType = if (architettura == ArchitetturaModello.NEMO_TRANSDUCER) "nemo_transducer" else "",
                tokens = tokens,
                numThreads = numeroThread,
            )
            ArchitetturaModello.NEMO_CTC -> OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = rete("model.onnx") { "model" in it }),
                tokens = tokens,
                numThreads = numeroThread,
            )
            ArchitetturaModello.OMNILINGUAL_CTC -> OfflineModelConfig(
                omnilingual = OfflineOmnilingualAsrCtcModelConfig(model = rete("model.onnx") { "model" in it }),
                tokens = tokens,
                numThreads = numeroThread,
            )
            ArchitetturaModello.SENSE_VOICE -> OfflineModelConfig(
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = rete("model.onnx") { "model" in it },
                    language = lingua ?: "auto",
                    useInverseTextNormalization = true,
                ),
                tokens = tokens,
                numThreads = numeroThread,
            )
            ArchitetturaModello.MOONSHINE -> OfflineModelConfig(
                moonshine = configurazioneMoonshine(::rete, file),
                tokens = tokens,
                numThreads = numeroThread,
            )
            ArchitetturaModello.WHISPER_ONNX -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = rete("encoder.onnx") { "encoder" in it },
                    decoder = rete("decoder.onnx") { "decoder" in it },
                    // Vuota: sherpa-onnx riconosce la lingua da sé
                    language = lingua.orEmpty(),
                    task = "transcribe",
                ),
                tokens = tokens,
                numThreads = numeroThread,
            )
            ArchitetturaModello.GGML -> throw IllegalArgumentException("I modelli GGML li esegue whisper.cpp, non sherpa-onnx.")
        }
    }

    /**
     * Moonshine v2 ha encoder e decoder unito; la v1 ha preprocess, encode e due decoder.
     */
    private fun configurazioneMoonshine(
        rete: (String, (String) -> Boolean) -> String,
        file: List<File>,
    ): OfflineMoonshineModelConfig {
        val versione2 = file.any { "merged" in it.name.lowercase() }
        return if (versione2) {
            OfflineMoonshineModelConfig(
                encoder = rete("encoder") { "encode" in it },
                mergedDecoder = rete("merged decoder") { "merged" in it },
            )
        } else {
            OfflineMoonshineModelConfig(
                preprocessor = rete("preprocess.onnx") { "preprocess" in it },
                encoder = rete("encode.onnx") { "encode" in it && "decode" !in it },
                uncachedDecoder = rete("uncached_decode.onnx") { "uncached" in it },
                cachedDecoder = rete("cached_decode.onnx") { "cached" in it && "uncached" !in it },
            )
        }
    }
}
