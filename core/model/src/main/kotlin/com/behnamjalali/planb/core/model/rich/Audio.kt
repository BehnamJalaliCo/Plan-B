package com.behnamjalali.planb.core.model.rich

import kotlinx.serialization.Serializable

/** A voice note's waveform: [BARS] loudness bars (0..100) drawn by the audio block. */
@Serializable
data class AudioData(val waveform: List<Int> = emptyList()) {
    companion object {
        const val BARS = 48

        /** Reduces loudness samples (0..1, in time order) to [count] bars, each the loudest sample of its slice. */
        fun bars(samples: List<Float>, count: Int = BARS): List<Int> {
            if (samples.isEmpty()) return emptyList()
            return List(count) { i ->
                val from = i * samples.size / count
                val to = ((i + 1) * samples.size / count).coerceAtLeast(from + 1).coerceAtMost(samples.size)
                val peak = (from until to).maxOfOrNull { samples[it.coerceAtMost(samples.lastIndex)] } ?: 0f
                (peak.coerceIn(0f, 1f) * 100).toInt()
            }
        }
    }
}
