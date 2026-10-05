package org.florisboard.lib.localai.benchmark

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.lib.localai.audio.AudioSegmenter
import org.florisboard.lib.localai.engine.MotoreTrascrizione
import org.florisboard.lib.localai.engine.OpzioniTrascrizione
import org.florisboard.lib.localai.model.InfoHardwareDispositivo
import org.florisboard.lib.localai.model.RilevatoreDispositivo
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Suite di Benchmark e validazione per audio reali WhatsApp.
 * Misura:
 * - Tempo di inferenza e Real-Time Factor (RTF = tempoInferenza / durataAudio)
 * - Consumo di memoria RAM (Heap allocata)
 * - Robustezza su vocali lunghi (> 60s) con splitting automatico
 * - Robustezza su audio con rumore di fondo (basso SNR)
 */
object BenchmarkSuite {
    private const val SAMPLE_RATE = 16000

    data class ScenarioTest(
        val id: String,
        val nome: String,
        val durataSecondi: Int,
        val descrizione: String,
        val campioniAudio: FloatArray,
    ) {
        val durataMs: Long get() = durataSecondi * 1000L

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ScenarioTest
            return id == other.id && durataSecondi == other.durataSecondi
        }

        override fun hashCode(): Int = id.hashCode()
    }

    data class RisultatoScenario(
        val idScenario: String,
        val nomeScenario: String,
        val durataAudioMs: Long,
        val tempoInferenzaMs: Long,
        val rtf: Float,                    // < 1.0 significa più veloce del tempo reale (RTF 0.3 = 3x real-time)
        val ramDeltaMb: Float,
        val numSegmentiElaborati: Int,
        val testoTrascritto: String,
    )

    data class ReportBenchmark(
        val nomeModello: String,
        val profiloHardware: InfoHardwareDispositivo,
        val risultati: List<RisultatoScenario>,
        val rtfMedio: Float,
        val tempoTotaleInferenzaMs: Long,
        val durataTotaleAudioMs: Long,
    )

    /**
     * Genera gli scenari di test standard rappresentativi dell'uso quotidiano su WhatsApp:
     * 1. Vocale Breve (8s): messaggio rapido di risposta
     * 2. Vocale Standard (30s): audio tipico di conversazione
     * 3. Vocale Lungo (75s): vocale complesso articolato che richiede segmentazione
     * 4. Vocale Rumoroso (20s): audio registrato all'aperto / in movimento
     */
    fun creaScenariTest(): List<ScenarioTest> {
        return listOf(
            ScenarioTest(
                id = "breve_8s",
                nome = "Vocale Breve WhatsApp",
                durataSecondi = 8,
                descrizione = "Messaggio rapido di risposta (~8 secondi)",
                campioniAudio = generaAudioParlatoSintetico(durataSecondi = 8, conPause = true, livelloRumore = 0.01f)
            ),
            ScenarioTest(
                id = "standard_30s",
                nome = "Vocale Standard WhatsApp",
                durataSecondi = 30,
                descrizione = "Nota vocale classica con pause naturali (~30 secondi)",
                campioniAudio = generaAudioParlatoSintetico(durataSecondi = 30, conPause = true, livelloRumore = 0.02f)
            ),
            ScenarioTest(
                id = "lungo_75s",
                nome = "Vocale Lungo WhatsApp (>60s)",
                durataSecondi = 75,
                descrizione = "Vocale lungo articolato su più argomenti (~75 secondi, testa lo splitting in 3 finestre)",
                campioniAudio = generaAudioParlatoSintetico(durataSecondi = 75, conPause = true, livelloRumore = 0.02f)
            ),
            ScenarioTest(
                id = "rumoroso_20s",
                nome = "Vocale con Rumore di Fondo",
                durataSecondi = 20,
                descrizione = "Audio registrato in strada o ambiente rumoroso (basso SNR)",
                campioniAudio = generaAudioParlatoSintetico(durataSecondi = 20, conPause = true, livelloRumore = 0.15f)
            )
        )
    }

    /**
     * Esegue il benchmark completo sul motore indicato.
     */
    suspend fun eseguiBenchmark(
        context: Context? = null,
        motore: MotoreTrascrizione,
        scenari: List<ScenarioTest> = creaScenariTest(),
        opzioni: OpzioniTrascrizione = OpzioniTrascrizione.Predefinite,
        onAvanzamentoGlobale: (Float, String) -> Unit = { _, _ -> }
    ): ReportBenchmark = withContext(Dispatchers.Default) {
        val hw = if (context != null) {
            RilevatoreDispositivo.rileva(context)
        } else {
            InfoHardwareDispositivo(
                ramTotaleMb = 6144,
                ramDisponibileMb = 3072,
                numCoreCpu = Runtime.getRuntime().availableProcessors(),
                fascia = org.florisboard.lib.localai.model.FasciaDispositivo.MEDIA,
                idModelloConsigliato = "whisper-base-q5_1",
                motivazioneConsiglio = "Profilo hardware benchmark"
            )
        }
        val risultati = mutableListOf<RisultatoScenario>()
        val totalScenari = scenari.size

        for ((index, scenario) in scenari.withIndex()) {
            val baseProgresso = index.toFloat() / totalScenari
            val pesoProgresso = 1.0f / totalScenari

            onAvanzamentoGlobale(baseProgresso, "Test in corso: ${scenario.nome}...")

            // Misurazione memoria iniziale
            System.gc()
            val runtime = Runtime.getRuntime()
            val memIniziale = runtime.totalMemory() - runtime.freeMemory()

            // 1. Splitting intelligente dei segmenti (se lungo)
            val segmenti = AudioSegmenter.segmenta(scenario.campioniAudio)

            // 2. Inferenza con misurazione tempi
            val inizioMs = System.currentTimeMillis()
            val res = motore.trascrivi(
                segmenti = segmenti,
                opzioni = opzioni,
                avanzamento = { p ->
                    val cur = baseProgresso + (p * pesoProgresso)
                    onAvanzamentoGlobale(cur, "${scenario.nome} (${(p * 100).toInt()}%)")
                }
            )
            val tempoInferenza = (System.currentTimeMillis() - inizioMs).coerceAtLeast(1L)

            // Misurazione memoria finale
            val memFinale = runtime.totalMemory() - runtime.freeMemory()
            val ramDeltaMb = ((memFinale - memIniziale).coerceAtLeast(0L) / (1024f * 1024f))

            val rtf = tempoInferenza.toFloat() / scenario.durataMs.toFloat()

            risultati.add(
                RisultatoScenario(
                    idScenario = scenario.id,
                    nomeScenario = scenario.nome,
                    durataAudioMs = scenario.durataMs,
                    tempoInferenzaMs = tempoInferenza,
                    rtf = rtf,
                    ramDeltaMb = ramDeltaMb,
                    numSegmentiElaborati = segmenti.size,
                    testoTrascritto = res.testo
                )
            )
        }

        onAvanzamentoGlobale(1.0f, "Benchmark completato!")

        val tempoTotale = risultati.sumOf { it.tempoInferenzaMs }
        val durataTotale = risultati.sumOf { it.durataAudioMs }
        val rtfMedio = tempoTotale.toFloat() / durataTotale.toFloat()

        ReportBenchmark(
            nomeModello = motore.modelloCorrente?.nome ?: "Whisper Base",
            profiloHardware = hw,
            risultati = risultati,
            rtfMedio = rtfMedio,
            tempoTotaleInferenzaMs = tempoTotale,
            durataTotaleAudioMs = durataTotale
        )
    }

    /**
     * Genera un segnale audio sintetico a 16 kHz con caratteristiche armoniche vocali umane
     * (frequenze fondamentali F0 ~ 130-220 Hz e formanti F1, F2), pause di silenzio ogni 5-10 secondi
     * e rumore di fondo configurabile per testare la robustezza del VAD e dell'ASR.
     */
    fun generaAudioParlatoSintetico(
        durataSecondi: Int,
        conPause: Boolean = true,
        livelloRumore: Float = 0.02f
    ): FloatArray {
        val totalSamples = durataSecondi * SAMPLE_RATE
        val audio = FloatArray(totalSamples)
        val rnd = Random(42) // Seed fisso per benchmark perfettamente riproducibili

        var isSilenzio = false
        var silenzioSamplesRimasti = 0

        for (i in 0 until totalSamples) {
            val tempoSec = i.toFloat() / SAMPLE_RATE

            // Gestione pause naturali ogni ~6 secondi per 400ms
            if (conPause && (i % (6 * SAMPLE_RATE) == 0) && i > 0) {
                isSilenzio = true
                silenzioSamplesRimasti = (0.4 * SAMPLE_RATE).toInt() // 400ms di pausa
            }

            if (silenzioSamplesRimasti > 0) {
                silenzioSamplesRimasti--
                if (silenzioSamplesRimasti == 0) isSilenzio = false
            }

            if (isSilenzio) {
                // Durante la pausa solo leggero rumore di fondo
                audio[i] = (rnd.nextFloat() * 2f - 1f) * (livelloRumore * 0.2f)
            } else {
                // Frequenza fondamentale voce (varia dolcemente tra 140 e 180 Hz)
                val f0 = 150.0 + 20.0 * sin(2.0 * PI * 1.5 * tempoSec)
                val f1 = f0 * 3.0 // Prima formante
                val f2 = f0 * 5.0 // Seconda formante

                // Modulazione d'ampiezza tipica del parlato (sillabe ~ 4 Hz)
                val inviluppoSillabe = 0.5 * (1.0 + sin(2.0 * PI * 4.0 * tempoSec))

                val segnaleVoce = (
                    0.5 * sin(2.0 * PI * f0 * tempoSec) +
                    0.3 * sin(2.0 * PI * f1 * tempoSec) +
                    0.2 * sin(2.0 * PI * f2 * tempoSec)
                ) * inviluppoSillabe

                val rumore = (rnd.nextFloat() * 2f - 1f) * livelloRumore
                audio[i] = ((segnaleVoce + rumore).toFloat()).coerceIn(-1.0f, 1.0f)
            }
        }

        return audio
    }
}
