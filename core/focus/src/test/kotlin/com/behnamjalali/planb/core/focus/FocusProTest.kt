package com.behnamjalali.planb.core.focus

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.behnamjalali.planb.core.data.wellbeing.WellbeingState
import com.behnamjalali.planb.core.model.AmbientSound
import com.behnamjalali.planb.core.model.FocusSession
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.StrictMode
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/** Focus Pro (Plan-B Pro #26): generated sounds, fades and strict mode's Do Not Disturb. */
class FocusProTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("focus-state").toFile()

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    // region noise
    private fun samples(sound: AmbientSound, seconds: Int, seed: Long = 7): FloatArray {
        val generator = NoiseGenerator(sound, seed = seed)
        return FloatArray(NoiseGenerator.SAMPLE_RATE * seconds).also { generator.fill(it) }
    }

    private fun rms(values: FloatArray, from: Int = 0, to: Int = values.size): Double =
        sqrt((from until to).sumOf { values[it].toDouble() * values[it] } / (to - from))

    /** Lag-1 autocorrelation: near 0 for white noise, close to 1 for low-frequency (brown) noise. */
    private fun lag1(values: FloatArray): Double {
        val mean = values.average()
        var num = 0.0
        var den = 0.0
        for (i in values.indices) {
            val d = values[i] - mean
            den += d * d
            if (i > 0) num += d * (values[i - 1] - mean)
        }
        return num / den
    }

    @Test
    fun everySound_isBounded_audible_andDeterministicForASeed() {
        AmbientSound.entries.forEach { sound ->
            val a = samples(sound, 3)
            assertThat(a.all { it in -1f..1f }).isTrue()
            assertThat(rms(a)).isGreaterThan(0.02)
            assertThat(a.count { abs(it) >= 0.999f }.toDouble() / a.size).isLessThan(0.001) // hardly any clipping
            assertThat(samples(sound, 1).toList()).isEqualTo(samples(sound, 1).toList())
            assertThat(samples(sound, 1, seed = 8).toList()).isNotEqualTo(samples(sound, 1).toList())
        }
    }

    @Test
    fun colorsOfNoise_haveTheirSpectralTilt() {
        val white = lag1(samples(AmbientSound.WHITE, 2))
        val pink = lag1(samples(AmbientSound.PINK, 2))
        val brown = lag1(samples(AmbientSound.BROWN, 2))
        assertThat(abs(white)).isLessThan(0.05)
        assertThat(pink).isGreaterThan(0.5)
        assertThat(brown).isGreaterThan(0.95)
        assertThat(brown).isGreaterThan(pink)
    }

    @Test
    fun ocean_swells_whileRainStaysSteady() {
        fun windows(values: FloatArray) = (0 until values.size / NoiseGenerator.SAMPLE_RATE).map {
            rms(values, it * NoiseGenerator.SAMPLE_RATE, (it + 1) * NoiseGenerator.SAMPLE_RATE)
        }
        val ocean = windows(samples(AmbientSound.OCEAN, 24))
        assertThat(ocean.max() / ocean.min()).isGreaterThan(2.0)
        val rain = windows(samples(AmbientSound.RAIN, 6))
        assertThat(rain.max() / rain.min()).isLessThan(2.0)
    }

    @Test
    fun gainRamp_fadesLinearlyAndVolumeIsPerceptual() {
        val ramp = GainRamp(sampleRate = 100, fadeSeconds = 1f)
        ramp.target = 0.5f
        repeat(50) { ramp.step() }
        assertThat(ramp.gain).isWithin(1e-4f).of(0.5f)
        ramp.step()
        assertThat(ramp.gain).isEqualTo(0.5f)
        ramp.target = 0f
        repeat(25) { ramp.step() }
        assertThat(ramp.gain).isWithin(1e-4f).of(0.25f)
        assertThat(GainRamp.perceptual(50)).isEqualTo(0.25f)
        assertThat(GainRamp.perceptual(150)).isEqualTo(1f)
        assertThat(GainRamp.perceptual(-3)).isEqualTo(0f)
    }
    // endregion

    // region strict mode
    private class FakeDnd(var access: Boolean = true, var filter: Int = StrictMode.FILTER_ALL) : DndController {
        val sets = mutableListOf<Int>()
        override fun hasAccess() = access
        override fun currentFilter(): Int? = filter
        override fun setFilter(filter: Int): Boolean {
            if (!access) return false
            this.filter = filter
            sets += filter
            return true
        }
    }

    private var stores = 0
    private fun state() = WellbeingState(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "state${stores++}.preferences_pb") })

    private fun session(status: FocusStatus = FocusStatus.RUNNING, strict: Boolean = true) =
        FocusSession(id = 1, startedAt = Instant.EPOCH, plannedDurationMillis = 60_000, status = status, strict = strict)

    @Test
    fun strictSession_turnsOnDoNotDisturb_andPutsTheOldFilterBackOnPauseAndEnd() = runBlocking<Unit> {
        val dnd = FakeDnd()
        val coordinator = StrictModeCoordinator(dnd, state())
        coordinator.apply(session())
        assertThat(dnd.filter).isEqualTo(StrictMode.FILTER_PRIORITY)
        coordinator.apply(session()) // still running: nothing more
        coordinator.apply(session(FocusStatus.PAUSED))
        assertThat(dnd.filter).isEqualTo(StrictMode.FILTER_ALL)
        coordinator.apply(session())
        assertThat(dnd.filter).isEqualTo(StrictMode.FILTER_PRIORITY)
        coordinator.apply(null) // cancelled or finished
        assertThat(dnd.filter).isEqualTo(StrictMode.FILTER_ALL)
        assertThat(dnd.sets).containsExactly(2, 1, 2, 1).inOrder()
        // A session without strict mode never touches it.
        coordinator.apply(session(strict = false))
        assertThat(dnd.sets).hasSize(4)
    }

    @Test
    fun restore_survivesProcessDeath_andLeavesTheUsersOwnChangeAlone() = runBlocking<Unit> {
        val dnd = FakeDnd()
        val stored = state()
        StrictModeCoordinator(dnd, stored).apply(session())
        // The process dies; a new coordinator (same device state) sees the session ended.
        StrictModeCoordinator(dnd, stored).apply(null)
        assertThat(dnd.filter).isEqualTo(StrictMode.FILTER_ALL)
        assertThat(stored.strictMode()).isNull()
        // The user switched to "alarms only" during the session: kept.
        StrictModeCoordinator(dnd, stored).apply(session())
        dnd.filter = 4
        StrictModeCoordinator(dnd, stored).apply(null)
        assertThat(dnd.filter).isEqualTo(4)
        assertThat(stored.strictMode()).isNull()
    }

    @Test
    fun withoutAccess_orWithDoNotDisturbAlreadyOn_nothingChanges() = runBlocking<Unit> {
        val noAccess = FakeDnd(access = false)
        StrictModeCoordinator(noAccess, state()).apply(session())
        assertThat(noAccess.filter).isEqualTo(StrictMode.FILTER_ALL)
        val alreadyOn = FakeDnd(filter = StrictMode.FILTER_PRIORITY)
        val stored = state()
        StrictModeCoordinator(alreadyOn, stored).apply(session())
        StrictModeCoordinator(alreadyOn, stored).apply(null)
        assertThat(alreadyOn.sets).isEmpty()
        assertThat(alreadyOn.filter).isEqualTo(StrictMode.FILTER_PRIORITY)
    }
    // endregion
}
