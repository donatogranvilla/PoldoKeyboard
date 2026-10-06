package io.github.donatogranvilla.poldokeyboard.archivio

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Accesso alla tabella delle trascrizioni. Come nel resto di FlorisBoard le funzioni sono bloccanti:
 * [ArchivioTrascrizioni] le chiama fuori dal thread principale.
 */
@Dao
interface TrascrizioniDao {
    /** Le trascrizioni più recenti; il flusso si aggiorna anche quando a scrivere è un altro processo. */
    @Query("SELECT * FROM $TABELLA_TRASCRIZIONI ORDER BY creata_il DESC, id DESC LIMIT :limite")
    fun osservaRecenti(limite: Int): Flow<List<Trascrizione>>

    /** Tutto lo storico, dalla trascrizione più recente, per la schermata dell'app. */
    @Query("SELECT * FROM $TABELLA_TRASCRIZIONI ORDER BY creata_il DESC, id DESC")
    fun osservaTutte(): Flow<List<Trascrizione>>

    /** La trascrizione con questo id, o null se è stata eliminata. */
    @Query("SELECT * FROM $TABELLA_TRASCRIZIONI WHERE id = :id")
    fun trova(id: Long): Trascrizione?

    /** Le trascrizioni in questo stato, dalla più vecchia. */
    @Query("SELECT * FROM $TABELLA_TRASCRIZIONI WHERE stato = :stato ORDER BY creata_il ASC, id ASC")
    fun conStato(stato: StatoTrascrizione): List<Trascrizione>

    /** I nomi delle copie dei vocali, per cancellarle insieme alle righe. */
    @Query("SELECT nome_file_audio FROM $TABELLA_TRASCRIZIONI")
    fun nomiFileAudio(): List<String>

    /** Quante trascrizioni hanno aggiornamenti che l'utente non ha ancora visto. */
    @Query("SELECT COUNT(*) FROM $TABELLA_TRASCRIZIONI WHERE NOT vista")
    fun contaNonViste(): Int

    /** Inserisce una nuova trascrizione e ne restituisce l'id. */
    @Insert
    fun inserisci(trascrizione: Trascrizione): Long

    /** Riscrive tutti i campi della trascrizione con lo stesso id. */
    @Update
    fun aggiorna(trascrizione: Trascrizione)

    /** Aggiorna solo l'avanzamento, la scrittura più frequente durante l'elaborazione. */
    @Query("UPDATE $TABELLA_TRASCRIZIONI SET avanzamento = :avanzamento WHERE id = :id")
    fun aggiornaAvanzamento(id: Long, avanzamento: Float)

    /** Segna come visti tutti gli aggiornamenti. */
    @Query("UPDATE $TABELLA_TRASCRIZIONI SET vista = 1 WHERE NOT vista")
    fun segnaTutteViste()

    /** Elimina la riga con questo id. */
    @Query("DELETE FROM $TABELLA_TRASCRIZIONI WHERE id = :id")
    fun elimina(id: Long)

    /** Elimina tutte le righe. */
    @Query("DELETE FROM $TABELLA_TRASCRIZIONI")
    fun eliminaTutte()
}

/**
 * Database dello storico delle trascrizioni, condiviso dal processo dell'app e da quello
 * del servizio di trascrizione.
 */
@Database(entities = [Trascrizione::class], version = 1)
abstract class DatabaseTrascrizioni : RoomDatabase() {
    /** Il DAO della tabella delle trascrizioni. */
    abstract fun trascrizioniDao(): TrascrizioniDao

    companion object {
        /**
         * Apre il database. L'invalidazione multi-istanza avvisa ogni processo delle scritture degli altri:
         * così app e tastiera vedono l'avanzamento calcolato dal servizio.
         * Niente migrazione distruttiva, perché cancellerebbe lo storico: ogni nuova versione dello schema
         * richiede una migrazione.
         */
        fun nuovo(context: Context): DatabaseTrascrizioni {
            return Room
                .databaseBuilder(context, DatabaseTrascrizioni::class.java, TABELLA_TRASCRIZIONI)
                .enableMultiInstanceInvalidation()
                .build()
        }
    }
}
