package io.github.donatogranvilla.poldokeyboard.comune

import android.content.Context
import android.view.inputmethod.InputMethodManager

/**
 * true se l'utente ha attivato la tastiera di trascrizione nelle impostazioni di sistema.
 */
fun tastieraAbilitata(context: Context): Boolean {
    val inputMethodManager = context.getSystemService(InputMethodManager::class.java) ?: return false
    return inputMethodManager.enabledInputMethodList.any { it.packageName == context.packageName }
}
