package com.behnamjalali.planb.e2e

import com.behnamjalali.planb.core.data.wellbeing.HealthDataSource
import com.behnamjalali.planb.core.health.HealthModule
import com.behnamjalali.planb.core.testing.FakeHealthDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Robolectric has no Health Connect: JVM app tests use an in-memory one (Plan-B Pro #27) that
 * is available but grants nothing until a test does.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [HealthModule::class])
object TestHealthModule {
    @Provides
    @Singleton
    fun provideFakeHealth(): FakeHealthDataSource = FakeHealthDataSource()

    @Provides
    fun provideHealth(fake: FakeHealthDataSource): HealthDataSource = fake
}
