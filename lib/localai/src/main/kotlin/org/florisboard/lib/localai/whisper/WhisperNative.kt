package org.florisboard.lib.localai.whisper

/**
 * JNI bridge declarations for whisper.cpp C/C++ core.
 */
object WhisperNative {
    /**
     * La variante del backend CPU caricata, adatta alle istruzioni del processore, come
     * "libggml-cpu-android_armv8.2_2.so"; null se la libreria nativa non c'è o nessuna variante si può usare.
     */
    val varianteCpu: String?

    init {
        varianteCpu = try {
            System.loadLibrary("whisper_jni")
            caricaVarianteCpu()
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    /** true se la libreria nativa e una variante del backend CPU adatta al telefono sono caricate. */
    val isAvailable: Boolean get() = varianteCpu != null

    private external fun caricaVarianteCpu(): String?

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextHandle: Long)

    /** La lingua parlata nei primi 30 s dei campioni, come "it"; null per i modelli solo inglese o se fallisce. */
    external fun rilevaLingua(contextHandle: Long, samples: FloatArray, threads: Int): String?

    /** Carica il modello Silero del rilevatore di parlato; 0 se non riesce. */
    external fun initVad(modelPath: String): Long
    external fun freeVad(vadHandle: Long)

    /**
     * I tratti di parlato nei campioni a 16 kHz, già allargati di [margineMs] per lato, come coppie
     * consecutive di inizio e fine in secondi; vuoto se non c'è parlato, null se il rilevamento non riesce.
     */
    external fun trovaParlato(
        vadHandle: Long,
        samples: FloatArray,
        soglia: Float,
        parlatoMinimoMs: Int,
        silenzioMinimoMs: Int,
        parlatoMassimoS: Float,
        margineMs: Int,
    ): FloatArray?

    external fun fullTranscribe(
        contextHandle: Long,
        samples: FloatArray,
        language: String?,
        threads: Int,
        translate: Boolean,
        prompt: String?,
    ): String?
}
