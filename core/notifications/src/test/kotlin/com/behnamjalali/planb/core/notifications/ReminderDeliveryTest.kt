package com.behnamjalali.planb.core.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.Habit
import com.behnamjalali.planb.core.model.HabitSchedule
import com.behnamjalali.planb.core.model.Task
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import java.time.Duration
import java.time.LocalTime
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReminderDeliveryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var graph: TestDataGraph
    private lateinit var scheduler: AlarmReminderScheduler
    private lateinit var delivery: ReminderDelivery
    private lateinit var nagState: NagStateStore
    private lateinit var actions: ReminderActions
    private val prefsDir = java.nio.file.Files.createTempDirectory("nag").toFile()
    private val storeScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val time get() = graph.time
    private val today get() = time.today()
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Before
    fun setUp() {
        graph = TestDataGraph()
        val notifier = Notifier(context)
        notifier.createChannels()
        nagState = NagStateStore(
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = storeScope) { java.io.File(prefsDir, "nag.preferences_pb") },
        )
        scheduler = AlarmReminderScheduler(
            context, Lazy { graph.tasks }, Lazy { graph.events }, Lazy { graph.habits }, Lazy { graph.focus }, notifier, graph.time,
            Lazy { graph.planning }, nagState,
        )
        delivery = ReminderDelivery(
            context, graph.tasks, graph.events, graph.habits, graph.focus, notifier, scheduler, graph.time, graph.settings, graph.planning,
        )
        actions = ReminderActions(graph.tasks, notifier, scheduler, nagState, graph.time)
    }

    @After
    fun tearDown() {
        graph.close()
        storeScope.cancel()
        prefsDir.deleteRecursively()
    }

    private fun posted(kind: ReminderKind, id: Long) =
        notifications.activeNotifications.any { it.id == Notifier.notificationId(kind, id) }

    @Test
    fun oneOffEventReminder_staysVisibleAfterDelivery() = runBlocking<Unit> {
        val start = time.now().plus(Duration.ofHours(2)).atZone(time.zone()).toLocalTime().withSecond(0).withNano(0)
        val id = graph.events.save(CalendarEvent(title = "Dentist", date = today, startTime = start, reminderOffsetMinutes = 30))
        scheduler.syncEvent(id)
        val planned = alarms.peekNextScheduledAlarm()!!.triggerAtMs
        time.instant = java.time.Instant.ofEpochMilli(planned)

        delivery.deliver(ReminderKind.EVENT, id, today, java.time.Instant.ofEpochMilli(planned))

        assertThat(posted(ReminderKind.EVENT, id)).isTrue()
        // No later occurrence: the alarm is gone, the notification is not.
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun habitReminder_staysVisible_andNextDayIsScheduled() = runBlocking<Unit> {
        val reminderTime = time.localNow().toLocalTime().plusMinutes(5).withSecond(0).withNano(0)
        val id = graph.habits.save(Habit(title = "Read", startDate = today, reminderTime = reminderTime))
        scheduler.syncHabit(id)
        val planned = alarms.peekNextScheduledAlarm()!!.triggerAtMs
        time.instant = java.time.Instant.ofEpochMilli(planned)

        delivery.deliver(ReminderKind.HABIT, id, today, java.time.Instant.ofEpochMilli(planned))

        assertThat(posted(ReminderKind.HABIT, id)).isTrue()
        val next = alarms.peekNextScheduledAlarm()!!.triggerAtMs
        assertThat(next).isEqualTo(planned + Duration.ofDays(1).toMillis())
    }

    @Test
    fun staleAlarm_doesNotNotify() = runBlocking<Unit> {
        val due = time.localNow().plusHours(3)
        val id = graph.tasks.save(Task(title = "Call", dueDate = due.toLocalDate(), dueTime = due.toLocalTime().withSecond(0).withNano(0), reminderOffsetMinutes = 60))
        scheduler.syncTask(id)
        val planned = java.time.Instant.ofEpochMilli(alarms.peekNextScheduledAlarm()!!.triggerAtMs)
        // An alarm left over from an older version of the reminder (e.g. before delete-all + restore).
        val stale = planned.minus(Duration.ofMinutes(45))
        time.instant = stale

        delivery.deliver(ReminderKind.TASK, id, today, stale)

        assertThat(posted(ReminderKind.TASK, id)).isFalse()
        // The real reminder is still scheduled.
        assertThat(alarms.peekNextScheduledAlarm()!!.triggerAtMs).isEqualTo(planned.toEpochMilli())

        time.instant = planned
        delivery.deliver(ReminderKind.TASK, id, today, planned)
        assertThat(posted(ReminderKind.TASK, id)).isTrue()
    }

    @Test
    fun taskText_usesPersianSeparator() = runBlocking<Unit> {
        graph.settings.update { it.copy(language = AppLanguage.PERSIAN) }
        val due = time.localNow().plusHours(1).withSecond(0).withNano(0)
        val id = graph.tasks.save(Task(title = "تماس", dueDate = due.toLocalDate(), dueTime = due.toLocalTime(), reminderOffsetMinutes = 0))
        scheduler.syncTask(id)
        val planned = java.time.Instant.ofEpochMilli(alarms.peekNextScheduledAlarm()!!.triggerAtMs)
        time.instant = planned

        delivery.deliver(ReminderKind.TASK, id, today, planned)

        val text = notifications.activeNotifications.single().notification.extras.getCharSequence("android.text").toString()
        assertThat(text).startsWith("تماس، ")
        assertThat(text).doesNotContain("·")
    }

    @Test
    fun focusEndAlarm_completesSession_andRecordsTimeOnTask() = runBlocking<Unit> {
        val taskId = graph.tasks.save(Task(title = "Write"))
        graph.focus.start(Duration.ofMinutes(25).toMillis(), taskId)
        time.advance(Duration.ofMinutes(25))

        delivery.deliver(ReminderKind.FOCUS, 0, today, null)

        assertThat(graph.focus.getActive()).isNull()
        assertThat(graph.tasks.getTask(taskId)!!.actualMinutes).isEqualTo(25)
    }

    @Test
    fun rescheduleAll_restoresRunningFocusEnd_andHabitsFarAhead() = runBlocking<Unit> {
        graph.focus.start(Duration.ofMinutes(30).toMillis(), null)
        time.advance(Duration.ofMinutes(10))
        val habit = graph.habits.save(
            Habit(title = "Review", schedule = HabitSchedule.EveryNDays(30), startDate = today.plusDays(1), reminderTime = LocalTime.of(9, 0)),
        )

        scheduler.rescheduleAll()

        val triggers = alarms.scheduledAlarms.map { it.triggerAtMs }
        assertThat(triggers).contains(time.now().plus(Duration.ofMinutes(20)).toEpochMilli())
        assertThat(triggers).contains(today.plusDays(1).atTime(9, 0).atZone(time.zone()).toInstant().toEpochMilli())
        assertThat(graph.habits.getHabit(habit)).isNotNull()
    }

    // region Plan-B Pro #12: several reminders, nagging, Done and Snooze

    private fun nextAlarm(): java.time.Instant = java.time.Instant.ofEpochMilli(alarms.peekNextScheduledAlarm()!!.triggerAtMs)

    private fun actionTitles() = notifications.activeNotifications.single().notification.actions.orEmpty().map { it.title.toString() }

    @Test
    fun extraReminders_fireOneAfterAnother_throughOneAlarm() = runBlocking<Unit> {
        graph.settings.update { it.copy(language = AppLanguage.ENGLISH) }
        val due = time.localNow().plusHours(3).withSecond(0).withNano(0)
        val id = graph.tasks.save(Task(title = "Call", dueDate = due.toLocalDate(), dueTime = due.toLocalTime(), reminderOffsetMinutes = 0))
        graph.planning.setReminders(
            id,
            listOf(com.behnamjalali.planb.core.model.TaskReminder(kind = com.behnamjalali.planb.core.model.TaskReminderKind.OFFSET, offsetMinutes = 60)),
            nagIntervalMinutes = 10,
        )
        scheduler.syncTask(id)
        val first = nextAlarm()
        assertThat(first).isEqualTo(due.minusHours(1).atZone(time.zone()).toInstant())
        time.instant = first
        delivery.deliver(ReminderKind.TASK, id, today, first)
        assertThat(posted(ReminderKind.TASK, id)).isTrue()
        // Pro reminders get the Done and Snooze buttons.
        assertThat(actionTitles()).containsExactly("Done", "Snooze 10 min").inOrder()
        graph.settings.update { it.copy(language = AppLanguage.PERSIAN) }
        delivery.deliver(ReminderKind.TASK, id, today, first)
        assertThat(actionTitles()).containsExactly("انجام شد", "۱۰ دقیقهٔ دیگر").inOrder()
        // The single alarm moved on to the primary reminder.
        assertThat(alarms.scheduledAlarms).hasSize(1)
        assertThat(nextAlarm()).isEqualTo(due.atZone(time.zone()).toInstant())
    }

    @Test
    fun nagging_repeatsUntilDone_andDoneCompletesTheTask() = runBlocking<Unit> {
        val due = time.localNow().plusMinutes(30).withSecond(0).withNano(0)
        val id = graph.tasks.save(Task(title = "Pills", dueDate = due.toLocalDate(), dueTime = due.toLocalTime(), reminderOffsetMinutes = 0, nag = true))
        graph.planning.setReminders(id, emptyList(), nagIntervalMinutes = 5)
        scheduler.syncTask(id)
        val fired = nextAlarm()
        time.instant = fired
        delivery.deliver(ReminderKind.TASK, id, today, fired)
        assertThat(nextAlarm()).isEqualTo(fired.plus(Duration.ofMinutes(5)))
        // The repetition passes the stale-alarm check and notifies again.
        time.instant = fired.plus(Duration.ofMinutes(5))
        delivery.deliver(ReminderKind.TASK, id, today, time.now())
        assertThat(posted(ReminderKind.TASK, id)).isTrue()
        assertThat(nextAlarm()).isEqualTo(fired.plus(Duration.ofMinutes(10)))

        actions.done(id)

        assertThat(graph.tasks.getTask(id)!!.isCompleted).isTrue()
        assertThat(posted(ReminderKind.TASK, id)).isFalse()
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun nagging_stopsAfterTwelveRepeats() = runBlocking<Unit> {
        val due = time.localNow().plusMinutes(1).withSecond(0).withNano(0)
        val id = graph.tasks.save(Task(title = "Stretch", dueDate = due.toLocalDate(), dueTime = due.toLocalTime(), reminderOffsetMinutes = 0, nag = true))
        scheduler.syncTask(id)
        val fired = nextAlarm()
        time.instant = fired.plus(Duration.ofMinutes(11 * 10L + 1))
        scheduler.syncTask(id)
        assertThat(nextAlarm()).isEqualTo(fired.plus(Duration.ofMinutes(120)))
        time.instant = fired.plus(Duration.ofMinutes(121))
        scheduler.syncTask(id)
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun snooze_postponesAndStopsEarlierNagging_dismissStopsIt() = runBlocking<Unit> {
        val due = time.localNow().plusMinutes(20).withSecond(0).withNano(0)
        val id = graph.tasks.save(Task(title = "Email", dueDate = due.toLocalDate(), dueTime = due.toLocalTime(), reminderOffsetMinutes = 0, nag = true))
        scheduler.syncTask(id)
        val fired = nextAlarm()
        time.instant = fired
        delivery.deliver(ReminderKind.TASK, id, today, fired)

        time.advance(Duration.ofMinutes(2))
        actions.snooze(id)
        assertThat(posted(ReminderKind.TASK, id)).isFalse()
        val snoozed = time.now().plus(Duration.ofMinutes(10))
        assertThat(nextAlarm()).isEqualTo(snoozed)

        time.instant = snoozed
        delivery.deliver(ReminderKind.TASK, id, today, snoozed)
        assertThat(posted(ReminderKind.TASK, id)).isTrue()
        // Nagging continues from the snoozed reminder until the notification is swiped away.
        assertThat(nextAlarm()).isEqualTo(snoozed.plus(Duration.ofMinutes(10)))
        actions.dismiss(id)
        assertThat(alarms.scheduledAlarms).isEmpty()
        assertThat(graph.tasks.getTask(id)!!.isCompleted).isFalse()
    }

    @Test
    fun freeReminder_hasNoButtons() = runBlocking<Unit> {
        val due = time.localNow().plusHours(1).withSecond(0).withNano(0)
        val id = graph.tasks.save(Task(title = "Plain", dueDate = due.toLocalDate(), dueTime = due.toLocalTime(), reminderOffsetMinutes = 0))
        scheduler.syncTask(id)
        val planned = nextAlarm()
        time.instant = planned
        delivery.deliver(ReminderKind.TASK, id, today, planned)
        assertThat(actionTitles()).isEmpty()
    }

    @Test
    fun requestCodes_neverCollide() {
        val actionNames = listOf(ReminderActionReceiver.ACTION_DONE, ReminderActionReceiver.ACTION_SNOOZE, ReminderActionReceiver.ACTION_DISMISS)
        val codes = (1L..2_000L).flatMap { id ->
            ReminderKind.entries.map { Notifier.notificationId(it, id) } + actionNames.map { Notifier.actionRequestCode(id, it) }
        }
        assertThat(codes.toSet()).hasSize(codes.size)
    }

    // endregion
}
