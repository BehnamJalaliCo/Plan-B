package com.behnamjalali.planb.widget

import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders as MaterialColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.behnamjalali.planb.R
import com.behnamjalali.planb.core.designsystem.theme.planBColorScheme
import com.behnamjalali.planb.core.model.ColorTheme

/**
 * Material You colors on Android 12+ (the wallpaper palette), the Plan-B palette before that;
 * both follow the system light/dark setting.
 */
@Composable
fun PlanBGlanceTheme(content: @Composable () -> Unit) {
    val colors: ColorProviders = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme.colors
    } else {
        MaterialColorProviders(light = planBColorScheme(ColorTheme.CLASSIC, false), dark = planBColorScheme(ColorTheme.CLASSIC, true))
    }
    GlanceTheme(colors = colors, content = content)
}

@Composable
private fun WidgetFrame(onClick: Action? = null, content: @Composable () -> Unit) {
    val base = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(12.dp)
    Box(if (onClick != null) base.clickable(onClick) else base) { content() }
}

@Composable
private fun Title(text: String, trailing: String? = null) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        if (trailing != null) {
            Text(trailing, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
    }
}

@Composable
private fun Body(text: String, modifier: GlanceModifier = GlanceModifier) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp), modifier = modifier)
}

/** Shown to non-Pro users: the widget stays, its content is locked, a tap opens the Pro screen. */
@Composable
fun LockedContent(state: WidgetUi.Locked, openPro: Action) {
    WidgetFrame(openPro) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.title, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            Spacer(GlanceModifier.height(4.dp))
            Text(state.message, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center))
            Spacer(GlanceModifier.height(8.dp))
            Box(
                GlanceModifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(16.dp).padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(state.action, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
fun TodayContent(state: TodayWidgetState, open: Action, complete: (Long) -> Action) {
    WidgetFrame {
        Column(GlanceModifier.fillMaxSize()) {
            Column(GlanceModifier.fillMaxWidth().clickable(open)) {
                Title(state.title, state.progressLabel)
                Body(state.date)
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = GlanceTheme.colors.primary,
                    backgroundColor = GlanceTheme.colors.surfaceVariant,
                )
            }
            Spacer(GlanceModifier.height(6.dp))
            if (state.tasks.isEmpty()) {
                Body(state.empty, GlanceModifier.padding(top = 8.dp))
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(state.tasks, itemId = { it.id }) { task ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckBox(
                                checked = false,
                                onCheckedChange = complete(task.id),
                                text = task.title,
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight().semantics { contentDescription = "${state.completeLabel}: ${task.title}" },
                            )
                            if (task.meta != null) {
                                Text(task.meta, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HabitsContent(state: HabitsWidgetState, open: Action, checkIn: (Long) -> Action) {
    WidgetFrame {
        Column(GlanceModifier.fillMaxSize()) {
            Column(GlanceModifier.fillMaxWidth().clickable(open)) { Title(state.title, state.date) }
            Spacer(GlanceModifier.height(6.dp))
            if (state.habits.isEmpty()) {
                Body(state.empty)
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(state.habits, itemId = { it.id }) { habit ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckBox(
                                checked = habit.done,
                                onCheckedChange = checkIn(habit.id),
                                text = habit.title,
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight().semantics { contentDescription = "${state.checkInLabel}: ${habit.title}, ${habit.detail}" },
                            )
                            Text(habit.detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusContent(state: FocusWidgetState, open: Action, toggle: Action) {
    val context = LocalContext.current
    val timerColor = GlanceTheme.colors.onSurface.getColor(context).toArgb()
    WidgetFrame(open) {
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Title(state.title, state.status)
            Spacer(GlanceModifier.height(4.dp))
            if (state.running) {
                // A platform chronometer counts down on its own, so the widget never needs per-second updates.
                val views = RemoteViews(context.packageName, R.layout.widget_focus_timer).apply {
                    setChronometer(R.id.widget_focus_chronometer, SystemClock.elapsedRealtime() + state.remainingMillis, null, true)
                    setChronometerCountDown(R.id.widget_focus_chronometer, true)
                    setTextColor(R.id.widget_focus_chronometer, timerColor)
                }
                AndroidRemoteViews(views, GlanceModifier.semantics { contentDescription = "${state.status} ${state.remaining}" })
            } else {
                Text(state.remaining, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 32.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(8.dp))
            Box(
                GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(18.dp).padding(horizontal = 16.dp, vertical = 8.dp).clickable(toggle),
            ) {
                Text(state.action, style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
fun CalendarContent(state: CalendarWidgetState, open: Action) {
    WidgetFrame(open) {
        Column(GlanceModifier.fillMaxSize()) {
            Title(state.title)
            Spacer(GlanceModifier.height(4.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                state.weekdays.forEach { day ->
                    Text(
                        day,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
            state.weeks.forEach { week ->
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    week.forEach { cell -> DayCell(cell, GlanceModifier.defaultWeight()) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(cell: CalendarCell, modifier: GlanceModifier) {
    Column(modifier.semantics { contentDescription = cell.description }, horizontalAlignment = Alignment.CenterHorizontally) {
        val color = when {
            cell.today -> GlanceTheme.colors.onPrimary
            cell.inMonth -> GlanceTheme.colors.onSurface
            else -> GlanceTheme.colors.outline
        }
        val box = if (cell.today) GlanceModifier.size(24.dp).background(GlanceTheme.colors.primary).cornerRadius(12.dp) else GlanceModifier.size(24.dp)
        Box(box, contentAlignment = Alignment.Center) {
            Text(cell.day, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = if (cell.today) FontWeight.Bold else FontWeight.Normal))
        }
        Box(
            GlanceModifier.size(4.dp).cornerRadius(2.dp).background(if (cell.busy) GlanceTheme.colors.tertiary else GlanceTheme.colors.widgetBackground),
        ) {}
    }
}

@Composable
fun QuickAddContent(state: QuickAddWidgetState, capture: Action) {
    WidgetFrame(capture) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(40.dp).background(GlanceTheme.colors.primary).cornerRadius(20.dp), contentAlignment = Alignment.Center) {
                Text("+", style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(state.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Body(state.hint)
            }
        }
    }
}
