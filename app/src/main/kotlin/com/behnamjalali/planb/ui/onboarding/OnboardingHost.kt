package com.behnamjalali.planb.ui.onboarding

import android.content.Context
import android.content.res.Resources
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.os.ConfigurationCompat
import androidx.core.app.LocaleManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.UserSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * First run: language → welcome → intro slides, then [onFinished] (which marks onboarding
 * completed). Every step sits on the plain window background, so the activity recreation
 * that applies a newly chosen language shows nothing but that background: the picker fades
 * out before the choice is stored, and the welcome starts only once the resources are in the
 * chosen language.
 */
@Composable
fun OnboardingHost(settings: UserSettings, onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val motion = PlanBTheme.motion
    val finish by rememberUpdatedState(onFinished)
    Surface(
        // Test tags double as resource ids so UiAutomator (benchmarks, baseline profiles) can find them.
        Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
        color = MaterialTheme.colorScheme.background,
    ) {
        val current = step ?: return@Surface
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val enter = if (motion.enabled) fadeIn(tween(ENTER_MS, delayMillis = 90)) + scaleIn(tween(ENTER_MS + 120, delayMillis = 90), initialScale = 0.97f) else fadeIn(tween(FADE_MS))
                enter togetherWith fadeOut(tween(if (motion.enabled) EXIT_MS else FADE_MS))
            },
            label = "onboarding",
        ) { target ->
            when (target) {
                OnboardingStep.LANGUAGE -> {
                    val context = LocalContext.current
                    val suggested = remember(context) { OnboardingFlow.suggestedLanguage(deviceLanguage(context)) }
                    LanguagePickerScreen(suggested, onChosen = viewModel::chooseLanguage)
                }
                OnboardingStep.WELCOME -> WhenResourcesIn(settings.language) { WelcomeScreen(onStart = viewModel::advance) }
                OnboardingStep.INTRO -> IntroScreen(onDone = viewModel::advance)
                OnboardingStep.DONE -> {
                    Box(Modifier.fillMaxSize())
                    // Hand over once the intro has faded out.
                    LaunchedEffect(Unit) {
                        snapshotFlow { transition.currentState == transition.targetState }.first { it }
                        finish()
                    }
                }
            }
        }
    }
}

/**
 * Shows [content] once the activity's resources are in [language]. Right after a language
 * is chosen the old resources are still active until the activity is recreated; showing
 * nothing meanwhile keeps the welcome from flashing in the previous language. If the switch
 * never arrives (e.g. a system-level override), the content appears anyway after a moment.
 */
@Composable
private fun WhenResourcesIn(language: AppLanguage, content: @Composable () -> Unit) {
    val active = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language
    val ready = active == language.tag
    var waitedOut by remember { mutableStateOf(false) }
    LaunchedEffect(ready) {
        if (!ready) {
            delay(LOCALE_SWITCH_TIMEOUT_MS)
            waitedOut = true
        }
    }
    if (ready || waitedOut) content()
}

/** The device's own language (not the app's), for the language suggestion. */
private fun deviceLanguage(context: Context): String? =
    runCatching { LocaleManagerCompat.getSystemLocales(context)[0]?.language }.getOrNull()
        ?: Resources.getSystem().configuration.locales[0]?.language

private const val ENTER_MS = 420
private const val EXIT_MS = 260
private const val FADE_MS = 220
private const val LOCALE_SWITCH_TIMEOUT_MS = 1_500L
