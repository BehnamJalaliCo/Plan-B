package com.behnamjalali.planb.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.DashboardSection
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.UserSettings
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UserPreferencesDataSourceTest {
    private fun newStore(dir: File, scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "test.preferences_pb") }

    @Test
    fun defaults_whenEmpty() = runTest {
        val dir = Files.createTempDirectory("prefs").toFile()
        val source = UserPreferencesDataSource(newStore(dir, backgroundScope))
        assertThat(source.current()).isEqualTo(UserSettings())
    }

    @Test
    fun update_persistsAcrossInstances() = runTest {
        val dir = Files.createTempDirectory("prefs").toFile()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val source = UserPreferencesDataSource(newStore(dir, CoroutineScope(scope.coroutineContext + SupervisorJob())))
        source.update {
            it.copy(
                language = AppLanguage.ENGLISH,
                themeMode = ThemeMode.DARK,
                calendarSystemOverride = CalendarSystem.JALALI,
                dashboard = it.dashboard.copy(hidden = setOf(DashboardSection.FOCUS)),
            )
        }
        val s = source.current()
        assertThat(s.language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(s.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(s.calendarSystem).isEqualTo(CalendarSystem.JALALI)
        assertThat(s.dashboard.hidden).containsExactly(DashboardSection.FOCUS)
    }

    @Test
    fun unknownOrCorruptValues_fallBackPerKey() = runTest {
        val dir = Files.createTempDirectory("prefs").toFile()
        val store = newStore(dir, backgroundScope)
        store.edit {
            it[stringPreferencesKey("theme")] = "PURPLE"
            it[stringPreferencesKey("language")] = "en"
            it[stringPreferencesKey("dashboard_order")] = "habits,unknown_future_section,tasks"
        }
        val s = UserPreferencesDataSource(store).current()
        assertThat(s.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(s.language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(s.dashboard.order.take(2)).containsExactly(DashboardSection.HABITS, DashboardSection.TASKS).inOrder()
        assertThat(s.dashboard.order).containsExactlyElementsIn(DashboardSection.entries)
    }

    @Test
    fun exportImport_roundTripKeepsOnboardingState() = runTest {
        val a = UserPreferencesDataSource(newStore(Files.createTempDirectory("a").toFile(), backgroundScope))
        a.update { it.copy(language = AppLanguage.ENGLISH, focusMinutes = 50, onboardingCompleted = true) }
        val exported = a.export()
        val b = UserPreferencesDataSource(newStore(Files.createTempDirectory("b").toFile(), backgroundScope))
        b.import(exported)
        val s = b.current()
        assertThat(s.language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(s.focusMinutes).isEqualTo(50)
        assertThat(s.onboardingCompleted).isFalse()
    }
}
