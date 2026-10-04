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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
