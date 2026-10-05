package com.behnamjalali.planb.feature.assistant

import com.behnamjalali.planb.core.ai.AiError
import com.behnamjalali.planb.core.ai.AiPlanItem
import com.behnamjalali.planb.core.ai.AiRole
import com.behnamjalali.planb.core.ai.AssistantPrompts
import com.behnamjalali.planb.core.data.repository.OfflineDayPlanRepository
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.DayPlanSettings
import com.behnamjalali.planb.core.model.Note
import com.behnamjalali.planb.core.model.NoteBlock
import com.behnamjalali.planb.core.model.NoteDocument
import com.behnamjalali.planb.core.model.Notebook
import com.behnamjalali.planb.core.model.Priority
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TimeRange
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.behnamjalali.planb.core.ui.AssistantAction
import com.behnamjalali.planb.core.ui.AssistantRequest
import com.behnamjalali.planb.core.ui.AssistantSource
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class PlanValidatorTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val day = LocalDate.of(2026, 10, 4)
    private val tasks = listOf(Task(id = 1, title = "a"), Task(id = 2, title = "b"), Task(id = 3, title = "c"), Task(id = 4, title = "d")).associateBy { it.id }
    private fun range(start: String, end: String) =
        TimeRange(ZonedDateTime.of(day, LocalTime.parse(start), zone).toInstant(), ZonedDateTime.of(day, LocalTime.parse(end), zone).toInstant())

    @Test
    fun keepsOnlyBlocksThatFit() {
        val items = listOf(
            AiPlanItem(1, "2026-10-04", "10:30", "11:15", "why"),
            AiPlanItem(1, null, "15:00", "15:30"), // the same task again
            AiPlanItem(2, null, "11:00", "11:30"), // overlaps the first proposal
            AiPlanItem(3, null, "12:30", "13:30"), // overlaps a busy meeting
            AiPlanItem(4, null, "08:00", "08:30"), // before working hours
            AiPlanItem(99, null, "16:00", "16:30"), // not offered
            AiPlanItem(2, "2026-10-09", "16:00", "16:30"), // not a planned day
            AiPlanItem(2, null, "۱۶:۰۰", "x"), // bad time
            AiPlanItem(2, null, "09:30", "09:45"), // before now
            AiPlanItem(2, null, "16:00", "16:30"),
        )
        val plan = PlanValidator.validate(items, tasks, listOf(day), DayPlanSettings(), listOf(range("13:00", "14:00")), LocalDateTime.of(day, LocalTime.of(10, 0)), zone)
        assertThat(plan.proposals.map { it.task.id to it.start }).containsExactly(1L to LocalTime.of(10, 30), 2L to LocalTime.of(16, 0)).inOrder()
        assertThat(plan.proposals.first().why).isEqualTo("why")
        assertThat(plan.skipped).isEqualTo(8)
    }

    @Test
    fun parsesTimes() {
        assertThat(PlanValidator.parseTime("9:05")).isEqualTo(LocalTime.of(9, 5))
        assertThat(PlanValidator.parseTime("17.30")).isEqualTo(LocalTime.of(17, 30))
        assertThat(PlanValidator.parseTime("25:00")).isNull()
        assertThat(PlanValidator.parseTime("soon")).isNull()
    }
}

class PlannerContextTest {
    @Test
    fun tasksAndNotes_readAsPlainLines() {
        val task = Task(
            id = 1, title = "گزارش\nماهانه", priority = Priority.HIGH, dueDate = LocalDate.of(2026, 10, 4), dueTime = LocalTime.of(17, 0),
            deadline = LocalDate.of(2026, 10, 6), projectId = 7, estimatedMinutes = 45, subtaskCount = 3, completedSubtaskCount = 1,
        )
        assertThat(PlannerContext.task(task, mapOf(7L to "کار"))).isEqualTo(
            "- [ ] گزارش ماهانه (high priority; planned Sunday 2026-10-04 17:00; deadline 2026-10-06; project کار; about 45 min; 1/3 subtasks done)",
        )
        assertThat(PlannerContext.today(LocalDate.of(2026, 10, 4), LocalTime.of(9, 5))).isEqualTo("Today is Sunday 2026-10-04, 09:05.")
        val note = Note(notebookId = 1, title = "سفر", document = NoteDocument(blocks = listOf(NoteBlock("1", text = "بلیت بخرم"))))
        assertThat(PlannerContext.note(note)).isEqualTo("سفر\n\nبلیت بخرم")
    }
}

