package com.behnamjalali.planb.e2e

import com.behnamjalali.planb.core.calendarsync.DeviceCalendar
import com.behnamjalali.planb.core.calendarsync.DeviceCalendarStore
import com.behnamjalali.planb.core.calendarsync.DeviceCalendarStoreModule
import com.behnamjalali.planb.core.calendarsync.FakeDeviceCalendarStore
import android.content.Context
import dagger.Module
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * Robolectric has no calendar provider: JVM app tests use an in-memory one with two Google
 * calendars (Plan-B Pro #3). Sync stays off unless a test turns it on.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DeviceCalendarStoreModule::class])
object TestDeviceCalendarModule {
    @Provides
    @Singleton
    fun provideFakeStore(@ApplicationContext context: Context): FakeDeviceCalendarStore = FakeDeviceCalendarStore(
        listOf(
            DeviceCalendar(1, "Work", "sara@gmail.com", "com.google", 0xFF3F7EE8.toInt(), writable = true),
            DeviceCalendar(2, "Family", "sara@gmail.com", "com.google", 0xFF33B679.toInt(), writable = true),
            DeviceCalendar(3, "Holidays in Iran", "sara@gmail.com", "com.google", 0xFF8E24AA.toInt(), writable = false),
        ),
        ownPackage = context.packageName,
    )

    @Provides
    fun provideStore(fake: FakeDeviceCalendarStore): DeviceCalendarStore = fake
}
