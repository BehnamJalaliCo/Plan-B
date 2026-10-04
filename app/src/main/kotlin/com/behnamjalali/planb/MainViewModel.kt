package com.behnamjalali.planb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.common.todayFlow
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    /** Keeps the stored language in sync when it was changed from system settings. */
    fun syncLanguage(appLanguage: AppLanguage) {
        viewModelScope.launch {
            settings.update { if (it.language != appLanguage) it.copy(language = appLanguage) else it }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settings.update { it.copy(onboardingCompleted = true) } }
    }
}
