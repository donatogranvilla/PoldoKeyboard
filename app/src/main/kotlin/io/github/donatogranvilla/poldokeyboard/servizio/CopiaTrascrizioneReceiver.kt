package io.github.donatogranvilla.poldokeyboard.servizio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.donatogranvilla.poldokeyboard.comune.Appunti

/**
 * Copia negli appunti il testo di una trascrizione, dal pulsante Copia della notifica.
 */
class CopiaTrascrizioneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val testo = intent.getStringExtra(EXTRA_TESTO) ?: return
        Appunti.copia(context, testo)
    }

    companion object {
        private const val EXTRA_TESTO = "testo"

        /**
         * L'azione Copia per la notifica di una trascrizione. Il testo viaggia con l'intento, come nella notifica
         * stessa: non serve rileggerlo dall'archivio.
         */
        fun intento(context: Context, idTrascrizione: Long, testo: String): PendingIntent {
            val intent = Intent(context, CopiaTrascrizioneReceiver::class.java).putExtra(EXTRA_TESTO, testo)
            // Un codice per trascrizione: con lo stesso codice le notifiche si sovrascriverebbero il testo a vicenda
            return PendingIntent.getBroadcast(
                context,
                idTrascrizione.toInt(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