@RunWith(RobolectricTestRunner::class)
class AssistantViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var ai: TestAiSettings
    private val api = ScriptedApi()

    @Before
    fun setUp() {
        graph = TestDataGraph()
        ai = TestAiSettings(graph.time)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        ai.close()
        graph.close()
    }

    private fun viewModel() = main.track(
        AssistantViewModel(
            ai.assistant(api), ai.repository, graph.tasks, graph.projects, graph.notes, graph.events,
            OfflineDayPlanRepository(graph.db, graph.db.taskDao(), graph.time), graph.settings, graph.time,
        ),
    )

    @Test
    fun notSetUp_showsSetupAndSendsNothing() = runBlocking<Unit> {
        val vm = viewModel()
        vm.state.awaitItem { it.ready == false }
        vm.setInput("hello")
        vm.send()
        assertThat(vm.state.value.messages).isEmpty()
        assertThat(api.requests).isEmpty()
    }

    @Test
    fun chat_streamsTheReply_withTheContextTheUserSaw() = runBlocking<Unit> {
        ai.configure()
        graph.tasks.save(Task(title = "خرید نان", dueDate = graph.time.today()))
        graph.events.save(CalendarEvent(title = "جلسه", date = graph.time.today(), startTime = LocalTime.of(15, 0), endTime = LocalTime.of(16, 0), allDay = false))
        api.reply = { "اول خرید نان را انجام بده." }
        val vm = viewModel()
        val ready = vm.state.awaitItem { it.ready == true && it.contextText.contains("خرید نان") }
        assertThat(ready.host).isEqualTo("api.avalai.ir")
        assertThat(ready.contextText).contains("15:00–16:00 جلسه")
        vm.setInput("امروز چه کنم؟")
        vm.send()
        val done = vm.state.awaitItem { s -> !s.streaming && s.messages.size == 2 && s.messages.last().text.isNotEmpty() }
        assertThat(done.messages.last().text).isEqualTo("اول خرید نان را انجام بده.")
        assertThat(done.input).isEmpty()
        val sent = api.requests.single()
        assertThat(sent.first().role).isEqualTo(AiRole.SYSTEM)
        assertThat(sent.last().text).contains(ready.contextText.trim())
        assertThat(sent.last().text).endsWith("امروز چه کنم؟")

        // A follow-up carries the history; "Nothing" sends no context.
        vm.setContext(ContextKind.NONE)
        vm.state.awaitItem { it.contextText.isEmpty() }
        vm.setInput("بعد؟")
        vm.send()
        vm.state.awaitItem { s -> !s.streaming && s.messages.size == 4 && s.messages.last().text.isNotEmpty() }
        val second = api.requests.last()
        assertThat(second.map { it.role }).containsExactly(AiRole.SYSTEM, AiRole.USER, AiRole.ASSISTANT, AiRole.USER).inOrder()
        assertThat(second.last().text).isEqualTo("بعد؟")
    }

    @Test
    fun noteContext_neverOffersLockedNotes() = runBlocking<Unit> {
        ai.configure()
        val book = graph.notes.saveNotebook(Notebook(title = "n"))
        val open = graph.notes.saveNote(Note(notebookId = book, title = "باز", document = NoteDocument(blocks = listOf(NoteBlock("1", text = "متن")))))
        val locked = graph.notes.saveNote(Note(notebookId = book, title = "قفل"))
        graph.db.noteDao().let { dao -> dao.updateNote(dao.getNote(locked)!!.copy(locked = true)) }
        val vm = viewModel()
        main.keepCollecting(vm.notesToPick)
        val notes = vm.notesToPick.awaitItem { it.isNotEmpty() }
        assertThat(notes.map { it.title }).containsExactly("باز")
        vm.setContext(ContextKind.NOTE, NoteRef(open, "باز"))
        assertThat(vm.state.awaitItem { it.contextText.isNotEmpty() }.contextText).isEqualTo("باز\n\nمتن")
    }

    @Test
    fun errors_areShownOnTheReply() = runBlocking<Unit> {
        ai.configure()
        api.error = AiError.NETWORK_UNREACHABLE
        val vm = viewModel()
        vm.state.awaitItem { it.ready == true }
        vm.setInput("hi")
        vm.send()
        val failed = vm.state.awaitItem { s -> !s.streaming && s.messages.lastOrNull()?.error != null }
        assertThat(failed.messages.last().error).isEqualTo(AiError.NETWORK_UNREACHABLE)
    }

    @Test
    fun planMyDay_isAPreviewUntilAccepted_andCanBeUndone() = runBlocking<Unit> {
        ai.configure()
        val today = graph.time.today()
        val a = graph.tasks.save(Task(title = "گزارش", dueDate = today, priority = Priority.HIGH))
        val b = graph.tasks.save(Task(title = "ورزش", dueDate = today))
        api.reply = { messages ->
            assertThat(messages.last().text).contains("\"plan\"")
            "```json\n{\"plan\": [{\"id\": $a, \"start\": \"13:00\", \"end\": \"14:00\"}, {\"id\": $b, \"start\": \"08:00\", \"end\": \"08:30\"}]}\n```"
        }
        val vm = viewModel()
        vm.state.awaitItem { it.ready == true }
        vm.plan(week = false)
        val preview = vm.state.awaitItem { it.plan?.loading == false }.plan!!
        assertThat(preview.proposals.map { it.task.id }).containsExactly(a)
        assertThat(preview.skipped).isEqualTo(1)
        // Nothing is written yet.
        assertThat(graph.tasks.getTask(a)!!.scheduledStart).isNull()

        val applied = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { vm.events.first() } }
        vm.acceptPlan()
        assertThat(applied.await()).isEqualTo(AssistantEvent.PlanApplied(1))
        val block = graph.tasks.getTask(a)!!
        assertThat(block.scheduledStart!!.atZone(graph.time.zone()).toLocalTime()).isEqualTo(LocalTime.of(13, 0))
        assertThat(vm.state.value.plan).isNull()

        val undone = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(10_000) { vm.events.first() } }
        vm.undoPlan()
        assertThat(undone.await()).isEqualTo(AssistantEvent.PlanUndone)
        assertThat(graph.tasks.getTask(a)!!.scheduledStart).isNull()
    }

    @Test
    fun planMyDay_withNothingOpen_saysSo() = runBlocking<Unit> {
        ai.configure()
        val vm = viewModel()
        vm.state.awaitItem { it.ready == true }
        vm.plan(week = true)
        assertThat(vm.state.awaitItem { it.plan?.loading == false }.plan!!.nothingToPlan).isTrue()
        assertThat(api.requests).isEmpty()
    }
}

