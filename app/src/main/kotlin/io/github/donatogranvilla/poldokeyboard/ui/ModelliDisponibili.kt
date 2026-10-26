package io.github.donatogranvilla.poldokeyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.donatogranvilla.poldokeyboard.R
import io.github.donatogranvilla.poldokeyboard.modelli.descrizioneLingue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.github.donatogranvilla.poldokeyboard.localai.engine.ModelloInstallato
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelCatalog
import io.github.donatogranvilla.poldokeyboard.localai.model.ModelManager

/**
 * I modelli installati e quello attivo, per scegliere con quale ritrascrivere un vocale
 * e per dire con quale è stata fatta una trascrizione.
 */
data class ModelliDisponibili(val installati: List<ModelloInstallato>, val idAttivo: String?) {
    /** Il nome di un modello: quello installato, altrimenti quello del catalogo, altrimenti l'id. */
    fun nome(id: String): String = installati.find { it.id == id }?.nome ?: ModelCatalog.findById(id)?.name ?: id
}

/**
 * Legge i modelli installati fuori dal thread principale, e di nuovo ogni volta che cambia [chiave].
 * @return null finché la lettura non è finita.
 */
@Composable
fun rememberModelliDisponibili(chiave: Any?): ModelliDisponibili? {
    val context = LocalContext.current
    val stato = produceState<ModelliDisponibili?>(null, chiave) {
        // Legge anche il catalogo, la prima volta: apre l'APK, meglio farlo qui che nel thread principale
        value = withContext(Dispatchers.IO) {
            val modelManager = ModelManager(context.applicationContext)
            ModelliDisponibili(modelManager.getInstalledModels(), modelManager.getActiveModelId())
        }
    }
    return stato.value
}

/**
 * L'elenco dei modelli installati tra cui scegliere quello con cui ritrascrivere un vocale. Per ogni modello:
 * le lingue, se è quello in uso e se ha prodotto il testo attuale.
 * @param idUsato Il modello che ha prodotto il testo attuale.
 */
@Composable
fun ElencoModelliPerRitrascrivere(
    modelli: ModelliDisponibili,
    idUsato: String?,
    onScelta: (ModelloInstallato) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        for (modello in modelli.installati) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onScelta(modello) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
            ) {
                Text(text = modello.nome, style = MaterialTheme.typography.bodyLarge)
                val note = buildList {
                    add(descrizioneLingue(modello.lingue))
                    if (modello.id == modelli.idAttivo) add(stringResource(R.string.elenco_in_uso))
                    if (modello.id == idUsato) add(stringResource(R.string.elenco_usato_per_questo))
                }
                Text(
                    text = note.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
