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

    @Test
    fun testPreservesMeaningWithoutInventingContent() {
        val input = "ci vediamo alle venti e trenta"
        val expected = "Ci vediamo alle venti e trenta"
        val actual = TextPostProcessor.process(input, "it")
        assertEquals(expected, actual)
    }
}
