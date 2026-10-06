package io.github.donatogranvilla.poldokeyboard.servizio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.donatogranvilla.poldokeyboard.R
import io.github.donatogranvilla.poldokeyboard.archivio.ArchivioTrascrizioni
import io.github.donatogranvilla.poldokeyboard.archivio.Trascrizione
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.LocalAiManager

/**
 * Servizio in primo piano che trascrive i vocali in coda nell'archivio, uno alla volta, nel processo
 * separato `:trascrizione`: se il motore esaurisce la memoria si chiude questo processo, non l'app.
 * A ogni vocale finito avvisa con una notifica che contiene il testo; si ferma da solo a coda vuota.
 */
class ServizioTrascrizione : Service() {
    // Il ciclo si governa dal thread principale; il lavoro pesante lo spostano altrove archivio e motore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var archivio: ArchivioTrascrizioni
    private lateinit var notificationManager: NotificationManager
    private var lavoro: Job? = null
    private var ultimoStartId = 0
    private var ultimaNotifica: Notification? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        archivio = ArchivioTrascrizioni.istanza(this)
        notificationManager = requireNotNull(getSystemService(NotificationManager::class.java))
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CANALE_NOTIFICHE,
                getString(R.string.notifica_canale_avanzamento),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        NotificheEsito.creaCanale(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android chiede il primo piano a ogni avvio, anche quando il servizio sta già lavorando
        avviaInPrimoPiano(ultimaNotifica ?: creaNotifica(nomeVocale = null, frazione = null))
        ultimoStartId = startId
        if (lavoro?.isActive != true) {
            lavoro = scope.launch { svuotaCoda() }
        }
        // Se Android chiude il processo non lo riavvia da solo: la coda riparte con la prossima condivisione
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Da Android 15 questi servizi hanno un tempo massimo giornaliero: ci si ferma e la coda resta
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Trascrive i vocali in coda finché ce ne sono, poi ferma il servizio.
     */
    private suspend fun svuotaCoda() {
        // Un gestore nuovo a ogni giro rilegge il modello attivo, che si sceglie nel processo dell'app
        val localAiManager = LocalAiManager(applicationContext)
        try {
            archivio.recuperaInterrotte()
            while (true) {
                val avviiConsiderati = ultimoStartId
                val prossima = archivio.prendiProssima()
                if (prossima == null) {
                    // Un vocale accodato durante il controllo arriva con un nuovo avvio: in quel caso si ricontrolla
                    if (avviiConsiderati == ultimoStartId) break
                    continue
                }
                trascrivi(prossima, localAiManager)
            }
        } finally {
            // Si arriva qui solo a motore fermo, quindi il modello si può liberare
            localAiManager.activeEngine.close()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            // Con l'ultimo id Android non ferma il servizio se nel frattempo è arrivata un'altra richiesta
            stopSelf(ultimoStartId)
        }
    }

    /**
     * Trascrive un vocale, salva l'esito nell'archivio e lo notifica. Un errore del motore diventa
     * un errore della trascrizione, mostrato all'utente, non una chiusura del servizio.
     */
    private suspend fun trascrivi(trascrizione: Trascrizione, localAiManager: LocalAiManager) {
        val wakeLock = acquisisciWakeLock()
        val avanzamento = MutableStateFlow(0f)
        // StateFlow tiene solo l'ultimo valore: con la pausa, database e notifica si aggiornano al più ogni mezzo secondo
        val scrittore = scope.launch {
            avanzamento.collect { frazione ->
                archivio.aggiornaAvanzamento(trascrizione.id, frazione)
                // A zero il motore non è ancora partito (decodifica, caricamento del modello): barra indeterminata
                notificationManager.notify(ID_NOTIFICA, creaNotifica(trascrizione.nomeOriginale, frazione.takeIf { it > 0f }))
                delay(INTERVALLO_AVANZAMENTO_MS)
            }
        }
        try {
            // Il modello chiesto con "Ritrascrivi con…", altrimenti quello attivo
            val esito = localAiManager.transcribeAudioFile(
                file = archivio.fileAudio(trascrizione),
                idModello = trascrizione.idModelloRichiesto,
            ) { frazione ->
                avanzamento.value = frazione
            }
            // Fermato prima di salvare l'esito, così un avanzamento in ritardo non lo sovrascrive
            scrittore.cancelAndJoin()
            esito.onSuccess { risultato ->
                archivio.completa(trascrizione.id, risultato, localAiManager.activeEngine.modelloCorrente?.id)
                NotificheEsito.pronta(this, trascrizione.id, risultato.testo)
            }.onFailure { errore ->
                val messaggio = errore.message ?: getString(R.string.errore_sconosciuto)
                archivio.segnaErrore(trascrizione.id, messaggio)
                NotificheEsito.nonRiuscita(this, trascrizione.id, messaggio)
            }
        } catch (e: CancellationException) {
            // Servizio fermato a metà, per esempio dal limite di tempo di Android: il vocale torna in coda
            withContext(NonCancellable) { archivio.rimettiInCoda(trascrizione.id) }
            throw e
        } finally {
            scrittore.cancel()
            wakeLock.release()
        }
    }

    /**
     * Tiene sveglia la CPU durante una trascrizione, così prosegue anche a schermo spento.
     * Il limite di tempo evita di scaricare la batteria se il motore si blocca.
     */
    private fun acquisisciWakeLock(): PowerManager.WakeLock {
        val powerManager = requireNotNull(getSystemService(PowerManager::class.java))
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG_WAKE_LOCK).apply {
            // Senza conteggio, rilasciarlo dopo la scadenza del limite non lancia eccezioni
            setReferenceCounted(false)
            acquire(DURATA_MASSIMA_WAKE_LOCK_MS)
        }
    }

