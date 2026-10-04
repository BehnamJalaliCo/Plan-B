package com.behnamjalali.planb.e2e

import android.content.Context
import androidx.room.Room
import com.behnamjalali.planb.core.database.DatabaseModule
import com.behnamjalali.planb.core.database.PlanBDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** In-memory database for end-to-end tests; DAOs are provided exactly as in production. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlanBDatabase =
        Room.inMemoryDatabaseBuilder(context, PlanBDatabase::class.java).build()

    @Provides fun taskDao(db: PlanBDatabase) = db.taskDao()
    @Provides fun tagDao(db: PlanBDatabase) = db.tagDao()
    @Provides fun projectDao(db: PlanBDatabase) = db.projectDao()
    @Provides fun noteDao(db: PlanBDatabase) = db.noteDao()
    @Provides fun habitDao(db: PlanBDatabase) = db.habitDao()
    @Provides fun goalDao(db: PlanBDatabase) = db.goalDao()
    @Provides fun eventDao(db: PlanBDatabase) = db.eventDao()
    @Provides fun focusDao(db: PlanBDatabase) = db.focusDao()
    @Provides fun templateDao(db: PlanBDatabase) = db.templateDao()
    @Provides fun searchDao(db: PlanBDatabase) = db.searchDao()
    @Provides fun backupDao(db: PlanBDatabase) = db.backupDao()
    @Provides fun noteDraftDao(db: PlanBDatabase) = db.noteDraftDao()
}
