package com.behnamjalali.planb.core.focus

import com.behnamjalali.planb.core.model.AmbientSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates Focus Pro's ambient sounds (Plan-B Pro #26) sample by sample, so no audio file is
 * bundled. Output is mono in [-1, 1] at [sampleRate]; the same [seed] gives the same samples.
 *
 * - White: uniform noise.
 * - Pink: white noise through Paul Kellet's refined pinking filter (−3 dB per octave).
 * - Brown: leaky integrated white noise (−6 dB per octave), a deep rumble.
 * - Rain: soft pink noise with a slowly changing intensity and many short, bright drops.
 * - Ocean: brown noise swelling and receding in waves of 7–12 seconds, with pink "foam" on
 *   the crests.
 */
class NoiseGenerator(val sound: AmbientSound, val sampleRate: Int = SAMPLE_RATE, seed: Long = 0x5EED) {
    private val random = Random(seed)

    // Pink filter state.
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0

    // Brown state.
    private var brown = 0.0

    // Rain: drops and a slow intensity.
    private var drop = 0.0
    private var dropDecay = 0.0
    private var dropTone = 0.0
    private var lastWhite = 0.0
    private var rainPhase = random.nextDouble() * 2 * PI

    // Ocean waves.
    private var wavePhase = 0.0
    private var waveStep = nextWaveStep()

    private fun white(): Double = random.nextDouble() * 2 - 1

    private fun pink(w: Double): Double {
        b0 = 0.99886 * b0 + w * 0.0555179
        b1 = 0.99332 * b1 + w * 0.0750759
        b2 = 0.96900 * b2 + w * 0.1538520
        b3 = 0.86650 * b3 + w * 0.3104856
        b4 = 0.55000 * b4 + w * 0.5329522
        b5 = -0.7616 * b5 - w * 0.0168980
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362
        b6 = w * 0.115926
        return out * 0.11
    }

    private fun brown(w: Double): Double {
        brown = (brown + 0.02 * w) / 1.02
        return brown * 3.5
    }

    private fun nextWaveStep(): Double = 2 * PI / ((7.0 + random.nextDouble() * 5.0) * sampleRate)

    private fun rain(): Double {
        val w = white()
        // Intensity drifts slowly (about a minute per cycle) between light and steady rain.
        rainPhase += 2 * PI * 0.017 / sampleRate
        val intensity = 0.75 + 0.25 * sin(rainPhase)
        val bed = pink(w) * 0.45 * intensity
        // About 40 drops a second, each a decaying burst of bright (high-passed) noise.
        if (random.nextDouble() < 40.0 * intensity / sampleRate) {
            drop = 0.25 + random.nextDouble() * 0.5
            dropDecay = exp(-1.0 / (sampleRate * (0.002 + random.nextDouble() * 0.006)))
            dropTone = 0.5 + random.nextDouble() * 0.5
        }
        val bright = w - lastWhite * dropTone
        lastWhite = w
        val drops = drop * bright * 0.5
        drop *= dropDecay
        return bed + drops
    }

    private fun ocean(): Double {
        val w = white()
        wavePhase += waveStep
        if (wavePhase >= 2 * PI) {
            wavePhase -= 2 * PI
            waveStep = nextWaveStep()
        }
        val swell = sin(wavePhase / 2).let { it * it } // 0..1, one crest per cycle
        val body = brown(w) * (0.2 + 0.8 * swell)
        val foam = pink(white()) * 0.5 * swell * swell
        return body * 0.8 + foam
    }

    /** The next sample. */
    fun next(): Float {
        val value = when (sound) {
            AmbientSound.WHITE -> white() * 0.35
            AmbientSound.PINK -> pink(white())
            AmbientSound.BROWN -> brown(white())
            AmbientSound.RAIN -> rain()
            AmbientSound.OCEAN -> ocean()
        }
        return value.coerceIn(-1.0, 1.0).toFloat()
    }

    fun fill(buffer: FloatArray, count: Int = buffer.size) {
        for (i in 0 until count) buffer[i] = next()
    }

    companion object {
        /** Enough for noise and low on CPU and battery. */
        const val SAMPLE_RATE = 22_050
    }
}
