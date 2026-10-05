package org.florisboard.lib.localai

import org.florisboard.lib.localai.audio.AudioResampler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
