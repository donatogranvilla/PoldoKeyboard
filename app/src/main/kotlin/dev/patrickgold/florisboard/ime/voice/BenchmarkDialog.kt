package dev.patrickgold.florisboard.ime.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch
import org.florisboard.lib.localai.LocalAiManager
import org.florisboard.lib.localai.benchmark.BenchmarkSuite

@Composable
fun BenchmarkDialog(
    localAiManager: LocalAiManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var globalProgress by remember { mutableFloatStateOf(0.0f) }
    var statusText by remember { mutableStateOf("Pronto per eseguire il test prestazionale") }
    var benchmarkReport by remember { mutableStateOf<BenchmarkSuite.ReportBenchmark?>(null) }

    fun startBenchmark() {
        scope.launch {
            isRunning = true
            benchmarkReport = null
            globalProgress = 0.02f
            localAiManager.ensureModelLoaded()

            val report = BenchmarkSuite.eseguiBenchmark(
                context = context,
                motore = localAiManager.activeEngine,
                onAvanzamentoGlobale = { p, status ->
                    globalProgress = p
                    statusText = status
                }
            )

            benchmarkReport = report
            isRunning = false
        }
    }

    Dialog(onDismissRequest = { if (!isRunning) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
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
                        text = "🚀 Test Prestazioni & Audio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isRunning
                    ) {
                        Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    text = "Valuta la velocità e l'accuratezza del modello su vocali reali WhatsApp (brevi, lunghi >60s e rumorosi).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Stato di esecuzione e progress bar
                if (isRunning) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${(globalProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { globalProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }

                // Contenuto scrollabile con risultati o introduzione
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    val report = benchmarkReport
                    if (report != null) {
                        // Riepilogo generale
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📊 Modello: ${report.nomeModello}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Audio Totale: ${(report.durataTotaleAudioMs / 1000)}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Tempo Inferenza: ${report.tempoTotaleInferenzaMs} ms",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Fattore RTF Medio: ${"%.2f".format(report.rtfMedio)}x (${if (report.rtfMedio <= 1.0f) "più veloce del tempo reale! ⚡" else "elaborazione accurata"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Tabella dei 4 scenari
                        for (scenarioRes in report.risultati) {
                            ScenarioResultCard(res = scenarioRes)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else if (!isRunning) {
                        // Schermata di avvio informativa
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Il test verificherà 4 scenari audio:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("• Vocale Breve (8s): messaggio rapido di risposta", style = MaterialTheme.typography.bodySmall)
                                Text("• Vocale Standard (30s): audio tipico di conversazione", style = MaterialTheme.typography.bodySmall)
                                Text("• Vocale Lungo (75s): verifica lo splitting a pause naturali", style = MaterialTheme.typography.bodySmall)
                                Text("• Vocale Rumoroso (20s): audio con rumore di fondo", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Tutto viene elaborato interamente sui core CPU del tuo smartphone senza inviare un singolo byte su internet.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Pulsanti inferiori
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isRunning,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Chiudi")
                    }

                    Button(
                        onClick = { startBenchmark() },
                        enabled = !isRunning,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (benchmarkReport == null) "Avvia Benchmark" else "Riesegui Test")
                    }
                }
            }
        }
    }
}

@Composable
private fun ScenarioResultCard(res: BenchmarkSuite.RisultatoScenario) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = res.nomeScenario,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Badge(
                    containerColor = if (res.rtf <= 1.0f) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "RTF: ${"%.2f".format(res.rtf)}",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Durata audio: ${(res.durataAudioMs / 1000.0).toInt()}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tempo inferenza: ${res.tempoInferenzaMs} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Segmenti: ${res.numSegmentiElaborati}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (res.testoTrascritto.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\"${res.testoTrascritto}\"",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                )
            }
        }
    }
}
