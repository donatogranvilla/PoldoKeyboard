package io.github.donatogranvilla.poldokeyboard.tastiera

import android.os.Build
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import io.github.donatogranvilla.poldokeyboard.MainActivity
import io.github.donatogranvilla.poldokeyboard.comune.Appunti
import io.github.donatogranvilla.poldokeyboard.ui.TemaPoldo

/**
 * Tastiera di sola trascrizione, come la digitazione vocale di Google: l'utente ci passa dalla sua tastiera,
 * sceglie una trascrizione, la inserisce nel campo e torna da solo alla tastiera di prima.
 */
class TastieraTrascrizioni : ServizioTastieraCompose() {

    @Composable
    override fun Contenuto() {
        TemaPoldo {
            PannelloTrascrizioni(
                onInserisci = { testo -> inserisci(testo) },
                onCopia = { testo -> Appunti.copia(this, testo) },
                onTornaAllaTastiera = { tornaAllaTastiera() },
                onApriModelli = { MainActivity.apriModelli(this) },
            )
        }
    }

    /**
     * Inserisce il testo nel campo e torna alla tastiera di prima.
     */
    private fun inserisci(testo: String) {
        currentInputConnection?.commitText(testo, 1)
        tornaAllaTastiera()
    }

    /**
     * Torna alla tastiera che l'utente usava prima di questa.
     */
    private fun tornaAllaTastiera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            val inputMethodManager = getSystemService(InputMethodManager::class.java) ?: return
            val token = window.window?.attributes?.token ?: return
            @Suppress("DEPRECATION")
            inputMethodManager.switchToLastInputMethod(token)
        }
    }
}
