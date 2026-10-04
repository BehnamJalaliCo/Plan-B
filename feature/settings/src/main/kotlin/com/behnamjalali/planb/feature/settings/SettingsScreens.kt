package com.behnamjalali.planb.feature.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.model.AppLanguage
import com.behnamjalali.planb.core.model.CalendarSystem
import com.behnamjalali.planb.core.model.CalendarView
import com.behnamjalali.planb.core.model.NumberFormatMode
import com.behnamjalali.planb.core.model.TaskView
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.billing.ProProduct
import com.behnamjalali.planb.core.ui.PlannerLocals
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ReminderOffsets
import com.behnamjalali.planb.core.ui.reminderLabel
import java.time.DayOfWeek

/** Plan-B Pro status shown in the first Settings row. */
enum class ProStatus { FREE, MONTHLY, LIFETIME }

/** A single-choice option for [ChoiceDialog]. */
data class Choice<T>(val value: T, val label: String)

@Composable
fun <T> ChoiceDialog(title: String, choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    PlannerDialog(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel),
        dismissLabel = "",
        onConfirm = onDismiss,
    ) {
        Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
            choices.forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = MinTouchTarget)
                        .selectable(c.value == selected, role = Role.RadioButton) {
                            onSelect(c.value)
                            onDismiss()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = c.value == selected, onClick = null)
                    Spacer(Modifier.width(Spacing.md))
                    Text(c.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
fun SettingsDestination(
    onBack: () -> Unit,
    onOpen: (Any) -> Unit,
    onOpenPro: () -> Unit,
    onCustomizeToday: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        PlannerLoadingState()
        return
    }
    SettingsScreen(
        settings = s,
        versionName = viewModel.version.name,
        versionCode = viewModel.version.code,
        onBack = onBack,
        onOpen = onOpen,
        onCustomizeToday = onCustomizeToday,
        onUpdate = viewModel::update,
        proStatus = when {
            !entitlement.isPro -> ProStatus.FREE
            entitlement.product == ProProduct.LIFETIME -> ProStatus.LIFETIME
            else -> ProStatus.MONTHLY
        },
        onOpenPro = onOpenPro,
        onLanguage = { language ->
            viewModel.update { it.copy(language = language) }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        },
    )
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    versionName: String,
    versionCode: Int,
    onBack: () -> Unit,
    onOpen: (Any) -> Unit,
    onCustomizeToday: () -> Unit,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onLanguage: (AppLanguage) -> Unit,
    proStatus: ProStatus = ProStatus.FREE,
    onOpenPro: () -> Unit = {},
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val formatter = PlannerLocals.formatter
    val numbers = PlannerLocals.numbers
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val notificationsOn = remember(refresh) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactOn = remember(refresh) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val jalali = stringResource(R.string.settings_calendar_jalali)
    val gregorian = stringResource(R.string.settings_calendar_gregorian)
    fun minutes(m: Int) = resources.getString(R.string.settings_minutes, numbers.format(m))
    val themeLabel = stringResource(
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> R.string.settings_theme_system
            ThemeMode.LIGHT -> R.string.settings_theme_light
            ThemeMode.DARK -> R.string.settings_theme_dark
        },
    )

    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.settings_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item {
                SettingsRow(
                    stringResource(R.string.settings_pro),
                    icon = Icons.Rounded.WorkspacePremium,
                    subtitle = when (proStatus) {
                        ProStatus.FREE -> stringResource(R.string.settings_pro_upgrade, numbers.format(ProFeature.entries.size))
                        ProStatus.MONTHLY -> stringResource(R.string.settings_pro_monthly)
                        ProStatus.LIFETIME -> stringResource(R.string.settings_pro_lifetime)
                    },
                    onClick = onOpenPro,
                )
            }
            item { PlannerSectionHeader(stringResource(R.string.settings_language)) }
            item {
                SettingsRow(
                    stringResource(R.string.settings_language),
                    icon = Icons.Rounded.Language,
                    subtitle = stringResource(if (settings.language == AppLanguage.PERSIAN) R.string.settings_language_fa else R.string.settings_language_en),
                    onClick = { dialog = "language" },
                )
            }
            item { PlannerSectionHeader(stringResource(R.string.settings_appearance)) }
            item { SettingsRow(stringResource(R.string.settings_appearance), icon = Icons.Rounded.Contrast, subtitle = themeLabel, onClick = { dialog = "theme" }) }
            item {
                SettingsRow(stringResource(R.string.settings_personalize), icon = Icons.Rounded.Palette,
                    subtitle = stringResource(R.string.settings_personalize_summary), onClick = { onOpen(AppearanceRoute) })
            }
            item { PlannerSectionHeader(stringResource(R.string.settings_calendar)) }
            item {
                SettingsRow(
                    stringResource(R.string.settings_calendar_system), icon = Icons.Rounded.CalendarMonth,
                    subtitle = when (settings.calendarSystemOverride) {
                        null -> stringResource(R.string.settings_calendar_auto, if (settings.calendarSystem == CalendarSystem.JALALI) jalali else gregorian)
                        CalendarSystem.JALALI -> jalali
                        CalendarSystem.GREGORIAN -> gregorian
                    },
                    onClick = { dialog = "calendar" },
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.settings_first_day), icon = Icons.Rounded.CalendarViewWeek,
                    subtitle = settings.firstDayOfWeekOverride?.let { formatter.weekdayName(it) }
                        ?: stringResource(R.string.settings_first_day_auto, formatter.weekdayName(settings.firstDayOfWeek)),
                    onClick = { dialog = "firstDay" },
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.settings_number_format), icon = Icons.Rounded.Numbers,
                    subtitle = stringResource(
                        when (settings.numberFormat) {
                            NumberFormatMode.AUTO -> R.string.settings_numbers_auto
                            NumberFormatMode.PERSIAN -> R.string.settings_numbers_persian
                            NumberFormatMode.LATIN -> R.string.settings_numbers_latin
                        },
                    ),
                    onClick = { dialog = "numbers" },
                )
            }
            item { PlannerSectionHeader(stringResource(R.string.settings_productivity)) }
            item { SettingsRow(stringResource(R.string.settings_default_reminder), icon = Icons.Rounded.Alarm, subtitle = reminderLabel(settings.defaultReminderMinutes), onClick = { dialog = "reminder" }) }
            item {
                SettingsRow(stringResource(R.string.settings_default_task_view), icon = Icons.AutoMirrored.Rounded.ViewList,
                    subtitle = taskViewName(settings.defaultTaskView), onClick = { dialog = "taskView" })
            }
            item {
                SettingsRow(stringResource(R.string.settings_default_calendar_view), icon = Icons.Rounded.ViewDay,
                    subtitle = calendarViewName(settings.defaultCalendarView), onClick = { dialog = "calendarView" })
            }
            item { SettingsRow(stringResource(R.string.settings_focus_length), icon = Icons.Rounded.Timer, subtitle = minutes(settings.focusMinutes), onClick = { dialog = "focus" }) }
            item { SettingsRow(stringResource(R.string.settings_break_length), icon = Icons.Rounded.Timer, subtitle = minutes(settings.shortBreakMinutes), onClick = { dialog = "break" }) }
            item {
                SettingsRow(
                    stringResource(R.string.settings_notifications), icon = Icons.Rounded.Notifications,
                    subtitle = stringResource(if (notificationsOn) R.string.settings_notifications_on else R.string.settings_notifications_off),
                    onClick = {
                        val action = notificationRowAction(
                            sdkInt = Build.VERSION.SDK_INT,
                            notificationsEnabled = notificationsOn,
                            permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                            requestedBefore = NotificationPermissionMemory.wasRequested(context),
                            showRationale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.findActivity()?.let {
                                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
                            } == true,
                        )
                        when (action) {
                            // Only returned on Android 13+, where the permission exists.
                            NotificationRowAction.REQUEST_PERMISSION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                NotificationPermissionMemory.markRequested(context)
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            NotificationRowAction.OPEN_SETTINGS -> runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        }
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SettingsRow(
                        stringResource(R.string.settings_exact_alarms), icon = Icons.Rounded.Alarm,
                        subtitle = stringResource(if (exactOn) R.string.settings_exact_alarms_on else R.string.settings_exact_alarms_off),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                }
            }
            item { PlannerSectionHeader(stringResource(R.string.settings_experience)) }
            item {
                SwitchRow(stringResource(R.string.settings_animations), stringResource(R.string.settings_animations_summary), Icons.Rounded.Animation,
                    settings.animationsEnabled) { v -> onUpdate { it.copy(animationsEnabled = v) } }
            }
            item {
                SwitchRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_summary), Icons.Rounded.Vibration,
                    settings.hapticsEnabled) { v -> onUpdate { it.copy(hapticsEnabled = v) } }
            }
            item { SettingsRow(stringResource(R.string.settings_dashboard), icon = Icons.Rounded.Dashboard, onClick = onCustomizeToday) }
            item { PlannerSectionHeader(stringResource(R.string.settings_data)) }
            item {
                SettingsRow(stringResource(R.string.settings_backup_restore), icon = Icons.Rounded.Backup,
                    subtitle = stringResource(R.string.settings_backup_restore_summary), onClick = { onOpen(BackupRoute) })
            }
            item { PlannerSectionHeader(stringResource(R.string.settings_application)) }
            item {
                SettingsRow(stringResource(R.string.settings_privacy), icon = Icons.Rounded.PrivacyTip,
                    subtitle = stringResource(R.string.settings_privacy_summary), onClick = { onOpen(PrivacyRoute) })
            }
            item {
                SettingsRow(stringResource(R.string.settings_about), icon = Icons.Rounded.Info,
                    subtitle = stringResource(R.string.settings_version, ltr(numbers.localize(versionName)), numbers.format(versionCode)), onClick = { onOpen(AboutRoute) })
            }
            item { SettingsRow(stringResource(R.string.settings_licenses), icon = Icons.Rounded.Description, onClick = { onOpen(LicensesRoute) }) }
        }
    }

    val close = { dialog = null }
    when (dialog) {
        "language" -> ChoiceDialog(
            stringResource(R.string.settings_language),
            listOf(Choice(AppLanguage.PERSIAN, stringResource(R.string.settings_language_fa)), Choice(AppLanguage.ENGLISH, stringResource(R.string.settings_language_en))),
            settings.language, onLanguage, close,
        )
        "theme" -> ChoiceDialog(
            stringResource(R.string.settings_appearance),
            listOf(
                Choice(ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)),
                Choice(ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)),
                Choice(ThemeMode.DARK, stringResource(R.string.settings_theme_dark)),
            ),
            settings.themeMode, { v -> onUpdate { it.copy(themeMode = v) } }, close,
        )
        "calendar" -> ChoiceDialog(
            stringResource(R.string.settings_calendar_system),
            listOf<Choice<CalendarSystem?>>(
                Choice(null, stringResource(R.string.settings_calendar_auto, if (settings.language == AppLanguage.PERSIAN) jalali else gregorian)),
                Choice(CalendarSystem.JALALI, jalali),
                Choice(CalendarSystem.GREGORIAN, gregorian),
            ),
            settings.calendarSystemOverride, { v -> onUpdate { it.copy(calendarSystemOverride = v) } }, close,
        )
        "firstDay" -> ChoiceDialog(
            stringResource(R.string.settings_first_day),
            listOf<Choice<DayOfWeek?>>(Choice(null, stringResource(R.string.settings_first_day_auto, formatter.weekdayName(
                if (settings.calendarSystem == CalendarSystem.JALALI) DayOfWeek.SATURDAY else DayOfWeek.MONDAY,
            )))) + listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY).map { Choice(it, formatter.weekdayName(it)) },
            settings.firstDayOfWeekOverride, { v -> onUpdate { it.copy(firstDayOfWeekOverride = v) } }, close,
        )
        "numbers" -> ChoiceDialog(
            stringResource(R.string.settings_number_format),
            listOf(
                Choice(NumberFormatMode.AUTO, stringResource(R.string.settings_numbers_auto)),
                Choice(NumberFormatMode.PERSIAN, stringResource(R.string.settings_numbers_persian)),
                Choice(NumberFormatMode.LATIN, stringResource(R.string.settings_numbers_latin)),
            ),
            settings.numberFormat, { v -> onUpdate { it.copy(numberFormat = v) } }, close,
        )
        "reminder" -> ChoiceDialog(
            stringResource(R.string.settings_default_reminder),
            ReminderOffsets.filterNotNull().map { Choice(it, reminderLabel(it)) },
            settings.defaultReminderMinutes, { v -> onUpdate { it.copy(defaultReminderMinutes = v) } }, close,
        )
        "taskView" -> ChoiceDialog(
            stringResource(R.string.settings_default_task_view),
            listOf(TaskView.TODAY, TaskView.INBOX, TaskView.UPCOMING, TaskView.ALL).map { Choice(it, taskViewName(it)) },
            settings.defaultTaskView, { v -> onUpdate { it.copy(defaultTaskView = v) } }, close,
        )
        "calendarView" -> ChoiceDialog(
            stringResource(R.string.settings_default_calendar_view),
            CalendarView.entries.map { Choice(it, calendarViewName(it)) },
            settings.defaultCalendarView, { v -> onUpdate { it.copy(defaultCalendarView = v) } }, close,
        )
        "focus" -> ChoiceDialog(
            stringResource(R.string.settings_focus_length), listOf(15, 20, 25, 30, 45, 50, 60, 90).map { Choice(it, minutes(it)) },
            settings.focusMinutes, { v -> onUpdate { it.copy(focusMinutes = v) } }, close,
        )
        "break" -> ChoiceDialog(
            stringResource(R.string.settings_break_length), listOf(3, 5, 10, 15).map { Choice(it, minutes(it)) },
            settings.shortBreakMinutes, { v -> onUpdate { it.copy(shortBreakMinutes = v) } }, close,
        )
    }
}

