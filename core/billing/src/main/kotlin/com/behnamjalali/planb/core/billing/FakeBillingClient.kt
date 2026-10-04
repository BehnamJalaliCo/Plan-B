package com.behnamjalali.planb.core.billing

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A store that always answers from memory, for debug builds and tests. Debug builds keep the
 * owned product in a private preferences file ([persistent]) so the developer toggle survives a
 * restart. Prices are not known, so the UI shows its own price texts.
 */
class FakeBillingClient(
    private val clock: () -> Instant,
    initiallyOwned: Set<ProProduct> = emptySet(),
    private val persist: (Set<ProProduct>) -> Unit = {},
) : BillingClient, DeveloperBilling {
    /** What [connect] reports; tests set it to simulate a missing or unreachable store. */
    @Volatile var availability: BillingAvailability = BillingAvailability.READY

    /** When set, the next purchase ends with this result instead of succeeding. */
    @Volatile var nextPurchaseResult: PurchaseResult? = null

    private val owned = MutableStateFlow(initiallyOwned)
    private val entitlement = MutableStateFlow<Entitlement?>(null)

    override val enabled: Boolean = true

    override fun observeEntitlement(): StateFlow<Entitlement?> = entitlement.asStateFlow()

    override suspend fun connect(): BillingAvailability = availability

    override suspend fun queryProducts(): Map<ProProduct, ProductDetails> =
        if (availability == BillingAvailability.READY) ProProduct.entries.associateWith { ProductDetails(it, price = null) } else emptyMap()

    override suspend fun purchase(activity: ComponentActivity, product: ProProduct): PurchaseResult = purchase(product)

    /** The purchase flow without an activity (tests). */
    fun purchase(product: ProProduct): PurchaseResult {
        failure()?.let { return PurchaseResult.Failed(it) }
        nextPurchaseResult?.let {
            nextPurchaseResult = null
            return it
        }
        update(owned.value + product)
        return PurchaseResult.Success(VerifiedPurchase(product, clock(), orderId = "fake-${product.sku}"))
    }

    override suspend fun queryPurchases(): PurchasesResult {
        failure()?.let { return PurchasesResult.Failed(it) }
        val purchases = owned.value.map { VerifiedPurchase(it, clock()) }
        entitlement.value = EntitlementPolicy.fromPurchases(purchases, clock())
        return PurchasesResult.Success(purchases)
    }

    override fun setOwned(product: ProProduct?) = update(setOfNotNull(product))

    private fun update(products: Set<ProProduct>) {
        owned.value = products
        persist(products)
        entitlement.value = EntitlementPolicy.fromPurchases(products.map { VerifiedPurchase(it, clock()) }, clock())
    }

    private fun failure(): BillingError? = when (availability) {
        BillingAvailability.READY -> null
        BillingAvailability.STORE_NOT_INSTALLED -> BillingError.STORE_NOT_INSTALLED
        BillingAvailability.NOT_CONFIGURED -> BillingError.NOT_CONFIGURED
        BillingAvailability.UNAVAILABLE -> BillingError.STORE_UNAVAILABLE
    }

    companion object {
        private const val FILE = "planb_fake_billing"
        private const val KEY = "owned"

        /** A fake store whose state is kept in a private preferences file (debug builds). */
        fun persistent(context: Context, clock: () -> Instant): FakeBillingClient {
            val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val owned = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull(ProProduct::fromSku).toSet()
            return FakeBillingClient(clock, owned) { products -> prefs.edit { putStringSet(KEY, products.map { it.sku }.toSet()) } }
        }
    }
}
