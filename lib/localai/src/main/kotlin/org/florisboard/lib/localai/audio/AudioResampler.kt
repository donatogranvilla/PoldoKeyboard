package org.florisboard.lib.localai.audio

import kotlin.math.floor

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

        val ratio = sourceSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()
        val targetLength = floor(monoSamples.size / ratio).toInt()
        val output = FloatArray(targetLength)

        for (i in 0 until targetLength) {
            val srcIndex = i * ratio
            val indexFloor = floor(srcIndex).toInt()
            val indexCeil = (indexFloor + 1).coerceAtMost(monoSamples.size - 1)
            val fraction = (srcIndex - indexFloor).toFloat()

            // Linear interpolation
            val s0 = monoSamples[indexFloor]
            val s1 = monoSamples[indexCeil]
            output[i] = s0 + fraction * (s1 - s0)
        }

        return output
    }

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
