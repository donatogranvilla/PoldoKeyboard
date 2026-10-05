package dev.patrickgold.florisboard.ime.voice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.patrickgold.florisboard.FlorisImeService
import kotlinx.coroutines.launch
import org.florisboard.lib.localai.LocalAiManager
import org.florisboard.lib.localai.engine.OpzioniTrascrizione

class TranscribeShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val audioUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (audioUri == null) {
            Toast.makeText(this, "Nessun file audio ricevuto da WhatsApp", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ShareTranscribeDialog(
                audioUri = audioUri,
                onDismiss = { finish() },
                onCopy = { text ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("PoldoKeyboard", text)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Testo copiato! Puoi incollarlo in WhatsApp.", Toast.LENGTH_SHORT).show()

                    // Also attempt direct commit if IME connection is open
                    FlorisImeService.currentInputConnection()?.commitText(text, 1)

                    finish()
                },
                onShareBack = { text ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Invia trascrizione"))
                    finish()
                }
            )
        }
    }
}

@Composable
private fun ShareTranscribeDialog(
    audioUri: Uri,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onShareBack: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val localAiManager = remember { LocalAiManager(context) }

    var selectedLang by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Decodifica vocale WhatsApp in corso...") }
    var progress by remember { mutableFloatStateOf(0.1f) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var showModelDialog by remember { mutableStateOf(false) }

    fun startTranscription(lang: String?) {
        scope.launch {
            isProcessing = true
            progress = 0.15f
            statusText = "Elaborazione con Whisper AI..."

            val opzioni = OpzioniTrascrizione(lingua = lang, formattaPunteggiatura = true)
            val result = localAiManager.transcribeAudioUri(audioUri, opzioni) { p ->
                progress = p
                statusText = "Trascrizione Whisper AI (${(p * 100).toInt()}%)..."
            }

            isProcessing = false
            if (result.isSuccess) {
                resultText = result.getOrThrow().testo
                statusText = "Trascrizione completata!"
            } else {
                statusText = "Errore: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    LaunchedEffect(Unit) {
        startTranscription(null)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Trascrizione Vocale WhatsApp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Model Selector Pill
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
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "🧠 Modello: $currentModelName ▾",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Language selection bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val langs = listOf(
                        null to "Auto",
                        "it" to "Italiano",
                        "en" to "English",
                        "es" to "Español",
                        "fr" to "Français",
                        "de" to "Deutsch"
                    )
                    for ((code, name) in langs) {
                        val isSelected = selectedLang == code
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF3F51B5) else Color(0xFF2D2D38))
                                .clickable {
                                    if (selectedLang != code) {
                                        selectedLang = code
                                        startTranscription(code)
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusText,
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC),
                        textAlign = TextAlign.Center
                    )
                } else if (resultText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF282832))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = resultText!!,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCopy(resultText!!) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("📋 Copia", fontSize = 13.sp)
                        }

                        Button(
                            onClick = { onShareBack(resultText!!) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                        ) {
                            Text("Invia", fontSize = 13.sp)
                        }
                    }
                } else {
                    Text(
                        text = statusText,
                        color = Color(0xFFFF8A80),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { startTranscription(selectedLang) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
                    ) {
                        Text("Riprova")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(onClick = onDismiss) {
                    Text("Chiudi", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                }
            }
        }
    }

    if (showModelDialog) {
        ModelManagementDialog(
            localAiManager = localAiManager,
            onDismiss = { showModelDialog = false },
            onModelSelected = {
                startTranscription(selectedLang)
            }
        )
    }
}
