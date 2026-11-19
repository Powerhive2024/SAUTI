package app.sauti.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Records speech in the exact format Gemma 3n's audio encoder expects: 16 kHz, mono, 16-bit PCM,
 * capped at 30 s. Audio stays on the phone; only the on-device model ever hears it.
 */
class VoiceRecorder {
    @Volatile private var stopRequested = false

    fun stop() { stopRequested = true }

    @SuppressLint("MissingPermission") // caller checks RECORD_AUDIO first
    suspend fun record(maxSeconds: Int = 30, onLevel: (Float) -> Unit = {}): ByteArray =
        withContext(Dispatchers.IO) {
            stopRequested = false
            val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SR,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SR),
            )
            val pcm = ByteArrayOutputStream()
            val buf = ShortArray(SR / 10)
            val maxSamples = SR * maxSeconds
            var total = 0
            try {
                rec.startRecording()
                while (coroutineContext.isActive && !stopRequested && total < maxSamples) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var peak = 0
                    val bb = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until n) {
                        bb.putShort(buf[i]); peak = maxOf(peak, kotlin.math.abs(buf[i].toInt()))
                    }
                    pcm.write(bb.array())
                    total += n
                    onLevel(peak / 32768f)
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
            wav(pcm.toByteArray())
        }

    companion object {
        const val SR = 16_000

        /** Wraps raw PCM16 mono in a standard 44-byte WAV header. */
        fun wav(pcm: ByteArray): ByteArray {
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt(36 + pcm.size); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(1)
            h.putInt(SR); h.putInt(SR * 2); h.putShort(2); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(pcm.size)
            return h.array() + pcm
        }
    }
}
