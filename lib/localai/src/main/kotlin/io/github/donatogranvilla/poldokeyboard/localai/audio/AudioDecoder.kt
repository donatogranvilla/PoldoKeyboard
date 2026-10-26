package io.github.donatogranvilla.poldokeyboard.localai.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/**
 * Hardware-accelerated audio decoder using Android's native [MediaExtractor] and [MediaCodec].
 * Natively decodes WhatsApp voice notes (Opus in Ogg containers), AAC, M4A, MP3, and WAV
 * directly into 16,000 Hz Mono FloatArray without any external FFmpeg dependency.
 */
object AudioDecoder {
    private const val TIMEOUT_US = 10000L

    /**
     * Decodes an audio file at [uri] to 16,000 Hz Mono FloatArray.
     */
    suspend fun decodeTo16kMono(context: Context, uri: Uri): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                decodeExtractor(extractor)
            } finally {
                extractor.release()
            }
        }
    }

    /**
     * Decodes an audio [file] to 16,000 Hz Mono FloatArray.
     */
    suspend fun decodeTo16kMono(file: File): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                decodeExtractor(extractor)
            } finally {
                extractor.release()
            }
        }
    }

    private fun decodeExtractor(extractor: MediaExtractor): FloatArray {
        var audioTrackIndex = -1
        var format: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex < 0 || format == null) {
            throw IllegalArgumentException("No audio track found in media source")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        val pcmOutputStream = ByteArrayOutputStream()

        try {
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEos = false
            var isOutputEos = false

            while (!isOutputEos) {
                if (!isInputEos) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer: ByteBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEos = true
                        } else {
                            val presentationTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize, presentationTimeUs, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEos = true
                    }

                    if (bufferInfo.size > 0) {
                        val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.get(chunk)
                        pcmOutputStream.write(chunk)
                    }

                    codec.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }

        val rawPcmBytes = pcmOutputStream.toByteArray()
        val rawFloats = AudioResampler.pcm16BytesToFloats(rawPcmBytes, 0, rawPcmBytes.size)

        return AudioResampler.resampleTo16kMono(
            inputSamples = rawFloats,
            sourceSampleRate = sampleRate,
            channelCount = channelCount
        )
    }
}