    /**
     * La notifica del servizio, con il nome del vocale se noto e l'avanzamento,
     * indeterminato finché il motore non comincia.
     */
    private fun creaNotifica(nomeVocale: String?, frazione: Float?): Notification {
        return NotificationCompat.Builder(this, CANALE_NOTIFICHE)
            .setSmallIcon(R.drawable.ic_transcription)
            .setContentTitle(getString(R.string.notifica_avanzamento_titolo))
            .setContentText(nomeVocale)
            .setProgress(100, ((frazione ?: 0f) * 100).toInt(), frazione == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
            .also { ultimaNotifica = it }
    }

    /**
     * Porta il servizio in primo piano con il suo tipo. Non usa ServiceCompat.startForeground: su Android 14
     * e successivi filtra il tipo con quelli noti ad Android 14 e scarta l'elaborazione multimediale, arrivata
     * con Android 15. Resta "nessun tipo", che Android 16 vieta alle app recenti, e il servizio si chiude.
     */
    private fun avviaInPrimoPiano(notifica: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_NOTIFICA, notifica, tipoServizio())
        } else {
            startForeground(ID_NOTIFICA, notifica)
        }
    }

    /**
     * Il tipo di servizio più adatto tra quelli che esistono su questa versione di Android:
     * elaborazione multimediale da Android 15, sincronizzazione dati prima.
     */
    private fun tipoServizio(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    }

    companion object {
        private const val CANALE_NOTIFICHE = "trascrizione"
        private const val ID_NOTIFICA = 2001
        private const val INTERVALLO_AVANZAMENTO_MS = 500L
        private const val TAG_WAKE_LOCK = "PoldoKeyboard:trascrizione"
        private val DURATA_MASSIMA_WAKE_LOCK_MS = TimeUnit.HOURS.toMillis(2)

        /**
         * Avvia il servizio dopo aver messo un vocale in coda. Se sta già lavorando,
         * il vocale verrà trascritto dopo quelli già in coda.
         */
        fun avvia(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ServizioTrascrizione::class.java))
        }
    }
}
