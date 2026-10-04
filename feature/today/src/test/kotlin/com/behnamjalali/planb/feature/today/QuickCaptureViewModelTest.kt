package com.behnamjalali.planb.feature.today

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.feature.today.capture.CaptureEvent
import com.behnamjalali.planb.feature.today.capture.QuickCaptureViewModel
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuickCaptureViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private fun viewModel() = main.track(
        QuickCaptureViewModel(
            SavedStateHandle(), graph.tasks, graph.notes, graph.events, graph.habits, graph.projects, graph.settings, graph.time,
        ),
    )

    @Test
    fun openingTheSheetOnALaterDay_defaultsToThatDay() {
        val vm = viewModel()
        val created = graph.time.today()
        assertThat(vm.dateEpoch.value).isEqualTo(created.toEpochDay())

        graph.time.advance(Duration.ofDays(1))
        vm.onOpened()
        assertThat(vm.dateEpoch.value).isEqualTo(created.plusDays(1).toEpochDay())
    }

    @Test
    fun openingTheSheet_keepsADateTheUserPicked() {
        val vm = viewModel()
        val picked = graph.time.today().plusDays(5)
        vm.setDate(picked)
        graph.time.advance(Duration.ofDays(1))
        vm.onOpened()
        assertThat(vm.dateEpoch.value).isEqualTo(picked.toEpochDay())
    }

    @Test
    fun doubleTapOnSave_capturesOnce() = runBlocking<Unit> {
        val vm = viewModel()
        vm.setTitle("Buy milk")
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.save("Notes")
        vm.save("Notes")
        assertThat(saved.await()).isInstanceOf(CaptureEvent.Saved::class.java)
        delay(300)
        val all = graph.tasks.observeTasks(TaskFilter(today = graph.time.today())).first()
        assertThat(all.map { it.title }).containsExactly("Buy milk")
    }
}
