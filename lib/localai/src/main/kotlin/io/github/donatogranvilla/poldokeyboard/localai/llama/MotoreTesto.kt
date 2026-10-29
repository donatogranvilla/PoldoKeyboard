package io.github.donatogranvilla.poldokeyboard.localai.llama

import io.github.donatogranvilla.poldokeyboard.localai.engine.CoreCpu
import io.github.donatogranvilla.poldokeyboard.localai.engine.MotoreNonDisponibileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Come un modello di testo vuole ricevere istruzione e testo: un modello del prompt con i marcatori dei turni di chat.
 * llama.cpp sa applicare da sé solo i formati più vecchi, e quello completo di Gemma 4 richiederebbe la sua libreria
 * common, che contiene anche codice di rete; per l'uso dell'app, un'istruzione e un testo, basta sostituire i due segnaposto.
 * @property modello Il prompt con i segnaposto [ISTRUZIONE] e [TESTO]; il token di inizio lo aggiunge llama.cpp.
 */
data class FormatoChat(val modello: String) {
    /** Il prompt completo, pronto per il modello. */
    fun componi(istruzione: String, testo: String): String =
        modello.replace(ISTRUZIONE, istruzione.trim()).replace(TESTO, testo.trim())

    companion object {
        const val ISTRUZIONE = "{istruzione}"
        const val TESTO = "{testo}"

        /** Gemma 4, senza ragionamento: istruzione nel turno di sistema, testo in quello dell'utente. */
        val GEMMA_4 = FormatoChat("<|turn>system\n$ISTRUZIONE<turn|>\n<|turn>user\n$TESTO<turn|>\n<|turn>model\n")
    }
}

/**
 * Esegue un modello di testo GGUF con llama.cpp: riceve un'istruzione e un testo e restituisce la risposta. È generico,
 * così la stessa strada serve a rendere leggibile una trascrizione, a riassumerla o a proporre una risposta.
 * Un modello da qualche GB occupa molta memoria: va caricato solo quando serve e chiuso subito dopo.
 */
class MotoreTesto : AutoCloseable {
    private var handle = 0L
    private var formato: FormatoChat? = null

    val isCaricato: Boolean get() = handle != 0L

    /**
     * Carica il modello: operazione lenta, da fare una volta prima di una serie di richieste.
     * @param contestoToken Quanti token possono stare insieme fra prompt e risposta.
     */
    suspend fun carica(file: File, formatoChat: FormatoChat, contestoToken: Int = CONTESTO_PREDEFINITO) =
        withContext(Dispatchers.IO) {
            require(file.exists()) { "Il file del modello non esiste in: ${file.absolutePath}" }
            if (!LlamaNative.isAvailable) throw MotoreNonDisponibileException("llama.cpp")
            close()
            val nuovo = LlamaNative.carica(file.absolutePath, contestoToken, CoreCpu.threadConsigliati())
            check(nuovo != 0L) { "llama.cpp non è riuscito a caricare il modello: ${file.name}" }
            handle = nuovo
            formato = formatoChat
        }

    /**
     * La risposta del modello all'[istruzione] applicata al [testo]. Si ferma se la coroutine viene annullata.
     * @param massimoToken Quanti token scrivere al più.
     * @param avanzamento Riceve il testo scritto finora, per mostrarlo mentre arriva.
     */
    suspend fun genera(
        istruzione: String,
        testo: String,
        massimoToken: Int,
        avanzamento: (String) -> Unit = {},
    ): String = withContext(Dispatchers.Default) {
        val formatoChat = checkNotNull(formato) { "Nessun modello caricato in MotoreTesto." }
        val lavoro = coroutineContext[Job]
        val risposta = LlamaNative.genera(handle, formatoChat.componi(istruzione, testo), massimoToken) { parziale ->
            avanzamento(parziale)
            lavoro?.isActive != false
        }
        coroutineContext.ensureActive()
        checkNotNull(risposta) { "llama.cpp non è riuscito a elaborare il testo" }.trim()
    }

    /** Interrompe la generazione in corso, da un altro thread. */
    fun ferma() {
        if (handle != 0L) LlamaNative.ferma(handle)
    }

    override fun close() {
        if (handle != 0L) {
            LlamaNative.libera(handle)
            handle = 0L
        }
        formato = null
    }

    companion object {
        /** Basta per un vocale di qualche minuto e la sua versione corretta. */
        const val CONTESTO_PREDEFINITO = 8192
    }
}
