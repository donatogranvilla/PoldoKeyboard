package io.github.donatogranvilla.poldokeyboard.localai.engine

import kotlinx.coroutines.delay
import io.github.donatogranvilla.poldokeyboard.localai.postprocess.TextPostProcessor

/**
 * Motore di trascrizione mock per test, preview e fallback quando nessun modello offline è ancora installato.
 */
class MotoreMock : MotoreTrascrizione {
    private var modello: ModelloInstallato? = null

    override val isModelloCaricato: Boolean get() = modello != null
    override val modelloCorrente: ModelloInstallato? get() = modello

    override suspend fun carica(modello: ModelloInstallato) {
        this.modello = modello
    }

    override suspend fun trascrivi(
        segmenti: List<SegmentoVoce>,
        opzioni: OpzioniTrascrizione,
        avanzamento: (Float) -> Unit,
    ): RisultatoTrascrizione {
        val totalSamples = segmenti.sumOf { it.campioniAudio.size }
        val durationMs = (totalSamples / 16.0).toLong()

        avanzamento(0.2f)
        delay(80)
        avanzamento(0.6f)
        delay(80)
        avanzamento(1.0f)

        val raw = "Questo è un messaggio vocale di prova trascritto localmente con successo."
        val post = if (opzioni.formattaPunteggiatura) {
            TextPostProcessor.process(raw, opzioni.lingua)
        } else {
            raw
        }

        return RisultatoTrascrizione(
            testo = post,
            linguaRilevata = opzioni.lingua ?: "it",
            durataAudioMs = durationMs,
            tempoInferenzaMs = 200,
            segmenti = listOf(SegmentoTrascritto(0, durationMs, post))
        )
    }

    override fun close() {
        modello = null
    }
}
