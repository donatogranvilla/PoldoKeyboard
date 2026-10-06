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
 * Lo usano sia l'app sia la tastiera di trascrizione.
 */
@Composable
fun testoTrascrizione(trascrizione: Trascrizione): String = when (trascrizione.stato) {
    StatoTrascrizione.IN_CODA -> stringResource(R.string.stato_in_coda)
    StatoTrascrizione.IN_CORSO -> stringResource(R.string.stato_in_corso, "${(trascrizione.avanzamento * 100).toInt()}%")
    StatoTrascrizione.COMPLETATA -> trascrizione.testo.orEmpty()
    StatoTrascrizione.ERRORE -> stringResource(R.string.stato_errore, trascrizione.errore.orEmpty())
}

/**
 * Data e ora di una trascrizione nel formato della lingua del telefono.
 */
fun dataOra(millisecondi: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millisecondi))
