package io.github.donatogranvilla.poldokeyboard.localai.whisper

/**
 * Una frase breve e punteggiata, data a Whisper come testo precedente prima di ogni blocco.
 *
 * Whisper imita lo stile del contesto: senza, alcuni fine-tune addestrati su sottotitoli smettono di mettere
 * virgole e punti quando un blocco comincia a metà discorso. La frase è sempre la stessa, mai il testo del blocco
 * precedente, così un errore o un loop non passa da un blocco all'altro. È corta e generica per non spingere il
 * modello verso parole particolari.
 */
internal object PromptPunteggiatura {
    private val FRASI = mapOf(
        "it" to "Ciao, come stai? Bene, grazie.",
        "en" to "Hi, how are you? Fine, thanks.",
        "es" to "Hola, ¿cómo estás? Bien, gracias.",
        "fr" to "Salut, comment ça va ? Bien, merci.",
        "de" to "Hallo, wie geht's? Gut, danke.",
        "pt" to "Oi, tudo bem? Tudo, obrigado.",
    )

    /** La frase per la lingua indicata; null se la lingua non è nota o non ne ha una. */
    fun per(lingua: String?): String? = lingua?.let { FRASI[it] }
}
