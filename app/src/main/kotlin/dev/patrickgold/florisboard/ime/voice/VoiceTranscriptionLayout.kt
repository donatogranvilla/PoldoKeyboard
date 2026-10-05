package dev.patrickgold.florisboard.ime.voice

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard3.LocalImeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.LocalAiManager
import org.florisboard.lib.localai.engine.TranscriptionOptions
import org.florisboard.lib.localai.engine.TranscriptionResult

private data class LangOption(val code: String?, val label: String)

private val SupportedLanguages = listOf(
    LangOption(null, "Auto (Rileva)"),
    LangOption("it", "Italiano"),
    LangOption("en", "English"),
    LangOption("es", "Español"),
    LangOption("fr", "Français"),
    LangOption("de", "Deutsch"),
    LangOption("pt", "Português"),
)

@Composable
fun VoiceTranscriptionLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imeController = LocalImeController.current
    val clipboardManager = LocalClipboardManager.current

    val localAiManager = remember { LocalAiManager(context) }

    var selectedLang by remember { mutableStateOf<String?>(null) }
    var autoCommitEnabled by remember { mutableStateOf(true) }
    var showModelDialog by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf("Pronto per la trascrizione") }
    var progress by remember { mutableFloatStateOf(0f) }

    var resultText by remember { mutableStateOf<String?>(null) }
    var lastDurationMs by remember { mutableLongStateOf(0L) }
    var lastInferenceMs by remember { mutableLongStateOf(0L) }

    fun closePanel() {
        imeController.updateStateBlocking {
            state = state.copy(flags = state.flags.withImeUiMode(ImeUiMode.TEXT))
        }
    }

    fun commitToActiveField(text: String) {
        val ic = FlorisImeService.currentInputConnection()
        if (ic != null) {
            ic.commitText(text, 1)
            closePanel()
        } else {
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, "Testo copiato negli appunti", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleTranscriptionResult(result: TranscriptionResult) {
        isProcessing = false
        resultText = result.text
        lastDurationMs = result.audioDurationMs
        lastInferenceMs = result.inferenceTimeMs

        if (autoCommitEnabled && result.text.isNotBlank()) {
            commitToActiveField(result.text)
        } else {
            processingStatus = "Trascrizione completata!"
        }
    }

    fun transcribeUri(uri: Uri) {
        scope.launch {
            isProcessing = true
            processingStatus = "Decodifica audio WhatsApp in corso..."
            progress = 0.1f

            val options = TranscriptionOptions(
                language = selectedLang,
                formatPunctuation = true
            )

            val result = localAiManager.transcribeAudioUri(uri, options) { p ->
                progress = p
                processingStatus = "Trascrizione locale Whisper AI (${(p * 100).toInt()}%)..."
            }

            if (result.isSuccess) {
                handleTranscriptionResult(result.getOrThrow())
            } else {
                isProcessing = false
                val err = result.exceptionOrNull()?.message ?: "Errore sconosciuto"
                processingStatus = "Errore: $err"
                Toast.makeText(context, "Errore decodifica: $err", Toast.LENGTH_LONG).show()
            }
        }
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { transcribeUri(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(Color(0xFF1E1E24))
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { closePanel() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Torna alla tastiera",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "LocalAI Transcriber",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }

            // Model selector button and Auto-commit toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val currentModelName = remember(showModelDialog) {
                    localAiManager.modelManager.getActiveInstalledModel()?.nome
                        ?: localAiManager.modelManager.getRecommendedModel().name.replace(" (Q5_1)", "")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showModelDialog = true }
                        .background(Color(0xFF2C3E50))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🧠 $currentModelName",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { autoCommitEnabled = !autoCommitEnabled }
                        .background(if (autoCommitEnabled) Color(0xFF2E7D32) else Color(0xFF3E3E48))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (autoCommitEnabled) "Auto ON" else "Auto OFF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // 2. Language chips horizontal scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (lang in SupportedLanguages) {
                val isSelected = selectedLang == lang.code
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFF3F51B5) else Color(0xFF2D2D38))
                        .clickable { selectedLang = lang.code }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = lang.label,
                        color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // 3. Central Status / Visualizer Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF282832))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isProcessing -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF4CAF50),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = processingStatus,
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                resultText != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = resultText!!,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Audio: ${lastDurationMs / 1000}s | Inferenza: ${lastInferenceMs}ms",
                                fontSize = 11.sp,
                                color = Color(0xFFAAAAAA)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF4CAF50))
                                        .clickable { commitToActiveField(resultText!!) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("✍️ Inserisci", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF555566))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(resultText!!))
                                            Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("📋 Copia", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Scegli un vocale salvato sul telefono, oppure condividilo da WhatsApp con «Trascrivi».",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Primary Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button A: Pick WhatsApp / Audio File
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2E7D32))
                    .clickable(enabled = !isProcessing) {
                        resultText = null
                        audioFilePicker.launch("audio/*")
                    }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Scegli un vocale",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }

    if (showModelDialog) {
        ModelManagementDialog(
            localAiManager = localAiManager,
            onDismiss = { showModelDialog = false }
        )
    }
}
