package io.github.donatogranvilla.poldokeyboard.localai

import io.github.donatogranvilla.poldokeyboard.localai.audio.AudioResampler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioResamplerTest {

    @Test
    fun testPcm16BytesToFloatsConversion() {
        // Little endian: 0x00, 0x00 = 0.0f; 0x00, 0x7F = 32512 (~0.992f); 0x00, 0x80 = -32768 (-1.0f)
        val bytes = byteArrayOf(
            0x00, 0x00,
            0x00, 0x7F,
            0x00, 0x80.toByte()
        )
        val floats = AudioResampler.pcm16BytesToFloats(bytes, 0, bytes.size)
        assertEquals(3, floats.size)
        assertEquals(0.0f, floats[0], 0.001f)
        assertTrue(floats[1] > 0.99f)
        assertEquals(-1.0f, floats[2], 0.001f)
    }

    @Test
    fun testResampleStereo48kToMono16k() {
        // 1 second of 48kHz stereo = 96,000 samples
        val stereoSamples = FloatArray(96000) { 0.5f }
        val resampled = AudioResampler.resampleTo16kMono(
            inputSamples = stereoSamples,
            sourceSampleRate = 48000,
            channelCount = 2
        )

        // 1 second of 16kHz mono = 16,000 samples
        assertEquals(16000, resampled.size)
        assertEquals(0.5f, resampled[0], 0.01f)
        assertEquals(0.5f, resampled[8000], 0.01f)
    }

    /** Valore efficace di un tono a 48 kHz dopo il passaggio a 16 kHz, lontano dai bordi. */
    private fun rmsDopoRicampionamento(frequenzaHz: Double): Double {
        val campioni = FloatArray(48000) { sin(2 * PI * frequenzaHz * it / 48000).toFloat() }
        val uscita = AudioResampler.resampleTo16kMono(campioni, 48000, 1)
        val centrali = uscita.copyOfRange(1000, uscita.size - 1000)
        return sqrt(centrali.sumOf { it.toDouble() * it } / centrali.size)
    }

    /** La voce sotto i 6 kHz passa intatta. */
    @Test
    fun testToniInBandaConservati() {
        assertEquals(1 / sqrt(2.0), rmsDopoRicampionamento(1000.0), 0.01)
        assertEquals(1 / sqrt(2.0), rmsDopoRicampionamento(5000.0), 0.03)
    }

    /** Sopra gli 8 kHz il filtro toglie il segnale, che senza filtro si ripiegherebbe in banda (12 kHz → 4 kHz). */
    @Test
    fun testNessunAliasing() {
        assertTrue(rmsDopoRicampionamento(12000.0) < 0.01)
        assertTrue(rmsDopoRicampionamento(20000.0) < 0.01)
    }

    /** Il filtro ha guadagno unitario in continua e lunghezza dispari, quindi un centro preciso. */
    @Test
    fun testFiltroNormalizzato() {
        val filtro = AudioResampler.filtroAntiAliasing(48000)
        assertEquals(1.0, filtro.sumOf { it.toDouble() }, 1e-4)
        assertEquals(1, filtro.size % 2)
    }

    @Test
    fun testResampleMono16kIdentity() {
        val input = FloatArray(1600) { 0.25f }
        val output = AudioResampler.resampleTo16kMono(
            inputSamples = input,
            sourceSampleRate = 16000,
            channelCount = 1
        )
        assertEquals(1600, output.size)
        assertEquals(0.25f, output[0])
    }
}
