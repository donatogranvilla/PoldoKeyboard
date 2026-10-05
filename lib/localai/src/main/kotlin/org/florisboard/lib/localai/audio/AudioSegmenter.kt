package org.florisboard.lib.localai.audio

import org.florisboard.lib.localai.engine.SegmentoVoce
import kotlin.math.sqrt

/**
 * Segmentatore audio intelligente per la gestione di messaggi vocali lunghi di WhatsApp (> 60s).
 *
 * I modelli Whisper operano internamente su finestre massime di 30 secondi.
 * Per messaggi vocali lunghi (60s, 90s, 180s o superiori), questo modulo suddivide
 * il flusso audio nei punti di silenzio naturale (pause del parlato), evitando di troncare
 * parole a metà e prevenendo problemi di memoria (OOM) o allucinazioni sui dispositivi mobili.
 */
object AudioSegmenter {
    private const val SAMPLE_RATE = 16000
    private const val MAX_WINDOW_SAMPLES = 28 * SAMPLE_RATE // 28 secondi per finestra (sicuro entro i 30s di Whisper)
    private const val MIN_WINDOW_SAMPLES = 10 * SAMPLE_RATE // Minimo 10 secondi prima di cercare un silenzio
    private const val SILENCE_FRAME_SIZE = 1600             // Finestra di 100ms per calcolo energia RMS
    private const val SILENCE_HOP_SIZE = 800                // Passo di 50ms

    /**
     * Suddivide un array di campioni a 16 kHz in segmenti coerenti pronti per l'inferenza.
     * @param campioni Array float normalizzato [-1.0f, 1.0f] a 16.000 Hz Mono.
     * @return Lista di [SegmentoVoce] con marcatura temporale accurata.
     */
    fun segmenta(campioni: FloatArray): List<SegmentoVoce> {
        if (campioni.isEmpty()) return emptyList()

        val totaleCampioni = campioni.size
        val durataTotaleMs = (totaleCampioni / 16.0).toLong()

        // Se l'audio dura meno di 30 secondi, non serve suddividerlo
        if (totaleCampioni <= 30 * SAMPLE_RATE) {
            return listOf(
                SegmentoVoce(
                    campioniAudio = campioni,
                    timestampInizioMs = 0L,
                    timestampFineMs = durataTotaleMs
                )
            )
        }

        val segmenti = mutableListOf<SegmentoVoce>()
        var offsetInizio = 0

        while (offsetInizio < totaleCampioni) {
            val campioniRimanenti = totaleCampioni - offsetInizio

            if (campioniRimanenti <= MAX_WINDOW_SAMPLES) {
                // Ultimo segmento finale
                val bufferFinale = FloatArray(campioniRimanenti)
                System.arraycopy(campioni, offsetInizio, bufferFinale, 0, campioniRimanenti)
                val inizioMs = (offsetInizio / 16.0).toLong()
                segmenti.add(
                    SegmentoVoce(
                        campioniAudio = bufferFinale,
                        timestampInizioMs = inizioMs,
                        timestampFineMs = durataTotaleMs
                    )
                )
                break
            }

            // Cerchiamo il punto di silenzio ottimale nella finestra [MIN_WINDOW, MAX_WINDOW]
            val cercaInizio = offsetInizio + MIN_WINDOW_SAMPLES
            val cercaFine = offsetInizio + MAX_WINDOW_SAMPLES

            val puntoTaglio = trovaPuntoSilenzio(campioni, cercaInizio, cercaFine)
            val lunghezzaSegmento = puntoTaglio - offsetInizio

            val buffer = FloatArray(lunghezzaSegmento)
            System.arraycopy(campioni, offsetInizio, buffer, 0, lunghezzaSegmento)

            val inizioMs = (offsetInizio / 16.0).toLong()
            val fineMs = (puntoTaglio / 16.0).toLong()

            segmenti.add(
                SegmentoVoce(
                    campioniAudio = buffer,
                    timestampInizioMs = inizioMs,
                    timestampFineMs = fineMs
                )
            )

            offsetInizio = puntoTaglio
        }

        return segmenti
    }

    /**
     * Trova il punto a minore energia sonora (pausa respirazione o silenzio naturale)
     * all'interno dell'intervallo [daIndice, aIndice].
     */
    private fun trovaPuntoSilenzio(campioni: FloatArray, daIndice: Int, aIndice: Int): Int {
        var minRms = Float.MAX_VALUE
        var migliorPunto = aIndice // Default: taglia al limite massimo se non trova pause

        var pos = daIndice
        while (pos + SILENCE_FRAME_SIZE <= aIndice) {
            var sommaQuadrati = 0.0f
            for (i in 0 until SILENCE_FRAME_SIZE) {
                val s = campioni[pos + i]
                sommaQuadrati += s * s
            }
            val rms = sqrt(sommaQuadrati / SILENCE_FRAME_SIZE)

            if (rms < minRms) {
                minRms = rms
                // Il punto ottimale di taglio è a metà della finestra silenziosa
                migliorPunto = pos + (SILENCE_FRAME_SIZE / 2)
            }

            // Se troviamo un silenzio molto profondo (RMS < 0.005), possiamo fermarci subito
            if (rms < 0.005f) {
                return pos + (SILENCE_FRAME_SIZE / 2)
            }

            pos += SILENCE_HOP_SIZE
        }

        return migliorPunto
    }
}
