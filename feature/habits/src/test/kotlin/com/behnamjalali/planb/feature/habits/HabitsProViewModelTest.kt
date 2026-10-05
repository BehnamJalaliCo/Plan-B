package com.behnamjalali.planb.feature.habits

import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.data.wellbeing.HealthAvailability
import com.behnamjalali.planb.core.model.BadgeDefinition
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.ChallengeStatus
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.behnamjalali.planb.core.testing.awaitItem
import com.google.common.truth.Truth.assertThat
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

/** Plan-B Pro habit screens (#27–#29) on the real data layer. */
@RunWith(RobolectricTestRunner::class)
class HabitsProViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private lateinit var graph: TestDataGraph
    private val today get() = graph.time.today()

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() {
        main.clearViewModels()
        graph.close()
    }

    private suspend fun habit(title: String = "Read", startDaysAgo: Long = 90, metric: HealthMetric? = null, threshold: Long? = null) =
        graph.habits.save(Habit(title = title, startDate = today.minusDays(startDaysAgo), healthMetric = metric, healthThreshold = threshold))

    // region #28 statistics
    private fun statsViewModel(id: Long, handle: SavedStateHandle = SavedStateHandle(mapOf("habitId" to id))) =
        main.track(HabitStatsViewModel(handle, graph.habits, graph.settings, graph.time)).also { main.keepCollecting(it.state) }

    @Test
    fun stats_inTheUsersCalendar_andTheYearCanBeBrowsedBackToTheStart() = runBlocking<Unit> {
        val id = habit(startDaysAgo = 400)
        (0L..44L).forEach { graph.habits.checkIn(id, today.minusDays(it)) }
        val vm = statsViewModel(id)
        val state = vm.state.awaitItem { !it.loading }
        val a = state.analytics!!
        assertThat(a.currentStreak.count).isEqualTo(45)
        assertThat(a.weeks).hasSize(12)
        assertThat(a.months).hasSize(12)
        assertThat(a.months.last().span).isEqualTo(a.month.span)
        // Persian by default: a Jalali year (1405 for October 2026).
        assertThat(state.year).isEqualTo(1405)
        assertThat(state.canGoForward).isFalse()
        assertThat(state.canGoBack).isTrue()
        vm.moveYear(-1)
        val previous = vm.state.awaitItem { it.year == 1404 }
        assertThat(previous.canGoForward).isTrue()
        assertThat(previous.yearDone).isEqualTo(45 - state.yearDone)
        // The year stays chosen across process death.
        main.clearViewModels()
        graph.settings.update { it.copy(calendarSystemOverride = CalendarSystem.GREGORIAN) }
        val again = statsViewModel(id, SavedStateHandle(mapOf("habitId" to id, "habit_stats_year_offset" to 1)))
        assertThat(again.state.awaitItem { !it.loading && it.year < 2026 }.year).isEqualTo(2025)
    }

    @Test
    fun stats_ofADeletedHabit_reportMissing() = runBlocking<Unit> {
        val id = habit()
        val vm = statsViewModel(id)
        vm.state.awaitItem { !it.loading }
        graph.habits.delete(id)
        vm.state.awaitItem { it.missing }
    }
    // endregion

    // region #29 challenges and badges
    private fun challengesViewModel(handle: SavedStateHandle = SavedStateHandle()) =
        main.track(ChallengesViewModel(handle, graph.achievements, graph.habits, graph.time)).also { main.keepCollecting(it.state) }

    @Test
    fun challenges_start_onlyOnFreeHabits_andGiveUp() = runBlocking<Unit> {
        val read = habit("Read")
        val walk = habit("Walk")
        val vm = challengesViewModel()
        assertThat(vm.state.awaitItem { !it.loading && it.habits.size == 2 }.active).isEmpty()
        val started = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.start(read, 21)
        assertThat(started.await()).isEqualTo(ChallengesEvent.Started)
        val state = vm.state.awaitItem { it.active.size == 1 }
        assertThat(state.active.single().challenge.title).isEqualTo("Read")
        assertThat(state.habits.map { it.id }).containsExactly(walk)
        // Only the offered lengths.
        val failed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.start(walk, 5)
        assertThat(failed.await()).isEqualTo(ChallengesEvent.Failed)
        vm.abandon(state.active.single().challenge.id)
        val after = vm.state.awaitItem { it.active.isEmpty() && it.finished.size == 1 }
        assertThat(after.finished.single().status).isEqualTo(ChallengeStatus.ABANDONED)
        assertThat(after.habits).hasSize(2)
    }

    @Test
    fun challenges_refreshAwardsBadgesOnlyForPro_andTheTabIsRemembered() = runBlocking<Unit> {
        repeat(10) { graph.tasks.setStatus(graph.tasks.save(Task(title = "t$it")), TaskStatus.DONE) }
        val handle = SavedStateHandle(mapOf("badges" to true))
        val vm = challengesViewModel(handle)
        assertThat(vm.tab.value).isEqualTo(1)
        vm.state.awaitItem { !it.loading }
        vm.refresh(pro = false)
        assertThat(graph.db.wellbeingDao().badges()).isEmpty()
        vm.refresh(pro = true)
        val state = vm.state.awaitItem { it.earned == 1 }
        assertThat(state.badges.first { it.earned }.badge).isEqualTo(BadgeDefinition.TASKS_10)
        vm.selectTab(0)
        assertThat(handle.get<Int>("challenges_tab")).isEqualTo(0)
    }

    @Test
    fun celebration_showsEachRecentBadgeOnce() = runBlocking<Unit> {
        graph.achievements.evaluate() // the device's first evaluation: nothing to celebrate
        repeat(10) { graph.tasks.setStatus(graph.tasks.save(Task(title = "t$it")), TaskStatus.DONE) }
        graph.achievements.evaluate()
        val vm = main.track(BadgeCelebrationViewModel(graph.achievements)).also { main.keepCollecting(it.pending) }
        val pending = vm.pending.awaitItem { it.isNotEmpty() }
        assertThat(pending.single().badge).isEqualTo(BadgeDefinition.TASKS_10)
        vm.dismiss(pending.single())
        vm.pending.awaitItem { it.isEmpty() }
    }

    @Test
    fun detail_startsAChallenge_andShowsItsProgress() = runBlocking<Unit> {
        val id = habit()
        graph.habits.checkIn(id, today)
        val vm = main.track(HabitDetailViewModel(SavedStateHandle(mapOf("habitId" to id)), graph.habits, graph.settings, graph.time, graph.achievements, graph.healthSync))
        main.keepCollecting(vm.uiState)
        assertThat(vm.uiState.awaitItem { !it.loading }.challenge).isNull()
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.events.first() } }
        vm.startChallenge(7)
        assertThat(event.await()).isEqualTo(HabitEvent.ChallengeStarted)
        val state = vm.uiState.awaitItem { it.challenge != null }
        assertThat(state.challenge!!.done).isEqualTo(1)
        assertThat(state.challenge!!.required).isEqualTo(7)
    }
    // endregion

    // region #27 Health Connect
    @Test
    fun detail_showsTodaysHealthConnectAmount_andSyncsOnRequest() = runBlocking<Unit> {
        graph.pro = true
        val id = habit(metric = HealthMetric.STEPS, threshold = 8_000)
        graph.health.set(HealthMetric.STEPS, today, 6_000, graph.time.zone())
        val vm = main.track(HabitDetailViewModel(SavedStateHandle(mapOf("habitId" to id)), graph.habits, graph.settings, graph.time, graph.achievements, graph.healthSync))
        main.keepCollecting(vm.uiState)
        assertThat(vm.uiState.awaitItem { !it.loading }.health).isNull() // no access yet
        graph.health.grant(HealthMetric.STEPS)
        graph.health.set(HealthMetric.STEPS, today, 9_000, graph.time.zone())
        vm.syncHealth()
        val state = vm.uiState.awaitItem { it.health != null && it.item?.amounts?.get(today) == 1 }
        assertThat(state.health!!.value).isEqualTo(9_000)
    }

    @Test
    fun habitsList_syncsHealthConnectWhenItOpens() = runBlocking<Unit> {
        graph.pro = true
        val id = habit(metric = HealthMetric.HYDRATION_ML, threshold = 2_000)
        graph.health.grant(HealthMetric.HYDRATION_ML)
        graph.health.set(HealthMetric.HYDRATION_ML, today.minusDays(1), 2_500, graph.time.zone())
        val vm = main.track(HabitsViewModel(SavedStateHandle(), graph.habits, graph.settings, graph.time, graph.healthSync))
        main.keepCollecting(vm.uiState)
        vm.uiState.awaitItem { !it.loading }
        withTimeout(10_000) { while (graph.habits.amountOn(id, today.minusDays(1)) == 0) kotlinx.coroutines.delay(20) }
    }

    @Test
    fun editor_linksAMetric_withItsUsualGoal_inFriendlyUnits() = runBlocking<Unit> {
        graph.pro = true
        val handle = SavedStateHandle()
        val vm = main.track(HabitEditorViewModel(handle, graph.habits, graph.time, graph.healthSync))
        // The new habit's form is in place (edits made before that would be replaced).
        withTimeout(10_000) { while (!handle.contains("habit_form_original")) kotlinx.coroutines.delay(20) }
        vm.update { it.copy(title = "Sleep well") }
        vm.setHealthMetric(HealthMetric.SLEEP_MINUTES)
        val form = vm.form.awaitItem { it.metric == HealthMetric.SLEEP_MINUTES }
        assertThat(form.healthThreshold).isEqualTo("7")
        assertThat(vm.healthPermissions()).containsExactly("read:SLEEP_MINUTES")
        assertThat(vm.health.value.granted).isFalse()
        graph.health.grant(HealthMetric.SLEEP_MINUTES)
        vm.refreshHealth()
        vm.health.awaitItem { it.granted && it.availability == HealthAvailability.AVAILABLE }
        vm.update { it.copy(healthThreshold = "۷٫۵") } // Persian digits and decimal separator
        assertThat(vm.form.awaitItem { it.healthThreshold == "۷٫۵" }.thresholdValue).isEqualTo(450)
        vm.update { it.copy(healthThreshold = "30") } // 30 hours of sleep is not a goal
        assertThat(vm.form.awaitItem { it.healthThreshold == "30" }.valid).isFalse()
        vm.update { it.copy(healthThreshold = "8") }
        vm.form.awaitItem { it.valid }
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(20_000) { vm.saved.first() } }
        vm.save()
        assertThat(saved.await()).isTrue()
        val habit = graph.habits.observeHabits(today, today).first().single().habit
        assertThat(habit.healthMetric).isEqualTo(HealthMetric.SLEEP_MINUTES)
        assertThat(habit.healthThreshold).isEqualTo(480)
        // Editing again shows the goal in hours, and unlinking clears it.
        assertThat(HabitForm.from(habit).healthThreshold).isEqualTo("8")
        assertThat(HabitForm.from(habit).copy(healthMetric = null).toHabit().healthThreshold).isNull()
    }

    @Test
    fun healthUnits_roundTrip() {
        assertThat(HealthUnits.parse(HealthMetric.STEPS, "۸۰۰۰")).isEqualTo(8_000)
        assertThat(HealthUnits.parse(HealthMetric.STEPS, "8000.5")).isNull()
        assertThat(HealthUnits.parse(HealthMetric.DISTANCE_METERS, "2.5")).isEqualTo(2_500)
        assertThat(HealthUnits.parse(HealthMetric.HYDRATION_ML, "-3")).isNull()
        assertThat(HealthUnits.parse(HealthMetric.ACTIVE_MINUTES, "abc")).isNull()
        assertThat(HealthUnits.format(HealthMetric.DISTANCE_METERS, 2_500)).isEqualTo("2.5")
        assertThat(HealthUnits.format(HealthMetric.SLEEP_MINUTES, 450)).isEqualTo("7.5")
        assertThat(HealthUnits.format(HealthMetric.STEPS, 8_000)).isEqualTo("8000")
    }
    // endregion
}
