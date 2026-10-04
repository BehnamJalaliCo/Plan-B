package com.behnamjalali.planb.core.billing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.behnamjalali.planb.core.testing.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test

class EntitlementPolicyTest {
    private val time = FakeTimeProvider()
    private val now get() = time.now()

    @Test
    fun noPurchases_isNotPro() {
        val e = EntitlementPolicy.fromPurchases(emptyList(), now)
        assertThat(e.isPro).isFalse()
        assertThat(e.lastVerifiedAt).isEqualTo(now)
    }

    @Test
    fun lifetimeWinsOverMonthly() {
        val e = EntitlementPolicy.fromPurchases(listOf(VerifiedPurchase(ProProduct.MONTHLY, now), VerifiedPurchase(ProProduct.LIFETIME, now)), now)
        assertThat(e.product).isEqualTo(ProProduct.LIFETIME)
        assertThat(e.isPro).isTrue()
    }

    @Test
    fun lifetime_neverExpiresOffline() {
        val cached = Entitlement(isPro = true, product = ProProduct.LIFETIME, lastVerifiedAt = now)
        assertThat(EntitlementPolicy.effective(cached, now.plus(Duration.ofDays(3650))).isPro).isTrue()
    }

    @Test
    fun monthly_keepsProForSevenDaysAfterTheLastCheck() {
        val cached = Entitlement(isPro = true, product = ProProduct.MONTHLY, lastVerifiedAt = now)
        assertThat(EntitlementPolicy.effective(cached, now.plus(Duration.ofDays(6))).isPro).isTrue()
        assertThat(EntitlementPolicy.effective(cached, now.plus(Duration.ofDays(7))).isPro).isTrue()
        assertThat(EntitlementPolicy.effective(cached, now.plus(Duration.ofDays(7)).plusSeconds(1)).isPro).isFalse()
    }

    @Test
    fun monthly_knownExpiryLaterThanGrace_isHonoured() {
        val cached = Entitlement(isPro = true, product = ProProduct.MONTHLY, expiresAt = now.plus(Duration.ofDays(20)), lastVerifiedAt = now)
        assertThat(EntitlementPolicy.effective(cached, now.plus(Duration.ofDays(19))).isPro).isTrue()
        assertThat(EntitlementPolicy.effective(cached, now.plus(Duration.ofDays(21))).isPro).isFalse()
    }

    @Test
    fun monthly_withoutVerificationTime_orFromTheFuture_isNotPro() {
        val unverified = Entitlement(isPro = true, product = ProProduct.MONTHLY)
        assertThat(EntitlementPolicy.effective(unverified, now).isPro).isFalse()
        // The clock was moved back by a month after the last check: no endless grace.
        val future = Entitlement(isPro = true, product = ProProduct.MONTHLY, lastVerifiedAt = now.plus(Duration.ofDays(30)))
        assertThat(EntitlementPolicy.effective(future, now).isPro).isFalse()
        // Small clock differences are tolerated.
        val slightlyAhead = Entitlement(isPro = true, product = ProProduct.MONTHLY, lastVerifiedAt = now.plus(Duration.ofHours(3)))
        assertThat(EntitlementPolicy.effective(slightlyAhead, now).isPro).isTrue()
    }

    @Test
    fun skus_matchTheBazaarPanel() {
        assertThat(ProProduct.MONTHLY.sku).isEqualTo("planb_pro_monthly")
        assertThat(ProProduct.LIFETIME.sku).isEqualTo("planb_pro_lifetime")
        assertThat(ProProduct.MONTHLY.type).isEqualTo(ProductType.SUBSCRIPTION)
        assertThat(ProProduct.LIFETIME.type).isEqualTo(ProductType.IN_APP)
        assertThat(ProProduct.fromSku("unknown")).isNull()
    }
}

class PurchaseSignatureVerifierTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)

    private fun sign(data: String): String = Signature.getInstance("SHA1withRSA").run {
        initSign(keys.private)
        update(data.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }

    @Test
    fun validSignature_isAccepted_andTamperedDataRejected() {
        val verifier = PurchaseSignatureVerifier.fromBase64(publicKey)!!
        val json = """{"orderId":"1","productId":"planb_pro_lifetime","purchaseState":0}"""
        val signature = sign(json)
        assertThat(verifier.verify(json, signature)).isTrue()
        assertThat(verifier.verify(json.replace("lifetime", "monthly"), signature)).isFalse()
        assertThat(verifier.verify(json, "not base64!")).isFalse()
        assertThat(verifier.verify(json, "")).isFalse()
    }

    @Test
    fun missingOrBrokenKey_meansNotConfigured() {
        assertThat(PurchaseSignatureVerifier.fromBase64("")).isNull()
        assertThat(PurchaseSignatureVerifier.fromBase64("   ")).isNull()
        assertThat(PurchaseSignatureVerifier.fromBase64("AAAA")).isNull()
    }
}

