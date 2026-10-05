package com.behnamjalali.planb.core.calendarsync

import com.behnamjalali.planb.core.model.CalendarEvent
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceBasis
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test

class EventMappingTest {
    private val zone = ZoneId.of("Asia/Tehran")
    private val date = LocalDate.of(2026, 10, 4)

    @Test
    fun timedEvent_roundTrips() {
        val event = CalendarEvent(id = 3, title = "جلسه", description = "notes", date = date, startTime = LocalTime.of(9, 30), endTime = LocalTime.of(11, 0), allDay = false)
        val device = EventMapping.toDevice(event, zone)
        assertThat(device.timeZone).isEqualTo("Asia/Tehran")
        assertThat(device.endMillis - device.startMillis).isEqualTo(90 * 60_000L)
        assertThat(EventMapping.applyRemote(event.copy(title = "x"), device, zone)).isEqualTo(event)
    }

    @Test
    fun timedEventWithoutEnd_isOneHourOnTheDevice() {
        val event = CalendarEvent(title = "Call", date = date, startTime = LocalTime.of(23, 30), allDay = false)
        val device = EventMapping.toDevice(event, zone)
        assertThat(device.endMillis - device.startMillis).isEqualTo(60 * 60_000L)
        // Ending after midnight on the device: Plan-B keeps the event on its day, ending 23:59.
        val back = EventMapping.applyRemote(event, device, zone)
        assertThat(back.date).isEqualTo(date)
        assertThat(back.endTime).isEqualTo(LocalTime.of(23, 59))
    }

    @Test
    fun allDayEvent_roundTrips() {
        val event = CalendarEvent(title = "Nowruz trip", date = date)
        val device = EventMapping.toDevice(event, zone)
        assertThat(device.allDay).isTrue()
        assertThat(EventMapping.applyRemote(event, device, zone)).isEqualTo(event)
    }

    @Test
    fun rrules() {
        assertThat(EventMapping.rrule(RecurrenceRule(RecurrenceFrequency.DAILY, interval = 2, count = 5))).isEqualTo("FREQ=DAILY;INTERVAL=2;COUNT=5")
        assertThat(EventMapping.rrule(RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY), until = LocalDate.of(2027, 3, 20))))
            .isEqualTo("FREQ=WEEKLY;BYDAY=MO,SA;UNTIL=20270320")
        assertThat(EventMapping.rrule(RecurrenceRule(RecurrenceFrequency.MONTHLY, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 2)))
            .isEqualTo("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=2")
        // No standard equivalent: Jalali months and years, "after completion".
        assertThat(EventMapping.rrule(RecurrenceRule(RecurrenceFrequency.MONTHLY, calendarSystem = CalendarSystem.JALALI))).isNull()
        assertThat(EventMapping.rrule(RecurrenceRule(RecurrenceFrequency.YEARLY, calendarSystem = CalendarSystem.JALALI))).isNull()
        assertThat(EventMapping.rrule(RecurrenceRule(RecurrenceFrequency.DAILY, basis = RecurrenceBasis.COMPLETION))).isNull()
        // Days and weeks are the same in both calendars.
        assertThat(EventMapping.rrule(RecurrenceRule(RecurrenceFrequency.WEEKLY, calendarSystem = CalendarSystem.JALALI))).isEqualTo("FREQ=WEEKLY")
    }

    @Test
    fun fingerprint_changesWithWhatPlanBSyncs() {
        val a = DeviceEventData("A", "", 0, 1000, false, "UTC")
        assertThat(EventMapping.fingerprint(a)).isEqualTo(EventMapping.fingerprint(a.copy()))
        assertThat(EventMapping.fingerprint(a)).isNotEqualTo(EventMapping.fingerprint(a.copy(title = "B")))
        assertThat(EventMapping.fingerprint(a)).isNotEqualTo(EventMapping.fingerprint(a.copy(endMillis = 2000)))
        // The zone is the provider's business (it may normalize it).
        assertThat(EventMapping.fingerprint(a)).isEqualTo(EventMapping.fingerprint(a.copy(timeZone = "Asia/Tehran")))
    }

    @Test
    fun providerDurations() {
        assertThat(ContentResolverCalendarStore.parseDuration("P3600S")).isEqualTo(3_600_000L)
        assertThat(ContentResolverCalendarStore.parseDuration("PT1H30M")).isEqualTo(5_400_000L)
        assertThat(ContentResolverCalendarStore.parseDuration("P1D")).isEqualTo(86_400_000L)
        assertThat(ContentResolverCalendarStore.parseDuration("P2W")).isEqualTo(14 * 86_400_000L)
        assertThat(ContentResolverCalendarStore.parseDuration("garbage")).isEqualTo(0L)
        assertThat(ContentResolverCalendarStore.formatDuration(5_400_000L, allDay = false)).isEqualTo("P5400S")
        assertThat(ContentResolverCalendarStore.formatDuration(86_400_000L, allDay = true)).isEqualTo("P1D")
    }
}
