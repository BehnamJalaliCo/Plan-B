package com.behnamjalali.planb.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

class ModelTest {
    @Test
    fun projectProgress_modes() {
        val base = Project(title = "p")
        assertThat(ProjectSummary(base, totalTasks = 4, completedTasks = 1).progress).isEqualTo(0.25f)
        assertThat(ProjectSummary(base, totalTasks = 0).progress).isEqualTo(0f)
        assertThat(ProjectSummary(base.copy(progressMode = ProgressMode.MILESTONES), totalMilestones = 2, completedMilestones = 2).progress)
            .isEqualTo(1f)
        assertThat(ProjectSummary(base.copy(progressMode = ProgressMode.MANUAL, manualProgress = 1.7f)).progress).isEqualTo(1f)
        assertThat(ProjectSummary(base.copy(status = ProjectStatus.COMPLETED), totalTasks = 3).progress).isEqualTo(1f)
    }

    @Test
    fun goalProgress_clamped() {
        assertThat(Goal(title = "g", target = 10.0, currentValue = 2.5).progress).isEqualTo(0.25f)
        assertThat(Goal(title = "g", target = 10.0, currentValue = 20.0).progress).isEqualTo(1f)
        assertThat(Goal(title = "g", target = 0.0, currentValue = 2.0).progress).isEqualTo(0f)
    }

    @Test
    fun focusSession_elapsedFromTimestamps() {
        val start = Instant.parse("2026-10-04T10:00:00Z")
        val running = FocusSession(startedAt = start, plannedDurationMillis = 1_500_000, accumulatedMillis = 60_000, runningSince = start)
        assertThat(running.elapsedMillis(start.plusSeconds(120))).isEqualTo(180_000)
        assertThat(running.remainingMillis(start.plusSeconds(10_000))).isEqualTo(0)
        val paused = running.copy(status = FocusStatus.PAUSED, runningSince = null)
        assertThat(paused.elapsedMillis(start.plusSeconds(999))).isEqualTo(60_000)
    }

    @Test
    fun noteDocument_roundTripAndRecovery() {
        val doc = NoteDocument(blocks = listOf(NoteBlock("1", BlockType.CHECKLIST, "Milk", checked = true), NoteBlock("2", BlockType.DIVIDER)))
        assertThat(NoteDocument.decode(doc.encode())).isEqualTo(doc)
        assertThat(doc.plainText()).isEqualTo("Milk")
        val recovered = NoteDocument.decode("plain legacy text")
        assertThat(recovered.blocks.single().text).isEqualTo("plain legacy text")
    }

    @Test
    fun settings_languageDefaults() {
        val fa = UserSettings()
        assertThat(fa.language).isEqualTo(AppLanguage.PERSIAN)
        assertThat(fa.calendarSystem).isEqualTo(CalendarSystem.JALALI)
        assertThat(fa.firstDayOfWeek).isEqualTo(java.time.DayOfWeek.SATURDAY)
        assertThat(fa.usePersianDigits).isTrue()
        val en = UserSettings(language = AppLanguage.ENGLISH)
        assertThat(en.calendarSystem).isEqualTo(CalendarSystem.GREGORIAN)
        assertThat(en.firstDayOfWeek).isEqualTo(java.time.DayOfWeek.MONDAY)
        assertThat(en.usePersianDigits).isFalse()
        val mixed = UserSettings(language = AppLanguage.ENGLISH, calendarSystemOverride = CalendarSystem.JALALI)
        assertThat(mixed.firstDayOfWeek).isEqualTo(java.time.DayOfWeek.SATURDAY)
    }
}
