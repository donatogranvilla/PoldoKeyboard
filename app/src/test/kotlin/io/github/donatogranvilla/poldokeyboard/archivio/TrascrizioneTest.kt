package io.github.donatogranvilla.poldokeyboard.archivio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Verifica la politica di ripresa delle trascrizioni interrotte da un processo terminato a metà lavoro.
 */
class TrascrizioneTest {
    private val interrotta = Trascrizione(
        id = 7,
        creataIl = 1_000L,
        nomeFileAudio = "vocale.opus",
        stato = StatoTrascrizione.IN_CORSO,
        avanzamento = 0.4f,
        vista = true,
    )

    /** Sotto la soglia torna in coda e riparte da capo. */
    @Test
    fun testSottoLaSogliaTornaInCoda() {
        val ripresa = interrotta.copy(tentativi = MAX_TENTATIVI - 1).dopoInterruzione()
        assertEquals(StatoTrascrizione.IN_CODA, ripresa.stato)
        assertEquals(0f, ripresa.avanzamento)
        assertEquals(MAX_TENTATIVI - 1, ripresa.tentativi)
    }

    /** Alla soglia diventa un errore da mostrare all'utente. */
    @Test
    fun testAllaSogliaDiventaErrore() {
        val ripresa = interrotta.copy(tentativi = MAX_TENTATIVI).dopoInterruzione()
        assertEquals(StatoTrascrizione.ERRORE, ripresa.stato)
        assertNotNull(ripresa.errore)
        assertNotNull(ripresa.conclusaIl)
        assertFalse(ripresa.vista)
    }

    /** La riga e il suo vocale restano gli stessi. */
    @Test
    fun testRigaEVocaleRestanoGliStessi() {
        val ripresa = interrotta.copy(tentativi = MAX_TENTATIVI).dopoInterruzione()
        assertEquals(interrotta.id, ripresa.id)
        assertEquals(interrotta.nomeFileAudio, ripresa.nomeFileAudio)
    }
}
