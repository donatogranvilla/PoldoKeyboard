package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.LayoutDirection
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.localai.LocalAiManager

/**
 * Activity trasparente che ospita la gestione dei modelli. Il selettore di file di sistema
 * restituisce il risultato solo a un'Activity, quindi dalla finestra della tastiera non si può usare.
 */
class GestioneModelliActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs by FlorisPreferenceStore
            ProvideLocalizedResources(
                resourcesContext = this,
                appName = R.string.floris_app_name,
                forceLayoutDirection = LayoutDirection.Ltr,
            ) {
                val theme by prefs.other.settingsTheme.collectAsState()
                FlorisAppTheme(theme) {
                    val localAiManager = remember { LocalAiManager(applicationContext) }
                    ModelManagementDialog(
                        localAiManager = localAiManager,
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Apre la gestione dei modelli da qualsiasi contesto, compresa la tastiera.
         */
        fun avvia(context: Context) {
            val intent = Intent(context, GestioneModelliActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
