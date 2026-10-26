package io.github.donatogranvilla.poldokeyboard.localai

import android.content.Context

/**
 * Impostazioni della trascrizione, condivise tra l'app e il servizio che gira in un altro processo:
 * per questo, come ModelManager, le SharedPreferences sono aperte in MODE_MULTI_PROCESS.
 */
class ImpostazioniTrascrizione(context: Context) {
    @Suppress("DEPRECATION")
    private val prefs = context.getSharedPreferences(NOME_PREFERENZE, Context.MODE_MULTI_PROCESS)

    /**
     * La lingua dei vocali per i modelli multilingue (codice ISO come "it"), oppure null perché il modello
     * la riconosca da sé. I modelli specializzati usano sempre la loro lingua.
     */
    var linguaPredefinita: String?
        get() = prefs.getString(CHIAVE_LINGUA, null)
        set(valore) {
            prefs.edit().putString(CHIAVE_LINGUA, valore).apply()
        }

    private companion object {
        const val NOME_PREFERENZE = "localai_settings"
        const val CHIAVE_LINGUA = "lingua_predefinita"
    }
}
