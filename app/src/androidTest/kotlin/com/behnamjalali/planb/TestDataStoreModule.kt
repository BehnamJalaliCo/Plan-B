package com.behnamjalali.planb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.datastore.DataStoreModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/**
 * Hilt builds a new singleton graph for every test on a device, while the app's files persist
 * between tests. DataStore allows only one active instance per file, so each graph gets its
 * own fresh preferences file.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TestDataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
        File(context.cacheDir, "test-prefs-${UUID.randomUUID()}.preferences_pb").apply { deleteOnExit() }
    }
}
