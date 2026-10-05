package com.behnamjalali.planb.core.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.data.repository.OfflineJournalRepository
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The daily journal reminder (Plan-B Pro #25). */
@RunWith(RobolectricTestRunner::class)
class JournalRemindersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var graph: TestDataGraph
    private lateinit var journal: OfflineJournalRepository
    private lateinit var reminders: JournalReminders
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Before
    fun setUp() {
        graph = TestDataGraph()
        val notifier = Notifier(context)
        notifier.createChannels()
        journal = OfflineJournalRepository(graph.db, graph.notes, graph.time)
        reminders = JournalReminders(context, notifier, graph.settings, journal, graph.time)
    }

    @After
    fun tearDown() = graph.close()

    private fun enable(on: Boolean) = runBlocking {
        graph.settings.update { it.copy(language = AppLanguage.ENGLISH, journal = it.journal.copy(reminder = on, reminderTime = LocalTime.of(21, 30))) }
    }

    @Test
    fun sync_armsAtTheChosenTime_andOffCancels() = runBlocking {
        enable(true)
        reminders.sync()
        val at = Instant.ofEpochMilli(alarms.scheduledAlarms.single().triggerAtMs).atZone(graph.time.zone()).toLocalDateTime()
        assertThat(at).isEqualTo(graph.time.today().atTime(21, 30))
        enable(false)
        reminders.sync()
        assertThat(alarms.scheduledAlarms).isEmpty()
    }

    @Test
    fun delivery_isSkippedOnADayWithAPage_andRearms() = runBlocking {
        enable(true)
        reminders.deliver()
        assertThat(shadowOf(notifications.getNotification(JournalReminders.REQUEST_CODE)).contentTitle.toString()).isEqualTo("Your journal")
        context.getSystemService(NotificationManager::class.java).cancelAll()
        journal.openPage(graph.time.today(), "Journal", "Today", null, null)
        reminders.deliver()
        assertThat(notifications.getNotification(JournalReminders.REQUEST_CODE)).isNull()
        assertThat(alarms.scheduledAlarms).hasSize(1)
    }

    @Test
    fun requestCode_neverCollidesWithOtherAlarms() {
        val actionNames = listOf(ReminderActionReceiver.ACTION_DONE, ReminderActionReceiver.ACTION_SNOOZE, ReminderActionReceiver.ACTION_DISMISS)
        val itemCodes = (0L..5_000L).flatMap { id ->
            ReminderKind.entries.map { Notifier.notificationId(it, id) } + actionNames.map { Notifier.actionRequestCode(id, it) }
        }.toSet()
        assertThat(itemCodes).doesNotContain(JournalReminders.REQUEST_CODE)
        assertThat(Ritual.entries.map { it.requestCode }).doesNotContain(JournalReminders.REQUEST_CODE)
    }
}
