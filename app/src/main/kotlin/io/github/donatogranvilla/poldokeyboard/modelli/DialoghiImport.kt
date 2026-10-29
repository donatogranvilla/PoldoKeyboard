package io.github.donatogranvilla.poldokeyboard.modelli

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.donatogranvilla.poldokeyboard.R
import io.github.donatogranvilla.poldokeyboard.localai.engine.ArchitetturaModello
import io.github.donatogranvilla.poldokeyboard.localai.model.AnalisiImport
import io.github.donatogranvilla.poldokeyboard.localai.model.FormatoFileModello
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelloPersonalizzato
import io.github.donatogranvilla.poldokeyboard.localai.model.RiconoscitoreFormato
import java.text.NumberFormat

/** Le estensioni tolte dal nome del file per proporre il nome del modello. */
private val ESTENSIONI_MODELLO = listOf(".tar.bz2", ".zip", ".bin", ".ggml")

/**
 * Le finestre dell'import, una per fase: avanzamento, dati del modello fuori catalogo, riepilogo e file eliminati.
 * @param posizione Il numero del file in import, contato da 1.
 * @param totale Quanti file sono stati scelti: se più di uno, le finestre dicono a quale si è arrivati.
 * @param onEliminaFile Elimina i file scaricati dei modelli installati.
 */
@Composable
fun DialoghiImport(
    stato: StatoImport,
    posizione: Int,
    totale: Int,
    onConfermaDati: (ModelloPersonalizzato) -> Unit,
    onAnnulla: () -> Unit,
    onChiudi: () -> Unit,
    onEliminaFile: () -> Unit,
) {
    val coda = if (totale > 1) stringResource(R.string.import_coda, posizione, totale) else null
    when (stato) {
        StatoImport.Nessuno -> Unit
        is StatoImport.Analisi -> Avanzamento(
            titolo = R.string.import_analisi_titolo,
            messaggio = R.string.import_analisi_messaggio,
            coda = coda,
            avanzamento = stato.avanzamento,
            onAnnulla = onAnnulla,
        )
        is StatoImport.Installazione -> Avanzamento(
            titolo = R.string.import_installazione_titolo,
            messaggio = if (stato.archivio) R.string.import_installazione_archivio else R.string.import_installazione_file,
            coda = coda,
            avanzamento = stato.avanzamento,
            onAnnulla = onAnnulla,
        )
        is StatoImport.DatiRichiesti -> DatiModello(
            analisi = stato.analisi,
            coda = coda,
            onConferma = onConfermaDati,
            onAnnulla = onAnnulla,
        )
        is StatoImport.Riepilogo -> Riepilogo(esiti = stato.esiti, onElimina = onEliminaFile, onChiudi = onChiudi)
        is StatoImport.FileEliminati -> Esito(
            titolo = R.string.import_eliminati_titolo,
            messaggio = esitoEliminazione(stato),
            onChiudi = onChiudi,
        )
    }
}

/**
 * L'avanzamento di una fase lunga. Si chiude solo con Annulla: un tocco fuori dalla finestra
 * non deve buttare via minuti di copia.
 * @param avanzamento Frazione completata, o null se non si conosce.
 */
