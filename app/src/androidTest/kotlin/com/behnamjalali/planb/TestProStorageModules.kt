package com.behnamjalali.planb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.behnamjalali.planb.core.ai.AiPreferences
import com.behnamjalali.planb.core.ai.AiStorageModule
import com.behnamjalali.planb.core.billing.BillingStorageModule
import com.behnamjalali.planb.core.billing.EntitlementPreferences
import com.behnamjalali.planb.core.common.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

/** Like [TestDataStoreModule]: a fresh Pro entitlement file for every Hilt graph on a device. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [BillingStorageModule::class])
object TestBillingStorageModule {
    @Provides
    @Singleton
    @EntitlementPreferences
    fun provideEntitlementDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = freshDataStore(context, scope, "test-entitlement")
}

/** Like [TestDataStoreModule]: a fresh AI settings file for every Hilt graph on a device. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AiStorageModule::class])
object TestAiStorageModule {
    @Provides
    @Singleton
    @AiPreferences
    fun provideAiDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = freshDataStore(context, scope, "test-ai")
}

private fun freshDataStore(context: Context, scope: CoroutineScope, prefix: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope) {
        File(context.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb").apply { deleteOnExit() }
    }
