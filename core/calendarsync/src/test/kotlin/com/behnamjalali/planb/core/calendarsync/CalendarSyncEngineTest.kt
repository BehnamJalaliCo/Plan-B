package com.behnamjalali.planb.core.calendarsync

import com.behnamjalali.planb.core.database.entity.CalendarLinkEntity
import com.behnamjalali.planb.core.model.AccentColor
import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Two-way sync (Plan-B Pro #3) against an in-memory calendar provider. */
@RunWith(RobolectricTestRunner::class)
class CalendarSyncEngineTest {
    private lateinit var graph: TestDataGraph
    private lateinit var store: FakeDeviceCalendarStore
    private lateinit var engine: CalendarSyncEngine
    private val zone: ZoneId = ZoneId.of("Asia/Tehran")
    private val google = DeviceCalendar(1, "Personal", "me@gmail.com", "com.google", 0xFF3366CC.toInt(), writable = true)
    private val holidays = DeviceCalendar(2, "Holidays", "me@gmail.com", "com.google", 0xFF0B8043.toInt(), writable = false)

    @Before
    fun setUp() {
        graph = TestDataGraph()
        graph.pro = true
        store = FakeDeviceCalendarStore(listOf(google, holidays))
        engine = CalendarSyncEngine(store, graph.events, graph.db, graph.preferences, { graph.pro }, graph.time, PACKAGE)
        runBlocking { graph.preferences.updateCalendarSync { it.copy(enabled = true, visibleCalendarIds = setOf(1, 2), targetCalendarId = 1) } }
    }

    @After
    fun tearDown() = graph.close()

    private val today: LocalDate get() = graph.time.today()

    private suspend fun links() = graph.db.calendarLinkDao().observeAll().first()

    private suspend fun meeting(): Long = graph.events.save(
        CalendarEvent(title = "Design review", date = today, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 30), allDay = false, color = AccentColor.MINT),
    )

    @Test
    fun newPlanBEvents_areWrittenToTheTarget_andLinked() = runBlocking<Unit> {
        val id = meeting()
        val report = engine.sync()

        assertThat(report.outcome).isEqualTo(SyncReport.Outcome.SYNCED)
        assertThat(report.pushedNew).isEqualTo(1)
        val link = links().single()
        assertThat(link.localId).isEqualTo(id)
        assertThat(link.calendarId).isEqualTo(1)
        val remote = store.events.getValue(link.externalEventId)
        assertThat(remote.ownerPackage).isEqualTo(PACKAGE)
        assertThat(remote.data.title).isEqualTo("Design review")
        assertThat(remote.data.startMillis).isEqualTo(today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())
        assertThat(remote.data.endMillis).isEqualTo(today.atTime(10, 30).atZone(zone).toInstant().toEpochMilli())

        // Nothing changed: the second sync writes nothing.
        val writes = store.writes.size
        assertThat(engine.sync().pushedNew).isEqualTo(0)
        assertThat(store.writes).hasSize(writes)
    }

    @Test
    fun allDayEvents_areUtcMidnights() = runBlocking<Unit> {
        graph.events.save(CalendarEvent(title = "Trip", date = today))
        engine.sync()
        val remote = store.events.values.single()
        assertThat(remote.data.allDay).isTrue()
        assertThat(remote.data.timeZone).isEqualTo("UTC")
        assertThat(remote.data.startMillis).isEqualTo(today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        assertThat(remote.data.endMillis - remote.data.startMillis).isEqualTo(Duration.ofDays(1).toMillis())
    }

    @Test
    fun localEditsAndDeletes_followTheLink() = runBlocking<Unit> {
        val id = meeting()
        engine.sync()
        val remoteId = links().single().externalEventId

        graph.time.advance(Duration.ofMinutes(5))
        graph.events.save(graph.events.getEvent(id)!!.copy(title = "Design review (moved)", startTime = LocalTime.of(11, 0), endTime = LocalTime.of(12, 0)))
        assertThat(engine.sync().pushedChanges).isEqualTo(1)
        assertThat(store.events.getValue(remoteId).data.title).isEqualTo("Design review (moved)")
        assertThat(store.events.getValue(remoteId).data.startMillis).isEqualTo(today.atTime(11, 0).atZone(zone).toInstant().toEpochMilli())

        graph.events.delete(id)
        assertThat(engine.sync().deletedRemote).isEqualTo(1)
        assertThat(store.events).doesNotContainKey(remoteId)
        assertThat(links()).isEmpty()
    }

    @Test
    fun deviceEdits_comeBackIntoPlanB() = runBlocking<Unit> {
        val id = meeting()
        engine.sync()
        val remoteId = links().single().externalEventId

        val start = today.plusDays(1).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        store.editOnDevice(remoteId) { it.copy(title = "Design review with Sara", startMillis = start, endMillis = start + Duration.ofMinutes(45).toMillis()) }
        val report = engine.sync()

        assertThat(report.pulledChanges).isEqualTo(1)
        val local = graph.events.getEvent(id)!!
        assertThat(local.title).isEqualTo("Design review with Sara")
        assertThat(local.date).isEqualTo(today.plusDays(1))
        assertThat(local.startTime).isEqualTo(LocalTime.of(14, 0))
        assertThat(local.endTime).isEqualTo(LocalTime.of(14, 45))
        // Plan-B's own fields stay.
        assertThat(local.color).isEqualTo(AccentColor.MINT)
        // The pulled change is not echoed back.
        val writes = store.writes.size
        engine.sync()
        assertThat(store.writes).hasSize(writes)
    }

    @Test
    fun deletedOnTheDevice_deletesThePlanBEvent_unlessEditedSince() = runBlocking<Unit> {
        val kept = meeting()
        val removed = graph.events.save(CalendarEvent(title = "Dentist", date = today, startTime = LocalTime.of(16, 0), endTime = LocalTime.of(17, 0), allDay = false))
        engine.sync()
        val byLocal = links().associateBy { it.localId }

        store.deleteOnDevice(byLocal.getValue(removed).externalEventId)
        store.deleteOnDevice(byLocal.getValue(kept).externalEventId)
        graph.time.advance(Duration.ofMinutes(1))
        graph.events.save(graph.events.getEvent(kept)!!.copy(notes = "Bring the sketches"))

        val report = engine.sync()
        assertThat(report.deletedLocal).isEqualTo(1)
        assertThat(graph.events.getEvent(removed)).isNull()
        // Edited in Plan-B after the device deletion: written again instead of lost.
        assertThat(graph.events.getEvent(kept)).isNotNull()
        assertThat(links().map { it.localId }).containsExactly(kept)
        assertThat(store.events.values.map { it.data.title }).containsExactly("Design review")
    }

    @Test
    fun conflict_lastWriterWins() = runBlocking<Unit> {
        val id = meeting()
        engine.sync()
        val remoteId = links().single().externalEventId

        // Plan-B edit at t+1 min, device edit noticed at t+2 min: the device wins.
        graph.time.advance(Duration.ofMinutes(1))
        graph.events.save(graph.events.getEvent(id)!!.copy(title = "Edited in Plan-B"))
        store.editOnDevice(remoteId) { it.copy(title = "Edited on the phone") }
        val noticed = graph.time.now().plus(Duration.ofMinutes(1))
        engine.sync(remoteNoticedAt = noticed)
        assertThat(graph.events.getEvent(id)!!.title).isEqualTo("Edited on the phone")
        assertThat(store.events.getValue(remoteId).data.title).isEqualTo("Edited on the phone")

        // Device edit noticed at t+3 min, Plan-B edit at t+5 min: Plan-B wins.
        graph.time.advance(Duration.ofMinutes(2))
        store.editOnDevice(remoteId) { it.copy(title = "Phone again") }
        val noticedEarlier = graph.time.now()
        graph.time.advance(Duration.ofMinutes(2))
        graph.events.save(graph.events.getEvent(id)!!.copy(title = "Plan-B again"))
        engine.sync(remoteNoticedAt = noticedEarlier)
        assertThat(graph.events.getEvent(id)!!.title).isEqualTo("Plan-B again")
        assertThat(store.events.getValue(remoteId).data.title).isEqualTo("Plan-B again")
    }

    @Test
    fun linksToMissingCalendars_areDropped_andTheEventIsWrittenToTheTarget() = runBlocking<Unit> {
        val id = meeting()
        // A link restored from a backup made on another phone: calendar 77 does not exist here.
        graph.db.calendarLinkDao().upsert(CalendarLinkEntity(localType = "EVENT", localId = id, calendarId = 77, externalEventId = 5, lastSyncedAt = graph.time.now(), localVersion = 0, remoteVersion = "x"))

        val report = engine.sync()
        assertThat(report.droppedLinks).isEqualTo(1)
        assertThat(report.pushedNew).isEqualTo(1)
        assertThat(links().single().calendarId).isEqualTo(1)
    }

    @Test
    fun calendarDeletedOnTheDevice_isHandledGracefully() = runBlocking<Unit> {
        meeting()
        engine.sync()
        store.removeCalendar(1)
        graph.preferences.updateCalendarSync { it.copy(visibleCalendarIds = setOf(2)) }

        val report = engine.sync()
        assertThat(report.outcome).isEqualTo(SyncReport.Outcome.SYNCED)
        assertThat(report.droppedLinks).isEqualTo(1)
        assertThat(links()).isEmpty()
        // The target is gone: nothing is written anywhere else.
        assertThat(store.events).isEmpty()
    }

    @Test
    fun eventsPlanBDidNotCreate_areNeverTouched() = runBlocking<Unit> {
        val foreign = store.addForeign(1, DeviceEventData("Team offsite", "", 0, 3_600_000, false, "Asia/Tehran"))
        val id = meeting()
        // A stale link (for example from a backup) that points at someone else's event.
        graph.db.calendarLinkDao().upsert(CalendarLinkEntity(localType = "EVENT", localId = id, calendarId = 1, externalEventId = foreign, lastSyncedAt = graph.time.now(), localVersion = 0, remoteVersion = "x"))
        graph.events.delete(id)

        engine.sync()
        assertThat(store.events.getValue(foreign).data.title).isEqualTo("Team offsite")
        assertThat(store.writes.filter { it.endsWith(":$foreign") }).isEmpty()
        assertThat(links()).isEmpty()
    }

    @Test
    fun offWithoutProOrWithoutPermission_nothingIsWritten() = runBlocking<Unit> {
        meeting()
        graph.preferences.updateCalendarSync { it.copy(enabled = false) }
        assertThat(engine.sync().outcome).isEqualTo(SyncReport.Outcome.DISABLED)
        graph.preferences.updateCalendarSync { it.copy(enabled = true) }
        graph.pro = false
        assertThat(engine.sync().outcome).isEqualTo(SyncReport.Outcome.NOT_PRO)
        graph.pro = true
        store.permission = false
        assertThat(engine.sync().outcome).isEqualTo(SyncReport.Outcome.NO_PERMISSION)
        assertThat(graph.preferences.calendarSync.first().lastSyncFailed).isTrue()
        assertThat(store.writes).isEmpty()
    }

    @Test
    fun noTarget_meansReadOnly() = runBlocking<Unit> {
        graph.preferences.updateCalendarSync { it.copy(targetCalendarId = null) }
        meeting()
        engine.sync()
        assertThat(store.writes).isEmpty()
        // A read-only calendar is never a target either.
        graph.preferences.updateCalendarSync { it.copy(targetCalendarId = 2) }
        engine.sync()
        assertThat(store.writes).isEmpty()
    }

    @Test
    fun recurrences_areMirroredOnlyWhenAStandardRuleExists() = runBlocking<Unit> {
        graph.events.save(CalendarEvent(title = "Yoga", date = today, startTime = LocalTime.of(18, 0), endTime = LocalTime.of(19, 0), allDay = false, recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY)))
        graph.events.save(CalendarEvent(title = "Rent", date = today, recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY, calendarSystem = CalendarSystem.JALALI)))
        engine.sync()
        val remote = store.events.values.single()
        assertThat(remote.data.title).isEqualTo("Yoga")
        assertThat(remote.data.rrule).isEqualTo("FREQ=WEEKLY")
    }

    private companion object {
        const val PACKAGE = "com.behnamjalali.planb"
    }
}
