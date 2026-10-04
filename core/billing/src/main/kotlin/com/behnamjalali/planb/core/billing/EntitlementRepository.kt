package com.behnamjalali.planb.core.billing

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.behnamjalali.planb.core.common.ApplicationScope
import com.behnamjalali.planb.core.common.TimeProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The device-only file that caches the entitlement (never part of a backup or export). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class EntitlementPreferences

/** Last verified entitlement, kept on the device so Pro works offline. */
class EntitlementStore(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val isPro = booleanPreferencesKey("is_pro")
        val product = stringPreferencesKey("product")
        val expiresAt = longPreferencesKey("expires_at")
        val lastVerifiedAt = longPreferencesKey("last_verified_at")
    }

    val cached: Flow<Entitlement> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val product = ProProduct.fromSku(prefs[Keys.product])
            Entitlement(
                isPro = prefs[Keys.isPro] == true && product != null,
                product = product,
                expiresAt = prefs[Keys.expiresAt]?.let(Instant::ofEpochMilli),
                lastVerifiedAt = prefs[Keys.lastVerifiedAt]?.let(Instant::ofEpochMilli),
            )
        }

    suspend fun save(entitlement: Entitlement) {
        dataStore.edit { prefs ->
            prefs[Keys.isPro] = entitlement.isPro
            entitlement.product?.let { prefs[Keys.product] = it.sku } ?: prefs.remove(Keys.product)
            entitlement.expiresAt?.let { prefs[Keys.expiresAt] = it.toEpochMilli() } ?: prefs.remove(Keys.expiresAt)
            entitlement.lastVerifiedAt?.let { prefs[Keys.lastVerifiedAt] = it.toEpochMilli() } ?: prefs.remove(Keys.lastVerifiedAt)
        }
    }
}

/** Outcome of re-checking or restoring purchases with the store. */
sealed interface RestoreResult {
    data class Restored(val entitlement: Entitlement) : RestoreResult
    data object NothingToRestore : RestoreResult
    data class Failed(val error: BillingError) : RestoreResult
}

/**
 * The single source of truth for "is this user Pro?". The last store answer is cached on the
 * device, so Pro keeps working offline: lifetime forever, a monthly subscription for
 * [EntitlementPolicy.SUBSCRIPTION_GRACE] after the last successful check. Whenever the store
 * answers, its answer replaces the cache (so a cancelled subscription or a refund ends Pro).
 */
@Singleton
class EntitlementRepository @Inject constructor(
    private val billing: BillingClient,
    @EntitlementPreferences dataStore: DataStore<Preferences>,
    private val time: TimeProvider,
    @ApplicationScope scope: CoroutineScope,
) {
    private val store = EntitlementStore(dataStore)

    init {
        // Anything the store confirms (a purchase, a query, the debug toggle) is cached.
        scope.launch { billing.observeEntitlement().filterNotNull().collect { store.save(it) } }
    }

    /** The entitlement as it stands now, re-evaluated on every change of the cache. */
    val entitlement: Flow<Entitlement> = store.cached.map { EntitlementPolicy.effective(it, time.now()) }.distinctUntilChanged()

    val isPro: Flow<Boolean> = entitlement.map { it.isPro }.distinctUntilChanged()

    suspend fun current(): Entitlement = entitlement.first()

    suspend fun availability(): BillingAvailability = billing.connect()

    suspend fun products(): Map<ProProduct, ProductDetails> = billing.queryProducts()

    /**
     * Asks the store again (app start, opening the Pro screen). When the store cannot be
     * reached the cache is kept, so an offline user keeps Pro within the rules above.
     */
    suspend fun refresh(): RestoreResult = when (val result = billing.queryPurchases()) {
        is PurchasesResult.Failed -> RestoreResult.Failed(result.error)
        is PurchasesResult.Success -> {
            val entitlement = EntitlementPolicy.fromPurchases(result.purchases, time.now())
            store.save(entitlement)
            if (entitlement.isPro) RestoreResult.Restored(entitlement) else RestoreResult.NothingToRestore
        }
    }

    /** "Restore purchases": the same check, started by the user. */
    suspend fun restore(): RestoreResult = refresh()

    suspend fun purchase(activity: ComponentActivity, product: ProProduct): PurchaseResult {
        val result = billing.purchase(activity, product)
        if (result is PurchaseResult.Success) recordPurchase(result.purchase)
        return result
    }

    /** Caches a verified purchase at once; a lifetime purchase is never replaced by a monthly one. */
    internal suspend fun recordPurchase(purchase: VerifiedPurchase) {
        val cached = store.cached.first()
        val keepLifetime = cached.isPro && cached.product == ProProduct.LIFETIME
        if (!keepLifetime) store.save(EntitlementPolicy.fromPurchases(listOf(purchase), time.now()))
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BillingStorageModule {
    /** File name is internal; it is not the user preferences file, so it is never exported. */
    private const val FILE = "planb_entitlement"

    @Provides
    @Singleton
    @EntitlementPreferences
    fun provideEntitlementDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(FILE) },
    )
}
