package com.behnamjalali.planb.feature.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.RecurrenceBasis
import com.behnamjalali.planb.core.model.RecurrenceFrequency
import com.behnamjalali.planb.core.model.RecurrenceRule
import com.behnamjalali.planb.core.ui.CustomRecurrenceDialog
import com.behnamjalali.planb.core.ui.LocalDateFormatter
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.LocalToday
import com.behnamjalali.planb.core.ui.ProAccess
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberDateFormatter
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The custom repeat dialog with the Plan-B Pro #4 options (Gregorian, English). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
class RecurrenceDialogTest {
    @get:Rule val compose = createComposeRule()

    private val anchor = LocalDate.of(2026, 10, 12) // the second Monday of October 2026

    @Composable
    private fun Dialog(pro: Boolean, initial: RecurrenceRule?, opened: MutableList<ProFeature?>, onRule: (RecurrenceRule) -> Unit) {
        // Motion off, as in the app's screenshots.
        PlanBTheme(animationsEnabled = false) {
            val formatter = rememberDateFormatter(CalendarSystem.GREGORIAN, DayOfWeek.MONDAY, persianDigits = false)
            CompositionLocalProvider(
                LocalDateFormatter provides formatter,
                LocalToday provides anchor,
                LocalProAccess provides ProAccess(isPro = pro) { opened += it },
            ) {
                CustomRecurrenceDialog(initial, CalendarSystem.GREGORIAN, anchor, onDismiss = {}, onConfirm = onRule, allowAfterCompletion = true)
            }
        }
    }

    @Test
    fun proUser_picksTheSecondMonday_andSeesTheSummary() {
        val rules = mutableListOf<RecurrenceRule>()
        compose.setContent { Dialog(pro = true, RecurrenceRule(RecurrenceFrequency.MONTHLY), mutableListOf()) { rules += it } }
        compose.onNodeWithText("On a weekday, like the second Monday").performScrollTo().performClick()
        compose.onNodeWithText("Every month on the second Monday").assertExists()
        compose.onNodeWithText("Done").performClick()
        assertThat(rules.single()).isEqualTo(
            RecurrenceRule(RecurrenceFrequency.MONTHLY, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 2),
        )
    }

    @Test
    fun editingAnOrdinalRule_keepsIt() {
        val rules = mutableListOf<RecurrenceRule>()
        val initial = RecurrenceRule(RecurrenceFrequency.MONTHLY, weekdays = setOf(DayOfWeek.FRIDAY), setPosition = -1, count = 6)
        compose.setContent { Dialog(pro = true, initial, mutableListOf()) { rules += it } }
        compose.onNodeWithText("Every month on the last Friday, 6 times").assertExists()
        compose.onNodeWithText("Done").performClick()
        assertThat(rules.single()).isEqualTo(initial)
    }

    @Test
    fun fromTheEditorMenu() {
        val initial = RecurrenceRule(RecurrenceFrequency.MONTHLY, weekdays = setOf(DayOfWeek.MONDAY), setPosition = 2)
        compose.setContent {
            PlanBTheme(animationsEnabled = false) {
                val formatter = rememberDateFormatter(CalendarSystem.GREGORIAN, DayOfWeek.MONDAY, persianDigits = false)
                CompositionLocalProvider(
                    LocalDateFormatter provides formatter,
                    LocalToday provides anchor,
                    LocalProAccess provides ProAccess(isPro = true) {},
                ) {
                    TaskEditorScreen(
                        form = TaskForm(id = 5, title = "Bill", dueDate = anchor.plusDays(2).toEpochDay(), recurrence = initial.encode()),
                        isNew = false, projects = emptyList(), subtasks = emptyList(), calendarSystem = CalendarSystem.GREGORIAN,
                        snackbarHostState = androidx.compose.material3.SnackbarHostState(), onClose = {}, onUpdate = {}, onDueDate = {}, onDueTime = {},
                        onRecurrence = {}, onAddTag = {}, onRemoveTag = {}, onAddSubtask = {}, onRemovePendingSubtask = {}, onToggleSubtask = { _, _ -> },
                        onDeleteSubtask = {}, onOpenSubtask = {}, onSave = {}, onDelete = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Repeat").performScrollTo().performClick()
        compose.onNodeWithText("Custom…").performClick()
        // The editor's row and the dialog's summary.
        compose.onAllNodesWithText("Every month on the second Monday").assertCountEquals(2)
    }

    @Test
    fun proUser_countsFromCompletion() {
        val rules = mutableListOf<RecurrenceRule>()
        compose.setContent { Dialog(pro = true, RecurrenceRule(RecurrenceFrequency.DAILY, interval = 3), mutableListOf()) { rules += it } }
        compose.onNodeWithText("Count from completion").performClick()
        compose.onNodeWithText("3 days after completion").assertExists()
        compose.onNodeWithText("Done").performClick()
        assertThat(rules.single().basis).isEqualTo(RecurrenceBasis.COMPLETION)
    }

    @Test
    fun freeUser_proOptionOpensThePaywall_andKeepsTheRulePlain() {
        val opened = mutableListOf<ProFeature?>()
        val rules = mutableListOf<RecurrenceRule>()
        compose.setContent { Dialog(pro = false, RecurrenceRule(RecurrenceFrequency.MONTHLY), opened) { rules += it } }
        compose.onNodeWithText("On a weekday, like the second Monday").performScrollTo().performClick()
        compose.onNodeWithText("Count from completion").performClick()
        assertThat(opened).containsExactly(ProFeature.ADVANCED_RECURRENCE, ProFeature.ADVANCED_RECURRENCE)
        compose.onNodeWithText("Done").performClick()
        assertThat(rules.single()).isEqualTo(RecurrenceRule(RecurrenceFrequency.MONTHLY))
    }
}
