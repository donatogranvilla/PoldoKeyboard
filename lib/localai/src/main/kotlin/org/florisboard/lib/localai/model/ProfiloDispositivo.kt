package org.florisboard.lib.localai.model

import android.app.ActivityManager
import android.content.Context

enum class FasciaDispositivo(val etichetta: String, val descrizione: String) {
    BASE("Entry-Level", "2-3 GB RAM — Ottimale per modelli ultra-leggeri (Whisper Tiny)"),
    MEDIA("Media Gamma", "4-6 GB RAM — Ottimale per modelli bilanciati (Whisper Base)"),
    ALTA("Top di Gamma", "8+ GB RAM — Supporta modelli ad alta precisione (Whisper Small)")
}

data class InfoHardwareDispositivo(
    val ramTotaleMb: Int,
    val ramDisponibileMb: Int,
    val numCoreCpu: Int,
    val fascia: FasciaDispositivo,
    val idModelloConsigliato: String,
    val motivazioneConsiglio: String,
)

object RilevatoreDispositivo {
    fun rileva(context: Context): InfoHardwareDispositivo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = (memInfo.totalMem / (1024 * 1024)).toInt()
        val availRamMb = (memInfo.availMem / (1024 * 1024)).toInt()
        val cores = Runtime.getRuntime().availableProcessors()

        val fascia = when {
            totalRamMb < 3500 -> FasciaDispositivo.BASE
            totalRamMb < 6500 -> FasciaDispositivo.MEDIA
            else -> FasciaDispositivo.ALTA
        }

        val (consigliatoId, motivazione) = when (fascia) {
            FasciaDispositivo.BASE -> Pair(
                "whisper-tiny-q5_1",
                "RAM totale: ~${totalRamMb / 1024} GB. Consigliato Whisper Tiny (~31MB) per garantire velocità istantanea e zero rallentamenti sul telefono."
            )
            FasciaDispositivo.MEDIA -> Pair(
                "whisper-base-q5_1",
                "RAM totale: ~${(totalRamMb + 512) / 1024} GB. Consigliato Whisper Base (~57MB) per un perfetto equilibrio tra accuratezza della punteggiatura e fluidità."
            )
            FasciaDispositivo.ALTA -> Pair(
                "whisper-small-q5_1",
                "RAM totale: ~${(totalRamMb + 512) / 1024} GB ($cores core). Il dispositivo è potente: consigliato Whisper Small (~182MB) per massima accuratezza con nomi propri e vocali rumorosi."
            )
        }

        return InfoHardwareDispositivo(
            ramTotaleMb = totalRamMb,
            ramDisponibileMb = availRamMb,
            numCoreCpu = cores,
            fascia = fascia,
            idModelloConsigliato = consigliatoId,
            motivazioneConsiglio = motivazione
        )
    }
}
