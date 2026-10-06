package io.github.donatogranvilla.poldokeyboard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.donatogranvilla.poldokeyboard.R
import io.github.donatogranvilla.poldokeyboard.archivio.ArchivioTrascrizioni
import io.github.donatogranvilla.poldokeyboard.archivio.StatoTrascrizione
import io.github.donatogranvilla.poldokeyboard.archivio.Trascrizione
import io.github.donatogranvilla.poldokeyboard.comune.Appunti
import io.github.donatogranvilla.poldokeyboard.comune.tastieraAbilitata
import io.github.donatogranvilla.poldokeyboard.modelli.ModelManagementDialog
import io.github.donatogranvilla.poldokeyboard.servizio.ServizioTrascrizione
import kotlinx.coroutines.launch
import org.florisboard.lib.localai.LocalAiManager

/**
 * Schermata principale: le guide per attivare la tastiera e importare un modello, se mancano,
 * e lo storico completo delle trascrizioni. Il tocco su una trascrizione la mostra per intero.
 * @param ripresa Cambia a ogni ritorno nell'app, per ricontrollare la configurazione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataPrincipale(ripresa: Int, apriModelliAllAvvio: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archivio = remember { ArchivioTrascrizioni.istanza(context) }
    val flussoStorico = remember { archivio.osservaTutte() }
    val storico by flussoStorico.collectAsState(initial = emptyList())
    val localAiManager = remember { LocalAiManager(context.applicationContext) }
    var mostraModelli by rememberSaveable { mutableStateOf(apriModelliAllAvvio) }
    var aperta by remember { mutableStateOf<Trascrizione?>(null) }
    val tastieraAttiva = remember(ripresa) { tastieraAbilitata(context) }
    val modelloPresente = remember(ripresa, mostraModelli) {
        localAiManager.modelManager.getActiveInstalledModel() != null
    }

    RichiestaPermessoNotifiche()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { mostraModelli = true }) {
                        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.modelli))
                    }
                },
            )
        },
    ) { margini ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(margini),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!tastieraAttiva) {
                item {
                    SchedaGuida(
                        titolo = stringResource(R.string.guida_tastiera_titolo),
                        messaggio = stringResource(R.string.guida_tastiera_messaggio),
                        pulsante = stringResource(R.string.guida_tastiera_pulsante),
                        onClick = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                    )
                }
            }
            if (!modelloPresente) {
                item {
                    SchedaGuida(
                        titolo = stringResource(R.string.guida_modello_titolo),
                        messaggio = stringResource(R.string.guida_modello_messaggio),
                        pulsante = stringResource(R.string.guida_modello_pulsante),
                        onClick = { mostraModelli = true },
                    )
                }
            }
            if (storico.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.storico_vuoto),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(storico, key = { it.id }) { trascrizione ->
                VoceStorico(
                    trascrizione = trascrizione,
                    onApri = { aperta = trascrizione },
                    onElimina = { scope.launch { archivio.elimina(trascrizione) } },
                    onRiprova = {
                        scope.launch {
                            archivio.rimettiInCoda(trascrizione.id)
                            ServizioTrascrizione.avvia(context)
                        }
                    },
                )
            }
        }
    }

    if (mostraModelli) {
        ModelManagementDialog(localAiManager = localAiManager, onDismiss = { mostraModelli = false })
    }
    aperta?.let { trascrizione ->
        TestoCompleto(
            trascrizione = trascrizione,
            onCopia = { testo -> Appunti.copia(context, testo) },
            onChiudi = { aperta = null },
        )
    }
}

/**
 * Una trascrizione dello storico: data, testo o stato, e i pulsanti per riprovarla o eliminarla.
 */
@Composable
private fun VoceStorico(
    trascrizione: Trascrizione,
    onApri: () -> Unit,
    onElimina: () -> Unit,
    onRiprova: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApri),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dataOra(trascrizione.creataIl),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (trascrizione.stato == StatoTrascrizione.ERRORE) {
                    IconButton(onClick = onRiprova) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.azione_riprova))
                    }
                }
                IconButton(onClick = onElimina) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.azione_elimina))
                }
            }
            Text(
                text = testoTrascrizione(trascrizione),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Il testo completo di una trascrizione, da leggere e copiare.
 */
@Composable
fun TestoCompleto(
    trascrizione: Trascrizione,
    onCopia: (String) -> Unit,
    onChiudi: () -> Unit,
) {
    val testo = testoTrascrizione(trascrizione)
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(dataOra(trascrizione.creataIl)) },
        text = { Text(text = testo, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton(
                onClick = {
                    onCopia(testo)
                    onChiudi()
                },
                enabled = trascrizione.stato == StatoTrascrizione.COMPLETATA,
            ) {
                Text(stringResource(R.string.azione_copia))
            }
        },
        dismissButton = {
            TextButton(onClick = onChiudi) {
                Text(stringResource(R.string.azione_chiudi))
            }
        },
    )
}

/**
 * Una scheda che guida un passo della configurazione, con il pulsante per farlo.
 */
@Composable
private fun SchedaGuida(titolo: String, messaggio: String, pulsante: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = titolo, style = MaterialTheme.typography.titleMedium)
            Text(text = messaggio, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) {
                Text(pulsante)
            }
        }
    }
}

/**
 * Chiede il permesso delle notifiche da Android 13: senza, la notifica con il testo pronto non arriva.
 */
@Composable
private fun RichiestaPermessoNotifiche() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val concesso = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!concesso) richiesta.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
