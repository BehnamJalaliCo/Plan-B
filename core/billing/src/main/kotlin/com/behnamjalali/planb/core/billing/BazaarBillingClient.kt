package com.behnamjalali.planb.core.billing

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import com.behnamjalali.planb.core.common.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.ConnectionState
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.entity.PurchaseInfo
import ir.cafebazaar.poolakey.entity.PurchaseState
import ir.cafebazaar.poolakey.exception.BazaarNotFoundException
import ir.cafebazaar.poolakey.exception.BazaarNotSupportedException
import ir.cafebazaar.poolakey.exception.IAPNotSupportedException
import ir.cafebazaar.poolakey.exception.SubsNotSupportedException
import ir.cafebazaar.poolakey.request.PurchaseRequest
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Cafe Bazaar in-app billing through Poolakey, which talks to the installed Bazaar app (no
 * network access of its own). Every purchase is verified on the device with the Bazaar RSA
 * public key ([rsaPublicKey], from the build), by Poolakey and again by [PurchaseSignatureVerifier].
 * Nothing about purchases is logged.
 */
@Singleton
class BazaarBillingClient internal constructor(
    private val context: Context,
    private val time: TimeProvider,
    private val rsaPublicKey: String,
) : BillingClient {
    @Inject constructor(@ApplicationContext context: Context, time: TimeProvider) : this(context, time, BuildConfig.BAZAAR_RSA_KEY)

    private val verifier: PurchaseSignatureVerifier? = PurchaseSignatureVerifier.fromBase64(rsaPublicKey)
    private val entitlement = MutableStateFlow<Entitlement?>(null)
    private val connectLock = Mutex()
    private var payment: Payment? = null
    private var connection: Connection? = null

    override fun observeEntitlement(): StateFlow<Entitlement?> = entitlement.asStateFlow()

    override suspend fun connect(): BillingAvailability = connectLock.withLock {
        if (verifier == null) return BillingAvailability.NOT_CONFIGURED
        if (!isBazaarInstalled()) return BillingAvailability.STORE_NOT_INSTALLED
        if (connection?.getState() is ConnectionState.Connected) return BillingAvailability.READY
        withContext(Dispatchers.Main.immediate) {
            val payment = payment ?: Payment(
                context,
                PaymentConfiguration(localSecurityCheck = SecurityCheck.Enable(rsaPublicKey), shouldSupportSubscription = true),
            ).also { payment = it }
            suspendCancellableCoroutine { continuation ->
                connection = payment.connect {
                    connectionSucceed { if (continuation.isActive) continuation.resume(BillingAvailability.READY) }
                    connectionFailed { error ->
                        if (continuation.isActive) continuation.resume(availabilityFor(error))
                    }
                    disconnected { connection = null }
                }
            }
        }
    }

    override suspend fun queryProducts(): Map<ProProduct, ProductDetails> {
        if (connect() != BillingAvailability.READY) return emptyMap()
        val payment = payment ?: return emptyMap()
        val details = mutableMapOf<ProProduct, ProductDetails>()
        for (product in ProProduct.entries) {
            val found = withContext(Dispatchers.Main.immediate) {
                suspendCancellableCoroutine<ProductDetails?> { continuation ->
                    val callback: ir.cafebazaar.poolakey.callback.GetSkuDetailsCallback.() -> Unit = {
                        getSkuDetailsSucceed { list ->
                            val sku = list.firstOrNull { it.sku == product.sku }
                            if (continuation.isActive) continuation.resume(sku?.let { ProductDetails(product, it.price, it.title) })
                        }
                        getSkuDetailsFailed { if (continuation.isActive) continuation.resume(null) }
                    }
                    when (product.type) {
                        ProductType.SUBSCRIPTION -> payment.getSubscriptionSkuDetails(listOf(product.sku), callback)
                        ProductType.IN_APP -> payment.getInAppSkuDetails(listOf(product.sku), callback)
                    }
                }
            }
            found?.let { details[product] = it }
        }
        return details
    }

    override suspend fun purchase(activity: ComponentActivity, product: ProProduct): PurchaseResult {
        when (connect()) {
            BillingAvailability.READY -> Unit
            BillingAvailability.NOT_CONFIGURED -> return PurchaseResult.Failed(BillingError.NOT_CONFIGURED)
            BillingAvailability.STORE_NOT_INSTALLED -> return PurchaseResult.Failed(BillingError.STORE_NOT_INSTALLED)
            BillingAvailability.UNAVAILABLE -> return PurchaseResult.Failed(BillingError.STORE_UNAVAILABLE)
        }
        val payment = payment ?: return PurchaseResult.Failed(BillingError.STORE_UNAVAILABLE)
        val result = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<PurchaseResult> { continuation ->
                fun finish(result: PurchaseResult) { if (continuation.isActive) continuation.resume(result) }
                val request = PurchaseRequest(product.sku, "", null)
                val callback: ir.cafebazaar.poolakey.callback.PurchaseCallback.() -> Unit = {
                    purchaseSucceed { info ->
                        finish(verify(info)?.let { PurchaseResult.Success(it) } ?: PurchaseResult.Failed(BillingError.VERIFICATION_FAILED))
                    }
                    purchaseCanceled { finish(PurchaseResult.Cancelled) }
                    purchaseFailed { finish(PurchaseResult.Failed(errorFor(it))) }
                    failedToBeginFlow { finish(PurchaseResult.Failed(errorFor(it))) }
                }
                // The lifetime product is a non-consumable: it is purchased once and never consumed.
                when (product.type) {
                    ProductType.SUBSCRIPTION -> payment.subscribeProduct(activity.activityResultRegistry, request, callback)
                    ProductType.IN_APP -> payment.purchaseProduct(activity.activityResultRegistry, request, callback)
                }
            }
        }
        if (result is PurchaseResult.Success) {
            val current = entitlement.value?.takeIf { it.isPro && it.product == ProProduct.LIFETIME }
            entitlement.value = current ?: EntitlementPolicy.fromPurchases(listOf(result.purchase), time.now())
        }
        return result
    }

    override suspend fun queryPurchases(): PurchasesResult {
        when (connect()) {
            BillingAvailability.READY -> Unit
            BillingAvailability.NOT_CONFIGURED -> return PurchasesResult.Failed(BillingError.NOT_CONFIGURED)
            BillingAvailability.STORE_NOT_INSTALLED -> return PurchasesResult.Failed(BillingError.STORE_NOT_INSTALLED)
            BillingAvailability.UNAVAILABLE -> return PurchasesResult.Failed(BillingError.STORE_UNAVAILABLE)
        }
        val payment = payment ?: return PurchasesResult.Failed(BillingError.STORE_UNAVAILABLE)
        val inApp = query { payment.getPurchasedProducts(it) } ?: return PurchasesResult.Failed(BillingError.STORE_UNAVAILABLE)
        val subscriptions = query { payment.getSubscribedProducts(it) } ?: return PurchasesResult.Failed(BillingError.STORE_UNAVAILABLE)
        val purchases = (inApp + subscriptions)
            .filter { it.purchaseState == PurchaseState.PURCHASED }
            .mapNotNull(::verify)
        entitlement.value = EntitlementPolicy.fromPurchases(purchases, time.now())
        return PurchasesResult.Success(purchases)
    }

    private suspend fun query(start: (ir.cafebazaar.poolakey.callback.PurchaseQueryCallback.() -> Unit) -> Unit): List<PurchaseInfo>? =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                start {
                    querySucceed { if (continuation.isActive) continuation.resume(it) }
                    queryFailed { if (continuation.isActive) continuation.resume(null) }
                }
            }
        }

    /** Only known products of this app with a valid signature count. */
    private fun verify(info: PurchaseInfo): VerifiedPurchase? {
        val product = ProProduct.fromSku(info.productId) ?: return null
        if (info.packageName != context.packageName) return null
        if (verifier?.verify(info.originalJson, info.dataSignature) != true) return null
        return VerifiedPurchase(product, Instant.ofEpochMilli(info.purchaseTime), info.orderId)
    }

    private fun isBazaarInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(BAZAAR_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun availabilityFor(error: Throwable) = when (error) {
        is BazaarNotFoundException, is BazaarNotSupportedException -> BillingAvailability.STORE_NOT_INSTALLED
        else -> BillingAvailability.UNAVAILABLE
    }

    private fun errorFor(error: Throwable) = when (error) {
        is BazaarNotFoundException, is BazaarNotSupportedException -> BillingError.STORE_NOT_INSTALLED
        is IAPNotSupportedException, is SubsNotSupportedException -> BillingError.STORE_UNAVAILABLE
        else -> BillingError.UNKNOWN
    }

    private companion object {
        const val BAZAAR_PACKAGE = "com.farsitel.bazaar"
    }
}

