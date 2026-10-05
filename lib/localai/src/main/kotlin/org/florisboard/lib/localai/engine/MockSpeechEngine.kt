package org.florisboard.lib.localai.engine

import kotlinx.coroutines.delay
import org.florisboard.lib.localai.postprocess.TextPostProcessor
import java.io.File

/**
 * Mock speech engine used for automated testing, debugging, and verification
 * before downloading full Whisper models.
 */
class MockSpeechEngine : SpeechRecognitionEngine {
    override val engineType: EngineType = EngineType.MOCK_TEST
    override var isModelLoaded: Boolean = false
        private set
    override var currentModelPath: String? = null
        private set

    override suspend fun loadModel(modelFile: File): Result<Unit> {
        delay(50)
        isModelLoaded = true
        currentModelPath = modelFile.absolutePath
        return Result.success(Unit)
    }

    override suspend fun unloadModel() {
        isModelLoaded = false
        currentModelPath = null
    }

    override suspend fun transcribe(
        audioSamples: FloatArray,
        options: TranscriptionOptions,
        onProgress: ((Float) -> Unit)?
    ): Result<TranscriptionResult> {
        val durationMs = (audioSamples.size / 16.0).toLong()

        // Simulate inference progression
        for (p in 1..10) {
            delay(30)
            onProgress?.invoke(p / 10.0f)
        }

        val rawText = "Trascrizione di prova effettuata con successo."
        val finalText = if (options.formatPunctuation) {
            TextPostProcessor.process(rawText, options.language)
        } else {
            rawText
        }

        return Result.success(
            TranscriptionResult(
                text = finalText,
                language = options.language ?: "it",
                audioDurationMs = durationMs,
                inferenceTimeMs = 300,
                segments = listOf(
                    TranscriptionSegment(0, durationMs, finalText)
                )
            )
        )
    }
}
