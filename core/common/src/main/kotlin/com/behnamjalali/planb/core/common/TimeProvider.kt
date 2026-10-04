package com.behnamjalali.planb.core.common

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Injectable clock so time-dependent logic is deterministic in tests. */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
    // LocalDate.ofInstant needs API 34; atZone() works on every supported API level.
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
    fun localNow(): LocalDateTime = now().atZone(zone()).toLocalDateTime()

    /** Monotonic milliseconds (unaffected by wall-clock changes) for in-process timing. */
    fun monotonicMillis(): Long
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Clock.systemUTC().instant()
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun monotonicMillis(): Long = System.nanoTime() / 1_000_000
}
