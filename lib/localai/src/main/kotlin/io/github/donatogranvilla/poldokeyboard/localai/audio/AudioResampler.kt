package io.github.donatogranvilla.poldokeyboard.localai.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Resamples and downmixes raw audio to 16,000 Hz Mono PCM FloatArray
 * as strictly required by Whisper.cpp and Sherpa-onnx.
 */
object AudioResampler {
    const val TARGET_SAMPLE_RATE = 16000

    /**
     * Converts multi-channel, multi-sample-rate PCM float samples into 16kHz mono.
     *
     * @param inputSamples Interleaved PCM float samples (-1.0f to 1.0f)
     * @param sourceSampleRate Input sampling rate (e.g. 48000, 44100)
     * @param channelCount Number of interleaved channels (1 for mono, 2 for stereo)
     * @return 16,000 Hz Mono FloatArray
     */
    fun resampleTo16kMono(
        inputSamples: FloatArray,
        sourceSampleRate: Int,
        channelCount: Int
    ): FloatArray {
        if (inputSamples.isEmpty()) return FloatArray(0)

        // Step 1: Downmix to mono if multi-channel
        val monoSamples = if (channelCount > 1) {
            val numFrames = inputSamples.size / channelCount
            val mono = FloatArray(numFrames)
            val invChannels = 1.0f / channelCount
            for (i in 0 until numFrames) {
                var sum = 0.0f
                for (ch in 0 until channelCount) {
                    sum += inputSamples[i * channelCount + ch]
                }
                mono[i] = sum * invChannels
            }
            mono
        } else {
            inputSamples
        }

        // Step 2: Resample if sample rate differs from 16,000 Hz
        if (sourceSampleRate == TARGET_SAMPLE_RATE) {
            return monoSamples
        }

        // Scendendo di frequenza, quello che sta sopra gli 8 kHz va tolto prima: altrimenti si ripiega
        // nella banda che il modello analizza. Salendo non c'è nulla da togliere.
        val filtro = if (sourceSampleRate > TARGET_SAMPLE_RATE) filtroAntiAliasing(sourceSampleRate) else null
        val ratio = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
        val targetLength = floor(monoSamples.size / ratio).toInt()
        val output = FloatArray(targetLength)

        for (i in 0 until targetLength) {
            val srcIndex = i * ratio
            val indexFloor = floor(srcIndex).toInt()
            val indexCeil = (indexFloor + 1).coerceAtMost(monoSamples.size - 1)
            val fraction = (srcIndex - indexFloor).toFloat()

            val s0 = campioneFiltrato(monoSamples, indexFloor, filtro)
            // Con rapporti interi, come 48 → 16 kHz, la frazione è sempre zero: basta un campione filtrato
            if (fraction == 0f) {
                output[i] = s0
                continue
            }
            val s1 = campioneFiltrato(monoSamples, indexCeil, filtro)
            output[i] = s0 + fraction * (s1 - s0)
        }

        return output
    }

    /**
     * Il filtro passa-basso FIR che precede la decimazione: sinc con finestra di Blackman, taglio a [TAGLIO_HZ]
     * e guadagno unitario in continua. La lunghezza cresce con il rapporto, per tenere la banda di transizione
     * sotto i 2 kHz.
     */
    internal fun filtroAntiAliasing(sourceSampleRate: Int): FloatArray {
        val rapporto = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE
        var lunghezza = ceil(rapporto * COEFFICIENTI_PER_RAPPORTO).toInt()
        if (lunghezza % 2 == 0) lunghezza++
        val centro = (lunghezza - 1) / 2
        val taglio = TAGLIO_HZ / sourceSampleRate
        val coefficienti = DoubleArray(lunghezza) { k ->
            val x = (k - centro).toDouble()
            val sinc = if (x == 0.0) 2 * taglio else sin(2 * PI * taglio * x) / (PI * x)
            val finestra = 0.42 - 0.5 * cos(2 * PI * k / (lunghezza - 1)) + 0.08 * cos(4 * PI * k / (lunghezza - 1))
            sinc * finestra
        }
        val somma = coefficienti.sum()
        return FloatArray(lunghezza) { (coefficienti[it] / somma).toFloat() }
    }

    /**
     * Il campione in posizione [indice] dopo il filtro, o quello originale se il filtro non serve.
     * Ai bordi si ripete il primo o l'ultimo campione, così un segnale costante resta invariato.
     */
    private fun campioneFiltrato(campioni: FloatArray, indice: Int, filtro: FloatArray?): Float {
        if (filtro == null) return campioni[indice]
        val centro = filtro.size / 2
        val ultimo = campioni.size - 1
        var somma = 0f
        for (k in filtro.indices) {
            val j = (indice + k - centro).coerceIn(0, ultimo)
            somma += filtro[k] * campioni[j]
        }
        return somma
    }

    /** Frequenza di taglio del filtro anti-aliasing: sotto gli 8 kHz di Nyquist a 16 kHz, con margine. */
    private const val TAGLIO_HZ = 7400.0

    /** Coefficienti del filtro per ogni unità del rapporto di decimazione: 145 per 48 → 16 kHz. */
    private const val COEFFICIENTI_PER_RAPPORTO = 48

    /**
     * Converts a 16-bit signed integer PCM byte buffer into normalized floats (-1.0f to 1.0f).
     */
    fun pcm16BytesToFloats(bytes: ByteArray, offset: Int, length: Int): FloatArray {
        val numSamples = length / 2
        val floats = FloatArray(numSamples)
        var byteIdx = offset
        for (i in 0 until numSamples) {
            val low = bytes[byteIdx].toInt() and 0xFF
            val high = bytes[byteIdx + 1].toInt()
            val sample = (high shl 8) or low // Little-endian 16-bit signed
            floats[i] = sample / 32768.0f
            byteIdx += 2
        }
        return floats
    }
}
