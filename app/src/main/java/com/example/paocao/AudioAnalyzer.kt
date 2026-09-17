package com.example.paocao

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlin.math.log10
import kotlin.math.sqrt

class AudioAnalyzer(private val sampleRate: Int = 16000) {
    private var audioRecord: AudioRecord? = null

    @Volatile var currentDb: Float = -100f; private set
    @Volatile var noiseFloorDb: Float = -100f; private set

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun calibrate(durationSeconds: Int, onDone: (Float) -> Unit) {
        scope.launch {
            val bufSize = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate)
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
                )
            } catch (e: Exception) { onDone(-100f); return@launch }

            val total = sampleRate * durationSeconds
            val buf = ShortArray(total)
            rec.startRecording()
            var read = 0
            while (read < total) {
                val n = rec.read(buf, read, total - read)
                if (n <= 0) break
                read += n
            }
            rec.stop(); rec.release()
            noiseFloorDb = calcDb(buf)
            withContext(Dispatchers.Main) { onDone(noiseFloorDb) }
        }
    }

    fun start() {
        val bufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate)
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize
            )
        } catch (e: Exception) { null }

        audioRecord?.startRecording()
        job = scope.launch {
            val chunk = ShortArray(sampleRate / 2)
            while (isActive) {
                val n = audioRecord?.read(chunk, 0, chunk.size) ?: 0
                if (n > 0) currentDb = calcDb(chunk.copyOf(n))
            }
        }
    }

    fun stop() {
        job?.cancel()
        audioRecord?.stop(); audioRecord?.release(); audioRecord = null
    }

    fun getSnr(): Float = currentDb - noiseFloorDb

    private fun calcDb(samples: ShortArray): Float {
        if (samples.isEmpty()) return -100f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        val rms = sqrt(sum / samples.size)
        if (rms < 1e-9) return -100f
        return (20 * log10(rms / 32768.0)).toFloat()
    }
}
