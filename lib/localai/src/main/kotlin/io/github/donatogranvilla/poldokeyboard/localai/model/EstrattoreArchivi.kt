package io.github.donatogranvilla.poldokeyboard.localai.model

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Estrae gli archivi dei modelli sherpa-onnx, `.tar.bz2` o `.zip`, nella cartella del modello.
 * Gli archivi ufficiali tengono i file in un'unica cartella principale: in quel caso la toglie,
 * così i file finiscono direttamente nella cartella del modello.
 */
internal object EstrattoreArchivi {

    /**
     * Estrae l'archivio nella cartella indicata, che viene creata.
     * @param avanzamento Frazione dell'archivio letta, tra 0 e 1.
     */
    fun estrai(archivio: File, formato: FormatoFileModello, destinazione: File, avanzamento: (Float) -> Unit) {
        destinazione.mkdirs()
        val ingresso = IngressoContato(archivio.inputStream().buffered(), archivio.length(), avanzamento)
        when (formato) {
            FormatoFileModello.ARCHIVIO_TAR_BZ2 -> TarArchiveInputStream(BZip2CompressorInputStream(ingresso)).use { tar ->
                while (true) {
                    val voce = tar.nextEntry ?: break
                    if (!voce.isDirectory) scrivi(voce.name, tar, destinazione)
                }
            }
            FormatoFileModello.ARCHIVIO_ZIP -> ZipInputStream(ingresso).use { zip ->
                while (true) {
                    val voce = zip.nextEntry ?: break
                    if (!voce.isDirectory) scrivi(voce.name, zip, destinazione)
                }
            }
            else -> throw IllegalArgumentException("Il file non è un archivio .tar.bz2 o .zip.")
        }
        appiattisci(destinazione)
    }

    /**
     * Scrive una voce dell'archivio, rifiutando i percorsi che uscirebbero dalla cartella del modello.
     */
    private fun scrivi(nomeVoce: String, ingresso: InputStream, destinazione: File) {
        val file = File(destinazione, nomeVoce).canonicalFile
        require(file.path.startsWith(destinazione.canonicalPath + File.separator)) {
            "Archivio non valido: il percorso $nomeVoce esce dalla cartella del modello."
        }
        file.parentFile?.mkdirs()
        file.outputStream().use { uscita -> ingresso.copyTo(uscita) }
    }

    /**
     * Se in cima c'è solo una cartella, ne porta il contenuto al suo posto.
     */
    private fun appiattisci(cartella: File) {
        val unica = cartella.listFiles()?.singleOrNull() ?: return
        if (!unica.isDirectory) return
        unica.listFiles().orEmpty().forEach { it.renameTo(File(cartella, it.name)) }
        unica.delete()
    }

    /**
     * Conta i byte letti dall'archivio compresso e segnala l'avanzamento solo quando cambia la percentuale,
     * per non sommergere l'interfaccia di aggiornamenti.
     */
    private class IngressoContato(
        ingresso: InputStream,
        private val totale: Long,
        private val avanzamento: (Float) -> Unit,
    ) : FilterInputStream(ingresso) {
        private var letti = 0L
        private var ultimaPercentuale = -1L

        override fun read(): Int = super.read().also { if (it >= 0) aggiungi(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) aggiungi(it.toLong()) }

        private fun aggiungi(quanti: Long) {
            letti += quanti
            val percentuale = if (totale > 0) letti * 100 / totale else 0
            if (percentuale != ultimaPercentuale) {
                ultimaPercentuale = percentuale
                avanzamento((percentuale / 100f).coerceIn(0f, 1f))
            }
        }
    }
}
