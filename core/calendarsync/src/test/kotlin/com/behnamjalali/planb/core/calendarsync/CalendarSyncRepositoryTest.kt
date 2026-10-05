package com.behnamjalali.planb.core.calendarsync

import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Reading device events into the calendar views, importing them, and the settings actions. */
@RunWith(RobolectricTestRunner::class)
class CalendarSyncRepositoryTest {
    private lateinit var graph: TestDataGraph
    private lateinit var store: FakeDeviceCalendarStore
    private lateinit var repository: CalendarSyncRepository
    private val zone: ZoneId = ZoneId.of("Asia/Tehran")
    private val work = DeviceCalendar(1, "Work", "me@gmail.com", "com.google", 0xFF3366CC.toInt(), writable = true)
    private val family = DeviceCalendar(2, "Family", "me@gmail.com", "com.google", 0xFFE67C73.toInt(), writable = true)

    @Before
    fun setUp() {
        graph = TestDataGraph()
        graph.pro = true
        store = FakeDeviceCalendarStore(listOf(work, family))
        val engine = CalendarSyncEngine(store, graph.events, graph.db, graph.preferences, { graph.pro }, graph.time, PACKAGE)
        repository = CalendarSyncRepository(store, engine, graph.preferences, graph.db, graph.events, graph.time, PACKAGE)
    }

    @After
    fun tearDown() = graph.close()

    private fun millis(hour: Int, minute: Int = 0, days: Long = 0): Long =
        graph.time.today().plusDays(days).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun enable_showsEveryCalendar_andWritesNothingWithoutATarget() = runBlocking<Unit> {
        graph.events.save(CalendarEvent(title = "Mine", date = graph.time.today()))
        repository.enable()
        val settings = graph.preferences.calendarSync.first()
        assertThat(settings.enabled).isTrue()
        assertThat(settings.visibleCalendarIds).containsExactly(1L, 2L)
        assertThat(settings.targetCalendarId).isNull()
        assertThat(store.writes).isEmpty()
    }

    @Test
    fun deviceEvents_areShownPerDay_withoutPlanBMirrors() = runBlocking<Unit> {
        repository.enable()
        repository.setVisible(2, false)
        store.addForeign(1, DeviceEventData("Standup", "", millis(9), millis(9, 15), false, zone.id))
        store.addForeign(2, DeviceEventData("Grandma", "", millis(18), millis(20), false, zone.id))
        // A two-day all-day event.
        val day = graph.time.today().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        store.addForeign(1, DeviceEventData("Conference", "", day, day + Duration.ofDays(2).toMillis(), true, "UTC"))
        // Plan-B's own mirror is already a Plan-B event.
        graph.events.save(CalendarEvent(title = "Plan-B event", date = graph.time.today(), startTime = LocalTime.of(12, 0), endTime = LocalTime.of(13, 0), allDay = false))
        repository.setTarget(1)

        val today = graph.time.today()
        val items = withTimeout(10_000) { repository.observeItems(today, today.plusDays(3)).first { it.size >= 3 } }
        assertThat(items.map { it.title to it.date }).containsExactly(
            "Standup" to today,
            "Conference" to today,
            "Conference" to today.plusDays(1),
        )
        val standup = items.first { it.title == "Standup" }
        assertThat(standup.startTime).isEqualTo(LocalTime.of(9, 0))
        assertThat(standup.endTime).isEqualTo(LocalTime.of(9, 15))
        assertThat(standup.calendarName).isEqualTo("Work")
    }

    @Test
    fun noPermission_orSyncOff_showsNothing() = runBlocking<Unit> {
        store.addForeign(1, DeviceEventData("Standup", "", millis(9), millis(10), false, zone.id))
        val today = graph.time.today()
        assertThat(repository.observeItems(today, today).first()).isEmpty()
        repository.enable()
        store.permission = false
        assertThat(repository.observeItems(today, today).first()).isEmpty()
    }

    @Test
    fun import_copiesTheEvent_once_andNeverWritesItBack() = runBlocking<Unit> {
        repository.enable()
        repository.setTarget(1)
        val foreign = store.addForeign(1, DeviceEventData("Doctor", "Bring the card", millis(15, 30), millis(16, 30), false, zone.id))
        val today = graph.time.today()
        val item = withTimeout(10_000) { repository.observeItems(today, today).first { it.isNotEmpty() } }.single()

        val id = repository.import(item)!!
        assertThat(repository.import(item)).isEqualTo(id)
        val copy = graph.events.getEvent(id)!!
        assertThat(copy.title).isEqualTo("Doctor")
        assertThat(copy.description).isEqualTo("Bring the card")
        assertThat(copy.startTime).isEqualTo(LocalTime.of(15, 30))
        assertThat(copy.endTime).isEqualTo(LocalTime.of(16, 30))

        // The original is hidden (the copy shows instead), and syncing never touches it.
        assertThat(withTimeout(10_000) { repository.observeItems(today, today).first { it.isEmpty() } }).isEmpty()
        repository.syncNow()
        graph.events.save(copy.copy(title = "Doctor (moved)"))
        repository.syncNow()
        assertThat(store.events.getValue(foreign).data.title).isEqualTo("Doctor")
        assertThat(store.writes).isEmpty()
    }

    @Test
    fun createPlanBCalendar_makesItTheTarget() = runBlocking<Unit> {
        repository.enable()
        val id = repository.createPlanBCalendar("Plan-B", 0xFF6B5BD2.toInt())!!
        assertThat(store.calendars.single { it.id == id }.ownedByPlanB).isTrue()
        assertThat(graph.preferences.calendarSync.first().targetCalendarId).isEqualTo(id)
        // Asking again reuses it.
        assertThat(repository.createPlanBCalendar("Plan-B", 0)).isEqualTo(id)
    }

    @Test
    fun syncSettings_areDeviceOnly_andNotExported() = runBlocking<Unit> {
        repository.enable()
        repository.setTarget(1)
        val exported = graph.preferences.export()
        assertThat(exported.keys.filter { it.startsWith("calendar_sync") }).isEmpty()
        // A restore does not change them either.
        graph.preferences.import(exported + mapOf("calendar_sync_target_id" to "99"))
        assertThat(graph.preferences.calendarSync.first().targetCalendarId).isEqualTo(1)
    }

    private companion object {
        const val PACKAGE = "com.behnamjalali.planb"
    }
}
