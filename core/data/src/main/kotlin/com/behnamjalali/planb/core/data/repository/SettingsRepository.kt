package com.behnamjalali.planb.core.data.repository

import com.behnamjalali.planb.core.datastore.UserPreferencesDataSource
import com.behnamjalali.planb.core.model.UserSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun current(): UserSettings
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val source: UserPreferencesDataSource,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = source.settings
    override suspend fun current() = source.current()
    override suspend fun update(transform: (UserSettings) -> UserSettings) = source.update(transform)
}
