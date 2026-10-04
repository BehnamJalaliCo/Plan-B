package com.behnamjalali.planb.core.database

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Canonical storage formats (never tied to a display calendar):
 * LocalDate → epoch day, LocalTime → second of day, Instant → epoch millis (UTC).
 */
internal class Converters {
    @TypeConverter fun localDateToLong(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun longToLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun localTimeToInt(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun intToLocalTime(value: Int?): LocalTime? = value?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