@Composable
fun taskViewName(view: TaskView): String = stringResource(
    when (view) {
        TaskView.INBOX -> com.behnamjalali.planb.feature.settings.R.string.settings_view_inbox
        TaskView.TODAY -> com.behnamjalali.planb.feature.settings.R.string.settings_view_today
        TaskView.UPCOMING -> com.behnamjalali.planb.feature.settings.R.string.settings_view_upcoming
        else -> com.behnamjalali.planb.feature.settings.R.string.settings_view_all
    },
)

@Composable
fun calendarViewName(view: CalendarView): String = stringResource(
    when (view) {
        CalendarView.DAY -> R.string.settings_cal_day
        CalendarView.WEEK -> R.string.settings_cal_week
        CalendarView.MONTH -> R.string.settings_cal_month
        CalendarView.AGENDA -> R.string.settings_cal_agenda
    },
)

@Composable
fun TextScreen(
    title: String,
    body: List<String>,
    onBack: () -> Unit,
    bodyModifier: (index: Int) -> Modifier = { Modifier },
    footer: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(title, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            body.forEachIndexed { index, text -> Text(text, style = MaterialTheme.typography.bodyLarge, modifier = bodyModifier(index)) }
            footer()
        }
    }
}

@Composable
fun PrivacyDestination(onBack: () -> Unit) =
    TextScreen(stringResource(R.string.privacy_title), listOf(stringResource(R.string.privacy_body)), onBack)

