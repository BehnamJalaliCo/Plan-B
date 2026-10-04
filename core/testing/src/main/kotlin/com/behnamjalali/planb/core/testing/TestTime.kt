package com.behnamjalali.planb.core.testing

import com.behnamjalali.planb.core.common.TimeProvider
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Controllable clock for deterministic tests. */
class FakeTimeProvider(
    var instant: Instant = Instant.parse("2026-10-04T08:30:00Z"),
    private val zoneId: ZoneId = ZoneId.of("Asia/Tehran"),
) : TimeProvider {
    private var monotonic = 1_000_000L

    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
    override fun monotonicMillis(): Long = monotonic

    fun advance(duration: Duration) {
        instant = instant.plus(duration)
        monotonic += duration.toMillis()
    }

    fun setLocal(date: LocalDate, time: LocalTime = LocalTime.NOON) {
        instant = date.atTime(time).atZone(zoneId).toInstant()
    }
}
