package io.github.donatogranvilla.poldokeyboard

import android.content.Context
import android.content.Intent
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
 * Schermata dell'app: guida alla configurazione, storico completo delle trascrizioni e gestione dei modelli.
 */
class MainActivity : ComponentActivity() {
    // Cambia a ogni ritorno nell'app, perché la tastiera si attiva nelle impostazioni di sistema
    private var ripresa by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val apriModelli = intent.getBooleanExtra(EXTRA_APRI_MODELLI, false)
        setContent {
            TemaPoldo {
                SchermataPrincipale(ripresa = ripresa, apriModelliAllAvvio = apriModelli)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ripresa++
    }

    companion object {
        private const val EXTRA_APRI_MODELLI = "apri_modelli"

        /**
         * Apre l'app sulla gestione dei modelli, anche dalla tastiera di trascrizione.
         */
        fun apriModelli(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_APRI_MODELLI, true)
            context.startActivity(intent)
        }
    }
}
