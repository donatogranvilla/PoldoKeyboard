package io.github.donatogranvilla.poldokeyboard.localai

import io.github.donatogranvilla.poldokeyboard.localai.engine.CoreCpu
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifica la scelta dei thread sui processori con core di taglie diverse.
 */
class CoreCpuTest {

    /** Snapdragon 7s Gen 2: quattro Cortex-A78 a 2,4 GHz e quattro Cortex-A55 a 1,95 GHz, si usano i primi. */
    @Test
    fun testSnapdragon7sGen2UsaSoloICoreGrandi() {
        val frequenze = List(4) { 1_950_000L } + List(4) { 2_400_000L }
        assertEquals(4, CoreCpu.threadPerFrequenze(frequenze, 8))
    }

    /** Con tre gruppi di core si esclude solo il più lento. */
    @Test
    fun testTreGruppiEscludonoSoloIlPiuLento() {
        val frequenze = listOf(3_000_000L) + List(3) { 2_500_000L } + List(4) { 1_800_000L }
        assertEquals(4, CoreCpu.threadPerFrequenze(frequenze, 8))
    }

    /** Core tutti uguali: si usano tutti. */
    @Test
    fun testCoreTuttiUgualiSiUsanoTutti() {
        assertEquals(8, CoreCpu.threadPerFrequenze(List(8) { 2_000_000L }, 8))
    }

    /** Senza frequenze leggibili si usa metà dei processori, mai meno di 2 thread. */
    @Test
    fun testSenzaFrequenzeMetaDeiProcessori() {
        assertEquals(4, CoreCpu.threadPerFrequenze(emptyList(), 8))
        assertEquals(2, CoreCpu.threadPerFrequenze(emptyList(), 2))
    }
}
