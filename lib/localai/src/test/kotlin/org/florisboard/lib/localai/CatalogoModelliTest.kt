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

    /**
     * Ogni modello dichiara il motore della sua architettura, altrimenti verrebbe caricato nel motore sbagliato,
     * e lingue con codici ISO di due lettere.
     */
    @Test
    fun testMotoriELingueCoerenti() {
        for (modello in ModelCatalog.ALL_MODELS) {
            assertEquals(modello.architettura.motore, modello.tipoMotore, "Motore sbagliato per ${modello.id}")
            for (lingua in modello.lingue) {
                assertTrue(lingua.matches(Regex("[a-z]{2}")), "Lingua non valida in ${modello.id}: $lingua")
            }
        }
    }

    /** Per una lingua vengono prima i modelli specializzati, poi i multilingue che la coprono. */
    @Test
    fun testModelliPerLingua() {
        val italiani = ModelCatalog.perLingua("it")
        assertTrue(italiani.first().specializzato)
        assertTrue(italiani.all { it.copre("it") })
        assertTrue(italiani.any { !it.specializzato })
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
