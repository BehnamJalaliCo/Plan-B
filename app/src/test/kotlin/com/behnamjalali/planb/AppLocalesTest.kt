package com.behnamjalali.planb

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.testing.TestDataGraph
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AppLocalesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(LocaleManager::class.java)

    @Test
    fun freshInstall_defaultsToPersian() {
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        AppLocales.applyStartupLanguage(context, UserSettings().language)
        assertThat(AppLocales.current(context)).isEqualTo("fa")
    }

    @Test
    fun userChoice_isNotOverridden() {
        // Chosen in the system's per-app language settings; the stored preference follows later.
        manager.applicationLocales = LocaleList.forLanguageTags("en")
        AppLocales.applyStartupLanguage(context, AppLanguage.PERSIAN)
        assertThat(AppLocales.current(context)).isEqualTo("en")
    }

    @Test
    fun systemDefault_keepsTheStoredLanguage() {
        // "System default" in the system settings clears the per-app list.
        manager.applicationLocales = LocaleList.getEmptyLocaleList()
        assertThat(AppLocales.platformLanguage(context)).isNull()
        AppLocales.applyStartupLanguage(context, AppLanguage.ENGLISH)
        assertThat(AppLocales.current(context)).isEqualTo("en")
    }

    @Test
    fun apply_switchesOnlyWhenDifferent() {
        manager.applicationLocales = LocaleList.forLanguageTags("fa")
        AppLocales.apply(context, AppLanguage.ENGLISH)
        assertThat(AppLocales.platformLanguage(context)).isEqualTo(AppLanguage.ENGLISH)
        AppLocales.apply(context, AppLanguage.ENGLISH)
        assertThat(AppLocales.current(context)).isEqualTo("en")
    }

    @Test
    @Config(sdk = [30])
    fun beforeApi33_coldStart_keepsStoredEnglish() {
        // Application.onCreate before API 33: AppCompat has not loaded its stored list yet.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        AppLocales.applyStartupLanguage(context, AppLanguage.ENGLISH)
        assertThat(AppLocales.current(context)).isEqualTo("en")
        assertThat(AppLocales.platformLanguage(context)).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    @Config(sdk = [30])
    fun beforeApi33_freshInstall_defaultsToPersian() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        AppLocales.applyStartupLanguage(context, UserSettings().language)
        assertThat(AppLocales.current(context)).isEqualTo("fa")
    }

    @Test
    @Config(sdk = [30])
    fun beforeApi33_emptyList_neverOverwritesTheStoredLanguage() = runBlocking<Unit> {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        val graph = TestDataGraph()
        try {
            graph.settings.update { it.copy(language = AppLanguage.ENGLISH) }
            val viewModel = MainViewModel(graph.settings, graph.time)
            // An empty platform list is "no choice": the stored English stays.
            assertThat(viewModel.appLanguage(AppLocales.platformLanguage(context)).first()).isEqualTo(AppLanguage.ENGLISH)
            assertThat(graph.settings.current().language).isEqualTo(AppLanguage.ENGLISH)
            // A real platform choice is stored.
            assertThat(viewModel.appLanguage(AppLanguage.PERSIAN).first()).isEqualTo(AppLanguage.PERSIAN)
            assertThat(graph.settings.current().language).isEqualTo(AppLanguage.PERSIAN)
        } finally {
            graph.close()
        }
    }
}
