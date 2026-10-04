package com.behnamjalali.planb.feature.pro

import androidx.activity.ComponentActivity
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import com.behnamjalali.planb.core.billing.BillingAvailability
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.billing.FakeBillingClient
import com.behnamjalali.planb.core.billing.ProProduct
import com.behnamjalali.planb.core.billing.PurchaseResult
import com.behnamjalali.planb.core.common.NumberFormatter
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.behnamjalali.planb.core.testing.RealMainDispatcherRule
import com.behnamjalali.planb.core.testing.awaitItem
import com.behnamjalali.planb.core.ui.ProFeature
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaywallViewModelTest {
    @get:Rule val main = RealMainDispatcherRule()

    private val time = FakeTimeProvider()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("pro").toFile()
    private val billing = FakeBillingClient(clock = { time.now() })
    private val entitlements = EntitlementRepository(
        billing,
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "e.preferences_pb") },
        time,
        scope,
    )

    @After
    fun tearDown() {
        main.clearViewModels()
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun viewModel(featureId: String? = null) =
        main.track(PaywallViewModel(entitlements, SavedStateHandle(mapOf("featureId" to featureId))))

    private fun activity() = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    @Test
    fun opensFocusedOnTheTappedFeature_andConnects() = runBlocking<Unit> {
        val vm = viewModel(ProFeature.JOURNAL.id)
        val state = vm.state.awaitItem { it.availability != null }
        assertThat(state.focus).isEqualTo(ProFeature.JOURNAL)
        assertThat(state.availability).isEqualTo(BillingAvailability.READY)
        assertThat(state.selected).isEqualTo(ProProduct.LIFETIME)
        assertThat(state.canBuy).isTrue()
    }

    @Test
    fun purchase_unlocksPro_andShowsThanks() = runBlocking<Unit> {
        val vm = viewModel()
        vm.state.awaitItem { it.canBuy }
        vm.select(ProProduct.MONTHLY)
        vm.purchase(activity())
        val state = vm.state.awaitItem { it.entitlement.isPro }
        assertThat(state.entitlement.product).isEqualTo(ProProduct.MONTHLY)
        assertThat(vm.state.awaitItem { it.justPurchased != null }.justPurchased).isEqualTo(ProProduct.MONTHLY)
        assertThat(state.canBuy).isFalse()
    }

    @Test
    fun cancelledPurchase_andRestoreMessages() = runBlocking<Unit> {
        val vm = viewModel()
        vm.state.awaitItem { it.canBuy }
        billing.nextPurchaseResult = PurchaseResult.Cancelled
        vm.purchase(activity())
        assertThat(vm.state.awaitItem { it.message != null }.message).isEqualTo(PaywallMessage.CANCELLED)
        vm.messageShown()
        vm.restore()
        assertThat(vm.state.awaitItem { it.message != null }.message).isEqualTo(PaywallMessage.NOTHING_TO_RESTORE)
        billing.setOwned(ProProduct.LIFETIME)
        vm.restore()
        assertThat(vm.state.awaitItem { it.message == PaywallMessage.RESTORED }.entitlement.isPro).isTrue()
    }

    @Test
    fun storeStates_blockBuying() = runBlocking<Unit> {
        for (availability in listOf(BillingAvailability.STORE_NOT_INSTALLED, BillingAvailability.NOT_CONFIGURED, BillingAvailability.UNAVAILABLE)) {
            billing.availability = availability
            val vm = viewModel()
            val state = vm.state.awaitItem { it.availability != null }
            assertThat(state.availability).isEqualTo(availability)
            assertThat(state.canBuy).isFalse()
        }
    }

    @Test
    fun fallbackPrices_followTheDigitSetting() {
        assertThat(groupedAmount(FallbackPrices.MONTHLY_TOMAN, NumberFormatter(persianDigits = true))).isEqualTo("۳۹۹٬۰۰۰")
        assertThat(groupedAmount(FallbackPrices.LIFETIME_TOMAN, NumberFormatter(persianDigits = false))).isEqualTo("1,999,000")
    }
}
