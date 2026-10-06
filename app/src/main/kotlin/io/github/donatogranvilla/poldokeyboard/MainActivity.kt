package io.github.donatogranvilla.poldokeyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import io.github.donatogranvilla.poldokeyboard.ui.SchermataPrincipale
import io.github.donatogranvilla.poldokeyboard.ui.TemaPoldo

/**
 * Schermata dell'app: guida alla configurazione e storico completo delle trascrizioni.
 * La gestione dei modelli è nella sua activity, [io.github.donatogranvilla.poldokeyboard.modelli.ModelliActivity].
 */
class MainActivity : ComponentActivity() {
    // Cambia a ogni ritorno nell'app, perché la tastiera si attiva nelle impostazioni di sistema
    // e i modelli in un'altra schermata
    private var ripresa by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaPoldo {
                SchermataPrincipale(ripresa = ripresa)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ripresa++
    }
}
