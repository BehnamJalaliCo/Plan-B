package com.behnamjalali.planb.core.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.RitualState
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Morning/evening ritual reminders (Plan-B Pro #8). */
@RunWith(RobolectricTestRunner::class)
class RitualRemindersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var graph: TestDataGraph
    private lateinit var rituals: RitualReminders
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))
    private val zone get() = graph.time.zone()

    @Before
    fun setUp() {
        graph = TestDataGraph()
        val notifier = Notifier(context)
        notifier.createChannels()
        rituals = RitualReminders(context, notifier, graph.settings, graph.time)
    }

    @After
    fun tearDown() = graph.close()

    private fun enable(morning: Boolean = true, evening: Boolean = true) = runBlocking {
        graph.settings.update {
            it.copy(
                language = AppLanguage.ENGLISH,
                dayPlan = it.dayPlan.copy(morningReminder = morning, morningTime = LocalTime.of(7, 30), eveningReminder = evening, eveningTime = LocalTime.of(21, 0)),
            )
        }
    }

    private fun localAt(instant: Instant) = instant.atZone(zone).toLocalDateTime()

    @Test
    fun nextAt_isStrictlyAfterNow_andSkipsAGap() {
        val tehran = ZoneId.of("Asia/Tehran")
        val now = Instant.parse("2026-10-04T04:00:00Z") // 07:30 in Tehran
        assertThat(RitualReminders.nextAt(LocalTime.of(7, 30), now, tehran)).isEqualTo(Instant.parse("2026-10-05T04:00:00Z"))
        assertThat(RitualReminders.nextAt(LocalTime.of(8, 0), now, tehran)).isEqualTo(Instant.parse("2026-10-04T04:30:00Z"))
        val ny = ZoneId.of("America/New_York")
        val beforeSpring = Instant.parse("2026-03-08T05:00:00Z") // 00:00 EST
        assertThat(RitualReminders.nextAt(LocalTime.of(2, 30), beforeSpring, ny).atZone(ny).toLocalTime()).isEqualTo(LocalTime.of(3, 30))
    }

    @Test
    fun sync_armsBothReminders_andTurningThemOffCancels() = runBlocking {
        enable()
        rituals.sync()
        val scheduled = alarms.scheduledAlarms.map { localAt(Instant.ofEpochMilli(it.triggerAtMs)).toLocalTime() }
        // Now is 12:00: the morning reminder is tomorrow, the evening one today.
        assertThat(scheduled).containsExactly(LocalTime.of(7, 30), LocalTime.of(21, 0))
        enable(morning = false, evening = false)
        rituals.sync()
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun delivery_postsTheReminder_andArmsTheNextDay() = runBlocking {
        enable(morning = false)
        graph.time.setLocal(graph.time.today(), LocalTime.of(21, 0))
        rituals.deliver(Ritual.EVENING)
        val posted = notifications.getNotification(Ritual.EVENING.requestCode)
        assertThat(posted).isNotNull()
        assertThat(shadowOf(posted).contentTitle.toString()).isEqualTo("Evening shutdown")
        val next = Instant.ofEpochMilli(alarms.scheduledAlarms.single().triggerAtMs)
        assertThat(localAt(next)).isEqualTo(graph.time.today().plusDays(1).atTime(21, 0))
    }

    @Test
    fun delivery_isSkippedWhenTheRitualWasDoneToday_orTheReminderIsOff() = runBlocking {
        enable()
        graph.settings.update { it.copy(rituals = RitualState(morningDoneOn = graph.time.today())) }
        rituals.deliver(Ritual.MORNING)
        assertThat(notifications.getNotification(Ritual.MORNING.requestCode)).isNull()
        // Still armed for tomorrow.
        assertThat(alarms.scheduledAlarms).hasSize(1)
        enable(morning = false)
        rituals.deliver(Ritual.MORNING)
        assertThat(notifications.getNotification(Ritual.MORNING.requestCode)).isNull()
        // Turned off: no alarm is left for the morning.
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun ritualCodes_neverCollideWithItemReminders() {
        val actionNames = listOf(ReminderActionReceiver.ACTION_DONE, ReminderActionReceiver.ACTION_SNOOZE, ReminderActionReceiver.ACTION_DISMISS)
        val itemCodes = (0L..5_000L).flatMap { id ->
            ReminderKind.entries.map { Notifier.notificationId(it, id) } + actionNames.map { Notifier.actionRequestCode(id, it) }
        }.toSet()
        Ritual.entries.forEach { assertThat(itemCodes).doesNotContain(it.requestCode) }
        assertThat(Ritual.entries.map { it.requestCode }.toSet()).hasSize(Ritual.entries.size)
        assertThat(Ritual.fromPath("evening")).isEqualTo(Ritual.EVENING)
        assertThat(Ritual.fromPath("noon")).isNull()
    }
}
