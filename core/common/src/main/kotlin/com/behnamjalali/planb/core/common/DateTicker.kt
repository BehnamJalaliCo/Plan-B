package com.behnamjalali.planb.core.common

import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** Emits the current local date and again whenever it changes (checked each minute). */
fun TimeProvider.todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(today())
        val now = localNow()
        val untilNextMinute = 60_000L - (now.second * 1000L + now.nano / 1_000_000L)
        delay(untilNextMinute.coerceIn(1_000L, 60_000L))
    }
}.distinctUntilChanged()
