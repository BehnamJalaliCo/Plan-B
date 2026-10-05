package com.behnamjalali.planb.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.calendarsync.CalendarSyncRepository
import com.behnamjalali.planb.core.calendarsync.DeviceCalendar
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.CalendarDecorations
import com.behnamjalali.planb.core.model.CalendarSyncSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Settings › Holidays and device calendars (Plan-B Pro #2 and #3). */
@Serializable data object CalendarSettingsRoute

data class CalendarSettingsUiState(
    val loading: Boolean = true,
    val decorations: CalendarDecorations = CalendarDecorations(),
    val sync: CalendarSyncSettings = CalendarSyncSettings(),
    val permission: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    val syncing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sync: CalendarSyncRepository,
) : ViewModel() {
    /** Bumped after the permission dialog so the screen re-reads the permission. */
    private val permissionCheck = MutableStateFlow(0)
    private val syncing = MutableStateFlow(false)

    val state: StateFlow<CalendarSettingsUiState> = combine(
        settings.settings.map { it.calendarDecorations },
        sync.settings,
        permissionCheck.map { sync.hasPermission() },
        syncing,
    ) { decorations, syncSettings, permission, busy -> CalendarSettingsUiState(false, decorations, syncSettings, permission, syncing = busy) }
        .flatMapLatest { base ->
            if (base.sync.enabled && base.permission) sync.observeCalendars().map { base.copy(calendars = it) } else flowOf(base)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarSettingsUiState())

    fun setDecorations(transform: (CalendarDecorations) -> CalendarDecorations) {
        viewModelScope.launch { settings.update { it.copy(calendarDecorations = transform(it.calendarDecorations)) } }
    }

    /** Called after the permission dialog; turns sync on when access was granted. */
    fun onPermissionResult(granted: Boolean) {
        permissionCheck.update { it + 1 }
        if (granted) work { sync.enable() }
    }

    fun disable() = work { sync.disable() }

    fun setVisible(calendarId: Long, visible: Boolean) = work { sync.setVisible(calendarId, visible) }

    fun setTarget(calendarId: Long?) = work { sync.setTarget(calendarId) }

    fun createLocalCalendar(name: String, color: Int) = work { sync.createPlanBCalendar(name, color) }

    fun syncNow() = work { sync.syncNow() }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            syncing.value = true
            try {
                runCatchingSafely { block() }
            } finally {
                syncing.value = false
            }
        }
    }
}
