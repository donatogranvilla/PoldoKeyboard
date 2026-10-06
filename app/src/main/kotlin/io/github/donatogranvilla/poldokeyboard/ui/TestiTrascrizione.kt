package io.github.donatogranvilla.poldokeyboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.donatogranvilla.poldokeyboard.R
import io.github.donatogranvilla.poldokeyboard.archivio.StatoTrascrizione
import io.github.donatogranvilla.poldokeyboard.archivio.Trascrizione
import java.text.DateFormat
import java.util.Date

/**
 * Il testo da mostrare per una trascrizione: il testo trascritto se è pronta, altrimenti il suo stato.
 * Mentre la si rifà con un altro modello, o se la nuova trascrizione non riesce, il testo precedente
 * resta leggibile sotto lo stato. Lo usano sia l'app sia la tastiera di trascrizione.
 */
@Composable
fun testoTrascrizione(trascrizione: Trascrizione): String {
    val stato = when (trascrizione.stato) {
        StatoTrascrizione.COMPLETATA -> return trascrizione.testo.orEmpty()
        StatoTrascrizione.IN_CODA -> stringResource(R.string.stato_in_coda)
        StatoTrascrizione.IN_CORSO -> stringResource(R.string.stato_in_corso, "${(trascrizione.avanzamento * 100).toInt()}%")
        StatoTrascrizione.ERRORE -> stringResource(R.string.stato_errore, trascrizione.errore.orEmpty())
    }
    val precedente = trascrizione.testo
    return if (precedente.isNullOrBlank()) stato else "$stato\n\n$precedente"
}

/**
 * Con quale modello è stata fatta la trascrizione, o con quale la si sta rifacendo; null se non si sa.
 * @param modelli I modelli conosciuti, per mostrarne il nome; null finché non sono stati letti.
 */
@Composable
fun modelloTrascrizione(trascrizione: Trascrizione, modelli: ModelliDisponibili?): String? {
    if (modelli == null) return null
    val richiesto = trascrizione.idModelloRichiesto
    val usato = trascrizione.idModello
    val inLavorazione = trascrizione.stato == StatoTrascrizione.IN_CODA || trascrizione.stato == StatoTrascrizione.IN_CORSO
    return when {
        inLavorazione && richiesto != null -> stringResource(R.string.modello_richiesto, modelli.nome(richiesto))
        usato != null -> stringResource(R.string.modello_usato, modelli.nome(usato))
        else -> null
    }
}

/**
 * Data e ora di una trascrizione nel formato della lingua del telefono.
 */
fun dataOra(millisecondi: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millisecondi))
