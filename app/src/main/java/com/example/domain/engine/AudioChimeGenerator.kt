package com.example.domain.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sin

object AudioChimeGenerator {

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var activeJob: kotlinx.coroutines.Job? = null

    /**
     * Immediately stops any currently playing chime audio.
     */
    fun stopChime() {
        try {
            activeJob?.cancel()
            activeJob = null
            activeTrack?.let { track ->
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                        track.stop()
                    }
                } catch (_: Exception) {}
                try {
                    track.release()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        } finally {
            activeTrack = null
        }
    }

    /**
     * Synthesizes a peaceful, harmonic singing bowl chime tone offline using AudioTrack.
     * Combines a fundamental 432 Hz frequency with gentle overtones and an exponential decay envelope.
     */
    suspend fun playCalmingChime() = withContext(Dispatchers.IO) {
        try {
            stopChime()
            activeJob = coroutineContext[kotlinx.coroutines.Job]

            val sampleRate = 44100
            val durationSeconds = 2.5
            val numSamples = (sampleRate * durationSeconds).toInt()
            val samples = ShortArray(numSamples)

            val freq1 = 432.0 // Fundamental healing frequency
            val freq2 = 864.0 // First harmonic octave
            val freq3 = 1296.0 // Warm third harmonic

            for (i in 0 until numSamples) {
                val time = i.toDouble() / sampleRate
                // Exponential decay envelope
                val envelope = exp(-2.0 * time)
                
                // Composite harmonic wave
                val wave1 = sin(2.0 * Math.PI * freq1 * time) * 0.7
                val wave2 = sin(2.0 * Math.PI * freq2 * time) * 0.2
                val wave3 = sin(2.0 * Math.PI * freq3 * time) * 0.1
                
                val sampleValue = (wave1 + wave2 + wave3) * envelope
                samples[i] = (sampleValue * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            activeTrack = audioTrack
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            
            // Release resources after playback
            kotlinx.coroutines.delay(2600)
            if (activeTrack == audioTrack) {
                try {
                    audioTrack.stop()
                } catch (_: Exception) {}
                try {
                    audioTrack.release()
                } catch (_: Exception) {}
                activeTrack = null
            }
        } catch (_: Exception) {
            // Non-critical audio failure fallback
            stopChime()
        }
    }
}
