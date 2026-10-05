package com.behnamjalali.planb.feature.today

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.speech.DictationEvent
import com.behnamjalali.planb.core.speech.FakeVoiceDictation
import com.behnamjalali.planb.core.speech.VoiceInputViewModel
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

    /** Waits until the settings and projects the parser needs have been read. */
    private suspend fun smartViewModel(): QuickCaptureViewModel {
        val vm = viewModel()
        vm.setSmartInput(true)
        withTimeout(10_000) {
            while (true) {
                vm.setTitle("فردا")
                if (vm.parsed.value != null) break
                delay(20)
            }
        }
        vm.setTitle("")
        return vm
    }

    @Test
    fun freeUsers_textIsNotRead() {
        val vm = viewModel()
        vm.setTitle("فردا ساعت ۵ عصر نان بخرم")
        assertThat(vm.parsed.value).isNull()
    }

    @Test
    fun smartInput_savesEverythingReadFromTheText() = runBlocking<Unit> {
        val project = graph.projects.save(com.behnamjalali.planb.core.model.Project(title = "خانه"))
        val vm = smartViewModel()
        withTimeout(10_000) {
            while (true) {
                vm.setTitle("@خانه")
                if (vm.parsed.value?.project != null) break
                delay(20)
            }
        }
        vm.setTitle("فردا ساعت ۵ عصر خرید کاشی #خرید @خانه فوری ۴۵ دقیقه تا جمعه")
        val parsed = vm.parsed.value!!
        assertThat(parsed.title).isEqualTo("خرید کاشی")
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.save("Notes")
        val id = (saved.await() as CaptureEvent.Saved).id
        val task = graph.tasks.getTask(id)!!
        val today = graph.time.today()
        assertThat(task.title).isEqualTo("خرید کاشی")
        assertThat(task.dueDate).isEqualTo(today.plusDays(1))
        assertThat(task.dueTime).isEqualTo(java.time.LocalTime.of(17, 0))
        assertThat(task.priority).isEqualTo(com.behnamjalali.planb.core.model.Priority.HIGH)
        assertThat(task.estimatedMinutes).isEqualTo(45)
        assertThat(task.projectId).isEqualTo(project)
        assertThat(task.tags.map { it.name }).containsExactly("خرید")
        assertThat(task.deadline).isEqualTo(parsed.deadline)
        assertThat(task.reminderOffsetMinutes).isEqualTo(15)
    }

    /** Plan-B Pro #40: speech → recognizer (fake) → voice field → the same quick-add reading → a dated task. */
    @Test
    fun dictation_isReadLikeTypedText_andSavedAsADatedTask() = runBlocking<Unit> {
        val vm = smartViewModel()
        val recognizer = FakeVoiceDictation(
            script = listOf(
                DictationEvent.Ready,
                DictationEvent.Partial("فردا ساعت ۵"),
                DictationEvent.Partial("فردا ساعت ۵ عصر جلسه"),
                DictationEvent.Final("فردا ساعت ۵ عصر جلسه با علی"),
            ),
        )
        val voice = main.track(VoiceInputViewModel(recognizer))
        val heard = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { voice.results.first() } }
        voice.start("fa-IR")
        vm.applyDictation(heard.await())
        assertThat(recognizer.languages).containsExactly("fa-IR")
        val parsed = vm.parsed.value!!
        assertThat(parsed.title).isEqualTo("جلسه با علی")

        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.save("Notes")
        val task = graph.tasks.getTask((saved.await() as CaptureEvent.Saved).id)!!
        assertThat(task.title).isEqualTo("جلسه با علی")
        assertThat(task.dueDate).isEqualTo(graph.time.today().plusDays(1))
        assertThat(task.dueTime).isEqualTo(java.time.LocalTime.of(17, 0))
    }

    @Test
    fun dictation_joinsTextAlreadyTyped() {
        val vm = viewModel()
        vm.setTitle("خرید")
        vm.applyDictation(" نان و شیر ")
        assertThat(vm.title.value).isEqualTo("خرید نان و شیر")
        vm.setType(com.behnamjalali.planb.feature.today.capture.CaptureType.NOTE)
        vm.applyDictation("برای صبحانه")
        assertThat(vm.body.value).isEqualTo("برای صبحانه")
    }

    @Test
    fun dismissingAPart_keepsItsWordsInTheTitle_andAPickedDateReplacesTheReadOne() = runBlocking<Unit> {
        val vm = smartViewModel()
        vm.setTitle("فردا نان بخرم")
        val part = vm.parsed.value!!.parts.single()
        vm.dismissPart(part.key)
        assertThat(vm.parsed.value).isNull()
        vm.setTitle("پنجشنبه نان بخرم")
        assertThat(vm.parsed.value!!.date).isNotNull()
        val picked = graph.time.today().plusDays(10)
        vm.setDate(picked)
        // The picked date wins; «پنجشنبه» stays as text.
        assertThat(vm.parsed.value).isNull()
        assertThat(vm.dateEpoch.value).isEqualTo(picked.toEpochDay())
    }

    @Test
    fun events_ignoreTagsAndPriority() = runBlocking<Unit> {
        val vm = smartViewModel()
        vm.setType(com.behnamjalali.planb.feature.today.capture.CaptureType.EVENT)
        vm.setTitle("جلسه فردا ساعت ۱۰ صبح #کار فوری ۲ ساعت")
        val parsed = vm.parsed.value!!
        assertThat(parsed.title).isEqualTo("جلسه #کار فوری")
        assertThat(parsed.durationMinutes).isEqualTo(120)
        assertThat(parsed.tags).isEmpty()
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
