package io.github.donatogranvilla.poldokeyboard.servizio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.donatogranvilla.poldokeyboard.MainActivity
import io.github.donatogranvilla.poldokeyboard.R

/**
 * Notifiche con l'esito di una trascrizione: il testo completo, leggibile dalla tendina senza aprire nulla,
 * e il pulsante Copia. Il tocco apre lo storico nell'app.
 */
object NotificheEsito {
    private const val CANALE = "esiti"

    /** Gli id delle notifiche di esito partono da qui, per non scontrarsi con quella del servizio. */
    private const val ID_BASE = 10_000

    /**
     * Crea il canale delle notifiche di esito, con importanza normale: arrivano a lavoro finito e servono.
     */
    fun creaCanale(context: Context) {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(CANALE, context.getString(R.string.notifica_canale_esiti), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /**
     * Il testo è pronto: la notifica lo mostra per intero, con il pulsante per copiarlo.
     */
    fun pronta(context: Context, idTrascrizione: Long, testo: String) {
        val notifica = NotificationCompat.Builder(context, CANALE)
            .setSmallIcon(R.drawable.ic_transcription)
            .setContentTitle(context.getString(R.string.notifica_pronta_titolo))
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setContentIntent(apriStorico(context))
            .addAction(0, context.getString(R.string.azione_copia), CopiaTrascrizioneReceiver.intento(context, idTrascrizione, testo))
            .setAutoCancel(true)
            .build()
        mostra(context, idTrascrizione, notifica)
    }

    /**
     * La trascrizione non è riuscita: la notifica spiega perché.
     */
    fun nonRiuscita(context: Context, idTrascrizione: Long, messaggio: String) {
        val notifica = NotificationCompat.Builder(context, CANALE)
            .setSmallIcon(R.drawable.ic_transcription)
            .setContentTitle(context.getString(R.string.notifica_errore_titolo))
            .setContentText(messaggio)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messaggio))
            .setContentIntent(apriStorico(context))
            .setAutoCancel(true)
            .build()
        mostra(context, idTrascrizione, notifica)
    }

    /**
     * Pubblica la notifica, una per trascrizione. Senza il permesso delle notifiche Android la scarterebbe:
     * il testo resta comunque nell'app e nella tastiera di trascrizione.
     */
    private fun mostra(context: Context, idTrascrizione: Long, notifica: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify((ID_BASE + idTrascrizione).toInt(), notifica)
    }

    /**
     * Apre lo storico delle trascrizioni nell'app.
     */
    private fun apriStorico(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
