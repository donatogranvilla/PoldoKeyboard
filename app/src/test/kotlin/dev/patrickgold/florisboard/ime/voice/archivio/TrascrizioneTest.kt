package dev.patrickgold.florisboard.ime.voice.archivio

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Verifica la politica di ripresa delle trascrizioni interrotte da un processo terminato a metà lavoro.
 */
class TrascrizioneTest : FunSpec({
    val interrotta = Trascrizione(
        id = 7,
        creataIl = 1_000L,
        nomeFileAudio = "vocale.opus",
        stato = StatoTrascrizione.IN_CORSO,
        avanzamento = 0.4f,
        vista = true,
    )

    test("sotto la soglia torna in coda e riparte da capo") {
        val ripresa = interrotta.copy(tentativi = MAX_TENTATIVI - 1).dopoInterruzione()
        ripresa.stato shouldBe StatoTrascrizione.IN_CODA
        ripresa.avanzamento shouldBe 0f
        ripresa.tentativi shouldBe MAX_TENTATIVI - 1
    }

    test("alla soglia diventa un errore da mostrare nella scheda") {
        val ripresa = interrotta.copy(tentativi = MAX_TENTATIVI).dopoInterruzione()
        ripresa.stato shouldBe StatoTrascrizione.ERRORE
        ripresa.errore.shouldNotBeNull()
        ripresa.conclusaIl.shouldNotBeNull()
        ripresa.vista shouldBe false
    }

    test("la riga e il suo vocale restano gli stessi") {
        val ripresa = interrotta.copy(tentativi = MAX_TENTATIVI).dopoInterruzione()
        ripresa.id shouldBe interrotta.id
        ripresa.nomeFileAudio shouldBe interrotta.nomeFileAudio
    }
})
