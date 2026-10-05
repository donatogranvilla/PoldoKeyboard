package org.florisboard.lib.localai.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Direct microphone recorder streaming 16,000 Hz 16-bit Mono PCM.
 */
class AudioRecorder {
    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val pcmOutputStream = ByteArrayOutputStream()
    private var isRecording = false

    val isCurrentlyRecording: Boolean get() = isRecording

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAmplitude: ((Float) -> Unit)? = null
    ): Boolean {
        if (isRecording) return false

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) return false

        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)
        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }

            audioRecord = record
            pcmOutputStream.reset()
            record.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(bufferSize)
                while (isActive && isRecording) {
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        synchronized(pcmOutputStream) {
                            pcmOutputStream.write(buffer, 0, readBytes)
                        }

                        // Calculate average amplitude for visualizer
                        if (onAmplitude != null) {
                            var maxAmp = 0
                            var i = 0
                            while (i < readBytes - 1) {
                                val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                                val absSample = abs(sample)
                                if (absSample > maxAmp) maxAmp = absSample
                                i += 2
                            }
                            val normAmp = (maxAmp / 32768.0f).coerceIn(0.0f, 1.0f)
                            withContext(Dispatchers.Main) {
                                onAmplitude(normAmp)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            isRecording = false
            audioRecord?.release()
            audioRecord = null
            false
        }
    }

    suspend fun stopRecording(): FloatArray = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext FloatArray(0)

        isRecording = false
        recordingJob?.cancelAndJoin()
        recordingJob = null

        audioRecord?.apply {
            try {
                stop()
            } catch (_: Exception) {}
            release()
        }
        audioRecord = null

        val bytes = synchronized(pcmOutputStream) {
            pcmOutputStream.toByteArray()
        }
        AudioResampler.pcm16BytesToFloats(bytes, 0, bytes.size)
    }

    fun cancel() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        audioRecord = null
        pcmOutputStream.reset()
    }
}
