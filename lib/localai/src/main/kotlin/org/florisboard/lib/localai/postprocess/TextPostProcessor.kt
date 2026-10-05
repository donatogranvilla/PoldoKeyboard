package org.florisboard.lib.localai.postprocess

import java.util.Locale

/**
 * Natural text normalization and punctuation post-processor.
 * Improves readability without altering the original meaning or hallucinating information.
 */
object TextPostProcessor {
    private val REPEATING_TOKEN_REGEX = Regex("""(?i)(\b[\w']+\b)(\s+\1){3,}""")
    private val BRACKETED_SOUNDS_REGEX = Regex("""\[.*?\]|\(.*?\)|<.*?>""")

    /**
     * Applies non-destructive normalization and punctuation cleanup.
     *
     * @param rawText Raw output from speech recognition engine
     * @param languageCode ISO language code (e.g. "it", "fr", "es", "en")
     */
    fun process(rawText: String, languageCode: String? = null): String {
        var text = rawText.trim()
        if (text.isEmpty()) return ""

        // 1. Strip repetitive Whisper hallucinations or background annotations (e.g. [Musica], (risate))
        text = text.replace(BRACKETED_SOUNDS_REGEX, " ")

        // 2. Collapse runaway repeating words from noisy audio loops
        text = text.replace(REPEATING_TOKEN_REGEX, "$1")

        // 3. Normalize whitespace
        text = text.replace(Regex("""\s+"""), " ").trim()

        // 4. Fix spaces around punctuation (remove space before commas/dots/question marks/exclamations)
        text = text.replace(Regex("""\s+([,.:;?!])"""), "$1")

        // Ensure space after punctuation if missing (e.g. "ciao,domani" -> "ciao, domani")
        text = text.replace(Regex("""([,.:;?!])([^\s0-9,.:;?!])"""), "$1 $2")

        // 5. Capitalize first letter of each sentence
        text = capitalizeSentences(text, languageCode)

        return text
    }

    private fun capitalizeSentences(text: String, languageCode: String?): String {
        val locale = if (!languageCode.isNullOrBlank()) Locale.forLanguageTag(languageCode) else Locale.getDefault()
        val sb = StringBuilder()
        var capitalizeNext = true

        for (ch in text) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercase(locale))
                capitalizeNext = false
            } else {
                sb.append(ch)
                if (ch == '.' || ch == '?' || ch == '!') {
                    capitalizeNext = true
                }
            }
        }
        return sb.toString()
    }
}
