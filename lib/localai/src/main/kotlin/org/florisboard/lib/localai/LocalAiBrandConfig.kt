package org.florisboard.lib.localai

/**
 * Configurazione Centralizzata del Brand e dei Nomi dell'Applicazione.
 *
 * NOTA PER LA DISTRIBUZIONE COMMERCIALE:
 * Per sostituire il nome del brand o dell'applicazione con qualsiasi altro nome commerciale,
 * è sufficiente modificare i valori di queste costanti (e il valore di 'app_name' in strings.xml).
 * Non è necessario toccare altre parti del codice sorgente.
 */
object LocalAiBrandConfig {
    /** Nome sintetico del brand */
    const val BRAND_NAME = "LocalAI"

    /** Nome completo dell'applicazione */
    const val APP_FULL_NAME = "LocalAI Keyboard"

    /** Titolo del componente di trascrizione su tastiera */
    const val TRANSCRIBER_NAME = "LocalAI Transcriber"

    /** Etichetta dell'azione di condivisione da WhatsApp e altre app */
    const val SHARE_ACTION_LABEL = "Trascrivi con LocalAI"

    /** Titolo della sezione impostazioni */
    const val SETTINGS_TITLE = "Input Vocale & AI Locale"

    /** Descrizione riassuntiva della funzionalità per l'utente */
    const val SETTINGS_SUMMARY = "Trascrizione 100% offline, privacy totale (zero server) con modelli Whisper intercambiabili."

    /** Versione del protocollo AI Locale */
    const val VERSION_NAME = "1.0.0"
}
