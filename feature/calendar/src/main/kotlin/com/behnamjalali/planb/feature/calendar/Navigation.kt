package com.behnamjalali.planb.feature.calendar

import kotlinx.serialization.Serializable

@Serializable
data object CalendarRoute

@Serializable
data class EventEditorRoute(val eventId: Long = 0, val dateEpochDay: Long? = null)
