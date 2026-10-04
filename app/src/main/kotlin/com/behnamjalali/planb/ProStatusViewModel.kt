package com.behnamjalali.planb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.billing.EntitlementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Whether the user has Plan-B Pro, for [com.behnamjalali.planb.core.ui.LocalProAccess]. */
@HiltViewModel
class ProStatusViewModel @Inject constructor(entitlements: EntitlementRepository) : ViewModel() {
    val isPro: StateFlow<Boolean> = entitlements.isPro.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
