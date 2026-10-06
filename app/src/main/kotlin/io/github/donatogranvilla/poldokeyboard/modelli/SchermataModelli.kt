package io.github.donatogranvilla.poldokeyboard.modelli

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.donatogranvilla.poldokeyboard.R
import org.florisboard.lib.localai.model.ModelInfo

/** Ricerca dei modelli Whisper in formato ggml su Hugging Face. */
private const val URL_RICERCA_HUGGING_FACE = "https://huggingface.co/models?search=whisper%20ggml"

/** Le release con i modelli di riconoscimento vocale di sherpa-onnx. */
private const val URL_MODELLI_SHERPA = "https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models"

/**
 * La gestione dei modelli: guida alla scelta, lingua dei vocali, modelli sul telefono e da scaricare
 * filtrabili per lingua, import di un file scaricato e test delle prestazioni.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataModelli(viewModel: ModelliViewModel, onIndietro: () -> Unit) {
    val context = LocalContext.current
    var mostraTest by rememberSaveable { mutableStateOf(false) }
    var sceltaLingua by rememberSaveable { mutableStateOf(false) }
    var daEliminare by remember { mutableStateOf<ModelloSulTelefono?>(null) }
    val selettore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importa(uri)
    }
    val installati = viewModel.installatiFiltrati
    val daScaricare = viewModel.daScaricare
    val profilo = viewModel.profilo

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.modelli)) },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.azione_indietro))
                    }
                },
                actions = {
                    IconButton(onClick = { mostraTest = true }) {
                        Icon(Icons.Default.Speed, contentDescription = stringResource(R.string.modelli_test_prestazioni))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.modelli_importa)) },
                icon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                onClick = { selettore.launch(arrayOf("*/*")) },
            )
        },
    ) { margini ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(margini),
            // Lo spazio in fondo evita che il pulsante Importa copra l'ultima scheda
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { GuidaModelli(onApri = { indirizzo -> apriNelBrowser(context, indirizzo) }) }
            item { SchedaLinguaVocali(lingua = viewModel.linguaVocali, onCambia = { sceltaLingua = true }) }
            item { FiltroLingue(selezionata = viewModel.filtroLingua, onSeleziona = viewModel::filtra) }

            item { TitoloSezione(stringResource(R.string.modelli_sezione_installati)) }
            if (installati.isEmpty()) {
                item {
                    val messaggio = if (viewModel.filtroLingua == null) {
                        R.string.modelli_nessuno_installato
                    } else {
                        R.string.modelli_nessuno_per_lingua
                    }
                    TestoNota(stringResource(messaggio))
                }
            }
            items(installati, key = { "installato-${it.modello.id}" }) { voce ->
                SchedaModelloInstallato(
                    voce = voce,
                    attivo = voce.modello.id == viewModel.idAttivo,
                    onUsa = { viewModel.attiva(voce.modello.id) },
                    onElimina = { daEliminare = voce },
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TitoloSezione(stringResource(R.string.modelli_sezione_da_scaricare))
                    TestoNota(stringResource(R.string.modelli_da_scaricare_spiegazione))
                    TestoNota(stringResource(R.string.modelli_dispositivo, (profilo.ramTotaleMb + 512) / 1024, profilo.numCoreCpu))
                }
            }
            if (daScaricare.isEmpty()) {
                item { TestoNota(stringResource(R.string.modelli_tutti_scaricati)) }
            }
            items(daScaricare, key = { "catalogo-${it.id}" }) { modello ->
                SchedaModelloCatalogo(
                    modello = modello,
                    consigliato = modello.id == profilo.idModelloConsigliato,
                    onScarica = {
                        if (apriNelBrowser(context, modello.downloadUrl)) {
                            Toast.makeText(context, R.string.download_avviato, Toast.LENGTH_LONG).show()
                        }
                    },
                    onApri = { indirizzo -> apriNelBrowser(context, indirizzo) },
                )
            }
        }
    }

    if (sceltaLingua) {
        SceltaLinguaVocali(
            attuale = viewModel.linguaVocali,
            onScelta = { lingua ->
                viewModel.impostaLinguaVocali(lingua)
                sceltaLingua = false
            },
            onChiudi = { sceltaLingua = false },
        )
    }
    daEliminare?.let { voce ->
        ConfermaEliminazione(
            voce = voce,
            onConferma = {
                viewModel.elimina(voce.modello.id)
                daEliminare = null
            },
            onAnnulla = { daEliminare = null },
        )
    }
    DialoghiImport(
        stato = viewModel.statoImport,
        onConfermaDati = viewModel::confermaDati,
        onAnnulla = viewModel::annullaImport,
        onChiudi = viewModel::chiudiEsito,
    )
    if (mostraTest) {
        BenchmarkDialog(localAiManager = viewModel.motoreTest, onDismiss = { mostraTest = false })
    }
}

