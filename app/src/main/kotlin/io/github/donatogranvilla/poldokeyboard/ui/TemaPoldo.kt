package io.github.donatogranvilla.poldokeyboard.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Tema dell'app e della tastiera di trascrizione: da Android 12 prende i colori dallo sfondo del telefono,
 * come le app di sistema; prima usa i colori standard di Material 3. Segue il tema chiaro o scuro.
 */
@Composable
fun TemaPoldo(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scuro = isSystemInDarkTheme()
    val colori = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (scuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        scuro -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colori, content = content)
}
