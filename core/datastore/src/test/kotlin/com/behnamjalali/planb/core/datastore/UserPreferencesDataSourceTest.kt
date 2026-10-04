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
import java.time.DayOfWeek
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

    @Test
    fun import_withMissingKeys_keepsCurrentValues() = runTest {
        val store = UserPreferencesDataSource(newStore(Files.createTempDirectory("c").toFile(), backgroundScope))
        store.update { it.copy(focusMinutes = 45, themeMode = ThemeMode.DARK) }
        // An older backup that only knew the language.
        store.import(mapOf("language" to "en", "theme" to "NOT_A_THEME"))
        val s = store.current()
        assertThat(s.language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(s.focusMinutes).isEqualTo(45)
        assertThat(s.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(store.export()).doesNotContainKey("onboarding_completed")
    }

    @Test
    fun corruptFile_isReplacedWithDefaults_andStaysWritable() = runTest {
        val dir = Files.createTempDirectory("corrupt").toFile()
        val file = File(dir, "test.preferences_pb")
        file.writeBytes(byteArrayOf(0x0A, 0x7F, 0x00, 0x13, 0x37, 0xFF.toByte(), 0x42) + "not a protobuf".toByteArray())
        val source = UserPreferencesDataSource(createPreferencesDataStore(backgroundScope) { file })
        assertThat(source.current()).isEqualTo(UserSettings())
        source.update { it.copy(language = AppLanguage.ENGLISH) }
        assertThat(source.current().language).isEqualTo(AppLanguage.ENGLISH)
        assertThat(source.export()["language"]).isEqualTo("en")
    }

    @Test
    fun searchIndexVersion_isDeviceStateAndNotExported() = runTest {
        val source = UserPreferencesDataSource(newStore(Files.createTempDirectory("f").toFile(), backgroundScope))
        assertThat(source.searchIndexVersion()).isEqualTo(0)
        source.setSearchIndexVersion(2)
        assertThat(source.searchIndexVersion()).isEqualTo(2)
        assertThat(source.export()).doesNotContainKey("search_index_version")
    }

    @Test
    fun import_autoCalendarAndFirstDay_resetOverrides() = runTest {
        val source = UserPreferencesDataSource(newStore(Files.createTempDirectory("d").toFile(), backgroundScope))
        val exported = source.export() // follows the language: no overrides
        val target = UserPreferencesDataSource(newStore(Files.createTempDirectory("e").toFile(), backgroundScope))
        target.update { it.copy(calendarSystemOverride = CalendarSystem.GREGORIAN, firstDayOfWeekOverride = DayOfWeek.SUNDAY) }
        target.import(exported)
        assertThat(target.current().calendarSystemOverride).isNull()
        assertThat(target.current().firstDayOfWeekOverride).isNull()
    }
}
