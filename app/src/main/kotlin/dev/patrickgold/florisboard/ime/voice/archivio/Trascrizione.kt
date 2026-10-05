package dev.patrickgold.florisboard.ime.voice.archivio

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Nome della tabella, usato anche come nome del file del database. */
internal const val TABELLA_TRASCRIZIONI = "trascrizioni"

/**
 * Fasi di una trascrizione: in coda appena il vocale viene condiviso, in corso mentre il motore lavora,
 * poi completata o in errore.
 */
enum class StatoTrascrizione {
    IN_CODA,
    IN_CORSO,
    COMPLETATA,
    ERRORE,
}

/**
 * Un vocale condiviso con la tastiera e la sua trascrizione. La stessa riga è un elemento della coda
 * del servizio e una voce dello storico, che si conserva fino alla pulizia manuale.
 * @property nomeFileAudio Nome della copia privata del vocale, tenuta per poterlo ritrascrivere con un altro modello.
 * @property nomeOriginale Nome del file condiviso, se l'app di provenienza lo espone.
 * @property avanzamento Frazione elaborata tra 0 e 1, aggiornata dal servizio.
 * @property tentativi Quante volte il servizio l'ha presa in carico.
 * @property vista false finché l'utente non ha visto l'ultimo aggiornamento nella scheda Trascrizioni.
 */
@Entity(tableName = TABELLA_TRASCRIZIONI)
data class Trascrizione(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "creata_il") val creataIl: Long,
    @ColumnInfo(name = "nome_file_audio") val nomeFileAudio: String,
    @ColumnInfo(name = "nome_originale") val nomeOriginale: String? = null,
    val stato: StatoTrascrizione = StatoTrascrizione.IN_CODA,
    val avanzamento: Float = 0f,
    val tentativi: Int = 0,
    val testo: String? = null,
    val errore: String? = null,
    @ColumnInfo(name = "id_modello") val idModello: String? = null,
    @ColumnInfo(name = "durata_audio_ms") val durataAudioMs: Long? = null,
    @ColumnInfo(name = "tempo_inferenza_ms") val tempoInferenzaMs: Long? = null,
    @ColumnInfo(name = "conclusa_il") val conclusaIl: Long? = null,
    val vista: Boolean = false,
)

/**
 * Prese in carico oltre le quali un vocale interrotto non torna più in coda da solo:
 * dopo due interruzioni è probabile che sia il vocale stesso a far chiudere il processo.
 */
const val MAX_TENTATIVI = 2

/**
 * Decide cosa diventa una trascrizione rimasta [StatoTrascrizione.IN_CORSO] all'avvio del servizio:
 * il processo che la elaborava è terminato prima della fine (chiusura forzata, memoria esaurita, riavvio).
 * Sotto [MAX_TENTATIVI] torna in coda; oltre diventa un errore, da riprovare a mano.
 * @return La trascrizione da salvare al posto di questa.
 */
fun Trascrizione.dopoInterruzione(): Trascrizione {
    if (tentativi < MAX_TENTATIVI) {
        return copy(stato = StatoTrascrizione.IN_CODA, avanzamento = 0f)
    }
    // Errore e non nuova coda: un vocale che fa chiudere il processo lo farebbe a ogni avvio del servizio
    return copy(
        stato = StatoTrascrizione.ERRORE,
        errore = "Trascrizione interrotta $tentativi volte. Riprova, magari con un modello più leggero.",
        conclusaIl = System.currentTimeMillis(),
        vista = false,
    )
}
