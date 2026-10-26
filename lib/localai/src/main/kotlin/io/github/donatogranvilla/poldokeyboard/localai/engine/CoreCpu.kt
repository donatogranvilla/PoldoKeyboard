package io.github.donatogranvilla.poldokeyboard.localai.engine

import java.io.File

/**
 * Quanti thread dare al motore di trascrizione in base ai core del processore.
 */
object CoreCpu {
    private val CARTELLA_CORE = Regex("cpu\\d+")

    /**
     * I thread consigliati per questo dispositivo, dalle frequenze massime dei core lette in /sys.
     */
    fun threadConsigliati(): Int {
        val frequenzeMassime = File("/sys/devices/system/cpu")
            .listFiles { file -> file.name.matches(CARTELLA_CORE) }
            ?.mapNotNull { core ->
                runCatching { File(core, "cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
            }
            .orEmpty()
        return threadPerFrequenze(frequenzeMassime, Runtime.getRuntime().availableProcessors())
    }

    /**
     * Esclude i core del gruppo più lento: sui processori big.LITTLE ggml divide il lavoro in parti uguali
     * e ogni passo aspetta il thread più lento, quindi un core piccolo in più rallenta invece di aiutare.
     * È la stessa regola dell'esempio Android di whisper.cpp.
     * @param frequenzeMassime La frequenza massima di ogni core; vuota se non si può leggere.
     * @param processori I processori disponibili, usati quando le frequenze mancano.
     * @return Un numero di thread tra 2 e 8.
     */
    fun threadPerFrequenze(frequenzeMassime: List<Long>, processori: Int): Int {
        if (frequenzeMassime.size < 2) return (processori / 2).coerceIn(2, 8)
        val minima = frequenzeMassime.min()
        val veloci = frequenzeMassime.count { it > minima }
        // Core tutti uguali: nessuno da escludere
        return (if (veloci > 0) veloci else frequenzeMassime.size).coerceIn(2, 8)
    }
}
