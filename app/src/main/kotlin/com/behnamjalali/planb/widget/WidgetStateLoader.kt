package com.behnamjalali.planb.widget

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.data.repository.EventRepository
import com.behnamjalali.planb.core.data.repository.FocusRepository
import com.behnamjalali.planb.core.data.repository.HabitRepository
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.data.repository.TaskFilter
import com.behnamjalali.planb.core.data.repository.TaskRepository
import com.behnamjalali.planb.core.datetime.MonthGrid
import com.behnamjalali.planb.core.datetime.PlannerDateFormatter
import com.behnamjalali.planb.core.model.FocusStatus
import com.behnamjalali.planb.core.model.HabitStats
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Reads what each widget shows, localized with the app's own language and calendar settings. */
@Singleton
class WidgetStateLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val focus: FocusRepository,
    private val events: EventRepository,
    private val settings: SettingsRepository,
    private val entitlements: EntitlementRepository,
    private val time: TimeProvider,
) {
    private class Env(val context: Context, val formatter: PlannerDateFormatter, val settings: UserSettings) {
        fun s(id: Int, vararg args: Any): String = context.getString(id, *args)
    }

    private suspend fun env(): Env {
        val s = settings.current()
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(s.language.tag)) }
        val localized = context.createConfigurationContext(config)
        val formatter = PlannerDateFormatter(
            resources = localized.resources,
            calendarSystem = s.calendarSystem,
            firstDayOfWeek = s.firstDayOfWeek,
            numbers = NumberFormatter(s.usePersianDigits),
            use24Hour = s.usePersianDigits || DateFormat.is24HourFormat(context),
        )
        return Env(localized, formatter, s)
    }

    private suspend fun <T> gated(build: suspend (Env) -> T): WidgetUi<T> {
        val env = env()
        return if (entitlements.current().isPro) {
            WidgetUi.Ready(build(env))
        } else {
            WidgetUi.Locked(env.s(R.string.widget_locked_title), env.s(R.string.widget_locked_message), env.s(R.string.widget_locked_action))
        }
    }

    suspend fun today(): WidgetUi<TodayWidgetState> = gated { env ->
        val today = time.today()
        val zone = time.zone()
        val open = tasks.observeTasks(TaskFilter(view = TaskView.TODAY, today = today)).first()
        val done = tasks.observeCompletedCount(today.atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()).first()
        val total = open.size + done
        val f = env.formatter
        TodayWidgetState(
            title = env.s(R.string.widget_today_title),
            date = f.fullDate(today),
            progress = if (total == 0) 0f else done.toFloat() / total,
            progressLabel = env.s(R.string.widget_today_progress, f.numbers.format(done), f.numbers.format(total)),
            tasks = open.take(MAX_TASKS).map { t ->
                val meta = when {
                    t.dueDate != null && t.dueDate!! < today -> f.shortDate(t.dueDate!!, today)
                    t.dueTime != null -> f.time(t.dueTime!!)
                    else -> null
                }
                WidgetTask(t.id, t.title, meta)
            },
            empty = env.s(R.string.widget_today_empty),
            completeLabel = env.s(R.string.widget_complete),
        )
    }

    suspend fun habits(): WidgetUi<HabitsWidgetState> = gated { env ->
        val today = time.today()
        val f = env.formatter
        val list = habits.observeHabits(today, today).first()
            .filter { HabitStats.isScheduled(it.habit, today) }
            .map { h ->
                val amount = h.amounts[today] ?: 0
                WidgetHabit(
                    id = h.habit.id,
                    title = h.habit.title,
                    done = HabitStats.isDone(h.habit, h.amounts, today),
                    detail = env.s(R.string.widget_habit_detail, f.numbers.format(amount), f.numbers.format(h.habit.target)),
                )
            }
        HabitsWidgetState(env.s(R.string.widget_habits_title), f.dayMonth(today), list, env.s(R.string.widget_habits_empty), env.s(R.string.widget_check_in))
    }

    suspend fun focus(): WidgetUi<FocusWidgetState> = gated { env ->
        val f = env.formatter
        val session = focus.getActive()
        val now = time.now()
        when (session?.status) {
            FocusStatus.RUNNING, FocusStatus.PAUSED -> {
                val remaining = session.remainingMillis(now)
                val running = session.status == FocusStatus.RUNNING
                FocusWidgetState(
                    title = env.s(R.string.widget_focus_title),
                    status = env.s(if (running) R.string.widget_focus_running else R.string.widget_focus_paused),
                    remaining = f.timer(remaining),
                    remainingMillis = remaining,
                    running = running,
                    action = env.s(if (running) R.string.widget_focus_pause else R.string.widget_focus_resume),
                )
            }
            else -> {
                val planned = env.settings.focusMinutes * 60_000L
                FocusWidgetState(
                    title = env.s(R.string.widget_focus_title),
                    status = env.s(R.string.widget_focus_idle),
                    remaining = f.timer(planned),
                    remainingMillis = planned,
                    running = false,
                    action = env.s(R.string.widget_focus_start),
                )
            }
        }
    }

    suspend fun calendar(): WidgetUi<CalendarWidgetState> = gated { env ->
        val today = time.today()
        val f = env.formatter
        val month = f.monthOf(today)
        val grid = MonthGrid.build(f.engine, month, f.firstDayOfWeek)
        val from = grid.first().first().date
        val to = grid.last().last().date
        val busy = buildSet {
            tasks.observeTasks(TaskFilter(view = TaskView.ALL, today = today, dueFrom = from, dueTo = to)).first().mapNotNullTo(this) { it.dueDate }
            events.occurrences(from, to).mapTo(this) { it.date }
        }
        CalendarWidgetState(
            title = f.monthYear(month),
            weekdays = f.weekdays().map(f::weekdayNarrow),
            weeks = grid.map { week ->
                week.map { cell ->
                    val isBusy = cell.date in busy
                    CalendarCell(
                        day = f.dayNumber(cell.date),
                        inMonth = cell.inMonth,
                        today = cell.date == today,
                        busy = isBusy,
                        description = if (isBusy) env.s(R.string.widget_calendar_busy, f.fullDate(cell.date)) else f.fullDate(cell.date),
                    )
                }
            },
        )
    }

    suspend fun quickAdd(): WidgetUi<QuickAddWidgetState> = gated { env ->
        QuickAddWidgetState(env.s(R.string.widget_quick_add_title), env.s(R.string.widget_quick_add_hint))
    }

    private companion object {
        const val MAX_TASKS = 8
    }
}
