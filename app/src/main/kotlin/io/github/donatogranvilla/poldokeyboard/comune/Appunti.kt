package io.github.donatogranvilla.poldokeyboard.comune

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import io.github.donatogranvilla.poldokeyboard.R

/**
 * Copia delle trascrizioni negli appunti di sistema.
 */
object Appunti {
    /**
     * Copia il testo segnandolo come sensibile: sono vocali di altre persone, e da Android 13
     * il sistema non ne mostra l'anteprima.
     */
    fun copia(context: Context, testo: String) {
        val clip = ClipData.newPlainText(context.getString(R.string.app_name), testo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        requireNotNull(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)
        // Da Android 13 la conferma la mostra il sistema; prima la diamo noi
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.trascrizione_copiata, Toast.LENGTH_SHORT).show()
        }
    }
}
