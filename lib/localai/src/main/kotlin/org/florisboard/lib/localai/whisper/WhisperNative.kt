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
    external fun fullTranscribe(
        contextHandle: Long,
        samples: FloatArray,
        language: String?,
        threads: Int,
        translate: Boolean
    ): String?
}
