package org.florisboard.lib.localai

import org.florisboard.lib.localai.postprocess.TextPostProcessor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextPostProcessorTest {

    @Test
    fun testPunctuationAndSpacingNormalization() {
        val input = "ciao , come stai ? tutto bene !"
        val expected = "Ciao, come stai? Tutto bene!"
        val actual = TextPostProcessor.process(input, "it")
        assertEquals(expected, actual)
    }

    @Test
    fun testMissingSpaceAfterPunctuation() {
        val input = "domani alle otto.ci vediamo li"
        val expected = "Domani alle otto. Ci vediamo li"
        val actual = TextPostProcessor.process(input, "it")
        assertEquals(expected, actual)
    }

    @Test
    fun testStripHallucinationBrackets() {
        val input = "[Musica] buongiorno a tutti [Applausi]"
        val expected = "Buongiorno a tutti"
        val actual = TextPostProcessor.process(input, "it")
        assertEquals(expected, actual)
    }

    /** I loop di Whisper si riconoscono anche con la punteggiatura e con frasi di più parole. */
    @Test
    fun testLoopRidottiAUnaOccorrenza() {
        assertEquals("Grazie.", TextPostProcessor.process("Grazie. Grazie. Grazie.", "it"))
        assertEquals(
            "Ci vediamo domani",
            TextPostProcessor.process("ci vediamo domani ci vediamo domani ci vediamo domani", "it"),
        )
        assertEquals(
            "Allora ci sentiamo stasera, va bene?",
            TextPostProcessor.process("allora ci sentiamo stasera, ci sentiamo stasera, ci sentiamo stasera, va bene?", "it"),
        )
    }

    /** Le ripetizioni brevi dette davvero restano. */
    @Test
    fun testRipetizioniNaturaliConservate() {
        assertEquals("No no no, non ci vado", TextPostProcessor.process("no no no, non ci vado", "it"))
        assertEquals("Ciao ciao", TextPostProcessor.process("ciao ciao", "it"))
    }

    /** I crediti dei sottotitoli inventati sul non-parlato spariscono, in ogni lingua. */
    @Test
    fun testCreditiSottotitoliRimossi() {
        assertEquals(
            "Ci vediamo stasera.",
            TextPostProcessor.process("Ci vediamo stasera. Sottotitoli creati dalla comunità Amara.org", "it"),
        )
        assertEquals("", TextPostProcessor.process("Sottotitoli e revisione a cura di QTSS", "it"))
        assertEquals("", TextPostProcessor.process("Sous-titres réalisés par la communauté d'Amara.org", "fr"))
    }

    /** Tra parentesi tonde si tolgono le annotazioni brevi, non le frasi. */
    @Test
    fun testParentesiTonde() {
        assertEquals("Ci vediamo stasera", TextPostProcessor.process("ci vediamo (risate) stasera", "it"))
        assertEquals(
            "Arrivo (se riesco a liberarmi prima) stasera",
            TextPostProcessor.process("arrivo (se riesco a liberarmi prima) stasera", "it"),
        )
    }

    @Test
    fun testPreservesMeaningWithoutInventingContent() {
        val input = "ci vediamo alle venti e trenta"
        val expected = "Ci vediamo alle venti e trenta"
        val actual = TextPostProcessor.process(input, "it")
        assertEquals(expected, actual)
    }
}
