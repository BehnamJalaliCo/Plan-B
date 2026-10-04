package com.behnamjalali.planb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MainUiState {
    data object Loading : MainUiState
    data class Ready(val settings: UserSettings) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    time: TimeProvider,
) : ViewModel() {
    /** Current local date; updates at midnight so "Today" never goes stale. */
    val today: StateFlow<LocalDate> = time.todayFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, time.today())

    val uiState: StateFlow<MainUiState> = settings.settings
        .map<UserSettings, MainUiState> { MainUiState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState.Loading)

    /**
     * The stored language, to be applied to the app whenever it changes (settings, restore).
     * First, a language chosen in the system's per-app settings ([platformLanguage], API 33+)
     * is stored. Null means the platform has no choice ("System default", or not loaded yet
     * before API 33): the stored language is kept, never overwritten.
     */
    fun appLanguage(platformLanguage: AppLanguage?): Flow<AppLanguage> = flow {
        if (platformLanguage != null) {
            settings.update { if (it.language != platformLanguage) it.copy(language = platformLanguage) else it }
        }
        emitAll(settings.settings.map { it.language }.distinctUntilChanged())
    }

    /** The theme setting, for the window and system bars. */
    val themeMode: Flow<ThemeMode> = settings.settings.map { it.themeMode }.distinctUntilChanged()

    fun completeOnboarding() {
        viewModelScope.launch { settings.update { it.copy(onboardingCompleted = true) } }
    }
}
