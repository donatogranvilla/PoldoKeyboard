package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController
import dev.patrickgold.florisboard.ime.keyboard3.interaction.LocalInteractionController
import dev.patrickgold.florisboard.ime.keyboard3.interaction.showShortToast
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.voice.archivio.ArchivioTrascrizioni
import dev.patrickgold.florisboard.ime.voice.archivio.StatoTrascrizione
import dev.patrickgold.florisboard.ime.voice.archivio.Trascrizione
import java.time.Instant
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.systemService
import org.florisboard.lib.compose.LocalLocalizedDateTimeFormatter
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggButton
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText
import org.k3lp.lib.text.asK3String

/** Larghezza della conferma di pulizia, la stessa degli appunti. */
private val LarghezzaConferma = 240.dp

/** Oltre questa lunghezza l'anteprima si accorcia; il tocco inserisce comunque tutto il testo. */
private const val LUNGHEZZA_ANTEPRIMA = 280

/**
 * Scheda Trascrizioni della tastiera: le ultime trascrizioni dei vocali condivisi, con il loro stato.
 * Il tocco inserisce il testo nel campo, la pressione lunga lo copia. Usa gli stili degli appunti,
 * così segue il tema della tastiera senza nuovi elementi nei fogli di stile.
 */
@Composable
fun VoiceTranscriptionLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imeController = LocalImeController.current
    val interactionController = LocalInteractionController.current
    val clipboardManager by context.clipboardManager()
    val archivio = remember { ArchivioTrascrizioni.istanza(context) }
    val flussoRecenti = remember { archivio.osservaRecenti() }
    val keyguardManager = remember { context.systemService(AndroidKeyguardManager::class) }

    // Come per gli appunti, a telefono bloccato i testi dei vocali restano nascosti
    val telefonoBloccato = keyguardManager.let { it.isDeviceLocked || it.isKeyguardLocked }
    // null finché il database non risponde, per non far lampeggiare la vista vuota
    val trascrizioni by flussoRecenti.collectAsState(initial = null)
    var confermaPulizia by remember { mutableStateOf(false) }
    val messaggioCopiata = stringRes(R.string.localai_transcription_copied)

    // Quello che la scheda mostra è visto: la tastiera non la riaprirà da sola per gli stessi aggiornamenti
    LaunchedEffect(trascrizioni, telefonoBloccato) {
        if (!telefonoBloccato && trascrizioni.orEmpty().any { !it.vista }) {
            archivio.segnaTutteViste()
        }
    }

    // Vocali in coda ma servizio fermo, per esempio chiuso da Android: la scheda lo rimette in moto
    val ciSonoVocaliInCoda = trascrizioni.orEmpty().any { it.stato == StatoTrascrizione.IN_CODA }
    LaunchedEffect(ciSonoVocaliInCoda) {
        if (ciSonoVocaliInCoda) avviaServizio(context)
    }

    /**
     * Torna alla tastiera delle lettere.
     */
    fun tornaAllaTastiera() {
        imeController.updateStateBlocking {
            state = state.copy(flags = state.flags.withImeUiMode(ImeUiMode.TEXT))
        }
    }

    /**
     * Inserisce il testo nel campo della chat e torna alla tastiera, pronta per l'invio.
     */
    fun inserisci(testo: String) {
        imeController.updateStateBlocking {
            emitText(testo.asK3String())
            state = state.copy(flags = state.flags.withImeUiMode(ImeUiMode.TEXT))
        }
    }

    /**
     * Copia il testo con gli appunti di FlorisBoard, che lo aggiungono anche al loro storico.
     */
    fun copia(testo: String) {
        clipboardManager.addNewPlaintext(testo)
        scope.launch { interactionController.showShortToast(messaggioCopiata) }
    }

    /**
     * Intestazione con il ritorno alla tastiera, la gestione dei modelli e la pulizia dello storico.
     */
    @Composable
    fun Intestazione() {
        SnyggRow(FlorisImeUi.ClipboardHeader.elementName,
            modifier = Modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dimensioneTasto = Modifier
                .sizeIn(maxHeight = FlorisImeSizing.smartbarHeight)
                .aspectRatio(1f)
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { tornaAllaTastiera() },
                modifier = dimensioneTasto,
            ) {
                SnyggIcon(imageVector = Icons.AutoMirrored.Filled.ArrowBack)
            }
            SnyggText(
                elementName = FlorisImeUi.ClipboardHeaderText.elementName,
                modifier = Modifier.weight(1f),
                text = stringRes(R.string.localai_transcriber_title),
            )
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { GestioneModelliActivity.avvia(context) },
                modifier = dimensioneTasto,
            ) {
                SnyggIcon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = stringRes(R.string.localai_transcription_models),
                )
            }
            SnyggIconButton(
                elementName = FlorisImeUi.ClipboardHeaderButton.elementName,
                onClick = { confermaPulizia = true },
                modifier = dimensioneTasto,
                enabled = !telefonoBloccato && !trascrizioni.isNullOrEmpty() && !confermaPulizia,
            ) {
                SnyggIcon(imageVector = Icons.Default.DeleteSweep)
            }
        }
    }

    /**
     * Una trascrizione: data, stato e testo, con i pulsanti per riprovare un errore o eliminarla.
     */
    @Composable
    fun VoceTrascrizione(trascrizione: Trascrizione) {
        val testo = trascrizione.testo.orEmpty()
        val pronta = trascrizione.stato == StatoTrascrizione.COMPLETATA && testo.isNotBlank()
        SnyggBox(
            elementName = FlorisImeUi.ClipboardItem.elementName,
            modifier = Modifier.fillMaxWidth(),
            clickAndSemanticsModifier = Modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                enabled = pronta && !confermaPulizia,
                onClick = { inserisci(testo) },
                onLongClick = { copia(testo) },
            ),
        ) {
            SnyggColumn(modifier = Modifier.fillMaxWidth()) {
                SnyggRow(
                    elementName = FlorisImeUi.ClipboardItemDescription.elementName,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SnyggIcon(imageVector = iconaStato(trascrizione.stato))
                    SnyggText(
                        modifier = Modifier.weight(1f),
                        text = LocalLocalizedDateTimeFormatter.current.format(Instant.ofEpochMilli(trascrizione.creataIl)),
                    )
                    if (trascrizione.stato == StatoTrascrizione.ERRORE) {
                        SnyggIconButton(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            onClick = {
                                scope.launch {
                                    archivio.rimettiInCoda(trascrizione.id)
                                    avviaServizio(context)
                                }
                            },
                            enabled = !confermaPulizia,
                        ) {
                            SnyggIcon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringRes(R.string.localai_transcription_retry),
                            )
                        }
                    }
                    SnyggIconButton(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        onClick = { scope.launch { archivio.elimina(trascrizione) } },
                        enabled = !confermaPulizia,
                    ) {
                        SnyggIcon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringRes(R.string.localai_transcription_delete),
                        )
                    }
                }
                SnyggText(
                    modifier = Modifier.fillMaxWidth(),
                    text = testoVoce(trascrizione),
                )
            }
        }
    }

    /**
     * Chiede conferma prima di eliminare tutte le trascrizioni, come la pulizia degli appunti.
     */
    @Composable
    fun ConfermaPulizia() {
        SnyggRow(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { confermaPulizia = false }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            SnyggColumn(
                elementName = FlorisImeUi.ClipboardClearAllDialog.elementName,
                modifier = Modifier
                    .width(LarghezzaConferma)
                    .pointerInput(Unit) {
                        // Un tocco sul riquadro non deve chiuderlo
                        detectTapGestures { }
                    },
            ) {
                SnyggText(
                    elementName = FlorisImeUi.ClipboardClearAllDialogMessage.elementName,
                    text = stringRes(R.string.localai_transcriptions_clear_all_message),
                )
                SnyggRow(FlorisImeUi.ClipboardClearAllDialogButtons.elementName) {
                    Spacer(modifier = Modifier.weight(1f))
                    SnyggButton(
                        elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                        attributes = mapOf("action" to "no"),
                        onClick = { confermaPulizia = false },
                    ) {
                        SnyggText(text = stringRes(R.string.action__no))
                    }
                    val messaggioPulizia = stringRes(R.string.localai_transcriptions_cleared)
                    SnyggButton(
                        elementName = FlorisImeUi.ClipboardClearAllDialogButton.elementName,
                        attributes = mapOf("action" to "yes"),
                        onClick = {
                            scope.launch {
                                archivio.eliminaTutte()
                                interactionController.showShortToast(messaggioPulizia)
                            }
                            confermaPulizia = false
                        },
                    ) {
                        SnyggText(text = stringRes(R.string.action__yes))
                    }
                }
            }
        }
    }

    /**
     * Un messaggio centrato nella scheda, per lo storico vuoto o il telefono bloccato.
     */
    @Composable
    fun Avviso(titolo: String, messaggio: String) {
        SnyggColumn(FlorisImeUi.ClipboardContent.elementName,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(text = titolo)
            SnyggText(text = messaggio)
        }
    }

    SnyggColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.imeUiHeight()),
    ) {
        Intestazione()
        val elenco = trascrizioni
        when {
            telefonoBloccato -> Avviso(
                titolo = stringRes(R.string.localai_transcriptions_locked_title),
                messaggio = stringRes(R.string.localai_transcriptions_locked_message),
            )
            // Il database risponde in un istante: intanto la scheda resta vuota
            elenco == null -> Unit
            elenco.isEmpty() -> Avviso(
                titolo = stringRes(R.string.localai_transcriptions_empty_title),
                messaggio = stringRes(R.string.localai_transcriptions_empty_message),
            )
            else -> SnyggBox(FlorisImeUi.ClipboardContent.elementName,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (confermaPulizia) 0.12f else 1f),
                ) {
                    items(elenco, key = { it.id }) { trascrizione ->
                        VoceTrascrizione(trascrizione)
                    }
                }
                if (confermaPulizia) {
                    ConfermaPulizia()
                }
            }
        }
    }
}

