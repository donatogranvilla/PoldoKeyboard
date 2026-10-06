package dev.patrickgold.florisboard.ime.voice

import android.content.Context
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.keyboard3.ImeController
import dev.patrickgold.florisboard.ime.voice.archivio.ArchivioTrascrizioni
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidKeyguardManager
import org.florisboard.lib.android.systemService

/**
 * Apre da sola la scheda Trascrizioni quando la tastiera ricompare e c'è un aggiornamento non ancora visto:
 * di solito si torna nella chat dopo aver condiviso un vocale.
 */
object AperturaAutomaticaTrascrizioni {
    /**
     * Da chiamare quando la finestra della tastiera diventa visibile. L'archivio si interroga fuori dal thread
     * principale, poi la scheda si apre solo se l'utente è ancora sulla tastiera delle lettere.
     */
    fun alRientro(context: Context, scope: CoroutineScope, imeController: ImeController) {
        scope.launch {
            // A telefono bloccato la scheda nasconde i testi: aprirla non servirebbe
            val keyguardManager = context.systemService(AndroidKeyguardManager::class)
            if (keyguardManager.isDeviceLocked || keyguardManager.isKeyguardLocked) return@launch

            val ciSonoNovita = try {
                ArchivioTrascrizioni.istanza(context).ciSonoNovita()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Un problema dell'archivio non deve mai far chiudere la tastiera
                false
            }
            if (!ciSonoNovita) return@launch

            imeController.updateState {
                // Se nel frattempo l'utente ha aperto emoji o appunti, non lo si interrompe
                if (state.flags.imeUiMode == ImeUiMode.TEXT) {
                    state = state.copy(flags = state.flags.withImeUiMode(ImeUiMode.VOICE_TRANSCRIPTION))
                }
            }
        }
    }
}
