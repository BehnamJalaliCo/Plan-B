package com.behnamjalali.planb.core.focus

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.behnamjalali.planb.core.model.AmbientSound

/**
 * Streams a [NoiseGenerator] to an [AudioTrack] on its own thread (Plan-B Pro #26). Volume
 * changes and sound switches are faded ([GainRamp]), so nothing clicks. Thread-safe: [play],
 * [setMuted] and [stop] may be called from any thread.
 */
class AmbientPlayer {
    @Volatile private var target: AmbientSound? = null
    @Volatile private var volume = 0f
    @Volatile private var muted = false
    private var thread: Thread? = null

    val playing: AmbientSound? get() = target

    /** Plays [sound] at [volumePercent] (0..100); switching sounds crossfades through silence. */
    @Synchronized
    fun play(sound: AmbientSound, volumePercent: Int) {
        target = sound
        volume = GainRamp.perceptual(volumePercent)
        if (thread == null) {
            thread = Thread(::run, "planb-ambient").apply {
                isDaemon = true
                start()
            }
        }
    }

    /** Silences playback without stopping it (another app took the audio focus for a moment). */
    fun setMuted(muted: Boolean) {
        this.muted = muted
    }

    /** Fades out and releases the audio track. */
    @Synchronized
    fun stop() {
        target = null
    }

    /** Ends the playback thread once it faded out, unless [play] was called meanwhile. */
    @Synchronized
    private fun finished(): Boolean {
        if (target != null) return false
        thread = null
        return true
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val rate = NoiseGenerator.SAMPLE_RATE
        val minBuffer = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val frames = maxOf(minBuffer / 2, rate / 10)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(frames * 2 * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrElse {
            Log.w(TAG, "No audio track (${it.javaClass.simpleName})")
            synchronized(this) { thread = null }
            return
        }
        val floats = FloatArray(frames)
        val shorts = ShortArray(frames)
        val ramp = GainRamp(rate)
        var generator: NoiseGenerator? = null
        try {
            track.play()
            while (true) {
                val wanted = target
                if (wanted == null && ramp.gain == 0f && finished()) break
                // Switch only once faded out; fade in the new sound.
                val switching = generator != null && generator.sound != wanted
                if ((generator == null || switching) && ramp.gain == 0f && wanted != null) generator = NoiseGenerator(wanted, rate, System.nanoTime())
                ramp.target = if (wanted == null || switching || muted || generator == null) 0f else volume
                val source = generator
                if (source == null) floats.fill(0f) else source.fill(floats, frames)
                for (i in 0 until frames) shorts[i] = (floats[i] * ramp.step() * Short.MAX_VALUE).toInt().toShort()
                if (track.write(shorts, 0, frames) < 0) break
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Ambient playback stopped (${e.javaClass.simpleName})")
        } finally {
            runCatching { track.stop() }
            track.release()
            synchronized(this) { if (thread === Thread.currentThread()) thread = null }
        }
    }

    private companion object {
        const val TAG = "PlanB"
    }
}

/** A gain that moves linearly towards [target] over [fadeSeconds], one sample per [step]. */
class GainRamp(sampleRate: Int, fadeSeconds: Float = 0.6f) {
    private val delta = 1f / (sampleRate * fadeSeconds)
    var target = 0f
    var gain = 0f
        private set

    fun step(): Float {
        gain = when {
            gain < target -> minOf(target, gain + delta)
            gain > target -> maxOf(target, gain - delta)
            else -> gain
        }
        return gain
    }

    companion object {
        /** A volume slider position (0..100) as a gain that sounds evenly spaced (squared). */
        fun perceptual(percent: Int): Float = (percent.coerceIn(0, 100) / 100f).let { it * it }
    }
}