/**
 * L'icona che riassume lo stato di una trascrizione.
 */
private fun iconaStato(stato: StatoTrascrizione): ImageVector = when (stato) {
    StatoTrascrizione.IN_CODA -> Icons.Default.HourglassEmpty
    StatoTrascrizione.IN_CORSO -> Icons.Default.Autorenew
    StatoTrascrizione.COMPLETATA -> Icons.Default.Subtitles
    StatoTrascrizione.ERRORE -> Icons.Default.ErrorOutline
}

/**
 * Il testo da mostrare per una trascrizione: l'anteprima se è pronta, altrimenti lo stato.
 */
@Composable
private fun testoVoce(trascrizione: Trascrizione): String = when (trascrizione.stato) {
    StatoTrascrizione.IN_CODA -> stringRes(R.string.localai_transcription_queued)
    StatoTrascrizione.IN_CORSO -> stringRes(
        R.string.localai_transcription_running,
        "v" to "${(trascrizione.avanzamento * 100).toInt()}%",
    )
    StatoTrascrizione.COMPLETATA -> trascrizione.testo.orEmpty().let { testo ->
        if (testo.length > LUNGHEZZA_ANTEPRIMA) testo.take(LUNGHEZZA_ANTEPRIMA).trimEnd() + "…" else testo
    }
    StatoTrascrizione.ERRORE -> stringRes(
        R.string.localai_transcription_failed,
        "v" to trascrizione.errore.orEmpty(),
    )
}

/**
 * Avvia il servizio dalla tastiera. Se Android non lo consente, il vocale aspetta la prossima condivisione:
 * meglio un vocale in attesa che una tastiera che si chiude.
 */
private fun avviaServizio(context: Context) {
    runCatching { ServizioTrascrizione.avvia(context) }
}
