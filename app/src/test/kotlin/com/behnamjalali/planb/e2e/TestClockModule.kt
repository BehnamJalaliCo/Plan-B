package com.behnamjalali.planb.e2e

import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.CommonModule
import com.behnamjalali.planb.core.common.Dispatcher
import com.behnamjalali.planb.core.common.PlanBDispatcher
import com.behnamjalali.planb.core.common.TimeProvider
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Instant
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Freezes the app clock (10:00 Tehran time, 4 Oct 2026 = 12 Mehr 1405) so end-to-end
 * flows and full-app screenshots are deterministic on every machine and every day.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CommonModule::class])
object TestClockModule {
    val START: Instant = Instant.parse("2026-10-04T06:30:00Z")

    @Provides
    @Singleton
    fun provideClock(): FakeTimeProvider = FakeTimeProvider(START)

    @Provides
    fun provideTimeProvider(clock: FakeTimeProvider): TimeProvider = clock

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(@Dispatcher(PlanBDispatcher.Default) dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)
}
