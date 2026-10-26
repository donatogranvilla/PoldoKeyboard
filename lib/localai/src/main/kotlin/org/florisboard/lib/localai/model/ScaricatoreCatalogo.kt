package org.florisboard.lib.localai.model

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** La connessione a GitHub non è riuscita: niente rete, server irraggiungibile o tempo scaduto. */
class ConnessioneNonRiuscitaException(causa: IOException) :
    IOException("Connessione a GitHub non riuscita.", causa)

/** GitHub ha risposto con un codice diverso da 200, come 404 se il catalogo non è sul branch predefinito. */
class RispostaServerException(val codice: Int) : IOException("GitHub ha risposto con il codice $codice.")

/**
 * Scarica il catalogo dei modelli pubblicato su GitHub. È l'unico file dell'app che usa la rete, e la CI fallisce se
 * classi di rete compaiono altrove: un solo indirizzo fisso, solo HTTPS, nessun redirect verso altri server, e
 * nessun dato inviato oltre alla richiesta del file.
 */
internal object ScaricatoreCatalogo {
    /**
     * Il catalogo sul branch predefinito del repository ("HEAD"). È lo stesso file incluso nell'app: se si sposta,
     * le versioni già installate non lo trovano più.
     */
    const val INDIRIZZO =
        "https://raw.githubusercontent.com/donatogranvilla/PoldoKeyboard/HEAD/lib/localai/src/main/resources/catalogo_modelli.json"

    /** Anche con centinaia di modelli il catalogo resta sotto 1 MB: un file più grande non è il catalogo. */
    private const val DIMENSIONE_MASSIMA = 1024 * 1024

    private const val ATTESA_MASSIMA_MS = 15_000

    /**
     * Scarica il catalogo. Va chiamata fuori dal thread principale.
     * @return Il testo del catalogo, da leggere con [LetturaCatalogo].
     * @throws ConnessioneNonRiuscitaException se la connessione non riesce.
     * @throws RispostaServerException se GitHub risponde con un codice diverso da 200.
     * @throws CatalogoNonValidoException se il file supera la dimensione massima.
     */
    fun scarica(): String {
        val connessione = URL(INDIRIZZO).openConnection() as HttpsURLConnection
        try {
            connessione.connectTimeout = ATTESA_MASSIMA_MS
            connessione.readTimeout = ATTESA_MASSIMA_MS
            connessione.instanceFollowRedirects = false
            connessione.useCaches = false
            val codice = connessione.responseCode
            if (codice != HttpURLConnection.HTTP_OK) throw RispostaServerException(codice)
            return connessione.inputStream.use(::leggiConLimite).decodeToString()
        } catch (e: RispostaServerException) {
            throw e
        } catch (e: IOException) {
            throw ConnessioneNonRiuscitaException(e)
        } finally {
            connessione.disconnect()
        }
    }

    /** Legge tutto il flusso, fermandosi se supera [DIMENSIONE_MASSIMA]. */
    private fun leggiConLimite(ingresso: InputStream): ByteArray {
        val uscita = ByteArrayOutputStream()
        val blocco = ByteArray(8 * 1024)
        while (true) {
            val letti = ingresso.read(blocco)
            if (letti < 0) break
            if (uscita.size() + letti > DIMENSIONE_MASSIMA) {
                throw CatalogoNonValidoException("Il file scaricato è troppo grande per essere il catalogo.")
            }
            uscita.write(blocco, 0, letti)
        }
        return uscita.toByteArray()
    }
}
