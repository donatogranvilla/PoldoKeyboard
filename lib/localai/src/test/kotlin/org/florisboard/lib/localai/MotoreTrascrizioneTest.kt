package org.florisboard.lib.localai

import kotlinx.coroutines.runBlocking
import org.florisboard.lib.localai.engine.*
import org.florisboard.lib.localai.model.ModelCatalog
import org.florisboard.lib.localai.whisper.MotoreWhisperCpp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class MotoreTrascrizioneTest {

    @Test
    fun testMotoreMockWorkflow() = runBlocking {
        val motore: MotoreTrascrizione = MotoreMock()
        assertFalse(motore.isModelloCaricato)

        val tempFile = File.createTempFile("test_model", ".bin")
        val modello = ModelloInstallato(
            id = "test-tiny",
            nome = "Whisper Tiny Test",
            percorsoFile = tempFile,
            dimensioneByte = 1000L,
            tipo = TipoMotore.MOCK
        )

        motore.carica(modello)
        assertTrue(motore.isModelloCaricato)
        assertEquals(modello, motore.modelloCorrente)

        // Genera campioni audio a 16 kHz (1 secondo = 16000 campioni)
        val audio1Sec = FloatArray(16000) { 0.1f }
        val segmento = SegmentoVoce(audio1Sec)

        val progressUpdates = mutableListOf<Float>()
        val risultato = motore.trascrivi(
            segmenti = listOf(segmento),
            opzioni = OpzioniTrascrizione(lingua = "it", formattaPunteggiatura = true),
            avanzamento = { p -> progressUpdates.add(p) }
        )

        assertNotNull(risultato.testo)
        assertTrue(risultato.testo.isNotEmpty())
        assertEquals(1000L, risultato.durataAudioMs)
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(1.0f, progressUpdates.last())

        motore.close()
        assertFalse(motore.isModelloCaricato)
        tempFile.delete()
    }

    @Test
    fun testMotoreWhisperCppLifecycle() = runBlocking {
        val motore: MotoreTrascrizione = MotoreWhisperCpp()
        assertFalse(motore.isModelloCaricato)

        // Verifica eccezione su file inesistente
        val fakeFile = File("non_existent_model_file.bin")
        val modelloInesistente = ModelloInstallato(
            id = "fake",
            nome = "Fake",
            percorsoFile = fakeFile
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { motore.carica(modelloInesistente) }
        }

        // Test con file reale temporaneo
        val tempFile = File.createTempFile("whisper_test", ".bin")
        val modello = ModelloInstallato(
            id = "whisper-base",
            nome = "Whisper Base",
            percorsoFile = tempFile
        )

        motore.carica(modello)
        assertTrue(motore.isModelloCaricato)

        // Trascrizione con 2 segmenti audio concatenati (0.5s + 0.5s)
        val seg1 = SegmentoVoce(FloatArray(8000))
        val seg2 = SegmentoVoce(FloatArray(8000))

        val progressList = mutableListOf<Float>()
        val res = motore.trascrivi(
            segmenti = listOf(seg1, seg2),
            opzioni = OpzioniTrascrizione(lingua = "it", formattaPunteggiatura = true),
            avanzamento = { progressList.add(it) }
        )

        assertEquals(1000L, res.durataAudioMs)
        assertTrue(res.testo.isNotEmpty())
        assertEquals(1.0f, progressList.last())

        motore.close()
        assertFalse(motore.isModelloCaricato)
        tempFile.delete()
    }

    @Test
    fun testCatalogoModelliHardwareAttributes() {
        assertEquals(7, ModelCatalog.ALL_MODELS.size)

        for (model in ModelCatalog.ALL_MODELS) {
            assertTrue(model.sizeBytes > 0)
            assertTrue(model.sha256.length == 64)
            assertTrue(model.downloadUrl.startsWith("https://"))
            assertTrue(model.stelleAccuratezza in 1..5)
            assertTrue(model.fulminiVelocita in 1..5)
            assertTrue(model.ramConsigliataMb > 0)
        }

        // Verifica che Tiny abbia la velocità massima e Small la massima accuratezza
        val tiny = ModelCatalog.WHISPER_TINY_Q5
        val small = ModelCatalog.WHISPER_SMALL_Q5
        assertTrue(tiny.fulminiVelocita >= small.fulminiVelocita)
        assertTrue(small.stelleAccuratezza >= tiny.stelleAccuratezza)
    }

    @Test
    fun testTypeAdapters() {
        val opzioni = OpzioniTrascrizione(
            lingua = "it",
            formattaPunteggiatura = true,
            numThread = 6,
            temperatura = 0.2f,
            traduciInInglese = true
        )
        val legacyOptions = opzioni.toTranscriptionOptions()
        assertEquals("it", legacyOptions.language)
        assertTrue(legacyOptions.formatPunctuation)
        assertEquals(6, legacyOptions.threads)
        assertTrue(legacyOptions.translateToEnglish)

        val back = legacyOptions.toOpzioniTrascrizione()
        assertEquals(opzioni.lingua, back.lingua)
        assertEquals(opzioni.formattaPunteggiatura, back.formattaPunteggiatura)
        assertEquals(opzioni.numThread, back.numThread)
    }
}
