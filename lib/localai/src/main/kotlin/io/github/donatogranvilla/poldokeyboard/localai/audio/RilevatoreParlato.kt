package io.github.donatogranvilla.poldokeyboard.localai.audio

import android.content.Context
import io.github.donatogranvilla.poldokeyboard.localai.whisper.WhisperNative
import java.io.File

/**
 * Trova i tratti di parlato con Silero VAD v6, eseguito da whisper.cpp.
 *
 * Togliere il non-parlato prima della trascrizione è l'intervento che più riduce le allucinazioni di Whisper,
 * che sul silenzio e sul rumore inventa frasi. Il modello (885 KB, licenza MIT) è negli asset; whisper.cpp lo
 * legge da un file, quindi la prima volta viene copiato nella memoria dell'app.
 */
class RilevatoreParlato private constructor(private var handle: Long) : AutoCloseable {

    /**
     * I tratti di parlato nei campioni a 16 kHz mono, già allargati di [MARGINE_MS] per lato. Se alla soglia
     * normale non c'è parlato riprova con una più bassa, per non scartare un vocale detto sottovoce.
     * @param durataMassimaS Il parlato continuo più lungo di così viene diviso nella pausa più adatta.
     * @return I tratti in ordine, vuoto se non c'è parlato, null se il rilevamento non riesce.
     */
    @Synchronized
    fun trova(campioni: FloatArray, durataMassimaS: Int): List<TrattoParlato>? {
        check(handle != 0L) { "Rilevatore di parlato già chiuso." }
        val tratti = trovaConSoglia(campioni, durataMassimaS, SOGLIA) ?: return null
        if (tratti.isNotEmpty()) return tratti
        return trovaConSoglia(campioni, durataMassimaS, SOGLIA_SOTTOVOCE)
    }

    private fun trovaConSoglia(campioni: FloatArray, durataMassimaS: Int, soglia: Float): List<TrattoParlato>? {
        val secondi = WhisperNative.trovaParlato(
            vadHandle = handle,
            samples = campioni,
            soglia = soglia,
            parlatoMinimoMs = PARLATO_MINIMO_MS,
            silenzioMinimoMs = SILENZIO_MINIMO_MS,
            parlatoMassimoS = durataMassimaS.toFloat(),
            margineMs = MARGINE_MS,
        ) ?: return null
        return (secondi.indices step 2)
            .map { i ->
                TrattoParlato(
                    inizio = (secondi[i] * FREQUENZA).toInt().coerceIn(0, campioni.size),
                    fine = (secondi[i + 1] * FREQUENZA).toInt().coerceIn(0, campioni.size),
                )
            }
            .filter { it.lunghezza > 0 }
    }

    @Synchronized
    override fun close() {
        if (handle != 0L) {
            WhisperNative.freeVad(handle)
            handle = 0L
        }
    }

    companion object {
        private const val FREQUENZA = 16_000
        private const val ASSET_MODELLO = "vad/ggml-silero-v6.2.0.bin"

        /** La soglia consigliata da Silero, whisper.cpp e sherpa-onnx. */
        private const val SOGLIA = 0.5f

        /** La soglia del secondo tentativo, quando alla prima non si trova parlato. */
        private const val SOGLIA_SOTTOVOCE = 0.3f

        /** Il parlato più breve di così è un rumore, come nei valori predefiniti di Silero. */
        private const val PARLATO_MINIMO_MS = 250

        /** Una pausa più lunga di così chiude un tratto di parlato. */
        private const val SILENZIO_MINIMO_MS = 300

        /**
         * Il margine attorno al parlato, che protegge l'inizio e la fine delle parole. Basso perché i fine-tune
         * italiani soffrono il rumore attorno al parlato più dei modelli originali.
         */
        private const val MARGINE_MS = 200

        /**
         * Il rilevatore pronto all'uso, oppure null se la libreria nativa non c'è, come sui telefoni non arm64,
         * o se il modello non si carica: in quel caso si torna a dividere l'audio nelle pause.
         */
        fun crea(context: Context): RilevatoreParlato? {
            if (!WhisperNative.isAvailable) return null
            val file = runCatching { copiaModello(context) }.getOrNull() ?: return null
            // Una libreria nativa di una build vecchia, senza le funzioni del VAD, non deve bloccare la trascrizione
            val handle = try {
                WhisperNative.initVad(file.absolutePath)
            } catch (_: UnsatisfiedLinkError) {
                0L
            }
            return if (handle != 0L) RilevatoreParlato(handle) else null
        }

        /**
         * Copia il modello dagli asset, se non c'è già. Passa da un file temporaneo, così una copia interrotta
         * non lascia un modello a metà. Il nome contiene la versione: un modello nuovo non riusa il vecchio file.
         */
        private fun copiaModello(context: Context): File {
            val file = File(context.noBackupFilesDir, ASSET_MODELLO)
            if (file.exists()) return file
            file.parentFile?.mkdirs()
            val temporaneo = File(file.parentFile, "${file.name}.tmp")
            context.assets.open(ASSET_MODELLO).use { ingresso ->
                temporaneo.outputStream().use { uscita -> ingresso.copyTo(uscita) }
            }
            check(temporaneo.renameTo(file)) { "Impossibile salvare il modello del rilevatore di parlato." }
            return file
        }
    }
}