@Composable
fun AboutDestination(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val numbers = PlannerLocals.numbers
    var developerDialog by rememberSaveable { mutableStateOf(false) }
    if (developerDialog) {
        val entitlement by viewModel.entitlement.collectAsStateWithLifecycle()
        ChoiceDialog(
            stringResource(R.string.settings_developer_title),
            listOf<Choice<ProProduct?>>(
                Choice(null, stringResource(R.string.settings_developer_none)),
                Choice(ProProduct.MONTHLY, stringResource(R.string.settings_pro_monthly)),
                Choice(ProProduct.LIFETIME, stringResource(R.string.settings_pro_lifetime)),
            ),
            entitlement.product.takeIf { entitlement.isPro },
            viewModel::setDeveloperPro,
        ) { developerDialog = false }
    }
    TextScreen(
        stringResource(R.string.about_title),
        listOf(
            stringResource(R.string.app_display_name),
            stringResource(R.string.settings_version, ltr(numbers.localize(viewModel.version.name)), numbers.format(viewModel.version.code)),
            stringResource(R.string.about_body),
            stringResource(R.string.about_developer),
        ),
        onBack,
        // Debug builds only: a long press on the version opens the developer section.
        bodyModifier = { index ->
            if (index == 1 && viewModel.developerOptions) {
                Modifier.combinedClickable(onClick = {}, onLongClick = { developerDialog = true })
            } else {
                Modifier
            }
        },
    ) {
        val context = LocalContext.current
        val noEmailApp = stringResource(R.string.about_no_email_app, SUPPORT_EMAIL)
        val subject = stringResource(R.string.about_feedback_subject, viewModel.version.name)
        SettingsRow(
            stringResource(R.string.about_feedback),
            icon = Icons.Rounded.Email,
            subtitle = SUPPORT_EMAIL,
            onClick = {
                // mailto: opens the user's email app; nothing is sent by Plan-B itself.
                val intent = Intent(Intent.ACTION_SENDTO, "mailto:$SUPPORT_EMAIL".toUri())
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, subject)
                runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, noEmailApp, Toast.LENGTH_LONG).show()
                }
            },
        )
        Text(stringResource(R.string.about_made_with), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Keeps Latin/number runs such as "1.0.0-debug" in order inside right-to-left text. */
private fun ltr(text: String) = "\u2066$text\u2069"

/** Support and feedback address shown in About. */
const val SUPPORT_EMAIL = "behnamjalali88@gmail.com"

@Composable
fun LicensesDestination(onBack: () -> Unit) = TextScreen(
    stringResource(R.string.licenses_title),
    listOf(
        stringResource(R.string.licenses_fonts),
        stringResource(R.string.licenses_androidx),
        stringResource(R.string.licenses_kotlin),
        stringResource(R.string.licenses_dagger),
        stringResource(R.string.licenses_poolakey),
        stringResource(R.string.licenses_okhttp),
        stringResource(R.string.licenses_icons),
    ),
    onBack,
)

/** What tapping the notifications row does. */
internal enum class NotificationRowAction { REQUEST_PERMISSION, OPEN_SETTINGS }

/**
 * The permission dialog is only useful while Android will still show it: before the first
 * request, or after a single denial (when it asks for a rationale). After "Don't allow" a
 * second time, or when notifications are blocked in the system settings although the
 * permission is granted, only the app's notification settings can turn them on.
 */
internal fun notificationRowAction(
    sdkInt: Int,
    notificationsEnabled: Boolean,
    permissionGranted: Boolean,
    requestedBefore: Boolean,
    showRationale: Boolean,
): NotificationRowAction = when {
    notificationsEnabled || sdkInt < Build.VERSION_CODES.TIRAMISU || permissionGranted -> NotificationRowAction.OPEN_SETTINGS
    requestedBefore && !showRationale -> NotificationRowAction.OPEN_SETTINGS
    else -> NotificationRowAction.REQUEST_PERMISSION
}

/** Remembers that the notification permission was requested (Android cannot tell "never asked" from "denied for good"). */
internal object NotificationPermissionMemory {
    private const val FILE = "planb_permission_requests"
    private const val KEY = "post_notifications_requested"

    fun wasRequested(context: Context): Boolean =
        runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false) }.getOrDefault(false)

    fun markRequested(context: Context) {
        runCatching { context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit { putBoolean(KEY, true) } }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
