package com.behnamjalali.planb.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Validates every migration against the committed schema JSON files.
 * Runs on the JVM under Robolectric against the exported schema files.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PlanBDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesDataAndAddsDrafts() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO notebooks (id, title, icon, color, sort_order, created_at, updated_at, archived) " +
                    "VALUES (1, 'دفتر', 'book', 'lavender', 0, 0, 0, 0)",
            )
            execSQL(
                "INSERT INTO notes (id, notebook_id, section_id, title, content, content_format, pinned, favorite, sort_order, " +
                    "created_at, updated_at, archived) VALUES (7, 1, NULL, 'یادداشت', '{}', 'blocks-v1', 0, 0, 0, 0, 0, 0)",
            )
            execSQL(
                "INSERT INTO tasks (id, title, description, status, completed, priority, sort_order, created_at, updated_at, " +
                    "archived, notes) VALUES (3, 'Task', '', 'TODO', 0, 0, 0, 0, 0, 0, '')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2)
        db.query("SELECT title FROM notes WHERE id = 7").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("یادداشت")
        }
        db.execSQL("PRAGMA foreign_keys = ON") // Room enables this for app connections
        db.execSQL("INSERT INTO note_drafts (note_id, title, content, updated_at) VALUES (7, 't', 'c', 1)")
        db.execSQL("DELETE FROM notes WHERE id = 7")
        db.query("SELECT COUNT(*) FROM note_drafts").use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(0) // cascade
        }
    }

    @Test
    fun migrate2To3_preservesDataAndAddsProSchema() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO notebooks (id, title, icon, color, sort_order, created_at, updated_at, archived) " +
                    "VALUES (1, 'دفتر', 'book', 'lavender', 0, 0, 0, 0)",
            )
            execSQL(
                "INSERT INTO notes (id, notebook_id, section_id, title, content, content_format, pinned, favorite, sort_order, " +
                    "created_at, updated_at, archived) VALUES (7, 1, NULL, 'یادداشت', '{\"version\":1}', 'blocks-v1', 1, 0, 0, 5, 6, 0)",
            )
            execSQL("INSERT INTO note_drafts (note_id, title, content, updated_at) VALUES (7, 'پیش‌نویس', '{}', 9)")
            execSQL(
                "INSERT INTO tasks (id, title, description, status, completed, priority, due_date, reminder_offset_minutes, " +
                    "recurrence, sort_order, created_at, updated_at, archived, notes) " +
                    "VALUES (3, 'گزارش', '', 'TODO', 0, 3, 20000, 15, 'FREQ=WEEKLY;INTERVAL=1;CAL=JALALI', 0, 0, 0, 0, '')",
            )
            execSQL(
                "INSERT INTO tasks (id, title, description, status, completed, priority, sort_order, created_at, updated_at, " +
                    "archived, notes) VALUES (4, 'Other', '', 'TODO', 0, 0, 1, 0, 0, 0, '')",
            )
            execSQL(
                "INSERT INTO habits (id, title, icon, color, schedule, target, unit, start_date, created_at, updated_at, archived) " +
                    "VALUES (2, 'آب', 'water', 'mint', 'DAILY', 8, 'لیوان', 20000, 0, 0, 0)",
            )
            execSQL("INSERT INTO habit_completions (id, habit_id, date, amount, created_at) VALUES (1, 2, 20000, 3, 0)")
            execSQL(
                "INSERT INTO focus_sessions (id, linked_task_id, started_at, ended_at, planned_duration_ms, actual_duration_ms, " +
                    "status, running_since, accumulated_ms) VALUES (1, 3, 100, 200, 1500000, 1500000, 'COMPLETED', NULL, 0)",
            )
            execSQL("INSERT INTO search_index (rowid, entity_type, entity_id, content) VALUES (49, 1, 3, 'گزارش')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3)

        // Existing rows survive unchanged, and new columns take their neutral defaults.
        db.query("SELECT title, priority, due_date, reminder_offset_minutes, recurrence, deadline, scheduled_start, nag, deleted_at FROM tasks WHERE id = 3").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("گزارش")
            assertThat(it.getInt(1)).isEqualTo(3)
            assertThat(it.getLong(2)).isEqualTo(20000)
            assertThat(it.getInt(3)).isEqualTo(15)
            assertThat(it.getString(4)).isEqualTo("FREQ=WEEKLY;INTERVAL=1;CAL=JALALI")
            assertThat(it.isNull(5)).isTrue()
            assertThat(it.isNull(6)).isTrue()
            assertThat(it.getInt(7)).isEqualTo(0)
            assertThat(it.isNull(8)).isTrue()
        }
        db.query("SELECT title, pinned, deleted_at, locked, encrypted_payload FROM notes WHERE id = 7").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("یادداشت")
            assertThat(it.getInt(1)).isEqualTo(1)
            assertThat(it.isNull(2)).isTrue()
            assertThat(it.getInt(3)).isEqualTo(0)
            assertThat(it.isNull(4)).isTrue()
        }
        db.query("SELECT title FROM note_drafts WHERE note_id = 7").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("پیش‌نویس")
        }
        db.query("SELECT amount, (SELECT health_metric FROM habits WHERE id = 2) FROM habit_completions WHERE id = 1").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getInt(0)).isEqualTo(3)
            assertThat(it.isNull(1)).isTrue()
        }
        db.query("SELECT strict, sound_id, linked_task_id FROM focus_sessions WHERE id = 1").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getInt(0)).isEqualTo(0)
            assertThat(it.isNull(1)).isTrue()
            assertThat(it.getLong(2)).isEqualTo(3)
        }
        db.query("SELECT entity_id FROM search_index WHERE search_index MATCH 'گزار*'").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getLong(0)).isEqualTo(3)
        }

        // The new tables work and their foreign keys cascade or set null as designed.
        db.execSQL("PRAGMA foreign_keys = ON") // Room enables this for app connections
        db.execSQL("INSERT INTO task_reminders (task_id, kind, offset_minutes) VALUES (3, 'OFFSET', 60)")
        db.execSQL("INSERT INTO task_dependencies (task_id, depends_on_task_id) VALUES (4, 3)")
        db.execSQL("INSERT INTO note_versions (note_id, created_at, title, content, size) VALUES (7, 1, 't', '{}', 2)")
        db.execSQL("INSERT INTO notes (id, notebook_id, title, content, content_format, pinned, favorite, sort_order, created_at, updated_at, archived) VALUES (8, 1, 'b', '{}', 'blocks-v1', 0, 0, 0, 0, 0, 0)")
        db.execSQL("INSERT INTO note_links (from_note_id, to_note_id) VALUES (8, 7)")
        db.execSQL("INSERT INTO journal_entries (date, note_id, created_at, updated_at) VALUES (20000, 7, 0, 0)")
        db.execSQL("INSERT INTO mood_entries (date, mood, energy, note_id, created_at, updated_at) VALUES (20000, 4, 3, 7, 0, 0)")
        db.execSQL("INSERT INTO challenges (kind, title, target_days, start_date, habit_id, created_at, updated_at) VALUES ('HABIT_STREAK', 'c', 21, 20000, 2, 0, 0)")
        db.execSQL("INSERT INTO attachments (owner_type, owner_id, kind, file_name, mime_type, size_bytes, created_at) VALUES ('NOTE', 7, 'IMAGE', 'a.jpg', 'image/jpeg', 10, 0)")
        db.execSQL("INSERT INTO badges (key, earned_at) VALUES ('first_week', 0)")
        db.execSQL("INSERT INTO activity_log (entity_type, entity_id, action, at) VALUES ('TASK', 3, 'CREATED', 0)")
        db.execSQL("INSERT INTO calendar_links (local_type, local_id, calendar_id, external_event_id, last_synced_at) VALUES ('TASK', 3, 1, 99, 0)")
        db.execSQL("INSERT INTO saved_filters (name, icon, color, query, sort_order, created_at, updated_at) VALUES ('f', 'star', 'mint', '{}', 0, 0, 0)")
        // The defaults declared for new NOT NULL columns apply to inserts that omit them.
        db.query("SELECT status FROM challenges").use { it.moveToFirst(); assertThat(it.getString(0)).isEqualTo("ACTIVE") }
        db.query("SELECT tags FROM mood_entries").use { it.moveToFirst(); assertThat(it.getString(0)).isEmpty() }

        db.execSQL("DELETE FROM tasks WHERE id = 3")
        assertThat(db.count("task_reminders")).isEqualTo(0)
        assertThat(db.count("task_dependencies")).isEqualTo(0)
        db.execSQL("DELETE FROM habits WHERE id = 2")
        db.query("SELECT habit_id FROM challenges").use { it.moveToFirst(); assertThat(it.isNull(0)).isTrue() }
        db.execSQL("DELETE FROM notes WHERE id = 7")
        assertThat(db.count("note_versions")).isEqualTo(0)
        assertThat(db.count("note_links")).isEqualTo(0)
        assertThat(db.count("journal_entries")).isEqualTo(0)
        db.query("SELECT note_id FROM mood_entries").use { it.moveToFirst(); assertThat(it.isNull(0)).isTrue() }
    }

    @Test
    fun migrate1To3_runsEveryStep() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO tasks (id, title, description, status, completed, priority, sort_order, created_at, updated_at, " +
                    "archived, notes) VALUES (3, 'Task', '', 'TODO', 0, 0, 0, 0, 0, 0, '')",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, *Migrations.ALL)
        db.query("SELECT title, nag FROM tasks").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("Task")
            assertThat(it.getInt(1)).isEqualTo(0)
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun openLatestThroughAllMigrations() = runBlocking {
        helper.createDatabase(dbName, 1).close()
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlanBDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
        assertThat(db.taskDao().count()).isEqualTo(0)
        db.close()
    }
}
