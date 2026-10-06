package io.github.donatogranvilla.poldokeyboard.tastiera

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Tastiera che disegna la sua interfaccia con Compose. Un'Activity fornisce da sola ciclo di vita e stato
 * salvato a Compose; un servizio di input no, quindi li fornisce questa classe.
 */
abstract class ServizioTastieraCompose : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registroCicloVita = LifecycleRegistry(this)
    private val controlloreStatoSalvato = SavedStateRegistryController.create(this)
    private val archivioViewModel = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = registroCicloVita

    override val viewModelStore: ViewModelStore
        get() = archivioViewModel

    override val savedStateRegistry: SavedStateRegistry
        get() = controlloreStatoSalvato.savedStateRegistry

    /**
     * Il contenuto della tastiera.
     */
    @Composable
    abstract fun Contenuto()

    override fun onCreate() {
        super.onCreate()
        controlloreStatoSalvato.performRestore(null)
        registroCicloVita.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onCreateInputView(): View {
        // Compose cerca questi proprietari risalendo dalle viste: nella finestra di una tastiera vanno messi a mano
        val decorView = requireNotNull(window.window).decorView
        decorView.setViewTreeLifecycleOwner(this)
        decorView.setViewTreeViewModelStoreOwner(this)
        decorView.setViewTreeSavedStateRegistryOwner(this)
        return ComposeView(this).apply {
            setContent { Contenuto() }
        }
    }

    override fun onWindowShown() {
        super.onWindowShown()
        registroCicloVita.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        registroCicloVita.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        super.onDestroy()
        registroCicloVita.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        archivioViewModel.clear()
    }
}
