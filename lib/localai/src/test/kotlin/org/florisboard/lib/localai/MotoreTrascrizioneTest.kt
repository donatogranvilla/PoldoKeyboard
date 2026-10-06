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

    /** Senza libreria nativa, come nei test su JVM, il motore fallisce invece di restituire testo segnaposto. */
    @Test
    fun testMotoreWhisperCppSenzaLibreriaNativa() = runBlocking {
        val motore: MotoreTrascrizione = MotoreWhisperCpp()
        assertFalse(motore.isModelloCaricato)

        // Un file inesistente viene rifiutato per primo
        val modelloInesistente = ModelloInstallato(
            id = "fake",
            nome = "Fake",
            percorsoFile = File("non_existent_model_file.bin")
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { motore.carica(modelloInesistente) }
        }

        // Con un file vero ma senza libreria nativa il caricamento segnala il motore mancante
        val tempFile = File.createTempFile("whisper_test", ".bin")
        val modello = ModelloInstallato(
            id = "whisper-base",
            nome = "Whisper Base",
            percorsoFile = tempFile
        )
        assertThrows(MotoreNonDisponibileException::class.java) {
            runBlocking { motore.carica(modello) }
        }
        assertFalse(motore.isModelloCaricato)

        // Senza modello caricato anche la trascrizione è un errore
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                motore.trascrivi(listOf(SegmentoVoce(FloatArray(8000))), OpzioniTrascrizione(), {})
            }
        }

        motore.close()
        tempFile.delete()
    }

    @Test
    fun testCatalogoModelliHardwareAttributes() {
        // Il catalogo comprende almeno i sette Whisper ufficiali
        assertTrue(ModelCatalog.ALL_MODELS.size >= 7)

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
}
