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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import io.github.donatogranvilla.poldokeyboard.R
import org.florisboard.lib.localai.engine.ArchitetturaModello
import org.florisboard.lib.localai.model.AnalisiImport
import org.florisboard.lib.localai.model.FormatoFileModello
import org.florisboard.lib.localai.model.ModelloPersonalizzato
import org.florisboard.lib.localai.model.RiconoscitoreFormato
import java.text.NumberFormat

/** Le estensioni tolte dal nome del file per proporre il nome del modello. */
private val ESTENSIONI_MODELLO = listOf(".tar.bz2", ".zip", ".bin", ".ggml")

/**
 * Le finestre dell'import, una per fase: avanzamento, dati del modello fuori catalogo, esito.
 */
@Composable
fun DialoghiImport(
    stato: StatoImport,
    onConfermaDati: (ModelloPersonalizzato) -> Unit,
    onAnnulla: () -> Unit,
    onChiudi: () -> Unit,
) {
    when (stato) {
        StatoImport.Nessuno -> Unit
        is StatoImport.Analisi -> Avanzamento(
            titolo = R.string.import_analisi_titolo,
            messaggio = R.string.import_analisi_messaggio,
            avanzamento = stato.avanzamento,
            onAnnulla = onAnnulla,
        )
        is StatoImport.Installazione -> Avanzamento(
            titolo = R.string.import_installazione_titolo,
            messaggio = if (stato.archivio) R.string.import_installazione_archivio else R.string.import_installazione_file,
            avanzamento = stato.avanzamento,
            onAnnulla = onAnnulla,
        )
        is StatoImport.DatiRichiesti -> DatiModello(analisi = stato.analisi, onConferma = onConfermaDati, onAnnulla = onAnnulla)
        is StatoImport.Completato -> Esito(
            titolo = R.string.import_completato_titolo,
            messaggio = stringResource(
                if (stato.verificato) R.string.import_completato_verificato else R.string.import_completato_personalizzato,
                stato.modello.nome,
            ),
            onChiudi = onChiudi,
        )
        is StatoImport.Errore -> Esito(
            titolo = R.string.import_errore_titolo,
            messaggio = spiegazioneProblema(stato.problema),
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
private fun Avanzamento(@StringRes titolo: Int, @StringRes messaggio: Int, avanzamento: Float?, onAnnulla: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(titolo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
private fun DatiModello(analisi: AnalisiImport, onConferma: (ModelloPersonalizzato) -> Unit, onAnnulla: () -> Unit) {
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

/** La finestra con l'esito dell'import, riuscito o no. */
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
