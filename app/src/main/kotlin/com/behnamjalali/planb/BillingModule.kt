package com.behnamjalali.planb

import android.content.Context
import com.behnamjalali.planb.core.billing.BazaarBillingClient
import com.behnamjalali.planb.core.billing.BillingClient
import com.behnamjalali.planb.core.billing.DeveloperBilling
import com.behnamjalali.planb.core.billing.FakeBillingClient
import com.behnamjalali.planb.core.common.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Release builds buy through Cafe Bazaar. Debug builds (and tests) use [FakeBillingClient],
 * which also powers the hidden developer toggle; release builds have no developer controls.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides
    @Singleton
    fun provideBillingClient(
        @ApplicationContext context: Context,
        bazaar: Provider<BazaarBillingClient>,
        time: TimeProvider,
    ): BillingClient = if (BuildConfig.FAKE_BILLING) FakeBillingClient.persistent(context) { time.now() } else bazaar.get()

    @Provides
    fun provideDeveloperBilling(client: BillingClient): DeveloperBilling =
        if (BuildConfig.FAKE_BILLING) client as? DeveloperBilling ?: DeveloperBilling.Disabled else DeveloperBilling.Disabled
}
