package io.github.donatogranvilla.poldokeyboard.modelli

import android.content.Context
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.donatogranvilla.poldokeyboard.R
import io.github.donatogranvilla.poldokeyboard.localai.engine.ArchitetturaModello
import io.github.donatogranvilla.poldokeyboard.localai.model.FormatoFileModello
import java.text.Collator
import java.util.Locale

/**
 * Le lingue dei vocali che l'utente riceve, con i codici ISO 639-1 usati dai modelli. Il marocchino (darija)
 * non ha un codice suo nei modelli Whisper: si trascrive come arabo, "ar".
 */
val LINGUE_VOCALI = listOf("it", "en", "es", "fr", "de", "pt", "ca", "ru", "hr", "sl", "sq", "el", "tr", "ar")

/** Codice dell'arabo, che nell'app comprende il marocchino. */
private const val CODICE_ARABO = "ar"

/** Quante lingue elencare per un modello prima di riassumere le altre in un numero. */
private const val MAX_LINGUE_ELENCATE = 4

/** Un megabyte, per convertire la RAM consigliata dei modelli. */
private const val BYTE_PER_MB = 1024L * 1024

/**
 * Il nome della lingua nella lingua del telefono, con l'iniziale maiuscola; per l'arabo ricorda che vale
 * anche per il marocchino.
 */
@Composable
fun nomeLingua(codice: String): String {
    if (codice == CODICE_ARABO) return stringResource(R.string.lingua_arabo_marocchino)
    val locale = Locale.getDefault()
    return Locale.forLanguageTag(codice).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

/**
 * Le lingue dei vocali in ordine alfabetico, secondo i nomi mostrati.
 */
@Composable
fun lingueOrdinate(): List<String> {
    val nomi = LINGUE_VOCALI.associateWith { nomeLingua(it) }
    val ordine = remember { Collator.getInstance() }
    return LINGUE_VOCALI.sortedWith(compareBy(ordine) { nomi.getValue(it) })
}

/**
 * Le lingue di un modello in una riga: tutte, la sola lingua di uno specializzato, oppure l'elenco.
 */
@Composable
fun descrizioneLingue(lingue: List<String>): String = when (lingue.size) {
    0 -> stringResource(R.string.lingue_tutte)
    1 -> stringResource(R.string.lingue_specializzato, nomeLingua(lingue.single()))
    else -> {
        val elenco = lingue.take(MAX_LINGUE_ELENCATE).map { nomeLingua(it) }.joinToString(", ")
        val altre = lingue.size - MAX_LINGUE_ELENCATE
        if (altre > 0) pluralStringResource(R.plurals.lingue_e_altre, altre, elenco, altre) else elenco
    }
}

/** Un voto da 1 a 5 in stelle piene e vuote. */
fun stelle(voto: Int): String {
    val pieno = voto.coerceIn(0, 5)
    return "★".repeat(pieno) + "☆".repeat(5 - pieno)
}

/** Una dimensione in byte nel formato del telefono, come "182 MB". */
fun dimensione(context: Context, byte: Long): String = Formatter.formatShortFileSize(context, byte)

/** La RAM consigliata, espressa in megabyte nel catalogo, nel formato del telefono. */
fun dimensioneRam(context: Context, megabyte: Int): String = dimensione(context, megabyte * BYTE_PER_MB)

/** Il nome del tipo di modello, con il motore che lo esegue. */
@StringRes
fun ArchitetturaModello.etichetta(): Int = when (this) {
    ArchitetturaModello.GGML -> R.string.architettura_ggml
    ArchitetturaModello.NEMO_TRANSDUCER -> R.string.architettura_nemo_transducer
    ArchitetturaModello.ZIPFORMER_TRANSDUCER -> R.string.architettura_zipformer
    ArchitetturaModello.NEMO_CTC -> R.string.architettura_nemo_ctc
    ArchitetturaModello.OMNILINGUAL_CTC -> R.string.architettura_omnilingual
    ArchitetturaModello.MOONSHINE -> R.string.architettura_moonshine
    ArchitetturaModello.SENSE_VOICE -> R.string.architettura_sense_voice
    ArchitetturaModello.WHISPER_ONNX -> R.string.architettura_whisper_onnx
}

/** Come si riconoscono gli archivi di questo tipo, per aiutare a sceglierlo nell'import. */
@StringRes
fun ArchitetturaModello.archiviTipici(): Int = when (this) {
    ArchitetturaModello.GGML -> R.string.file_ggml
    ArchitetturaModello.NEMO_TRANSDUCER -> R.string.archivi_nemo_transducer
    ArchitetturaModello.ZIPFORMER_TRANSDUCER -> R.string.archivi_zipformer
    ArchitetturaModello.NEMO_CTC -> R.string.archivi_nemo_ctc
    ArchitetturaModello.OMNILINGUAL_CTC -> R.string.archivi_omnilingual
    ArchitetturaModello.MOONSHINE -> R.string.archivi_moonshine
    ArchitetturaModello.SENSE_VOICE -> R.string.archivi_sense_voice
    ArchitetturaModello.WHISPER_ONNX -> R.string.archivi_whisper_onnx
}

/** Il file da scaricare per un modello di questo tipo. */
@StringRes
fun ArchitetturaModello.fileDaScaricare(): Int = if (singoloFile) R.string.file_ggml else R.string.file_archivio

/** Il formato riconosciuto nell'import, come lo legge l'utente. */
@StringRes
fun FormatoFileModello.nomeFormato(multilingue: Boolean?): Int = when (this) {
    FormatoFileModello.GGML_WHISPER -> when (multilingue) {
        false -> R.string.formato_ggml_inglese
        else -> R.string.formato_ggml_multilingue
    }
    FormatoFileModello.ARCHIVIO_TAR_BZ2 -> R.string.formato_tar_bz2
    FormatoFileModello.ARCHIVIO_ZIP -> R.string.formato_zip
    else -> R.string.formato_non_importabile
}

/** Perché un file in questo formato non si importa, e cosa scaricare al suo posto. */
@StringRes
fun FormatoFileModello.spiegazione(): Int = when (this) {
    FormatoFileModello.GGUF -> R.string.formato_gguf
    FormatoFileModello.SAFETENSORS -> R.string.formato_safetensors
    FormatoFileModello.PYTORCH -> R.string.formato_pytorch
    FormatoFileModello.ONNX_SINGOLO -> R.string.formato_onnx_singolo
    else -> R.string.formato_sconosciuto
}
