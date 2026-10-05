package org.florisboard.lib.localai

import org.florisboard.lib.localai.model.ModelCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verifica che le impronte del catalogo permettano di riconoscere senza ambiguità un file importato.
 */
class CatalogoModelliTest {

    /** Ogni impronta è uno SHA-256 esadecimale minuscolo e compare una sola volta. */
    @Test
    fun testImpronteValideEUniche() {
        val impronte = ModelCatalog.ALL_MODELS.map { it.sha256 }
        for (impronta in impronte) {
            assertTrue(impronta.matches(Regex("[0-9a-f]{64}")), "Impronta non valida: $impronta")
        }
        assertEquals(impronte.size, impronte.toSet().size, "Due modelli hanno la stessa impronta")
    }

    /** Un file si riconosce dalla sua impronta, anche se scritta in maiuscolo. */
    @Test
    fun testRiconoscimentoPerImpronta() {
        val tiny = ModelCatalog.WHISPER_TINY_Q5
        assertEquals(tiny, ModelCatalog.findBySha256(tiny.sha256))
        assertEquals(tiny, ModelCatalog.findBySha256(tiny.sha256.uppercase()))
        assertNull(ModelCatalog.findBySha256("0".repeat(64)))
    }
}
