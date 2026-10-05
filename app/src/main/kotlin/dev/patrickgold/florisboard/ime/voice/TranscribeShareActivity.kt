package dev.patrickgold.florisboard.ime.voice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.voice.archivio.ArchivioTrascrizioni
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Riceve i vocali condivisi da WhatsApp o da altre app senza mostrare finestre: li copia nell'archivio,
 * avvia il servizio di trascrizione e si chiude, così l'utente resta nella chat.
 * La trascrizione comparirà nella scheda della tastiera.
 */
class TranscribeShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vocali = vocaliCondivisi(intent)
        lifecycleScope.launch {
            // Vanno copiati prima di chiudere: il permesso di leggerli dura quanto questa Activity
            val accodati = accodaTutti(vocali)
            if (accodati > 0) {
                ServizioTrascrizione.avvia(applicationContext)
            }
            Toast.makeText(applicationContext, messaggioEsito(accodati, vocali.size), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Mette in coda i vocali nell'ordine ricevuto; uno illeggibile non ferma gli altri.
     * @return Quanti vocali sono entrati in coda.
     */
    private suspend fun accodaTutti(vocali: List<Uri>): Int {
        val archivio = ArchivioTrascrizioni.istanza(applicationContext)
        var accodati = 0
        for (vocale in vocali) {
            try {
                archivio.accoda(vocale)
                accodati++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si passa al successivo: il messaggio finale dice quanti non si sono potuti leggere
            }
        }
        return accodati
    }

    /**
     * Il messaggio dopo la condivisione: quanti vocali sono in coda e se qualcuno è rimasto fuori.
     */
    private fun messaggioEsito(accodati: Int, ricevuti: Int): String = when {
        accodati == 0 -> getString(R.string.localai_share_failed)
        accodati < ricevuti -> getString(R.string.localai_share_partial, accodati, ricevuti)
        else -> resources.getQuantityString(R.plurals.localai_share_queued, accodati, accodati)
    }

    companion object {
        /**
         * Gli URI dei vocali nell'intent di condivisione, uno solo o più insieme.
         */
        private fun vocaliCondivisi(intent: Intent): List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat
                .getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                .orEmpty()
                .filterNotNull()
            else -> emptyList()
        }
    }
}