@RunWith(RobolectricTestRunner::class)
class AiActionViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private lateinit var ai: TestAiSettings
    private val api = ScriptedApi()

    @Before
    fun setUp() {
        graph = TestDataGraph()
        ai = TestAiSettings(graph.time)
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        ai.close()
        graph.close()
    }

    private fun viewModel() = main.track(AiActionViewModel(ai.assistant(api), graph.tasks, graph.settings))

    private val note = AssistantRequest(AssistantSource.NOTE, "سفر", "باید بلیت بخرم و هتل رزرو کنم.", false, AssistantAction.entries)

    @Test
    fun textActions_streamAPreview_withoutFences() = runBlocking<Unit> {
        ai.configure()
        api.reply = { "```\nخلاصه‌ای کوتاه\n```" }
        val vm = viewModel()
        vm.run(AssistantAction.SUMMARIZE, note)
        val done = vm.state.awaitItem { !it.running && it.output.isNotEmpty() }
        assertThat(done.output).isEqualTo("خلاصه‌ای کوتاه")
        assertThat(done.sentChars).isEqualTo(note.text.length + note.title.length)
        assertThat(api.requests.single().last().text).contains(AssistantPrompts.CONTEXT_START)
        assertThat(api.requests.single().last().text).contains(note.text)
    }

    @Test
    fun extractedTasks_areCreatedOnlyOnConfirm_andCanBeUndone() = runBlocking<Unit> {
        ai.configure()
        api.reply = { "{\"items\": [\"بلیت بخر\", \"هتل رزرو کن\"]}" }
        val vm = viewModel()
        vm.run(AssistantAction.EXTRACT_TASKS, note)
        val listed = vm.state.awaitItem { !it.running && it.items.isNotEmpty() }
        assertThat(listed.items).containsExactly("بلیت بخر", "هتل رزرو کن").inOrder()
        assertThat(listed.selected).containsExactly(0, 1)
        vm.toggle(1)
        assertThat(vm.selectedItems()).containsExactly("بلیت بخر")
        vm.createTasks(projectId = null)
        val created = vm.state.awaitItem { it.createdTasks.isNotEmpty() }.createdTasks
        assertThat(graph.tasks.getTask(created.single())!!.title).isEqualTo("بلیت بخر")
        vm.undoCreatedTasks()
        vm.state.awaitItem { it.tasksUndone }
        assertThat(graph.tasks.getTask(created.single())?.deletedAt != null || graph.tasks.getTask(created.single()) == null).isTrue()
    }

    @Test
    fun notConfigured_andErrors_areNeutral() = runBlocking<Unit> {
        val vm = viewModel()
        vm.run(AssistantAction.REWRITE, note)
        assertThat(vm.state.awaitItem { !it.running && it.error != null }.error).isEqualTo(AiError.NOT_CONFIGURED)
        ai.configure()
        api.error = AiError.INVALID_KEY
        vm.run(AssistantAction.REWRITE, note)
        assertThat(vm.state.awaitItem { !it.running && it.error != null }.error).isEqualTo(AiError.INVALID_KEY)
    }
}

