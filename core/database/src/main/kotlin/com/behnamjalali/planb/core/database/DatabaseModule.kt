package com.behnamjalali.planb.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlanBDatabase =
        Room.databaseBuilder(context, PlanBDatabase::class.java, PlanBDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            // No destructive fallback: an unknown schema must fail loudly rather than erase user data.
            .build()

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
