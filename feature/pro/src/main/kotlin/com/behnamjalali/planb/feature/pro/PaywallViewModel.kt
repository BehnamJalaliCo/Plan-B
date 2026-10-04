package com.behnamjalali.planb.feature.pro

import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.behnamjalali.planb.core.billing.BillingAvailability
import com.behnamjalali.planb.core.billing.BillingError
import com.behnamjalali.planb.core.billing.Entitlement
import com.behnamjalali.planb.core.billing.EntitlementRepository
import com.behnamjalali.planb.core.billing.ProProduct
import com.behnamjalali.planb.core.billing.PurchaseResult
import com.behnamjalali.planb.core.billing.RestoreResult
import com.behnamjalali.planb.core.common.runCatchingSafely
import com.behnamjalali.planb.core.ui.ProFeature
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** The Pro screen; [featureId] focuses it on the feature the user tapped ([ProFeature.id]). */
@Serializable
data class PaywallRoute(val featureId: String? = null)

/** One-off results shown on the Pro screen until dismissed. */
enum class PaywallMessage { RESTORED, NOTHING_TO_RESTORE, RESTORE_FAILED, CANCELLED, FAILED, VERIFICATION_FAILED }

data class PaywallUiState(
    val focus: ProFeature? = null,
    /** Null while connecting to the store. */
    val availability: BillingAvailability? = null,
    val entitlement: Entitlement = Entitlement.NONE,
    /** The store's localized prices, when it gave them. */
    val storePrices: Map<ProProduct, String> = emptyMap(),
    val selected: ProProduct = ProProduct.LIFETIME,
    val busy: Boolean = false,
    /** Set right after a successful purchase, for the thank-you card. */
    val justPurchased: ProProduct? = null,
    val message: PaywallMessage? = null,
) {
    val canBuy: Boolean get() = availability == BillingAvailability.READY && !busy && !entitlement.isPro
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlements: EntitlementRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val focus = runCatching { ProFeature.fromId(savedStateHandle.toRoute<PaywallRoute>().featureId) }.getOrNull()

    private val _state = MutableStateFlow(PaywallUiState(focus = focus))
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(entitlement = e) } } }
        connect()
    }

    /** Connects to the store, reads prices and re-checks purchases (silently). */
    fun connect() {
        _state.update { it.copy(availability = null) }
        viewModelScope.launch {
            val availability = runCatchingSafely { entitlements.availability() }.getOrDefault(BillingAvailability.UNAVAILABLE)
            val prices = if (availability == BillingAvailability.READY) {
                runCatchingSafely { entitlements.products() }.getOrDefault(emptyMap()).mapNotNull { (product, details) ->
                    details.price?.takeIf { it.isNotBlank() }?.let { product to it }
                }.toMap()
            } else {
                emptyMap()
            }
            _state.update { it.copy(availability = availability, storePrices = prices) }
            if (availability == BillingAvailability.READY) runCatchingSafely { entitlements.refresh() }
        }
    }

    fun select(product: ProProduct) = _state.update { it.copy(selected = product) }

    fun purchase(activity: ComponentActivity) {
        val product = _state.value.selected
        if (!_state.value.canBuy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = runCatchingSafely { entitlements.purchase(activity, product) }.getOrElse { PurchaseResult.Failed(BillingError.UNKNOWN) }
            _state.update {
                when (result) {
                    is PurchaseResult.Success -> it.copy(busy = false, justPurchased = result.purchase.product)
                    PurchaseResult.Cancelled -> it.copy(busy = false, message = PaywallMessage.CANCELLED)
                    is PurchaseResult.Failed -> it.copy(
                        busy = false,
                        message = if (result.error == BillingError.VERIFICATION_FAILED) PaywallMessage.VERIFICATION_FAILED else PaywallMessage.FAILED,
                    )
                }
            }
        }
    }

    fun restore() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = runCatchingSafely { entitlements.restore() }.getOrElse { RestoreResult.Failed(BillingError.UNKNOWN) }
            _state.update {
                it.copy(
                    busy = false,
                    message = when (result) {
                        is RestoreResult.Restored -> PaywallMessage.RESTORED
                        RestoreResult.NothingToRestore -> PaywallMessage.NOTHING_TO_RESTORE
                        is RestoreResult.Failed -> PaywallMessage.RESTORE_FAILED
                    },
                )
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}
