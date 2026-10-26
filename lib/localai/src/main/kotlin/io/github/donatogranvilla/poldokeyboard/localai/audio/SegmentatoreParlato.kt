package io.github.donatogranvilla.poldokeyboard.localai.audio

import io.github.donatogranvilla.poldokeyboard.localai.engine.SegmentoVoce

/**
 * Un tratto di parlato trovato dal rilevatore, in campioni a 16 kHz: da [inizio] incluso a [fine] escluso.
 */
data class TrattoParlato(val inizio: Int, val fine: Int) {
    val lunghezza: Int get() = fine - inizio
}

/**
 * Unisce i tratti di parlato in blocchi da trascrivere in una volta, fino alla durata massima del motore.
 *
 * Trascrivere ogni frase da sola costa più chiamate e fa allucinare di più sui frammenti; unire fino a circa
 * 30 s dà a Whisper il contesto per cui è addestrato. Il non-parlato tra un tratto e l'altro resta finché è
 * breve, perché le pause aiutano la punteggiatura; quello lungo si accorcia, così meno audio da elaborare.
 */
object SegmentatoreParlato {
    private const val CAMPIONI_AL_MS = 16

    /** Le pause più lunghe di così si accorciano a questa durata, metà per lato. */
    const val PAUSA_MASSIMA_MS = 1000

    /**
     * @param campioni L'audio intero a 16 kHz mono.
     * @param tratti I tratti di parlato, in ordine e senza sovrapposizioni, come li dà il rilevatore.
     * @param durataMassimaS La durata massima di un blocco per il motore che trascrive.
     * @return I blocchi con i tempi riferiti all'audio originale; vuoto se non ci sono tratti.
     */
    fun segmenta(
        campioni: FloatArray,
        tratti: List<TrattoParlato>,
        durataMassimaS: Int,
        pausaMassimaMs: Int = PAUSA_MASSIMA_MS,
    ): List<SegmentoVoce> {
        val massimo = durataMassimaS * 1000 * CAMPIONI_AL_MS
        val pausaMassima = pausaMassimaMs * CAMPIONI_AL_MS
        val blocchi = mutableListOf<SegmentoVoce>()
        val pezzi = mutableListOf<TrattoParlato>()

        fun chiudiBlocco() {
            if (pezzi.isEmpty()) return
            val audio = FloatArray(pezzi.sumOf { it.lunghezza })
            var posizione = 0
            for (pezzo in pezzi) {
                System.arraycopy(campioni, pezzo.inizio, audio, posizione, pezzo.lunghezza)
                posizione += pezzo.lunghezza
            }
            blocchi.add(
                SegmentoVoce(
                    campioniAudio = audio,
                    timestampInizioMs = (pezzi.first().inizio / CAMPIONI_AL_MS).toLong(),
                    timestampFineMs = (pezzi.last().fine / CAMPIONI_AL_MS).toLong(),
                )
            )
            pezzi.clear()
        }

        for (tratto in tratti.map { TrattoParlato(it.inizio.coerceIn(0, campioni.size), it.fine.coerceIn(0, campioni.size)) }) {
            // Il rilevatore divide già il parlato più lungo del massimo: questo taglio netto è solo una sicurezza
            for (inizio in tratto.inizio until tratto.fine step massimo) {
                val parte = TrattoParlato(inizio, minOf(inizio + massimo, tratto.fine))
                val pausa = pezzi.lastOrNull()?.let { precedente -> pausaTra(precedente.fine, parte.inizio, pausaMassima) }
                    .orEmpty()
                if (pezzi.sumOf { it.lunghezza } + pausa.sumOf { it.lunghezza } + parte.lunghezza > massimo) {
                    chiudiBlocco()
                    pezzi.add(parte)
                } else {
                    pezzi.addAll(pausa)
                    pezzi.add(parte)
                }
            }
        }
        chiudiBlocco()
        return blocchi
    }

    /**
     * Il non-parlato da tenere tra due tratti: tutto se è breve, altrimenti l'inizio e la fine della pausa.
     */
    private fun pausaTra(fine: Int, inizio: Int, pausaMassima: Int): List<TrattoParlato> = when {
        inizio <= fine -> emptyList()
        inizio - fine <= pausaMassima -> listOf(TrattoParlato(fine, inizio))
        else -> listOf(
            TrattoParlato(fine, fine + pausaMassima / 2),
            TrattoParlato(inizio - pausaMassima / 2, inizio),
        )
    }
}
