package org.florisboard.lib.localai.postprocess

import java.util.Locale

/**
 * Natural text normalization and punctuation post-processor.
 * Improves readability without altering the original meaning or hallucinating information.
 */
object TextPostProcessor {
    // Tonde solo con un'annotazione breve, come "(risate)": una frase vera tra parentesi resta
    private val BRACKETED_SOUNDS_REGEX = Regex("""\[.*?\]|<.*?>|\((?:\s*[^()\s]+){1,3}\s*\)""")

    /**
     * Le frasi con i crediti dei sottotitoli che Whisper ha imparato dai dati di addestramento e inventa sul
     * non-parlato, in ogni lingua: si tolgono ovunque compaiano, perché nessuno le pronuncia in un vocale.
     */
    private val CREDITI_SOTTOTITOLI_REGEX =
        Regex("""(?i)[^.!?]*\b(?:amara\.org|qtss|untertitel im auftrag des zdf)\b[^.!?]*[.!?]?""")

    /** Lunghezza massima, in parole, di una frase ripetuta in loop. */
    private const val MAX_PAROLE_RIPETIZIONE = 10

    /** Una parola sola, senza punto, conta come loop da 4 volte in su: "no no no" detto davvero resta. */
    private const val MIN_RIPETIZIONI_PAROLA = 4

    /** Una frase di più parole conta come loop da 3 volte in su. */
    private const val MIN_RIPETIZIONI_FRASE = 3

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
        text = text.replace(CREDITI_SOTTOTITOLI_REGEX, " ")

        // 2. Collapse runaway repeating words from noisy audio loops
        text = rimuoviRipetizioni(text)

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

    /**
     * Riduce a una sola occorrenza le parole e le frasi fino a [MAX_PAROLE_RIPETIZIONE] parole ripetute di
     * seguito, come i loop di Whisper ("Grazie. Grazie. Grazie."). Il confronto ignora punteggiatura e maiuscole;
     * a parità di posizione vince la ripetizione che copre più parole. Resta la prima occorrenza, così com'è.
     */
    private fun rimuoviRipetizioni(testo: String): String {
        val parole = testo.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val normalizzate = parole.map { parola -> parola.lowercase().filter { it.isLetterOrDigit() } }
        val risultato = mutableListOf<String>()
        var i = 0
        while (i < parole.size) {
            var lunghezzaMigliore = 1
            var ripetizioniMigliori = 1
            for (n in 1..MAX_PAROLE_RIPETIZIONE) {
                var ripetizioni = 1
                while (i + (ripetizioni + 1) * n <= parole.size &&
                    (0 until n).all { normalizzate[i + it] == normalizzate[i + ripetizioni * n + it] }
                ) {
                    ripetizioni++
                }
                // Una parola che chiude una frase ("Grazie.") ripetuta è già una frase ripetuta
                val fraseIntera = parole[i + n - 1].last() in ".!?"
                val minimo = if (n == 1 && !fraseIntera) MIN_RIPETIZIONI_PAROLA else MIN_RIPETIZIONI_FRASE
                if (ripetizioni >= minimo && ripetizioni * n > ripetizioniMigliori * lunghezzaMigliore) {
                    lunghezzaMigliore = n
                    ripetizioniMigliori = ripetizioni
                }
            }
            risultato.addAll(parole.subList(i, i + lunghezzaMigliore))
            i += lunghezzaMigliore * ripetizioniMigliori
        }
        return risultato.joinToString(" ")
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
