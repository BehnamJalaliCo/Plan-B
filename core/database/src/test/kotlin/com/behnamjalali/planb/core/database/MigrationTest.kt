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
 * Runs on the JVM (Robolectric) and as an instrumentation test on CI devices.
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
    fun openLatestThroughAllMigrations() = runBlocking {
        helper.createDatabase(dbName, 1).close()
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlanBDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
        assertThat(db.taskDao().count()).isEqualTo(0)
        db.close()
    }
}
