package com.behnamjalali.planb.core.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardVoice
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StackedBarChart
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/** The groups the paywall lists Pro features in, in display order. */
enum class ProFeatureGroup(@StringRes val title: Int) {
    PLANNING(R.string.ui_pro_group_planning),
    NOTES(R.string.ui_pro_group_notes),
    HABITS(R.string.ui_pro_group_habits),
    PERSONAL(R.string.ui_pro_group_personal),
    SECURITY(R.string.ui_pro_group_security),
    AI(R.string.ui_pro_group_ai),
}

/**
 * Every Plan-B Pro feature, numbered as in docs/PRO.md. [id] is stable (it appears in
 * navigation arguments); never rename it. Features that exist today are free and are not
 * listed here.
 */
enum class ProFeature(
    val number: Int,
    val id: String,
    val group: ProFeatureGroup,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
) {
    PERSIAN_QUICK_ADD(1, "persian_quick_add", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_persian_quick_add, R.string.ui_pro_feature_persian_quick_add_desc, Icons.Rounded.AutoFixHigh),
    IRAN_HOLIDAYS(2, "iran_holidays", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_iran_holidays, R.string.ui_pro_feature_iran_holidays_desc, Icons.Rounded.Celebration),
    CALENDAR_SYNC(3, "calendar_sync", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_calendar_sync, R.string.ui_pro_feature_calendar_sync_desc, Icons.Rounded.Sync),
    ADVANCED_RECURRENCE(4, "advanced_recurrence", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_advanced_recurrence, R.string.ui_pro_feature_advanced_recurrence_desc, Icons.Rounded.EventRepeat),
    AUTO_PLANNING(5, "auto_planning", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_auto_planning, R.string.ui_pro_feature_auto_planning_desc, Icons.Rounded.AutoMode),
    TIME_BLOCKING(6, "time_blocking", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_time_blocking, R.string.ui_pro_feature_time_blocking_desc, Icons.Rounded.ViewWeek),
    DAY_TIMELINE(7, "day_timeline", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_day_timeline, R.string.ui_pro_feature_day_timeline_desc, Icons.Rounded.Timeline),
    DAILY_RITUALS(8, "daily_rituals", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_daily_rituals, R.string.ui_pro_feature_daily_rituals_desc, Icons.Rounded.WbTwilight),
    PROJECT_TIMELINE(9, "project_timeline", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_project_timeline, R.string.ui_pro_feature_project_timeline_desc, Icons.Rounded.StackedBarChart),
    SMART_LISTS(10, "smart_lists", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_smart_lists, R.string.ui_pro_feature_smart_lists_desc, Icons.Rounded.FilterAlt),
    DEADLINES(11, "deadlines", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_deadlines, R.string.ui_pro_feature_deadlines_desc, Icons.Rounded.EventBusy),
    MULTIPLE_REMINDERS(12, "multiple_reminders", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_multiple_reminders, R.string.ui_pro_feature_multiple_reminders_desc, Icons.Rounded.NotificationsActive),
    EISENHOWER(13, "eisenhower", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_eisenhower, R.string.ui_pro_feature_eisenhower_desc, Icons.Rounded.GridView),
    DEPENDENCIES(14, "dependencies", ProFeatureGroup.PLANNING, R.string.ui_pro_feature_dependencies, R.string.ui_pro_feature_dependencies_desc, Icons.Rounded.AccountTree),
    RICH_NOTES(15, "rich_notes", ProFeatureGroup.NOTES, R.string.ui_pro_feature_rich_notes, R.string.ui_pro_feature_rich_notes_desc, Icons.Rounded.Image),
    NOTE_LINKS(16, "note_links", ProFeatureGroup.NOTES, R.string.ui_pro_feature_note_links, R.string.ui_pro_feature_note_links_desc, Icons.Rounded.Link),
    DOCUMENT_SCAN(17, "document_scan", ProFeatureGroup.NOTES, R.string.ui_pro_feature_document_scan, R.string.ui_pro_feature_document_scan_desc, Icons.Rounded.DocumentScanner),
    HANDWRITING(18, "handwriting", ProFeatureGroup.NOTES, R.string.ui_pro_feature_handwriting, R.string.ui_pro_feature_handwriting_desc, Icons.Rounded.Draw),
    VOICE_NOTES(19, "voice_notes", ProFeatureGroup.NOTES, R.string.ui_pro_feature_voice_notes, R.string.ui_pro_feature_voice_notes_desc, Icons.Rounded.Mic),
    NOTE_DATABASES(20, "note_databases", ProFeatureGroup.NOTES, R.string.ui_pro_feature_note_databases, R.string.ui_pro_feature_note_databases_desc, Icons.Rounded.TableChart),
    NOTE_GRAPH(21, "note_graph", ProFeatureGroup.NOTES, R.string.ui_pro_feature_note_graph, R.string.ui_pro_feature_note_graph_desc, Icons.Rounded.Hub),
    WEB_CLIPPER(22, "web_clipper", ProFeatureGroup.NOTES, R.string.ui_pro_feature_web_clipper, R.string.ui_pro_feature_web_clipper_desc, Icons.Rounded.Share),
    MATH_CHARTS(23, "math_charts", ProFeatureGroup.NOTES, R.string.ui_pro_feature_math_charts, R.string.ui_pro_feature_math_charts_desc, Icons.Rounded.Functions),
    FOCUS_WRITING(24, "focus_writing", ProFeatureGroup.NOTES, R.string.ui_pro_feature_focus_writing, R.string.ui_pro_feature_focus_writing_desc, Icons.Rounded.EditNote),
    JOURNAL(25, "journal", ProFeatureGroup.NOTES, R.string.ui_pro_feature_journal, R.string.ui_pro_feature_journal_desc, Icons.Rounded.AutoStories),
    FOCUS_PRO(26, "focus_pro", ProFeatureGroup.HABITS, R.string.ui_pro_feature_focus_pro, R.string.ui_pro_feature_focus_pro_desc, Icons.Rounded.Headphones),
    HEALTH_CONNECT(27, "health_connect", ProFeatureGroup.HABITS, R.string.ui_pro_feature_health_connect, R.string.ui_pro_feature_health_connect_desc, Icons.Rounded.MonitorHeart),
    HABIT_STATS(28, "habit_stats", ProFeatureGroup.HABITS, R.string.ui_pro_feature_habit_stats, R.string.ui_pro_feature_habit_stats_desc, Icons.Rounded.QueryStats),
    CHALLENGES(29, "challenges", ProFeatureGroup.HABITS, R.string.ui_pro_feature_challenges, R.string.ui_pro_feature_challenges_desc, Icons.Rounded.EmojiEvents),
    MOOD_TRACKER(30, "mood_tracker", ProFeatureGroup.HABITS, R.string.ui_pro_feature_mood_tracker, R.string.ui_pro_feature_mood_tracker_desc, Icons.Rounded.Mood),
    REPORTS(31, "reports", ProFeatureGroup.PERSONAL, R.string.ui_pro_feature_reports, R.string.ui_pro_feature_reports_desc, Icons.Rounded.PictureAsPdf),
    WIDGETS(32, "widgets", ProFeatureGroup.PERSONAL, R.string.ui_pro_feature_widgets, R.string.ui_pro_feature_widgets_desc, Icons.Rounded.Widgets),
    THEMES(33, "themes", ProFeatureGroup.PERSONAL, R.string.ui_pro_feature_themes, R.string.ui_pro_feature_themes_desc, Icons.Rounded.Palette),
    QUICK_TILES(34, "quick_tiles", ProFeatureGroup.PERSONAL, R.string.ui_pro_feature_quick_tiles, R.string.ui_pro_feature_quick_tiles_desc, Icons.Rounded.TouchApp),
    WEAR_OS(35, "wear_os", ProFeatureGroup.PERSONAL, R.string.ui_pro_feature_wear_os, R.string.ui_pro_feature_wear_os_desc, Icons.Rounded.Watch),
    APP_LOCK(36, "app_lock", ProFeatureGroup.SECURITY, R.string.ui_pro_feature_app_lock, R.string.ui_pro_feature_app_lock_desc, Icons.Rounded.Fingerprint),
    AUTO_BACKUP(37, "auto_backup", ProFeatureGroup.SECURITY, R.string.ui_pro_feature_auto_backup, R.string.ui_pro_feature_auto_backup_desc, Icons.Rounded.Backup),
    TRASH_HISTORY(38, "trash_history", ProFeatureGroup.SECURITY, R.string.ui_pro_feature_trash_history, R.string.ui_pro_feature_trash_history_desc, Icons.Rounded.RestoreFromTrash),
    AI_ASSISTANT(39, "ai_assistant", ProFeatureGroup.AI, R.string.ui_pro_feature_ai_assistant, R.string.ui_pro_feature_ai_assistant_desc, Icons.Rounded.AutoAwesome),
    VOICE_INPUT(40, "voice_input", ProFeatureGroup.AI, R.string.ui_pro_feature_voice_input, R.string.ui_pro_feature_voice_input_desc, Icons.Rounded.KeyboardVoice),
    ;

    companion object {
        fun fromId(id: String?): ProFeature? = entries.firstOrNull { it.id == id }
    }
}
