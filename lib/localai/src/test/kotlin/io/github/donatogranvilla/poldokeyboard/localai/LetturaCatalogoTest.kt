package io.github.donatogranvilla.poldokeyboard.localai

import io.github.donatogranvilla.poldokeyboard.localai.model.CatalogoNonValidoException
import io.github.donatogranvilla.poldokeyboard.localai.model.LetturaCatalogo
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows

class LetturaCatalogoTest {

    private fun voce(
        id: String,
        architettura: String = "GGML",
        motore: String = "WHISPER_CPP",
        download: String = "https://huggingface.co/esempio/$id.bin",
        descrizione: String = "Un modello di prova.",
    ) = """
        {"id": "$id", "name": "Modello $id", "tipoMotore": "$motore", "architettura": "$architettura",
         "sizeBytes": 1000, "sha256": "${"a".repeat(64)}", "downloadUrl": "$download",
         "description": "$descrizione"}
    """.trimIndent()

    private fun catalogo(vararg voci: String) = voci.joinToString(prefix = "[", postfix = "]")

    /** Il catalogo incluso nell'app è tutto valido: una voce sbagliata a mano fa fallire questo test. */
    @Test
    fun testCatalogoInclusoTuttoValido() {
        val testo = checkNotNull(ModelCatalog::class.java.getResourceAsStream("/catalogo_modelli.json"))
            .bufferedReader().use { it.readText() }
        val letto = LetturaCatalogo.leggi(testo)
        assertEquals(0, letto.vociScartate)
        assertEquals(ModelCatalog.inclusi.size, letto.modelli.size)
    }

    /** Una voce che questa versione non conosce si scarta, le altre restano. */
    @Test
    fun testVoceSconosciutaScartata() {
        val letto = LetturaCatalogo.leggi(
            catalogo(
                voce("a"),
                voce("b", architettura = "ARCHITETTURA_FUTURA", motore = "SHERPA_ONNX"),
                voce("c", download = "http://non-sicuro.example/c.bin"),
                voce("d", architettura = "NEMO_TRANSDUCER", motore = "WHISPER_CPP"),
                voce("a", descrizione = "Doppione."),
            )
        )
        assertEquals(listOf("a"), letto.modelli.map { it.id })
        assertEquals("Un modello di prova.", letto.modelli.single().description)
        assertEquals(4, letto.vociScartate)
    }

    /** Un testo che non è un elenco di modelli non è un catalogo. */
    @Test
    fun testTestoNonValido() {
        assertThrows(CatalogoNonValidoException::class.java) { LetturaCatalogo.leggi("<html>404</html>") }
        assertThrows(CatalogoNonValidoException::class.java) { LetturaCatalogo.leggi("""{"id": "a"}""") }
        assertThrows(CatalogoNonValidoException::class.java) { LetturaCatalogo.leggi("[]") }
    }

    /** Una voce scaricata aggiorna quella con lo stesso id al suo posto; le nuove vanno in fondo; nessuna sparisce. */
    @Test
    fun testUnione() {
        val inclusi = LetturaCatalogo.leggi(catalogo(voce("a"), voce("b"), voce("c"))).modelli
        val scaricati = LetturaCatalogo.leggi(catalogo(voce("b", descrizione = "Aggiornato."), voce("z"))).modelli
        val uniti = LetturaCatalogo.unisci(inclusi, scaricati)
        assertEquals(listOf("a", "b", "c", "z"), uniti.map { it.id })
        assertEquals("Aggiornato.", uniti[1].description)
    }
}
