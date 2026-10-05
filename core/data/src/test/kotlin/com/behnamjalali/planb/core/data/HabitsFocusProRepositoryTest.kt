package com.behnamjalali.planb.core.data

import com.behnamjalali.planb.core.data.repository.OfflineJournalRepository
import com.behnamjalali.planb.core.data.wellbeing.HealthAvailability
import com.behnamjalali.planb.core.model.BadgeDefinition
import com.behnamjalali.planb.core.model.ChallengeStatus
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.HealthMetric
import com.behnamjalali.planb.core.model.StrictModeState
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.model.TaskStatus
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Plan-B Pro habits and focus in the data layer (#26–#30). */
@RunWith(RobolectricTestRunner::class)
class HabitsFocusProRepositoryTest {
    private lateinit var graph: TestDataGraph
    private val today get() = graph.time.today()

    @Before
    fun setUp() {
        graph = TestDataGraph()
    }

    @After
    fun tearDown() = graph.close()

    private suspend fun habit(schedule: HabitSchedule = HabitSchedule.Daily, startDaysAgo: Long = 30, target: Int = 1, metric: HealthMetric? = null, threshold: Long? = null) =
        graph.habits.save(Habit(title = "Walk", schedule = schedule, target = target, startDate = today.minusDays(startDaysAgo), healthMetric = metric, healthThreshold = threshold))

    // region #26 focus effects
    @Test
    fun focusRepository_reportsEveryChangeOfTheActiveSession_withSoundAndStrict() = runBlocking<Unit> {
        val focus = graph.focus
        val started = focus.start(Duration.ofMinutes(25).toMillis(), null, soundId = "rain", strict = true)
        assertThat(started.soundId).isEqualTo("rain")
        assertThat(started.strict).isTrue()
        focus.pause()
        focus.resume()
        focus.setSound(null)
        focus.setStrict(false)
        focus.setStrict(false) // no change: not reported
        focus.cancel()
        val changes = graph.focusEffects.changes
        assertThat(changes.map { it?.status }).containsExactly(
            FocusStatus.RUNNING, FocusStatus.PAUSED, FocusStatus.RUNNING, FocusStatus.RUNNING, FocusStatus.RUNNING, null,
        ).inOrder()
        assertThat(changes[3]!!.soundId).isNull()
        assertThat(changes[4]!!.strict).isFalse()
        // Stored with the session (and so in backups).
        assertThat(graph.db.focusDao().get(started.id)!!.soundId).isNull()
    }

    @Test
    fun focusEnd_fromTheAlarmIsReportedToo() = runBlocking<Unit> {
        graph.focus.start(60_000, null, soundId = "ocean", strict = true)
        graph.time.advance(Duration.ofMinutes(2))
        assertThat(graph.focus.completeIfElapsed()!!.status).isEqualTo(FocusStatus.COMPLETED)
        assertThat(graph.focusEffects.changes.last()).isNull()
    }

    @Test
    fun strictModeStateIsStoredOnTheDevice() = runBlocking<Unit> {
        val state = graph.wellbeingState
        assertThat(state.strictMode()).isNull()
        state.setStrictMode(StrictModeState(1, 2))
        assertThat(state.strictMode()).isEqualTo(StrictModeState(1, 2))
        state.setStrictMode(null)
        assertThat(state.strictMode()).isNull()
    }
    // endregion

    // region #27 Health Connect
    @Test
    fun healthSync_checksOffDaysThatReachedTheThreshold_onlyWithProAndPermission() = runBlocking<Unit> {
        val zone = graph.time.zone()
        val id = habit(target = 2, metric = HealthMetric.STEPS, threshold = 8_000)
        graph.health.set(HealthMetric.STEPS, today, 9_000, zone)
        graph.health.set(HealthMetric.STEPS, today.minusDays(1), 4_000, zone)
        graph.health.set(HealthMetric.STEPS, today.minusDays(3), 8_000, zone)
        // Not Pro, then no permission: nothing happens.
        assertThat(graph.healthSync.sync(force = true)).isEqualTo(0)
        graph.pro = true
        assertThat(graph.healthSync.sync(force = true)).isEqualTo(0)
        graph.health.grant(HealthMetric.STEPS)
        assertThat(graph.healthSync.sync(force = true)).isEqualTo(2)
        assertThat(graph.habits.amountOn(id, today)).isEqualTo(2)
        assertThat(graph.habits.amountOn(id, today.minusDays(3))).isEqualTo(2)
        assertThat(graph.habits.amountOn(id, today.minusDays(1))).isEqualTo(0)
        // Unchecking a day by hand sticks.
        graph.habits.checkIn(id, today, -2)
        assertThat(graph.healthSync.sync(force = true)).isEqualTo(0)
        assertThat(graph.habits.amountOn(id, today)).isEqualTo(0)
        // A day that reaches the threshold later is checked off then.
        graph.health.set(HealthMetric.STEPS, today.minusDays(1), 12_000, zone)
        assertThat(graph.healthSync.sync(force = true)).isEqualTo(1)
        assertThat(graph.healthSync.today(graph.habits.getHabit(id)!!)!!.value).isEqualTo(9_000)
    }

    @Test
    fun healthSync_isThrottled_andSkippedWithoutHealthConnect() = runBlocking<Unit> {
        graph.pro = true
        habit(metric = HealthMetric.SLEEP_MINUTES, threshold = 420)
        graph.health.grant(HealthMetric.SLEEP_MINUTES)
        graph.healthSync.sync(force = true)
        val reads = graph.health.reads
        assertThat(reads).isGreaterThan(0)
        graph.healthSync.sync()
        assertThat(graph.health.reads).isEqualTo(reads)
        graph.time.advance(Duration.ofMinutes(16))
        graph.healthSync.sync()
        assertThat(graph.health.reads).isGreaterThan(reads)
        graph.health.available = HealthAvailability.NEEDS_INSTALL
        val before = graph.health.reads
        graph.healthSync.sync(force = true)
        assertThat(graph.health.reads).isEqualTo(before)
    }
    // endregion

    // region #29 challenges and badges
    @Test
    fun firstEvaluation_awardsExistingAchievementsWithoutCelebrating() = runBlocking<Unit> {
        repeat(10) { graph.tasks.setStatus(graph.tasks.save(Task(title = "t$it")), TaskStatus.DONE) }
        assertThat(graph.achievements.evaluate()).containsExactly(BadgeDefinition.TASKS_10)
        assertThat(graph.achievements.observeUncelebrated().first()).isEmpty()
    }

    @Test
    fun challenges_followTheData_andOneActivePerHabit() = runBlocking<Unit> {
        val id = habit()
        val challenge = graph.achievements.startChallenge(id, 7)
        assertThat(graph.achievements.startChallenge(id, 21)).isEqualTo(challenge)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { graph.achievements.startChallenge(id, 0) } }
        graph.habits.checkIn(id, today)
        var progress = graph.achievements.observeChallenges().first().single()
        assertThat(progress.status).isEqualTo(ChallengeStatus.ACTIVE)
        assertThat(progress.done).isEqualTo(1)
        // Two days later with yesterday missed: failed, and stored as such after evaluating.
        graph.time.advance(Duration.ofDays(2))
        graph.achievements.evaluate()
        assertThat(graph.db.challengeDao().get(challenge)!!.status).isEqualTo("FAILED")
        // Checking in the missed day brings it back.
        graph.habits.checkIn(id, today.minusDays(1))
        graph.achievements.evaluate()
        assertThat(graph.db.challengeDao().get(challenge)!!.status).isEqualTo("ACTIVE")
        // Finish all seven days: completed on the last one, with a badge.
        (0L..6L).map { graph.time.today().minusDays(2).plusDays(it) }.forEach { if (graph.habits.amountOn(id, it) == 0) graph.habits.checkIn(id, it) }
        graph.time.advance(Duration.ofDays(4))
        val awarded = graph.achievements.evaluate()
        assertThat(awarded).containsAtLeast(BadgeDefinition.CHALLENGE_7, BadgeDefinition.STREAK_7)
        val stored = graph.db.challengeDao().get(challenge)!!
        assertThat(stored.status).isEqualTo("COMPLETED")
        assertThat(stored.completedAt).isEqualTo(stored.startDate.plusDays(6).atStartOfDay(graph.time.zone()).toInstant())
        progress = graph.achievements.observeChallenges().first().single()
        assertThat(progress.completedOn).isEqualTo(stored.startDate.plusDays(6))
        // Abandoned challenges stay abandoned; a new one can start.
        val second = graph.achievements.startChallenge(id, 21)
        graph.achievements.abandon(second)
        graph.achievements.evaluate()
        assertThat(graph.db.challengeDao().get(second)!!.status).isEqualTo("ABANDONED")
    }

    @Test
    fun badges_areAwardedOnce_withTheDayTheyWereReached_andCelebratedOnlyWhenRecent() = runBlocking<Unit> {
        // The first evaluation on a device catches up with existing data without celebrating.
        graph.tasks.setStatus(graph.tasks.save(Task(title = "old")), TaskStatus.DONE)
        assertThat(graph.achievements.evaluate()).isEmpty()
        repeat(9) { i ->
            val task = graph.tasks.save(Task(title = "t$i"))
            graph.tasks.setStatus(task, TaskStatus.DONE)
        }
        repeat(3) {
            graph.focus.start(Duration.ofMinutes(25).toMillis(), null)
            graph.time.advance(Duration.ofMinutes(25))
            graph.focus.finish()
        }
        val awarded = graph.achievements.evaluate()
        assertThat(awarded).containsExactly(BadgeDefinition.TASKS_10, BadgeDefinition.FOCUS_1)
        assertThat(graph.achievements.evaluate()).isEmpty()
        val badges = graph.db.wellbeingDao().badges()
        assertThat(badges.map { it.key }).containsExactly("tasks_10", "focus_1h")
        assertThat(badges.first().earnedAt).isEqualTo(today.atStartOfDay(graph.time.zone()).toInstant())
        val uncelebrated = graph.achievements.observeUncelebrated().first()
        assertThat(uncelebrated.map { it.badge }).containsExactly(BadgeDefinition.FOCUS_1, BadgeDefinition.TASKS_10).inOrder()
        graph.achievements.markCelebrated(uncelebrated.last().id)
        assertThat(graph.achievements.observeUncelebrated().first()).isEmpty()
        val gallery = graph.achievements.observeBadges().first().associateBy { it.badge }
        assertThat(gallery.getValue(BadgeDefinition.TASKS_10).earned).isTrue()
        assertThat(gallery.getValue(BadgeDefinition.TASKS_100).earned).isFalse()
        assertThat(gallery.getValue(BadgeDefinition.TASKS_100).progress.current).isEqualTo(10)
        // Old achievements found later are awarded but not celebrated.
        graph.time.advance(Duration.ofDays(10))
        val journal = OfflineJournalRepository(graph.db, graph.notes, graph.time)
        (1L..7L).forEach { journal.openPage(graph.time.today().minusDays(it + 5), "Journal", "p", null, null) }
        assertThat(graph.achievements.evaluate()).containsExactly(BadgeDefinition.JOURNAL_7)
        assertThat(graph.achievements.observeUncelebrated().first()).isEmpty()
    }
    // endregion

    // region #30 mood
    @Test
    fun moodTracker_addsSeveralCheckInsADay_besideTheJournalsOwn() = runBlocking<Unit> {
        val journal = OfflineJournalRepository(graph.db, graph.notes, graph.time)
        val page = journal.openPage(today, "Journal", "Today", null, null)
        journal.setPageMood(today, page, 2, 2)
        val first = graph.moods.checkIn(4, 3, listOf("#work", "calm, ok", "work"))
        graph.time.advance(Duration.ofHours(3))
        graph.moods.checkIn(null, 5)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { graph.moods.checkIn(null, null) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { graph.moods.checkIn(6, null) } }
        val entries = graph.moods.observeEntries(today, today).first()
        assertThat(entries).hasSize(3)
        assertThat(entries.first().energy).isEqualTo(5) // newest first
        assertThat(graph.moods.get(first)!!.tags).containsExactly("work", "calm  ok")
        // The journal still finds its own check-in.
        assertThat(journal.pageMood(today, page)!!.mood).isEqualTo(2)
        graph.moods.update(first, 5, null, emptyList())
        assertThat(graph.moods.get(first)!!.mood).isEqualTo(5)
        graph.moods.update(first, null, null, emptyList())
        assertThat(graph.moods.get(first)).isNull()
        assertThat(journal.pageMood(today, page)).isNotNull()
    }

    @Test
    fun moodTracker_focusMinutesPerDay() = runBlocking<Unit> {
        graph.focus.start(Duration.ofMinutes(30).toMillis(), null)
        graph.time.advance(Duration.ofMinutes(30))
        graph.focus.finish()
        assertThat(graph.moods.observeFocusMinutes(today.minusDays(1), today).first()).containsExactly(today, 30)
    }
    // endregion
}
