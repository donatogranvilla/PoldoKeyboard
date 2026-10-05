package org.florisboard.lib.localai.engine

import java.io.File

/**
 * Universal interface for offline local speech-to-text inference engines.
 * Neither the keyboard UI, clipboard, nor target applications know about
 * the internal ASR implementation.
 */
interface SpeechRecognitionEngine {
    /** The engine type (e.g. WHISPER_CPP or SHERPA_ONNX) */
    val engineType: EngineType

    /** Whether a model is currently loaded and ready in memory */
    val isModelLoaded: Boolean

    /** Current loaded model file path or identifier */
    val currentModelPath: String?

    /**
     * Loads an offline model file from disk into memory.
     * @param modelFile Absolute path to the model file (.bin, .onnx, etc.)
     * @return Result.success on success, Result.failure on error
     */
    suspend fun loadModel(modelFile: File): Result<Unit>

    /**
     * Unloads the current model from memory to free RAM.
     */
    suspend fun unloadModel()

    /**
     * Transcribes an array of 16,000 Hz, 16-bit Mono float samples.
     *
     * @param audioSamples Normalized PCM audio samples (-1.0f to 1.0f) at 16 kHz Mono.
     * @param options Configuration for language, threads, temperature, etc.
     * @param onProgress Optional callback receiving progress between 0.0f and 1.0f.
     * @return Result containing [TranscriptionResult] or error.
     */
    suspend fun transcribe(
        audioSamples: FloatArray,
        options: TranscriptionOptions = TranscriptionOptions.Default,
        onProgress: ((Float) -> Unit)? = null
    ): Result<TranscriptionResult>
}
