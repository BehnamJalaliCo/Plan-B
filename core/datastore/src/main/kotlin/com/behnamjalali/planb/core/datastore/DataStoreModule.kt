package com.behnamjalali.planb.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.behnamjalali.planb.core.common.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * Creates the preferences store. A corrupt file (e.g. a torn write) is replaced with empty
 * preferences, so the app falls back to defaults instead of failing every read and write.
 */
fun createPreferencesDataStore(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = produceFile,
    )

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    /** File name is internal and independent of the display name. */
    private const val FILE = "planb_user_preferences"

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = createPreferencesDataStore(scope) {
        context.preferencesDataStoreFile(FILE)
    }
}
