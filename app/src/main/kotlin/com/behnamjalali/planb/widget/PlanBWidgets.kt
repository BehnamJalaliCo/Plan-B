package com.behnamjalali.planb.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import kotlinx.coroutines.CoroutineScope
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.quick.FocusControls
import com.behnamjalali.planb.quick.QuickLinks
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Hilt access for widgets and their callbacks, which Android creates outside the DI graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun loader(): WidgetStateLoader
    fun tasks(): TaskRepository
    fun habits(): HabitRepository
    fun focusControls(): FocusControls
    fun time(): TimeProvider
    fun updater(): GlanceWidgetUpdater

    @ApplicationScope
    fun appScope(): CoroutineScope
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

private fun Context.open(uri: android.net.Uri): Action = actionStartActivity(QuickLinks.intent(this, uri))

private fun Context.openPro(): Action = open(QuickLinks.pro(ProFeature.WIDGETS.id))

/** (a) Today: progress and the next tasks; tapping a check box completes the task. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = context.widgetEntryPoint().loader().today()
        provideContent {
            PlanBGlanceTheme {
                when (state) {
                    is WidgetUi.Locked -> LockedContent(state, context.openPro())
                    is WidgetUi.Ready -> TodayContent(state.data, context.open(QuickLinks.TODAY)) { taskId ->
                        actionRunCallback<CompleteTaskAction>(actionParametersOf(WidgetKeys.ID to taskId))
                    }
                }
            }
        }
    }
}

/** (b) Quick add: opens Quick Capture. */
class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 48.dp), DpSize(250.dp, 48.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = context.widgetEntryPoint().loader().quickAdd()
        provideContent {
            PlanBGlanceTheme {
                when (state) {
                    is WidgetUi.Locked -> LockedContent(state, context.openPro())
                    is WidgetUi.Ready -> QuickAddContent(state.data, context.open(QuickLinks.CAPTURE))
                }
            }
        }
    }
}

/** (c) Habits: today's habits; tapping one checks it in (or undoes a full check-in). */
class HabitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = context.widgetEntryPoint().loader().habits()
        provideContent {
            PlanBGlanceTheme {
                when (state) {
                    is WidgetUi.Locked -> LockedContent(state, context.openPro())
                    is WidgetUi.Ready -> HabitsContent(state.data, context.open(QuickLinks.HABITS)) { habitId ->
                        actionRunCallback<CheckInHabitAction>(actionParametersOf(WidgetKeys.ID to habitId))
                    }
                }
            }
        }
    }
}

/** (d) Focus: remaining time and start/pause. */
class FocusWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = context.widgetEntryPoint().loader().focus()
        provideContent {
            PlanBGlanceTheme {
                when (state) {
                    is WidgetUi.Locked -> LockedContent(state, context.openPro())
                    is WidgetUi.Ready -> FocusContent(state.data, context.open(QuickLinks.FOCUS), actionRunCallback<ToggleFocusAction>())
                }
            }
        }
    }
}

/** (e) Monthly calendar in the user's calendar (Jalali or Gregorian) with dots on busy days. */
class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = context.widgetEntryPoint().loader().calendar()
        provideContent {
            PlanBGlanceTheme {
                when (state) {
                    is WidgetUi.Locked -> LockedContent(state, context.openPro())
                    is WidgetUi.Ready -> CalendarContent(state.data, context.open(QuickLinks.CALENDAR))
                }
            }
        }
    }
}

object WidgetKeys {
    val ID = ActionParameters.Key<Long>("planb_id")
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.ID] ?: return
        val entry = context.widgetEntryPoint()
        runCatching { entry.tasks().setCompleted(id, true) }
        entry.updater().updateNow()
    }
}

class CheckInHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.ID] ?: return
        val entry = context.widgetEntryPoint()
        runCatching {
            val habits = entry.habits()
            val habit = habits.getHabit(id) ?: return@runCatching
            val today = entry.time().today()
            val amount = habits.amountOn(id, today)
            // A done habit is undone (same as the app's check button); otherwise one more check-in.
            if (HabitStats.isDone(habit, mapOf(today to amount), today)) habits.checkIn(id, today, -amount) else habits.checkIn(id, today, 1)
        }
        entry.updater().updateNow()
    }
}

class ToggleFocusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entry = context.widgetEntryPoint()
        runCatching { entry.focusControls().toggle() }
        entry.updater().updateNow()
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}