@Composable
private fun Avanzamento(
    @StringRes titolo: Int,
    @StringRes messaggio: Int,
    coda: String?,
    avanzamento: Float?,
    onAnnulla: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(titolo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (coda != null) Text(text = coda, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(messaggio))
                if (avanzamento != null) {
                    LinearProgressIndicator(progress = { avanzamento }, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = NumberFormat.getPercentInstance().format(avanzamento.toDouble()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAnnulla) {
                Text(stringResource(R.string.azione_annulla))
            }
        },
    )
}

/**
 * I dati di un modello fuori catalogo: nome, tipo (solo per gli archivi: un file GGML è sempre Whisper)
 * e lingue. Propone quello che si capisce dal file: nome, tipo dal nome dell'archivio, inglese per i modelli .en.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatiModello(
    analisi: AnalisiImport,
    coda: String?,
    onConferma: (ModelloPersonalizzato) -> Unit,
    onAnnulla: () -> Unit,
) {
    val context = LocalContext.current
    val ggml = analisi.formato == FormatoFileModello.GGML_WHISPER
    val soloInglese = analisi.intestazioneGgml?.multilingue == false
    var nome by remember(analisi) { mutableStateOf(nomeProposto(analisi.nomeFile)) }
    var architettura by remember(analisi) {
        mutableStateOf(
            if (ggml) {
                ArchitetturaModello.GGML
            } else {
                RiconoscitoreFormato.architetturaProbabile(analisi.nomeFile) ?: ArchitetturaModello.NEMO_TRANSDUCER
            },
        )
    }
    var lingue by remember(analisi) { mutableStateOf(if (soloInglese) listOf("en") else emptyList()) }

    AlertDialog(
        onDismissRequest = onAnnulla,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.import_dati_titolo)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (coda != null) Text(text = coda, style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.import_dati_messaggio))
                Text(
                    text = stringResource(
                        R.string.import_file_info,
                        analisi.nomeFile ?: "—",
                        dimensione(context, analisi.dimensione),
                        stringResource(analisi.formato.nomeFormato(analisi.intestazioneGgml?.multilingue)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text(stringResource(R.string.import_nome)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!ggml) {
                    Column {
                        Text(stringResource(R.string.import_tipo), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.import_tipo_spiegazione), style = MaterialTheme.typography.bodySmall)
                        for (tipo in ArchitetturaModello.entries.filter { !it.singoloFile }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = tipo == architettura, onClick = { architettura = tipo }, role = Role.RadioButton)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = tipo == architettura, onClick = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(stringResource(tipo.etichetta()), style = MaterialTheme.typography.bodyMedium)
                                    Text(stringResource(tipo.archiviTipici()), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.import_lingue), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(if (soloInglese) R.string.import_solo_inglese else R.string.import_lingue_spiegazione),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (codice in lingueOrdinate()) {
                            FilterChip(
                                selected = codice in lingue,
                                onClick = { lingue = if (codice in lingue) lingue - codice else lingue + codice },
                                label = { Text(nomeLingua(codice)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConferma(ModelloPersonalizzato(nome = nome.trim(), architettura = architettura, lingue = lingue)) },
                enabled = nome.isNotBlank(),
            ) {
                Text(stringResource(R.string.azione_importa))
            }
        },
        dismissButton = {
            TextButton(onClick = onAnnulla) {
                Text(stringResource(R.string.azione_annulla))
            }
        },
    )
}

/**
 * Com'è andato l'import di ogni file scelto. Se qualche modello si è installato, propone di eliminare i file
 * scaricati, che occuperebbero lo spazio una seconda volta.
 */
@Composable
private fun Riepilogo(esiti: List<EsitoFile>, onElimina: () -> Unit, onChiudi: () -> Unit) {
    val context = LocalContext.current
    val installati = esiti.filter { it.modello != null }
    val titolo = when (installati.size) {
        esiti.size -> R.string.import_completato_titolo
        0 -> R.string.import_errore_titolo
        else -> R.string.import_riepilogo_titolo
    }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(titolo)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (esiti.size == 1) {
                    Text(messaggioEsito(esiti.single(), unico = true))
                } else {
                    for (esito in esiti) {
                        Column {
                            Text(text = esito.nomeFile ?: "—", style = MaterialTheme.typography.titleSmall)
                            Text(text = messaggioEsito(esito, unico = false), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // Ogni import attiva il suo modello: alla fine resta in uso l'ultimo installato
                    installati.lastOrNull()?.modello?.let { ultimo ->
                        Text(stringResource(R.string.import_in_uso_ultimo, ultimo.nome))
                    }
                }
                if (installati.isNotEmpty()) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.import_elimina_proposta,
                            installati.size,
                            installati.size,
                            dimensione(context, installati.sumOf { it.byte }),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            if (installati.isNotEmpty()) {
                TextButton(onClick = onElimina) {
                    Text(pluralStringResource(R.plurals.azione_elimina_file, installati.size))
                }
            } else {
                TextButton(onClick = onChiudi) {
                    Text(stringResource(R.string.azione_ok))
                }
            }
        },
        dismissButton = {
            if (installati.isNotEmpty()) {
                TextButton(onClick = onChiudi) {
                    Text(stringResource(R.string.azione_tieni))
                }
            }
        },
    )
}

/**
 * L'esito di un file: installato, verificato o no, oppure perché non è riuscito.
 * @param unico true se è l'unico file scelto: allora si dice anche che il modello è quello in uso.
 */
@Composable
private fun messaggioEsito(esito: EsitoFile, unico: Boolean): String {
    val modello = esito.modello
    val problema = esito.problema
    return when {
        modello != null && unico -> stringResource(
            if (esito.verificato) R.string.import_completato_verificato else R.string.import_completato_personalizzato,
            modello.nome,
        )
        modello != null -> stringResource(
            if (esito.verificato) R.string.import_file_verificato else R.string.import_file_installato,
            modello.nome,
        )
        problema != null -> spiegazioneProblema(problema)
        else -> stringResource(R.string.errore_import_sconosciuto)
    }
}

/** Quanti file scaricati sono stati eliminati, lo spazio liberato e quanti vanno cancellati a mano. */
@Composable
private fun esitoEliminazione(stato: StatoImport.FileEliminati): String {
    val context = LocalContext.current
    val parti = buildList {
        if (stato.eliminati > 0) {
            add(
                pluralStringResource(
                    R.plurals.import_eliminati,
                    stato.eliminati,
                    stato.eliminati,
                    dimensione(context, stato.byteLiberati),
                ),
            )
        }
        if (stato.nonEliminati > 0) {
            add(pluralStringResource(R.plurals.import_non_eliminati, stato.nonEliminati, stato.nonEliminati))
        }
    }
    return parti.joinToString(" ")
}

/** Una finestra con un esito da leggere e il pulsante OK. */
@Composable
private fun Esito(@StringRes titolo: Int, messaggio: String, onChiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(titolo)) },
        text = { Text(messaggio) },
        confirmButton = {
            TextButton(onClick = onChiudi) {
                Text(stringResource(R.string.azione_ok))
            }
        },
    )
}

/** Cosa è andato storto nell'import, e cosa fare, detto all'utente. */
@Composable
private fun spiegazioneProblema(problema: ProblemaImport): String = when (problema) {
    is ProblemaImport.FormatoSbagliato -> stringResource(problema.formato.spiegazione())
    is ProblemaImport.FormatoIncompatibile -> stringResource(
        R.string.formato_incompatibile,
        stringResource(problema.architettura.fileDaScaricare()),
    )
    is ProblemaImport.FileMancante -> stringResource(R.string.file_mancante, problema.nomeFile)
    is ProblemaImport.Altro -> problema.messaggio ?: stringResource(R.string.errore_import_sconosciuto)
}

/** Il nome del file senza l'estensione, da proporre come nome del modello. */
private fun nomeProposto(nomeFile: String?): String {
    val nome = nomeFile ?: return ""
    val estensione = ESTENSIONI_MODELLO.firstOrNull { nome.endsWith(it, ignoreCase = true) } ?: return nome
    return nome.dropLast(estensione.length)
}
