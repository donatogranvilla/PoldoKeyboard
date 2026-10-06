package io.github.donatogranvilla.poldokeyboard.modelli

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.donatogranvilla.poldokeyboard.ui.TemaPoldo

/**
 * La gestione dei modelli di trascrizione, a tutto schermo. Si apre dall'app e dalla tastiera di trascrizione.
 */
class ModelliActivity : ComponentActivity() {
    private val viewModel: ModelliViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaPoldo {
                SchermataModelli(viewModel = viewModel, onIndietro = ::finish)
            }
        }
    }

    companion object {
        /**
         * Apre la gestione dei modelli. Dalla tastiera, che non è un'activity, la porta in primo piano
         * riusando quella già aperta invece di aprirne un'altra.
         */
        fun apri(context: Context) {
            val intent = Intent(context, ModelliActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            }
            context.startActivity(intent)
        }
    }
}
