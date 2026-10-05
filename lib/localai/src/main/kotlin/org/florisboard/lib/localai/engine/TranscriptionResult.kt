package org.florisboard.lib.localai.engine

/**
 * Options passed to the SpeechRecognitionEngine for transcription.
 */
data class TranscriptionOptions(
    /** Initial prompt to guide vocabulary, capitalization, and context */
    val initialPrompt: String? = null,

    /** ISO 639-1 language code ("it", "en", "es", "fr", "de", "pt") or null for auto-detect */
    val language: String? = null,

    /** Whether to translate output to English (Whisper capability) */
    val translateToEnglish: Boolean = false,

    /** Number of CPU threads to allocate for inference */
    val threads: Int = 4,

    /** Temperature for sampling (0.0 for greedy deterministic decoding) */
    val temperature: Float = 0.0f,

    /** Enable Voice Activity Detection (VAD) preprocessing */
    val enableVad: Boolean = true,

    /** Whether to apply automatic punctuation and capitalization post-processing */
    val formatPunctuation: Boolean = true
) {
    companion object {
        val Default = TranscriptionOptions()
    }
}

/**
 * Segment of transcribed audio with timing.
 */
data class TranscriptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Output of the transcription pipeline.
 */
data class TranscriptionResult(
    /** The raw or normalized transcribed text */
    val text: String,

    /** Detected or requested language code */
    val language: String? = null,

    /** Audio duration in milliseconds */
    val audioDurationMs: Long = 0,

    /** Total inference time in milliseconds */
    val inferenceTimeMs: Long = 0,

    /** Individual segments */
    val segments: List<TranscriptionSegment> = emptyList()
)

fun RisultatoTrascrizione.toTranscriptionResult(): TranscriptionResult {
    return TranscriptionResult(
        text = testo,
        language = linguaRilevata,
        audioDurationMs = durataAudioMs,
        inferenceTimeMs = tempoInferenzaMs,
        segments = segmenti.map { TranscriptionSegment(it.inizioMs, it.fineMs, it.testo) }
    )
}

fun TranscriptionResult.toRisultatoTrascrizione(): RisultatoTrascrizione {
    return RisultatoTrascrizione(
        testo = text,
        segmenti = segments.map { SegmentoTrascritto(it.startMs, it.endMs, it.text) },
        linguaRilevata = language,
        durataAudioMs = audioDurationMs,
        tempoInferenzaMs = inferenceTimeMs
    )
}

fun OpzioniTrascrizione.toTranscriptionOptions(): TranscriptionOptions {
    return TranscriptionOptions(
        language = lingua,
        formatPunctuation = formattaPunteggiatura,
        threads = numThread,
        temperature = temperatura,
        translateToEnglish = traduciInInglese
    )
}

fun TranscriptionOptions.toOpzioniTrascrizione(): OpzioniTrascrizione {
    return OpzioniTrascrizione(
        lingua = language,
        formattaPunteggiatura = formatPunctuation,
        numThread = threads,
        temperatura = temperature,
        traduciInInglese = translateToEnglish
    )
}
