package com.behnamjalali.planb.core.datetime.iran

import java.time.LocalDate
import org.json.JSONObject

/**
 * Hijri month starts taken from a published Iranian calendar, bundled as
 * `iran_calendar/hijri_month_starts.json` (a Java resource of this module). The file names its
 * source and retrieval date. Unknown or malformed entries are skipped; a missing or broken file
 * means "no overrides", so the computed calendar is used.
 */
object PublishedHijriMonths {
    const val RESOURCE = "/iran_calendar/hijri_month_starts.json"

    fun parse(json: String): Map<HijriMonth, LocalDate> = runCatching {
        val starts = JSONObject(json).optJSONObject("monthStarts") ?: return emptyMap()
        buildMap {
            starts.keys().forEach { key ->
                val parts = key.split('-')
                val year = parts.getOrNull(0)?.toIntOrNull()
                val month = parts.getOrNull(1)?.toIntOrNull()
                val date = runCatching { LocalDate.parse(starts.getString(key)) }.getOrNull()
                if (year != null && month != null && month in 1..12 && date != null) put(HijriMonth(year, month), date)
            }
        }
    }.getOrDefault(emptyMap())

    fun bundled(): Map<HijriMonth, LocalDate> = runCatching {
        PublishedHijriMonths::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().decodeToString() }
    }.getOrNull()?.let(::parse).orEmpty()
}
