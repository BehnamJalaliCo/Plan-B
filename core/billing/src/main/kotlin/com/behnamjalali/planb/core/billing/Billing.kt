package com.behnamjalali.planb.core.billing

import androidx.activity.ComponentActivity
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** How a product is sold in Cafe Bazaar. */
enum class ProductType { SUBSCRIPTION, IN_APP }

/**
 * The two Plan-B Pro products. [sku] must match the product id in the Cafe Bazaar developer
 * panel. The lifetime product is a non-consumable: it is never consumed.
 */
enum class ProProduct(val sku: String, val type: ProductType) {
    MONTHLY("planb_pro_monthly", ProductType.SUBSCRIPTION),
    LIFETIME("planb_pro_lifetime", ProductType.IN_APP),
    ;

    companion object {
        fun fromSku(sku: String?): ProProduct? = entries.firstOrNull { it.sku == sku }
    }
}

/**
 * Whether the user has Plan-B Pro. [product] is the product that grants it, [expiresAt] the
 * end of the paid period when the store tells it (Cafe Bazaar does not, so it is usually
 * null), and [lastVerifiedAt] the last time the store confirmed the purchase.
 */
data class Entitlement(
    val isPro: Boolean,
    val product: ProProduct? = null,
    val expiresAt: Instant? = null,
    val lastVerifiedAt: Instant? = null,
) {
    companion object {
        val NONE = Entitlement(isPro = false)
    }
}

/** Store details of a product. [price] is the store's localized price text, if it gave one. */
data class ProductDetails(val product: ProProduct, val price: String?, val title: String? = null)

/** A purchase whose signature was verified on the device. */
data class VerifiedPurchase(
    val product: ProProduct,
    val purchaseTime: Instant,
    val orderId: String? = null,
)

/** Whether purchases can be made right now. */
enum class BillingAvailability {
    READY,

    /** The Cafe Bazaar app is not installed (or too old). */
    STORE_NOT_INSTALLED,

    /** This build has no Cafe Bazaar key (open-source builds). */
    NOT_CONFIGURED,

    /** Cafe Bazaar could not be reached right now. */
    UNAVAILABLE,
}

enum class BillingError { STORE_NOT_INSTALLED, NOT_CONFIGURED, STORE_UNAVAILABLE, VERIFICATION_FAILED, UNKNOWN }

sealed interface PurchaseResult {
    data class Success(val purchase: VerifiedPurchase) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val error: BillingError) : PurchaseResult
}

sealed interface PurchasesResult {
    /** The store answered; [purchases] are the user's active, verified Pro purchases. */
    data class Success(val purchases: List<VerifiedPurchase>) : PurchasesResult
    data class Failed(val error: BillingError) : PurchasesResult
}

/**
 * The store connection. Implementations: [BazaarBillingClient] (Cafe Bazaar, release builds)
 * and [FakeBillingClient] (debug builds and tests).
 */
interface BillingClient {
    suspend fun connect(): BillingAvailability

    /** Store prices; empty when the store cannot be asked (the UI then shows its own prices). */
    suspend fun queryProducts(): Map<ProProduct, ProductDetails>

    /** Starts the store's purchase flow from [activity] and waits for its result. */
    suspend fun purchase(activity: ComponentActivity, product: ProProduct): PurchaseResult

    /** The user's current purchases (also used for "Restore purchases"). */
    suspend fun queryPurchases(): PurchasesResult

    /**
     * The entitlement the store confirmed most recently in this process (null until the
     * first answer). [EntitlementRepository] caches it so Pro also works offline.
     */
    fun observeEntitlement(): StateFlow<Entitlement?>
}

/** Debug-only controls (a hidden developer section); disabled in release builds. */
interface DeveloperBilling {
    val enabled: Boolean

    /** Pretends the user owns [product] (null: owns nothing). */
    fun setOwned(product: ProProduct?)

    object Disabled : DeveloperBilling {
        override val enabled = false
        override fun setOwned(product: ProProduct?) = Unit
    }
}

/** Pure rules for turning purchases and cached state into an entitlement (unit-tested). */
object EntitlementPolicy {
    /** How long a monthly subscription stays active without reaching Cafe Bazaar. */
    val SUBSCRIPTION_GRACE: Duration = Duration.ofDays(7)

    /** Tolerated clock difference before a verification time counts as "in the future". */
    val CLOCK_TOLERANCE: Duration = Duration.ofDays(1)

    /** The entitlement a successful store answer grants; lifetime wins over monthly. */
    fun fromPurchases(purchases: List<VerifiedPurchase>, now: Instant): Entitlement {
        val product = when {
            purchases.any { it.product == ProProduct.LIFETIME } -> ProProduct.LIFETIME
            purchases.any { it.product == ProProduct.MONTHLY } -> ProProduct.MONTHLY
            else -> null
        }
        return Entitlement(isPro = product != null, product = product, lastVerifiedAt = now)
    }

    /**
     * What a cached entitlement is worth at [now] while the store cannot be asked: lifetime
     * never expires; a monthly subscription stays active until its known expiry or for
     * [SUBSCRIPTION_GRACE] after the last verification, whichever is later. A verification
     * time far in the future (the clock was moved back) does not extend it.
     */
    fun effective(cached: Entitlement, now: Instant): Entitlement {
        if (!cached.isPro) return cached
        return when (cached.product) {
            ProProduct.LIFETIME -> cached
            ProProduct.MONTHLY -> {
                val verified = cached.lastVerifiedAt ?: return cached.copy(isPro = false)
                if (verified > now.plus(CLOCK_TOLERANCE)) return cached.copy(isPro = false)
                val graceEnd = verified.plus(SUBSCRIPTION_GRACE)
                val until = cached.expiresAt?.let { maxOf(it, graceEnd) } ?: graceEnd
                if (now <= until) cached else cached.copy(isPro = false)
            }
            null -> cached.copy(isPro = false)
        }
    }
}