/**
 * La guida alla scelta e all'import di un modello, chiusa finché l'utente non la apre.
 * @param onApri Apre nel browser i siti dove cercare altri modelli.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidaModelli(onApri: (String) -> Unit) {
    var aperta by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { aperta = !aperta },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.guida_titolo),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(if (aperta) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (aperta) {
                Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ParagrafoGuida(R.string.guida_come_funziona_titolo, R.string.guida_come_funziona)
                    ParagrafoGuida(R.string.guida_specializzato_titolo, R.string.guida_specializzato)
                    ParagrafoGuida(R.string.guida_peso_titolo, R.string.guida_peso)
                    ParagrafoGuida(R.string.guida_whisper_titolo, R.string.guida_whisper)
                    ParagrafoGuida(R.string.guida_sherpa_titolo, R.string.guida_sherpa)
                    ParagrafoGuida(R.string.guida_formati_titolo, R.string.guida_formati)
                    ParagrafoGuida(R.string.guida_marocchino_titolo, R.string.guida_marocchino)
                    ParagrafoGuida(R.string.guida_cercare_titolo, R.string.guida_cercare)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onApri(URL_RICERCA_HUGGING_FACE) }) {
                            Text(stringResource(R.string.guida_apri_hugging_face))
                        }
                        OutlinedButton(onClick = { onApri(URL_MODELLI_SHERPA) }) {
                            Text(stringResource(R.string.guida_apri_sherpa))
                        }
                    }
                    ParagrafoGuida(R.string.guida_licenze_titolo, R.string.guida_licenze)
                }
            }
        }
    }
}

/** Un paragrafo della guida, con il suo titolo. */
@Composable
private fun ParagrafoGuida(@StringRes titolo: Int, @StringRes testo: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = stringResource(titolo), style = MaterialTheme.typography.titleSmall)
        Text(text = stringResource(testo), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * La lingua imposta ai modelli multilingue, con il pulsante per cambiarla.
 */
@Composable
private fun SchedaLinguaVocali(lingua: String?, onCambia: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.lingua_vocali_titolo), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (lingua == null) stringResource(R.string.lingua_vocali_automatica) else nomeLingua(lingua),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedButton(onClick = onCambia) {
                    Text(stringResource(R.string.lingua_vocali_cambia))
                }
            }
            Text(text = stringResource(R.string.lingua_vocali_spiegazione), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * I filtri per lingua, scorrevoli in orizzontale: "Tutte" e le lingue dei vocali.
 */
@Composable
private fun FiltroLingue(selezionata: String?, onSeleziona: (String?) -> Unit) {
    val lingue = lingueOrdinate()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.modelli_filtro_titolo), style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = selezionata == null,
                    onClick = { onSeleziona(null) },
                    label = { Text(stringResource(R.string.modelli_filtro_tutte)) },
                )
            }
            items(lingue) { codice ->
                FilterChip(
                    selected = selezionata == codice,
                    onClick = { onSeleziona(codice) },
                    label = { Text(nomeLingua(codice)) },
                )
            }
        }
    }
}

/**
 * Un modello installato: lingue, tipo, spazio occupato e i pulsanti per usarlo o eliminarlo.
 */
@Composable
private fun SchedaModelloInstallato(
    voce: ModelloSulTelefono,
    attivo: Boolean,
    onUsa: () -> Unit,
    onElimina: () -> Unit,
) {
    val context = LocalContext.current
    val modello = voce.modello
    val colori = if (attivo) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = colori) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = modello.nome, style = MaterialTheme.typography.titleMedium)
            Etichette(
                buildList {
                    if (attivo) add(Etichetta.IN_USO)
                    if (modello.lingue.size == 1) add(Etichetta.SPECIALIZZATO)
                    if (voce.catalogo == null) add(Etichetta.IMPORTATO)
                    if (voce.catalogo?.nonCommerciale == true) add(Etichetta.NON_COMMERCIALE)
                },
            )
            Dettaglio(stringResource(R.string.modello_lingue, descrizioneLingue(modello.lingue)))
            Dettaglio(stringResource(R.string.modello_tipo, stringResource(modello.architettura.etichetta())))
            Dettaglio(stringResource(R.string.modello_occupa, dimensione(context, voce.byteOccupati)))
            if (voce.catalogo == null) Dettaglio(stringResource(R.string.modello_non_verificato))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onElimina) {
                    Text(stringResource(R.string.azione_elimina))
                }
                if (!attivo) {
                    Button(onClick = onUsa) {
                        Text(stringResource(R.string.azione_usa))
                    }
                }
            }
        }
    }
}

/**
 * Un modello del catalogo da scaricare, con quello che serve per sceglierlo: lingue, qualità, peso,
 * file da scaricare, tipo e licenza.
 * @param onApri Apre nel browser la pagina del modello.
 */
