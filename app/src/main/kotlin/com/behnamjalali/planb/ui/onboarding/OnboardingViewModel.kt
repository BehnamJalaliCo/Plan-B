package com.behnamjalali.planb.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.model.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the first-run flow ([OnboardingFlow]). Scoped to the activity, so it outlives the
 * recreation that applies a newly chosen language; the transient step is also kept in
 * [SavedStateHandle] for process death.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val progress = savedState.getStateFlow(KEY_PROGRESS, OnboardingStep.WELCOME)

    /** The step to show; null until the stored settings are read. */
    val step: StateFlow<OnboardingStep?> = combine(settings.settings, progress, OnboardingFlow::resolve)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Stores [language] as the user's choice. The activity applies stored languages to the
     * platform (MainActivity), which recreates it in the new language when it differs.
     */
    fun chooseLanguage(language: AppLanguage) {
        viewModelScope.launch { settings.update { OnboardingFlow.choose(it, language) } }
    }

    /** Welcome → intro → done. */
    fun advance() {
        val current = step.value ?: return
        savedState[KEY_PROGRESS] = OnboardingFlow.next(current)
    }

    /** Skips the rest of the intro. */
    fun skip() {
        if (step.value != OnboardingStep.LANGUAGE) savedState[KEY_PROGRESS] = OnboardingStep.DONE
    }

    private companion object {
        const val KEY_PROGRESS = "onboarding_progress"
    }
}
