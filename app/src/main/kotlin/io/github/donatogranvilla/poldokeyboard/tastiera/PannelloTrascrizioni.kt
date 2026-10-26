package io.github.donatogranvilla.poldokeyboard.tastiera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donatogranvilla.poldokeyboard.R
import io.github.donatogranvilla.poldokeyboard.archivio.ArchivioTrascrizioni
import io.github.donatogranvilla.poldokeyboard.archivio.StatoTrascrizione
import io.github.donatogranvilla.poldokeyboard.archivio.Trascrizione
import io.github.donatogranvilla.poldokeyboard.servizio.ServizioTrascrizione
import io.github.donatogranvilla.poldokeyboard.ui.ElencoModelliPerRitrascrivere
import io.github.donatogranvilla.poldokeyboard.ui.dataOra
import io.github.donatogranvilla.poldokeyboard.ui.rememberModelliDisponibili
import io.github.donatogranvilla.poldokeyboard.ui.testoTrascrizione
import kotlinx.coroutines.launch
import io.github.donatogranvilla.poldokeyboard.localai.engine.ModelloInstallato

/** Altezza del pannello rispetto allo schermo: più di una tastiera, per leggere comodamente. */
private const val QUOTA_ALTEZZA_SCHERMO = 0.45f

/**
 * Pannello della tastiera di trascrizione: le ultime trascrizioni in anteprima; il tocco ne apre il testo
 * intero, da inserire nel campo o copiare.
 */
@Composable
fun PannelloTrascrizioni(
    onInserisci: (String) -> Unit,
    onCopia: (String) -> Unit,
    onTornaAllaTastiera: () -> Unit,
    onApriModelli: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archivio = remember { ArchivioTrascrizioni.istanza(context) }
    val flussoRecenti = remember { archivio.osservaRecenti() }
    val trascrizioni by flussoRecenti.collectAsState(initial = emptyList())
    // Si ricorda l'id, non la riga: così il testo aperto segue gli aggiornamenti del servizio
    var idAperta by remember { mutableStateOf<Long?>(null) }
    var sceltaModello by remember { mutableStateOf(false) }
    val aperta = trascrizioni.find { it.id == idAperta }
    val altezza = (LocalConfiguration.current.screenHeightDp * QUOTA_ALTEZZA_SCHERMO).dp

    // Vocali in coda ma servizio fermo, per esempio chiuso da Android: il pannello lo rimette in moto
    val ciSonoVocaliInCoda = trascrizioni.any { it.stato == StatoTrascrizione.IN_CODA }
    LaunchedEffect(ciSonoVocaliInCoda) {
        if (ciSonoVocaliInCoda) {
            // Se Android non consente l'avvio da qui, il vocale aspetta: meglio che chiudere la tastiera
            runCatching { ServizioTrascrizione.avvia(context) }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(altezza),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            Intestazione(onApriModelli = onApriModelli, onTornaAllaTastiera = onTornaAllaTastiera)
            when {
                // In una tastiera le finestre di dialogo non si aprono: la scelta del modello prende il posto del testo
                aperta != null && sceltaModello -> RitrascriviInTastiera(
                    trascrizione = aperta,
                    onScelta = { modello ->
                        sceltaModello = false
                        scope.launch {
                            archivio.ritrascrivi(aperta.id, modello.id)
                            // Se Android non consente l'avvio da qui, ci pensa il controllo dei vocali in coda
                            runCatching { ServizioTrascrizione.avvia(context) }
                        }
                    },
                    onApriModelli = onApriModelli,
                    onAnnulla = { sceltaModello = false },
                )
                aperta != null -> TestoIntero(
                    trascrizione = aperta,
                    onChiudi = { idAperta = null },
                    onRitrascrivi = { sceltaModello = true },
                    onInserisci = onInserisci,
                    onCopia = onCopia,
                )
                trascrizioni.isEmpty() -> Text(
                    text = stringResource(R.string.tastiera_vuota),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trascrizioni, key = { it.id }) { trascrizione ->
                        Anteprima(
                            trascrizione = trascrizione,
                            onClick = {
                                idAperta = trascrizione.id
                                sceltaModello = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Titolo del pannello con i pulsanti per i modelli e per tornare alla tastiera di prima.
 */
@Composable
private fun Intestazione(onApriModelli: () -> Unit, onTornaAllaTastiera: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tastiera_titolo),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onApriModelli) {
            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.modelli))
        }
        IconButton(onClick = onTornaAllaTastiera) {
            Icon(Icons.Default.Keyboard, contentDescription = stringResource(R.string.tastiera_torna))
        }
    }
}

/**
 * Una trascrizione in anteprima: data e prime righe del testo, o il suo stato.
 */
@Composable
private fun Anteprima(trascrizione: Trascrizione, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = dataOra(trascrizione.creataIl),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = testoTrascrizione(trascrizione),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * La scelta del modello con cui ritrascrivere un vocale, al posto del testo. Senza modelli
 * porta alla loro gestione.
 */
@Composable
private fun RitrascriviInTastiera(
    trascrizione: Trascrizione,
    onScelta: (ModelloInstallato) -> Unit,
    onApriModelli: () -> Unit,
    onAnnulla: () -> Unit,
) {
    val modelli = rememberModelliDisponibili(trascrizione.id)
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(text = stringResource(R.string.ritrascrivi_titolo), style = MaterialTheme.typography.titleSmall)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                modelli == null -> Unit
                modelli.installati.isEmpty() -> Text(
                    text = stringResource(R.string.ritrascrivi_nessun_modello),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                else -> {
                    Text(
                        text = stringResource(R.string.ritrascrivi_spiegazione),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ElencoModelliPerRitrascrivere(modelli = modelli, idUsato = trascrizione.idModello, onScelta = onScelta)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onAnnulla) {
                Text(stringResource(R.string.azione_annulla))
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onApriModelli) {
                Text(stringResource(R.string.guida_modello_pulsante))
            }
        }
    }
}

/**
 * Il testo intero di una trascrizione, scorrevole, con i pulsanti per inserirlo o copiarlo
 * e per ritrascriverlo con un altro modello.
 */
@Composable
private fun TestoIntero(
    trascrizione: Trascrizione,
    onChiudi: () -> Unit,
    onRitrascrivi: () -> Unit,
    onInserisci: (String) -> Unit,
    onCopia: (String) -> Unit,
) {
    val testo = testoTrascrizione(trascrizione)
    val pronta = trascrizione.stato == StatoTrascrizione.COMPLETATA && testo.isNotBlank()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dataOra(trascrizione.creataIl),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // Mentre il servizio la elabora non si può rimettere in coda
            IconButton(onClick = onRitrascrivi, enabled = trascrizione.stato != StatoTrascrizione.IN_CORSO) {
                Icon(Icons.Default.Autorenew, contentDescription = stringResource(R.string.azione_ritrascrivi))
            }
        }
        Text(
            text = testo,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onChiudi) {
                Text(stringResource(R.string.azione_chiudi))
            }
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { onCopia(testo) }, enabled = pronta) {
                Text(stringResource(R.string.azione_copia))
            }
            Button(onClick = { onInserisci(testo) }, enabled = pronta) {
                Text(stringResource(R.string.azione_inserisci))
            }
        }
    }
}