class EntitlementRepositoryTest {
    private val time = FakeTimeProvider()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dir: File = Files.createTempDirectory("entitlement").toFile()
    private val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(dir, "e.preferences_pb") }
    private val billing = FakeBillingClient(clock = { time.now() })
    private val repository = EntitlementRepository(billing, dataStore, time, scope)

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test
    fun freshInstall_isNotPro() = runBlocking {
        assertThat(repository.current().isPro).isFalse()
    }

    @Test
    fun purchase_grantsPro_andSurvivesOffline() = runBlocking {
        assertThat(billing.purchase(ProProduct.MONTHLY)).isInstanceOf(PurchaseResult.Success::class.java)
        awaitPro(true)
        assertThat(repository.current().product).isEqualTo(ProProduct.MONTHLY)

        // Bazaar becomes unreachable: Pro stays for the grace period, then ends.
        billing.availability = BillingAvailability.UNAVAILABLE
        assertThat(repository.refresh()).isEqualTo(RestoreResult.Failed(BillingError.STORE_UNAVAILABLE))
        time.advance(Duration.ofDays(6))
        assertThat(repository.current().isPro).isTrue()
        time.advance(Duration.ofDays(2))
        assertThat(repository.current().isPro).isFalse()

        // Back online with an active subscription: re-verified, Pro again.
        billing.availability = BillingAvailability.READY
        assertThat(repository.refresh()).isInstanceOf(RestoreResult.Restored::class.java)
        assertThat(repository.current().isPro).isTrue()
    }

    @Test
    fun lifetime_staysProOfflineForever_andIsNotDowngradedByMonthly() = runBlocking {
        billing.purchase(ProProduct.LIFETIME)
        awaitPro(true)
        repository.recordPurchase(VerifiedPurchase(ProProduct.MONTHLY, time.now()))
        assertThat(repository.current().product).isEqualTo(ProProduct.LIFETIME)
        billing.availability = BillingAvailability.STORE_NOT_INSTALLED
        time.advance(Duration.ofDays(400))
        repository.refresh()
        assertThat(repository.current().isPro).isTrue()
    }

    @Test
    fun restore_findsEarlierPurchase_orReportsNothing() = runBlocking {
        assertThat(repository.restore()).isEqualTo(RestoreResult.NothingToRestore)
        // Bought on another device with the same Bazaar account.
        val other = FakeBillingClient(clock = { time.now() }, initiallyOwned = setOf(ProProduct.LIFETIME))
        val restoring = EntitlementRepository(other, dataStore, time, scope)
        val result = restoring.restore()
        assertThat(result).isInstanceOf(RestoreResult.Restored::class.java)
        assertThat((result as RestoreResult.Restored).entitlement.product).isEqualTo(ProProduct.LIFETIME)
        assertThat(restoring.current().isPro).isTrue()
    }

    @Test
    fun storeSaysNotOwnedAnymore_endsPro() = runBlocking {
        billing.purchase(ProProduct.MONTHLY)
        awaitPro(true)
        billing.setOwned(null) // cancelled subscription (or refund)
        assertThat(repository.refresh()).isEqualTo(RestoreResult.NothingToRestore)
        assertThat(repository.current().isPro).isFalse()
    }

    @Test
    fun failedOrCancelledPurchase_changesNothing() = runBlocking {
        billing.nextPurchaseResult = PurchaseResult.Cancelled
        assertThat(billing.purchase(ProProduct.LIFETIME)).isEqualTo(PurchaseResult.Cancelled)
        billing.availability = BillingAvailability.NOT_CONFIGURED
        assertThat(billing.purchase(ProProduct.LIFETIME)).isEqualTo(PurchaseResult.Failed(BillingError.NOT_CONFIGURED))
        assertThat(repository.current().isPro).isFalse()
    }

    private suspend fun awaitPro(expected: Boolean) = withTimeout(5_000) {
        while (repository.entitlement.first().isPro != expected) delay(10)
    }
}
