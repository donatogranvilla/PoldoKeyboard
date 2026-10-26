package io.github.donatogranvilla.poldokeyboard.localai

import io.github.donatogranvilla.poldokeyboard.localai.audio.AudioSegmenter
import io.github.donatogranvilla.poldokeyboard.localai.benchmark.BenchmarkSuite
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AudioSegmenterTest {

    @Test
    fun testBreveNonVieneSplittato() {
        // 10 secondi a 16 kHz = 160.000 campioni
        val campioni10s = FloatArray(160000) { 0.05f }
        val segmenti = AudioSegmenter.segmenta(campioni10s)

        assertEquals(1, segmenti.size)
        assertEquals(0L, segmenti.first().timestampInizioMs)
        assertEquals(10000L, segmenti.first().timestampFineMs)
        assertEquals(160000, segmenti.first().campioniAudio.size)
    }

    @Test
    fun testAudioVuoto() {
        val vuoto = FloatArray(0)
        val segmenti = AudioSegmenter.segmenta(vuoto)
        assertTrue(segmenti.isEmpty())
    }

    @Test
    fun testVocaleLungoOltre60SecondiVieneSplittatoCorrettamente() {
        // Genera un audio vocale realistico di 75 secondi con pause naturali
        val audio75s = BenchmarkSuite.generaAudioParlatoSintetico(
            durataSecondi = 75,
            conPause = true,
            livelloRumore = 0.02f
        )
        val segmenti = AudioSegmenter.segmenta(audio75s)

        // 75 secondi divisi con max 28s a finestra devono produrre 3 segmenti
        assertTrue(segmenti.size >= 3, "Un audio di 75s deve essere suddiviso in almeno 3 segmenti, trovati: ${segmenti.size}")

        // Verifica continuità temporale tra i segmenti
        var prevFineMs = 0L
        var campioniTotali = 0
        for ((idx, seg) in segmenti.withIndex()) {
            assertEquals(prevFineMs, seg.timestampInizioMs, "I timestamp di inizio/fine devono essere continui al segmento $idx")
            assertTrue(seg.timestampFineMs > seg.timestampInizioMs)
            // Ogni segmento non deve superare la finestra massima Whisper di 28.5 secondi
            assertTrue(seg.durataMs <= 28500L, "La durata del segmento non deve eccedere la finestra Whisper (trovati: ${seg.durataMs} ms)")
            campioniTotali += seg.campioniAudio.size
            prevFineMs = seg.timestampFineMs
        }

        // La somma dei campioni di tutti i segmenti deve coprire l'intero file originale
        assertEquals(audio75s.size, campioniTotali)
        assertEquals(75000L, prevFineMs)
    }
}
