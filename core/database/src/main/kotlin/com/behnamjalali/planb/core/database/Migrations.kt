package com.behnamjalali.planb.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Every schema change ships with a hand-written, tested migration. */
object Migrations {
    /** v2: note_drafts table for editor draft recovery. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `note_drafts` (" +
                    "`note_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                    "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`note_id`), " +
                    "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
        }
    }

    /**
     * v3: the schema for every Plan-B Pro work package, in one step. Existing tables only gain
     * nullable columns or NOT NULL columns with a DEFAULT, so every existing row stays valid
     * and keeps its meaning; the new tables start empty. See DATABASE.md §7.
     */
    internal val SCHEMA_3_STATEMENTS: List<String> = listOf(
        "ALTER TABLE `tasks` ADD COLUMN `deadline` INTEGER",
        "ALTER TABLE `tasks` ADD COLUMN `scheduled_start` INTEGER",
        "ALTER TABLE `tasks` ADD COLUMN `scheduled_end` INTEGER",
        "ALTER TABLE `tasks` ADD COLUMN `nag` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `tasks` ADD COLUMN `deleted_at` INTEGER",
        "CREATE INDEX IF NOT EXISTS `index_tasks_deadline` ON `tasks` (`deadline`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_scheduled_start` ON `tasks` (`scheduled_start`)",
        "CREATE INDEX IF NOT EXISTS `index_tasks_deleted_at` ON `tasks` (`deleted_at`)",
        "ALTER TABLE `notes` ADD COLUMN `deleted_at` INTEGER",
        "ALTER TABLE `notes` ADD COLUMN `locked` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `notes` ADD COLUMN `encrypted_payload` BLOB",
        "CREATE INDEX IF NOT EXISTS `index_notes_deleted_at` ON `notes` (`deleted_at`)",
        "ALTER TABLE `habits` ADD COLUMN `health_metric` TEXT",
        "ALTER TABLE `habits` ADD COLUMN `health_threshold` INTEGER",
        "ALTER TABLE `focus_sessions` ADD COLUMN `sound_id` TEXT",
        "ALTER TABLE `focus_sessions` ADD COLUMN `strict` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `task_reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`task_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `offset_minutes` INTEGER, `at` INTEGER, " +
            "FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_task_reminders_task_id` ON `task_reminders` (`task_id`)",
        "CREATE TABLE IF NOT EXISTS `task_dependencies` (`task_id` INTEGER NOT NULL, " +
            "`depends_on_task_id` INTEGER NOT NULL, PRIMARY KEY(`task_id`, `depends_on_task_id`), " +
            "FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`depends_on_task_id`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_task_dependencies_depends_on_task_id` ON `task_dependencies` (`depends_on_task_id`)",
        "CREATE TABLE IF NOT EXISTS `saved_filters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `icon` TEXT NOT NULL, `color` TEXT NOT NULL, `query` TEXT NOT NULL, " +
            "`sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_saved_filters_sort_order` ON `saved_filters` (`sort_order`)",
        "CREATE TABLE IF NOT EXISTS `note_versions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`note_id` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
            "`content` TEXT NOT NULL, `size` INTEGER NOT NULL, " +
            "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_note_versions_note_id_created_at` ON `note_versions` (`note_id`, " +
            "`created_at`)",
        "CREATE TABLE IF NOT EXISTS `note_links` (`from_note_id` INTEGER NOT NULL, " +
            "`to_note_id` INTEGER NOT NULL, PRIMARY KEY(`from_note_id`, `to_note_id`), " +
            "FOREIGN KEY(`from_note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`to_note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_note_links_to_note_id` ON `note_links` (`to_note_id`)",
        "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`owner_type` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
            "`file_name` TEXT NOT NULL, `display_name` TEXT NOT NULL DEFAULT '', `mime_type` TEXT NOT NULL, " +
            "`size_bytes` INTEGER NOT NULL, `duration_ms` INTEGER, `width` INTEGER, `height` INTEGER, " +
            "`ocr_text` TEXT, `transcript` TEXT, `sort_order` INTEGER NOT NULL DEFAULT 0, " +
            "`created_at` INTEGER NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_attachments_owner_type_owner_id` ON `attachments` (`owner_type`, " +
            "`owner_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_file_name` ON `attachments` (`file_name`)",
        "CREATE TABLE IF NOT EXISTS `journal_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`date` INTEGER NOT NULL, `note_id` INTEGER NOT NULL, `prompt_id` TEXT, " +
            "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
            "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_journal_entries_date` ON `journal_entries` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_journal_entries_note_id` ON `journal_entries` (`note_id`)",
        "CREATE TABLE IF NOT EXISTS `mood_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`date` INTEGER NOT NULL, `time` INTEGER, `mood` INTEGER, `energy` INTEGER, " +
            "`tags` TEXT NOT NULL DEFAULT '', `note_id` INTEGER, `created_at` INTEGER NOT NULL, " +
            "`updated_at` INTEGER NOT NULL, " +
            "FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_mood_entries_date` ON `mood_entries` (`date`)",
        "CREATE INDEX IF NOT EXISTS `index_mood_entries_note_id` ON `mood_entries` (`note_id`)",
        "CREATE TABLE IF NOT EXISTS `challenges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`kind` TEXT NOT NULL, `title` TEXT NOT NULL, `target_days` INTEGER NOT NULL, " +
            "`start_date` INTEGER NOT NULL, `habit_id` INTEGER, `status` TEXT NOT NULL DEFAULT 'ACTIVE', " +
            "`completed_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
            "FOREIGN KEY(`habit_id`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX IF NOT EXISTS `index_challenges_habit_id` ON `challenges` (`habit_id`)",
        "CREATE INDEX IF NOT EXISTS `index_challenges_status` ON `challenges` (`status`)",
        "CREATE TABLE IF NOT EXISTS `badges` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`key` TEXT NOT NULL, `earned_at` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_badges_key` ON `badges` (`key`)",
        "CREATE TABLE IF NOT EXISTS `activity_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`entity_type` TEXT NOT NULL, `entity_id` INTEGER NOT NULL, `action` TEXT NOT NULL, " +
            "`at` INTEGER NOT NULL, `summary` TEXT NOT NULL DEFAULT '')",
        "CREATE INDEX IF NOT EXISTS `index_activity_log_entity_type_entity_id` ON `activity_log` (`entity_type`, " +
            "`entity_id`)",
        "CREATE INDEX IF NOT EXISTS `index_activity_log_at` ON `activity_log` (`at`)",
        "CREATE TABLE IF NOT EXISTS `calendar_links` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`local_type` TEXT NOT NULL, `local_id` INTEGER NOT NULL, `calendar_id` INTEGER NOT NULL, " +
            "`external_event_id` INTEGER NOT NULL, `last_synced_at` INTEGER NOT NULL, " +
            "`local_version` INTEGER, `remote_version` TEXT)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_calendar_links_local_type_local_id` ON `calendar_links` (`local_type`, " +
            "`local_id`)",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_calendar_links_calendar_id_external_event_id` ON `calendar_links` (`calendar_id`, " +
            "`external_event_id`)",
    )

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SCHEMA_3_STATEMENTS.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
