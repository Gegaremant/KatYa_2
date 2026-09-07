package com.katya.app.stt

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.katya.app.data.AppSettings
import com.katya.app.network.Requests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Cloud STT engine: captures raw 16 kHz mono PCM16 audio and sends it to an
 * OpenAI-compatible `/audio/transcriptions` endpoint (Whisper and derivatives).
 *
 * Push-to-talk flow: [startRecording] opens the mic on a background capture
 * thread; [stopAndTranscribe] stops the capture, wraps PCM into a WAV and
 * uploads it for transcription.
 */
class CloudSttEngine(
    private val appSettings: AppSettings,
    private val requests: Requests,
) : KoinComponent {

    private val lock = Any()
    private var pcmBuffer: ByteArrayOutputStream = ByteArrayOutputStream()

    @Volatile private var active = false
    private var captureThread: Thread? = null

    val isRecording: Boolean get() = active

    @android.annotation.SuppressLint("MissingPermission")
    fun startRecording() {
        stopCaptureInternal()
        synchronized(lock) { pcmBuffer = ByteArrayOutputStream() }
        active = true
        captureThread = Thread { captureLoop() }.apply {
            name = "cloud-stt-capture"
            isDaemon = true
            start()
        }
    }

    suspend fun stopAndTranscribe(): Result<String> = withContext(Dispatchers.IO) {
        try {
            stopCaptureInternal()
            val pcm = synchronized(lock) { pcmBuffer.toByteArray() }
            if (pcm.size < 4000) { // < ~125ms of audio — treat as silence
                Result.failure(IllegalStateException("Не распознано: слишком тихо или коротко"))
            } else {
                requests.transcribeSpeech(
                    url = appSettings.getCloudSttUrl(),
                    apiKey = appSettings.getCloudSttKey(),
                    model = appSettings.getCloudSttModel(),
                    audioBytes = buildWav(pcm),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun captureLoop() {
        val sampleRate = SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(sampleRate / 10)

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer,
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            active = false
            return
        }
        try {
            audioRecord.startRecording()
            val buffer = ByteArray(minBuffer)
            while (active) {
                val n = audioRecord.read(buffer, 0, buffer.size)
                if (n > 0) {
                    synchronized(lock) { pcmBuffer.write(buffer, 0, n) }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                audioRecord.stop()
            } catch (_: Exception) {}
            audioRecord.release()
        }
    }

    private fun stopCaptureInternal() {
        active = false
        captureThread?.let { thread ->
            try {
                thread.join(500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        captureThread = null
    }

    /** Wraps raw 16-bit PCM into a minimal RIFF/WAVE container for the API. */
    private fun buildWav(pcm: ByteArray): ByteArray {
        val byteRate = SAMPLE_RATE * 2 // 16-bit mono
        val dataSize = pcm.size
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { dos ->
            dos.writeBytes("RIFF")
            dos.writeInt(Integer.reverseBytes(36 + dataSize))
            dos.writeBytes("WAVE")
            dos.writeBytes("fmt ")
            dos.writeInt(Integer.reverseBytes(16))
            dos.writeShort(Integer.reverseBytes(1)) // PCM
            dos.writeShort(Integer.reverseBytes(1)) // mono
            dos.writeInt(Integer.reverseBytes(SAMPLE_RATE))
            dos.writeInt(Integer.reverseBytes(byteRate))
            dos.writeShort(Integer.reverseBytes(2)) // block align
            dos.writeShort(Integer.reverseBytes(16)) // bits per sample
            dos.writeBytes("data")
            dos.writeInt(Integer.reverseBytes(dataSize))
            dos.write(pcm)
        }
        return out.toByteArray()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
