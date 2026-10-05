package com.behnamjalali.planb.ui.onboarding

import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.UserSettings

/** The first-run screens, in order. */
enum class OnboardingStep { LANGUAGE, WELCOME, INTRO, DONE }

/**
 * The first-run flow as a pure state machine: language → welcome → intro slides → done.
 *
 * The language step comes from stored settings ([UserSettings.languageChosen]), so it is
 * never shown again once answered: not after the activity is recreated for the new language,
 * not after a restart. Welcome and intro are transient progress ([OnboardingViewModel] keeps
 * it across process death). Onboarding is marked completed only when the flow reaches
 * [OnboardingStep.DONE]; installs that completed it never see any of it.
 */
object OnboardingFlow {
    /** The step to show for the stored [settings] and the transient [progress]. */
    fun resolve(settings: UserSettings, progress: OnboardingStep): OnboardingStep = when {
        settings.onboardingCompleted -> OnboardingStep.DONE
        !settings.languageChosen -> OnboardingStep.LANGUAGE
        progress == OnboardingStep.LANGUAGE -> OnboardingStep.WELCOME
        else -> progress
    }

    /** The step after [step]; the language step moves on only by choosing a language. */
    fun next(step: OnboardingStep): OnboardingStep = when (step) {
        OnboardingStep.LANGUAGE -> OnboardingStep.LANGUAGE
        OnboardingStep.WELCOME -> OnboardingStep.INTRO
        OnboardingStep.INTRO, OnboardingStep.DONE -> OnboardingStep.DONE
    }

    /**
     * The language card to suggest for the device's own language ([deviceLanguage], an ISO
     * 639 code such as "fa" or "en"): Persian on Persian devices, English on any other known
     * language, and the app default (Persian) when the device does not say.
     */
    fun suggestedLanguage(deviceLanguage: String?): AppLanguage {
        val code = deviceLanguage?.trim()?.lowercase().orEmpty()
        return when {
            code.isEmpty() || code == "und" -> UserSettings().language
            code == "fa" || code == "fas" || code == "per" -> AppLanguage.PERSIAN
            else -> AppLanguage.ENGLISH
        }
    }

    /** Settings after choosing [language]: calendar and digits follow it through their "auto" defaults. */
    fun choose(settings: UserSettings, language: AppLanguage): UserSettings =
        settings.copy(language = language, languageChosen = true)
}
