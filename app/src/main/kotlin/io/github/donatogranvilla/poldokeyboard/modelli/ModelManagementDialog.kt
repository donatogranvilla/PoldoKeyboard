package io.github.donatogranvilla.poldokeyboard.modelli

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.florisboard.lib.localai.LocalAiManager
import org.florisboard.lib.localai.model.ModelCatalog
import org.florisboard.lib.localai.model.ModelInfo
import org.florisboard.lib.localai.model.ModelloNonRiconosciutoException

@Composable
fun ModelManagementDialog(
    localAiManager: LocalAiManager,
    onDismiss: () -> Unit,
    onModelSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelManager = localAiManager.modelManager
    val hwProfile = remember { modelManager.getHardwareProfile() }

    // Spento di norma: i file fuori catalogo, come i modelli specializzati in italiano, si accettano su richiesta
    var consentiFuoriCatalogo by remember { mutableStateOf(false) }

    var activeModelId by remember { mutableStateOf(modelManager.getActiveModelId()) }
    var avanzamentoImport by remember { mutableStateOf<Float?>(null) }
    var showBenchmarkDialog by remember { mutableStateOf(false) }

    // Selettore di sistema per importare il file di un modello scaricato dal browser
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                avanzamentoImport = 0f
                val result = modelManager.importaModello(uri, consentiFuoriCatalogo = consentiFuoriCatalogo) { frazione ->
                    avanzamentoImport = frazione
                }
                avanzamentoImport = null
                result.onSuccess { importato ->
                    // importaModello l'ha già reso attivo; lo caricherà il servizio di trascrizione, nel suo processo
                    activeModelId = importato.id
                    onModelSelected(importato.id)
                    Toast.makeText(context, "${importato.nome} importato e attivato.", Toast.LENGTH_SHORT).show()
                }.onFailure { errore ->
                    val messaggio = if (errore is ModelloNonRiconosciutoException) {
                        "File non riconosciuto: non corrisponde a nessun modello del catalogo."
                    } else {
                        "Errore importazione: ${errore.message}"
                    }
                    Toast.makeText(context, messaggio, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Apre nel browser la pagina di download del modello: la rete la usa il browser, non la tastiera.
     */
    fun apriPaginaDownload(model: ModelInfo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.downloadUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // Più larga della misura standard dei dialog, che sui telefoni lascia poco spazio alle schede dei modelli
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modelli AI Locali",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    text = "Scarica il file del modello dal browser, poi importalo qui: l'app non usa la rete e verifica ogni file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Scheda Profilo Hardware Dispositivo
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📱 Dispositivo: ",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "${(hwProfile.ramTotaleMb + 512) / 1024} GB RAM • ${hwProfile.numCoreCpu} Core CPU",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = hwProfile.motivazioneConsiglio,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                // Avanzamento della copia mentre un modello viene importato e verificato
                avanzamentoImport?.let { frazione ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Import e verifica in corso (${(frazione * 100).toInt()}%)...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { frazione },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                }

                // Lista Modelli
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (model in ModelCatalog.ALL_MODELS) {
                        val isDownloaded = remember(activeModelId, avanzamentoImport) {
                            modelManager.isModelDownloaded(model)
                        }
                        val isActive = activeModelId == model.id
                        val isRecommended = hwProfile.idModelloConsigliato == model.id

                        ModelCardItem(
                            model = model,
                            isDownloaded = isDownloaded,
                            isActive = isActive,
                            isRecommended = isRecommended,
                            onSelect = {
                                // Solo la scelta: caricare il modello qui occuperebbe memoria nel processo della tastiera
                                modelManager.setActiveModelId(model.id)
                                activeModelId = model.id
                                onModelSelected(model.id)
                            },
                            onApriPaginaDownload = { apriPaginaDownload(model) },
                            onDelete = {
                                modelManager.deleteModel(model.id)
                                activeModelId = modelManager.getActiveModelId()
                                Toast.makeText(context, "${model.name} eliminato dal dispositivo.", Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Tasto Importa Modello da file
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = avanzamentoImport == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("📁 Importa il file di un modello scaricato")
                    }

                    // Per i modelli che non sono nel catalogo, come i Whisper specializzati in italiano
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Accetta file fuori catalogo, senza verifica dell'impronta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = consentiFuoriCatalogo,
                            onCheckedChange = { consentiFuoriCatalogo = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showBenchmarkDialog = true },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("🚀 Test Prestazioni")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Chiudi")
                    }
                }
            }
        }
    }

    if (showBenchmarkDialog) {
        BenchmarkDialog(
            localAiManager = localAiManager,
            onDismiss = { showBenchmarkDialog = false }
        )
    }
}

@Composable
private fun ModelCardItem(
    model: ModelInfo,
    isDownloaded: Boolean,
    isActive: Boolean,
    isRecommended: Boolean,
    onSelect: () -> Unit,
    onApriPaginaDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        isRecommended -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive || isRecommended) 2.dp else 1.dp,
                color = if (borderColor != Color.Transparent) borderColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Titolo e Badge: il nome prende lo spazio libero e va a capo, i badge restano interi
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isRecommended) {
                        Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                            Text("Consigliato", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondary)
                        }
                    }
                    if (isActive) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("In Uso", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Descrizione
            Text(
                text = model.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Indicatori uno per riga: tutti su una riga, sugli schermi stretti la dimensione restava senza spazio
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accuratezza: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("⭐".repeat(model.stelleAccuratezza), fontSize = 11.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Velocità: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("⚡".repeat(model.fulminiVelocita), fontSize = 11.sp)
                }
                Text(
                    text = "Dimensione: ${model.formattedSize}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Azioni: pagina di download / Attiva / Elimina
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloaded) {
                    if (!isActive) {
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Elimina", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onSelect,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Attiva", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("✓ Attivo", fontSize = 12.sp)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onApriPaginaDownload,
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Scarica dal browser (${model.formattedSize})",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