/**
 * Verifies Cafe Bazaar purchase data: an RSA signature (SHA1withRSA, as Bazaar signs it) of the
 * purchase JSON, checked against the app's public key from the Bazaar developer panel.
 */
class PurchaseSignatureVerifier(private val publicKey: PublicKey) {
    fun verify(signedData: String, signatureBase64: String): Boolean = try {
        val signature = Base64.getDecoder().decode(signatureBase64)
        Signature.getInstance(ALGORITHM).run {
            initVerify(publicKey)
            update(signedData.toByteArray(Charsets.UTF_8))
            verify(signature)
        }
    } catch (e: IllegalArgumentException) {
        false
    } catch (e: java.security.GeneralSecurityException) {
        false
    }

    companion object {
        private const val ALGORITHM = "SHA1withRSA"

        /** Null when [base64Key] is empty or not an RSA public key (billing is then "not configured"). */
        fun fromBase64(base64Key: String): PurchaseSignatureVerifier? {
            if (base64Key.isBlank()) return null
            return try {
                val spec = X509EncodedKeySpec(Base64.getDecoder().decode(base64Key.trim()))
                PurchaseSignatureVerifier(KeyFactory.getInstance("RSA").generatePublic(spec))
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: java.security.GeneralSecurityException) {
                null
            }
        }
    }
}
