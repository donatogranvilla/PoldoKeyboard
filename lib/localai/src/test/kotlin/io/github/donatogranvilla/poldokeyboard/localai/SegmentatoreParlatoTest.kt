package io.github.donatogranvilla.poldokeyboard.localai

import io.github.donatogranvilla.poldokeyboard.localai.audio.SegmentatoreParlato
import io.github.donatogranvilla.poldokeyboard.localai.audio.TrattoParlato
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SegmentatoreParlatoTest {
    private val secondo = 16_000

    /** Un minuto di audio dove ogni campione vale il suo secondo, per riconoscere da dove viene. */
    private val minuto = FloatArray(60 * secondo) { (it / secondo).toFloat() }

    private fun tratto(daS: Double, aS: Double) = TrattoParlato((daS * secondo).toInt(), (aS * secondo).toInt())

    @Test
    fun testSenzaTrattiNessunBlocco() {
        assertTrue(SegmentatoreParlato.segmenta(minuto, emptyList(), durataMassimaS = 28).isEmpty())
    }

    /** Una pausa breve resta com'è: i due tratti diventano un blocco solo, con la pausa in mezzo. */
    @Test
    fun testPausaBreveConservata() {
        val blocchi = SegmentatoreParlato.segmenta(minuto, listOf(tratto(1.0, 3.0), tratto(3.5, 6.0)), 28)
        assertEquals(1, blocchi.size)
        assertEquals(5 * secondo, blocchi[0].campioniAudio.size)
        assertEquals(1000L, blocchi[0].timestampInizioMs)
        assertEquals(6000L, blocchi[0].timestampFineMs)
    }

    /** Una pausa di 10 s si accorcia a 1 s: mezzo secondo dopo il primo tratto e mezzo prima del secondo. */
    @Test
    fun testPausaLungaAccorciata() {
        val blocchi = SegmentatoreParlato.segmenta(minuto, listOf(tratto(0.0, 2.0), tratto(12.0, 14.0)), 28)
        assertEquals(1, blocchi.size)
        val audio = blocchi[0].campioniAudio
        assertEquals(5 * secondo, audio.size)
        // Dopo i 2 s di parlato, mezzo secondo dal secondo 2 e mezzo dal secondo 11, poi il parlato dal 12
        assertEquals(2f, audio[2 * secondo])
        assertEquals(11f, audio[3 * secondo - 1])
        assertEquals(12f, audio[3 * secondo])
        assertEquals(14000L, blocchi[0].timestampFineMs)
    }

    /** Oltre la durata massima si apre un nuovo blocco, senza la pausa tra i due. */
    @Test
    fun testDurataMassimaRispettata() {
        val tratti = listOf(tratto(0.0, 10.0), tratto(10.5, 20.0), tratto(20.5, 30.0), tratto(30.5, 40.0))
        val blocchi = SegmentatoreParlato.segmenta(minuto, tratti, durataMassimaS = 24)
        assertEquals(2, blocchi.size)
        assertTrue(blocchi.all { it.campioniAudio.size <= 24 * secondo })
        assertEquals(20000L, blocchi[0].timestampFineMs)
        assertEquals(20500L, blocchi[1].timestampInizioMs)
        assertEquals(40000L, blocchi[1].timestampFineMs)
        assertEquals(20f, blocchi[1].campioniAudio.first())
    }

    /** Un tratto più lungo del massimo, che il rilevatore non dovrebbe dare, si taglia comunque. */
    @Test
    fun testTrattoTroppoLungoTagliato() {
        val blocchi = SegmentatoreParlato.segmenta(minuto, listOf(tratto(0.0, 50.0)), durataMassimaS = 20)
        assertEquals(listOf(20 * secondo, 20 * secondo, 10 * secondo), blocchi.map { it.campioniAudio.size })
    }

    /** Un tratto che sfora la fine dell'audio si ferma all'ultimo campione. */
    @Test
    fun testTrattoOltreLaFine() {
        val audio = FloatArray(2 * secondo) { 0.5f }
        val blocchi = SegmentatoreParlato.segmenta(audio, listOf(tratto(1.0, 3.0)), 28)
        assertArrayEquals(FloatArray(secondo) { 0.5f }, blocchi.single().campioniAudio)
    }
}