@RunWith(RobolectricTestRunner::class)
class AiSettingsViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private val time = com.behnamjalali.planb.core.testing.FakeTimeProvider()
    private val ai = TestAiSettings(time)
    private val api = ScriptedApi()

    @After
    fun tearDown() {
        main.clearViewModels()
        ai.close()
    }

    @Test
    fun providerKeyModelAndConsent() = runBlocking<Unit> {
        val vm = main.track(AiSettingsViewModel(ai.repository, api))
        vm.state.awaitItem { it.loaded }
        assertThat(vm.providers.first().id).isEqualTo("iran_gateway")
        vm.selectProvider(com.behnamjalali.planb.core.ai.AiProviders.ANTHROPIC)
        vm.state.awaitItem { it.settings.providerId == "anthropic" }
        vm.setKeyInput("sk-ant-secret")
        vm.saveKey()
        // Saved encrypted, checked, and (no suggestions for this provider) the models are listed.
        val checked = vm.state.awaitItem { it.check == ConnectionCheck.Ok && it.models.isNotEmpty() && it.model == "model-a" }
        assertThat(checked.keyInput).isEmpty()
        assertThat(checked.models).containsExactly("model-a", "model-b").inOrder()
        vm.state.awaitItem { it.settings.model == "model-a" && it.settings.isComplete }
        assertThat(api.tested).isEqualTo(0)

        // On only after consent.
        vm.setEnabled(true)
        assertThat(vm.state.value.consentDialog).isTrue()
        assertThat(ai.repository.current().enabled).isFalse()
        vm.confirmConsent()
        vm.state.awaitItem { it.settings.enabled }
        assertThat(ai.repository.current().consentAt).isEqualTo(time.now())

        vm.forget()
        vm.state.awaitItem { it.settings.providerId == null && !it.settings.hasKey }
    }

    @Test
    fun failedChecks_areNeutral() = runBlocking<Unit> {
        api.error = AiError.INVALID_KEY
        val vm = main.track(AiSettingsViewModel(ai.repository, api))
        vm.state.awaitItem { it.loaded }
        vm.selectProvider(com.behnamjalali.planb.core.ai.AiProviders.DEEPSEEK)
        vm.state.awaitItem { it.settings.providerId == "deepseek" }
        vm.setKeyInput("bad")
        vm.saveKey()
        assertThat((vm.state.awaitItem { it.check is ConnectionCheck.Failed }.check as ConnectionCheck.Failed).error).isEqualTo(AiError.INVALID_KEY)
        // Turning on needs a complete setup; a failed check doesn't stop it (the key may work later).
        vm.setEnabled(true)
        assertThat(vm.state.value.consentDialog).isTrue()
    }
}
