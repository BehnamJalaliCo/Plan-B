package com.behnamjalali.planb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.behnamjalali.planb.core.ai.AiPreferences
import com.behnamjalali.planb.core.ai.AiStorageModule
import com.behnamjalali.planb.core.backup.AutoBackupStore
import com.behnamjalali.planb.core.backup.AutoBackupStoreModule
import com.behnamjalali.planb.core.billing.BillingStorageModule
import com.behnamjalali.planb.core.billing.EntitlementPreferences
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.data.security.SecurityStore
import com.behnamjalali.planb.core.data.security.SecurityStoreModule
import com.behnamjalali.planb.core.notifications.NagStateStoreModule
import com.behnamjalali.planb.core.notifications.NagStore
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
 * Every DataStore file of the app needs a module here (see [TestDataStoreModule]); a new file
 * added without one makes the device tests fail with "multiple DataStores active".
 *
 * Like [TestDataStoreModule]: a fresh Pro entitlement file for every Hilt graph on a device. */
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

/** Like [TestDataStoreModule]: a fresh App lock / locked-notes settings file for every Hilt graph. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SecurityStoreModule::class])
object TestSecurityStoreModule {
    @Provides
    @Singleton
    @SecurityStore
    fun provideSecurityDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = freshDataStore(context, scope, "test-security")
}

/** Like [TestDataStoreModule]: a fresh automatic-backup state file for every Hilt graph. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AutoBackupStoreModule::class])
object TestAutoBackupStoreModule {
    @Provides
    @Singleton
    @AutoBackupStore
    fun provideAutoBackupDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = freshDataStore(context, scope, "test-auto-backup")
}

/** Like [TestDataStoreModule]: a fresh nagging-reminder state file (snoozes, dismissals) for every Hilt graph. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [NagStateStoreModule::class])
object TestNagStateStoreModule {
    @Provides
    @Singleton
    @NagStore
    fun provideNagDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = freshDataStore(context, scope, "test-nag-state")
}

private fun freshDataStore(context: Context, scope: CoroutineScope, prefix: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(scope = scope) {
        File(context.cacheDir, "$prefix-${UUID.randomUUID()}.preferences_pb").apply { deleteOnExit() }
    }
