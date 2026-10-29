package io.github.donatogranvilla.poldokeyboard.localai.llama

/**
 * Il collegamento JNI con llama.cpp, che esegue i modelli di testo in formato GGUF. Usa lo stesso ggml e lo stesso
 * backend CPU di whisper.cpp.
 */
object LlamaNative {
    /**
     * La variante del backend CPU caricata, come "libggml-cpu-android_armv8.2_2.so"; null se la libreria nativa non
     * c'è o nessuna variante si può usare.
     */
    val varianteCpu: String?

    init {
        varianteCpu = try {
            System.loadLibrary("llama_jni")
            caricaVarianteCpu()
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    /** true se la libreria nativa e una variante del backend CPU adatta al telefono sono caricate. */
    val isAvailable: Boolean get() = varianteCpu != null

    /** Riceve il testo scritto finora durante la generazione. */
    fun interface AscoltatoreGenerazione {
        /** @return false per fermare la generazione. */
        fun avanzamento(testo: String): Boolean
    }

    private external fun caricaVarianteCpu(): String?

    /**
     * Carica il modello con un contesto di [token] token, che prompt e risposta insieme non possono superare.
     * @return Il riferimento al modello caricato, o 0 se il file non è valido o manca la memoria.
     */
    external fun carica(percorso: String, token: Int, thread: Int): Long

    external fun libera(handle: Long)

    /** Interrompe la generazione in corso; si può chiamare da un altro thread. */
    external fun ferma(handle: Long)

    /**
     * La risposta al [prompt], già composto con il formato di chat del modello: sempre la stessa per lo stesso prompt.
     * @return Il testo, anche parziale se la generazione è stata fermata; null se il prompt non entra nel contesto o
     *   l'elaborazione non riesce.
     */
    external fun genera(handle: Long, prompt: String, massimoToken: Int, ascoltatore: AscoltatoreGenerazione?): String?
}