@Composable
private fun SchedaModelloCatalogo(
    modello: ModelInfo,
    consigliato: Boolean,
    onScarica: () -> Unit,
    onApri: (String) -> Unit,
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = modello.name, style = MaterialTheme.typography.titleMedium)
            Etichette(
                buildList {
                    if (consigliato) add(Etichetta.CONSIGLIATO)
                    if (modello.specializzato) add(Etichetta.SPECIALIZZATO)
                    if (modello.nonCommerciale) add(Etichetta.NON_COMMERCIALE)
                },
            )
            Text(text = modello.description, style = MaterialTheme.typography.bodyMedium)
            Dettaglio(stringResource(R.string.modello_lingue, descrizioneLingue(modello.lingue)))
            Dettaglio(stringResource(R.string.modello_qualita, stelle(modello.stelleAccuratezza), stelle(modello.fulminiVelocita)))
            Dettaglio(
                stringResource(
                    R.string.modello_peso,
                    dimensione(context, modello.sizeBytes),
                    dimensioneRam(context, modello.ramConsigliataMb),
                ),
            )
            Dettaglio(stringResource(R.string.modello_tipo, stringResource(modello.architettura.etichetta())))
            Dettaglio(
                stringResource(R.string.modello_file, stringResource(modello.architettura.fileDaScaricare()), modello.quantizzazione),
            )
            Dettaglio(
                stringResource(if (modello.nonCommerciale) R.string.modello_licenza_nc else R.string.modello_licenza, modello.license),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                modello.paginaUrl?.let { pagina ->
                    TextButton(onClick = { onApri(pagina) }) {
                        Text(stringResource(R.string.azione_pagina))
                    }
                }
                FilledTonalButton(onClick = onScarica) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.azione_scarica))
                }
            }
        }
    }
}

/** Le etichette che si possono mostrare accanto al nome di un modello. */
private enum class Etichetta(@StringRes val testo: Int) {
    IN_USO(R.string.modello_in_uso),
    CONSIGLIATO(R.string.modello_consigliato),
    SPECIALIZZATO(R.string.modello_specializzato),
    IMPORTATO(R.string.modello_importato),
    NON_COMMERCIALE(R.string.modello_non_commerciale),
}

/**
 * Le etichette di un modello, che vanno a capo sugli schermi stretti. Ognuna ha i suoi colori.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Etichette(etichette: List<Etichetta>) {
    if (etichette.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (etichetta in etichette) {
            val colori = MaterialTheme.colorScheme
            val (sfondo, coloreTesto) = when (etichetta) {
                Etichetta.IN_USO -> colori.primary to colori.onPrimary
                Etichetta.CONSIGLIATO -> colori.tertiaryContainer to colori.onTertiaryContainer
                Etichetta.SPECIALIZZATO -> colori.secondaryContainer to colori.onSecondaryContainer
                Etichetta.IMPORTATO -> colori.surfaceVariant to colori.onSurfaceVariant
                Etichetta.NON_COMMERCIALE -> colori.errorContainer to colori.onErrorContainer
            }
            Surface(color = sfondo, contentColor = coloreTesto, shape = MaterialTheme.shapes.small) {
                Text(
                    text = stringResource(etichetta.testo),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Una riga di dettaglio di un modello. */
@Composable
private fun Dettaglio(testo: String) {
    Text(text = testo, style = MaterialTheme.typography.bodySmall)
}

/** Il titolo di una sezione dell'elenco. */
@Composable
private fun TitoloSezione(testo: String) {
    Text(
        text = testo,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Una nota sotto un titolo, meno in evidenza del resto. */
@Composable
private fun TestoNota(testo: String) {
    Text(text = testo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * La scelta della lingua dei vocali: automatica o una delle lingue dei vocali.
 */
@Composable
private fun SceltaLinguaVocali(attuale: String?, onScelta: (String?) -> Unit, onChiudi: () -> Unit) {
    val opzioni = listOf<String?>(null) + lingueOrdinate()
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(stringResource(R.string.lingua_vocali_titolo)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (codice in opzioni) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = codice == attuale, onClick = { onScelta(codice) }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = codice == attuale, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (codice == null) {
                                stringResource(R.string.lingua_vocali_automatica_dettaglio)
                            } else {
                                nomeLingua(codice)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChiudi) {
                Text(stringResource(R.string.azione_chiudi))
            }
        },
    )
}

/**
 * Chiede conferma prima di eliminare un modello, che poi andrebbe riscaricato.
 */
@Composable
private fun ConfermaEliminazione(voce: ModelloSulTelefono, onConferma: () -> Unit, onAnnulla: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text(stringResource(R.string.elimina_modello_titolo, voce.modello.nome)) },
        text = { Text(stringResource(R.string.elimina_modello_messaggio, dimensione(context, voce.byteOccupati))) },
        confirmButton = {
            TextButton(onClick = onConferma) {
                Text(stringResource(R.string.azione_elimina))
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
 * Apre un indirizzo nel browser: i modelli si scaricano da lì, perché l'app non usa la rete.
 * @return false se nessuna app sa aprirlo.
 */
private fun apriNelBrowser(context: Context, indirizzo: String): Boolean {
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, indirizzo.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.browser_assente, Toast.LENGTH_LONG).show()
        false
    }
}
