package org.florisboard.lib.localai

import org.florisboard.lib.localai.engine.ArchitetturaModello
import org.florisboard.lib.localai.model.EstrattoreArchivi
import org.florisboard.lib.localai.model.FormatoFileModello
import org.florisboard.lib.localai.model.RiconoscitoreFormato
import org.florisboard.lib.localai.sherpa.FileModelloSherpa
import org.florisboard.lib.localai.sherpa.ModelloIncompletoException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Verifica il riconoscimento dei formati, l'estrazione degli archivi e la ricerca dei file dei modelli sherpa-onnx.
 */
class ImportModelliTest {

    /** I primi byte di un file GGML di whisper.cpp con il vocabolario indicato. */
    private fun intestazioneGgml(vocaboli: Int): ByteArray {
        val byte = ByteArray(RiconoscitoreFormato.BYTE_INIZIALI)
        // "lmgg" è il numero magico 0x67676d6c scritto little-endian
        byteArrayOf(0x6c, 0x6d, 0x67, 0x67).copyInto(byte)
        for (i in 0 until 4) byte[4 + i] = (vocaboli shr (8 * i)).toByte()
        return byte
    }

    /** Un GGML multilingue si riconosce, e anche un modello solo inglese. */
    @Test
    fun testGgmlMultilingueESoloInglese() {
        val multilingue = intestazioneGgml(51866)
        assertEquals(FormatoFileModello.GGML_WHISPER, RiconoscitoreFormato.riconosci(multilingue, "ggml-model-q5_0.bin"))
        assertTrue(RiconoscitoreFormato.intestazioneGgml(multilingue)!!.multilingue)
        assertFalse(RiconoscitoreFormato.intestazioneGgml(intestazioneGgml(51864))!!.multilingue)
    }

    /** Archivi dai numeri magici, formati sbagliati dal nome. */
    @Test
    fun testArchiviEFormatiSbagliati() {
        val bzip2 = byteArrayOf('B'.code.toByte(), 'Z'.code.toByte(), 'h'.code.toByte(), '9'.code.toByte())
        assertEquals(FormatoFileModello.ARCHIVIO_TAR_BZ2, RiconoscitoreFormato.riconosci(bzip2, "modello.tar.bz2"))
        val zip = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)
        assertEquals(FormatoFileModello.ARCHIVIO_ZIP, RiconoscitoreFormato.riconosci(zip, "modello.zip"))
        val qualsiasi = ByteArray(16) { 1 }
        assertEquals(FormatoFileModello.SAFETENSORS, RiconoscitoreFormato.riconosci(qualsiasi, "model.safetensors"))
        assertEquals(FormatoFileModello.ONNX_SINGOLO, RiconoscitoreFormato.riconosci(qualsiasi, "encoder.onnx"))
        assertNull(RiconoscitoreFormato.intestazioneGgml(qualsiasi))
    }

    /** Dal nome degli archivi ufficiali si propone l'architettura giusta. */
    @Test
    fun testArchitetturaDalNomeDellArchivio() {
        val attese = mapOf(
            "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2" to ArchitetturaModello.NEMO_TRANSDUCER,
            "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2" to ArchitetturaModello.NEMO_CTC,
            "sherpa-onnx-nemo-transducer-giga-am-v2-russian-2025-04-19.tar.bz2" to ArchitetturaModello.NEMO_TRANSDUCER,
            "sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12.tar.bz2" to ArchitetturaModello.OMNILINGUAL_CTC,
            "sherpa-onnx-moonshine-base-en-int8.tar.bz2" to ArchitetturaModello.MOONSHINE,
            "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2" to ArchitetturaModello.SENSE_VOICE,
            "sherpa-onnx-whisper-small.tar.bz2" to ArchitetturaModello.WHISPER_ONNX,
            "sherpa-onnx-zipformer-gigaspeech-2023-12-12.tar.bz2" to ArchitetturaModello.ZIPFORMER_TRANSDUCER,
        )
        for ((nome, architettura) in attese) {
            assertEquals(architettura, RiconoscitoreFormato.architetturaProbabile(nome), nome)
        }
        assertNull(RiconoscitoreFormato.architetturaProbabile("modello.zip"))
        assertNull(RiconoscitoreFormato.architetturaProbabile(null))
    }

    /** Un archivio con una cartella principale viene estratto senza quella cartella. */
    @Test
    fun testEstrazioneToglieLaCartellaPrincipale() {
        val radice = Files.createTempDirectory("estrazione").toFile()
        val archivio = File(radice, "modello.zip")
        ZipOutputStream(archivio.outputStream()).use { zip ->
            for (nome in listOf("modello/encoder.onnx", "modello/tokens.txt")) {
                zip.putNextEntry(ZipEntry(nome))
                zip.write(nome.toByteArray())
                zip.closeEntry()
            }
        }
        val destinazione = File(radice, "estratto")
        EstrattoreArchivi.estrai(archivio, FormatoFileModello.ARCHIVIO_ZIP, destinazione) {}
        assertTrue(File(destinazione, "encoder.onnx").isFile)
        assertTrue(File(destinazione, "tokens.txt").isFile)
        radice.deleteRecursively()
    }

    /** Una voce che esce dalla cartella del modello viene rifiutata. */
    @Test
    fun testEstrazioneRifiutaPercorsiEsterni() {
        val radice = Files.createTempDirectory("estrazione").toFile()
        val archivio = File(radice, "malevolo.zip")
        ZipOutputStream(archivio.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("../fuori.txt"))
            zip.write(1)
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            EstrattoreArchivi.estrai(archivio, FormatoFileModello.ARCHIVIO_ZIP, File(radice, "estratto")) {}
        }
        radice.deleteRecursively()
    }

    /** Per un transducer NeMo si scelgono le versioni int8; se manca il joiner il modello è incompleto. */
    @Test
    fun testFileTransducerNemo() {
        val cartella = Files.createTempDirectory("parakeet").toFile()
        for (nome in listOf("encoder.onnx", "encoder.int8.onnx", "decoder.int8.onnx", "joiner.int8.onnx", "tokens.txt")) {
            File(cartella, nome).writeText(nome)
        }
        val configurazione = FileModelloSherpa.configurazione(cartella, ArchitetturaModello.NEMO_TRANSDUCER, 4, null)
        assertTrue(configurazione.transducer.encoder.endsWith("encoder.int8.onnx"))
        assertEquals("nemo_transducer", configurazione.modelType)
        assertEquals(4, configurazione.numThreads)

        File(cartella, "joiner.int8.onnx").delete()
        assertThrows(ModelloIncompletoException::class.java) {
            FileModelloSherpa.configurazione(cartella, ArchitetturaModello.NEMO_TRANSDUCER, 4, null)
        }
        cartella.deleteRecursively()
    }
}
