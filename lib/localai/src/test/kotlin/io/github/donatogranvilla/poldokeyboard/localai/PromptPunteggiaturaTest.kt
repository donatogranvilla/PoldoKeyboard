package io.github.donatogranvilla.poldokeyboard.localai

import io.github.donatogranvilla.poldokeyboard.localai.whisper.PromptPunteggiatura
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PromptPunteggiaturaTest {

    /** Le lingue con una frase pronta la ricevono; le altre, e la lingua automatica, nessun prompt. */
    @Test
    fun testFrasePerLingua() {
        assertEquals("Ciao, come stai? Bene, grazie.", PromptPunteggiatura.per("it"))
        assertNull(PromptPunteggiatura.per("sq"))
        assertNull(PromptPunteggiatura.per(null))
    }
}
