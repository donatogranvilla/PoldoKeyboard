package org.florisboard.lib.localai.whisper

/**
 * JNI bridge declarations for whisper.cpp C/C++ core.
 */
object WhisperNative {
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("whisper_jni")
            isNativeLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isNativeLoaded = false
        }
    }

    val isAvailable: Boolean get() = isNativeLoaded

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
