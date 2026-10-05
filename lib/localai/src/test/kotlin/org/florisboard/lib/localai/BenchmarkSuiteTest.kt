package org.florisboard.lib.localai

import kotlinx.coroutines.runBlocking
import org.florisboard.lib.localai.benchmark.BenchmarkSuite
import org.florisboard.lib.localai.engine.ModelloInstallato
import org.florisboard.lib.localai.engine.MotoreMock
import org.florisboard.lib.localai.engine.TipoMotore
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class BenchmarkSuiteTest {

    @Test
    fun testGenerazioneAudioSintetico() {
        val audio10s = BenchmarkSuite.generaAudioParlatoSintetico(
            durataSecondi = 10,
            conPause = true,
            livelloRumore = 0.05f
        )

        assertEquals(160000, audio10s.size)
        // I campioni devono essere normalizzati tra -1.0f e 1.0f
        for (sample in audio10s) {
            assertTrue(sample >= -1.0f && sample <= 1.0f, "Campione fuori range: $sample")
        }
    }

    @Test
    fun testEsecuzioneBenchmarkScenariCompleti() = runBlocking {
        val motore = MotoreMock()
        val tempModelFile = File.createTempFile("mock_benchmark", ".bin")
        val modello = ModelloInstallato(
            id = "mock-whisper-base",
            nome = "Whisper Base Benchmark",
            percorsoFile = tempModelFile,
            dimensioneByte = 57000000L,
            tipo = TipoMotore.MOCK
        )
        motore.carica(modello)

        val progressoTracciato = mutableListOf<Float>()
        val report = BenchmarkSuite.eseguiBenchmark(
            context = null,
            motore = motore,
            onAvanzamentoGlobale = { p, _ -> progressoTracciato.add(p) }
        )

        assertNotNull(report)
        assertEquals("Whisper Base Benchmark", report.nomeModello)
        assertEquals(4, report.risultati.size)

        // Verifica che l'avanzamento globale sia arrivato al 100%
        assertTrue(progressoTracciato.isNotEmpty())
        assertEquals(1.0f, progressoTracciato.last())

        // Verifica scenario lungo (75s)
        val scenarioLungo = report.risultati.find { it.idScenario == "lungo_75s" }
        assertNotNull(scenarioLungo)
        assertEquals(75000L, scenarioLungo!!.durataAudioMs)
        assertTrue(scenarioLungo.numSegmentiElaborati >= 3, "Il vocale da 75s deve essere suddiviso in segmenti per evitare OOM")
        assertTrue(scenarioLungo.testoTrascritto.isNotEmpty())

        // Verifica scenario rumoroso (20s)
        val scenarioRumoroso = report.risultati.find { it.idScenario == "rumoroso_20s" }
        assertNotNull(scenarioRumoroso)
        assertEquals(20000L, scenarioRumoroso!!.durataAudioMs)
        assertTrue(scenarioRumoroso.testoTrascritto.isNotEmpty())

        // Verifica RTF medio
        assertTrue(report.rtfMedio > 0.0f)
        assertTrue(report.tempoTotaleInferenzaMs > 0L)

        motore.close()
        tempModelFile.delete()
    }
}
