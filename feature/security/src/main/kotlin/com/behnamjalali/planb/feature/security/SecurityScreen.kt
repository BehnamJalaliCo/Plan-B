package com.behnamjalali.planb.feature.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.data.security.AppLockTimeout
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.DeviceAuth
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.PassphraseSetupDialog
import com.behnamjalali.planb.core.ui.PassphraseUnlockDialog
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProTeaser
import com.behnamjalali.planb.core.ui.findFragmentActivity
import com.behnamjalali.planb.core.ui.rememberProGuard

/** Callbacks of the Security screen; the destination fills them from the ViewModel. */
data class SecurityActions(
    val onBack: () -> Unit = {},
    val onToggleLock: (Boolean) -> Unit = {},
    val onTimeout: (AppLockTimeout) -> Unit = {},
    val onHideInRecents: (Boolean) -> Unit = {},
    val onSetPassphrase: () -> Unit = {},
    val onToggleFingerprint: (Boolean) -> Unit = {},
    val onLockNotesNow: () -> Unit = {},
)

@Composable
fun SecurityDestination(onBack: () -> Unit, snackbarHostState: SnackbarHostState, viewModel: SecurityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var wrong by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val text = when (event) {
                SecurityEvent.PassphraseSet -> {
                    dialog = null
                    R.string.security_passphrase_set
                }
                SecurityEvent.WrongPassphrase -> {
                    wrong = true
                    null
                }
                SecurityEvent.NotesLocked -> R.string.security_notes_locked
                SecurityEvent.FingerprintOn -> R.string.security_fingerprint_on
                SecurityEvent.Failed -> com.behnamjalali.planb.core.ui.R.string.ui_error_generic
            }
            text?.let { snackbarHostState.showSnackbar(resources.getString(it)) }
        }
    }
    // Unlocking for the fingerprint setup finished: continue with the fingerprint prompt.
    LaunchedEffect(state.vaultUnlocked, dialog) {
        if (state.vaultUnlocked && dialog == "unlock") dialog = null
    }

    val promptTitle = stringResource(R.string.security_lock_confirm_title)
    val fingerprintTitle = stringResource(R.string.security_fingerprint_prompt)
    val cancel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel)
    val noDeviceLock = stringResource(R.string.security_no_device_lock)
    val noFingerprint = stringResource(R.string.security_no_fingerprint)

    SecurityScreen(
        state = state,
        actions = SecurityActions(
            onBack = onBack,
            onToggleLock = { enable ->
                val activity = context.findFragmentActivity()
                when {
                    !enable -> viewModel.setLockEnabled(false)
                    activity == null || !DeviceAuth.canAuthenticate(context) -> dialog = "no_lock"
                    // Confirm once that the user can pass the device lock before relying on it.
                    else -> DeviceAuth.authenticate(activity, promptTitle, null) { ok -> if (ok) viewModel.setLockEnabled(true) }
                }
            },
            onTimeout = viewModel::setTimeout,
            onHideInRecents = viewModel::setHideInRecents,
            onSetPassphrase = { dialog = "setup" },
            onToggleFingerprint = { enable ->
                if (!enable) {
                    viewModel.disableFingerprint()
                } else if (!state.vaultUnlocked) {
                    wrong = false
                    dialog = "unlock"
                } else {
                    val activity = context.findFragmentActivity()
                    val cipher = viewModel.fingerprintCipher()
                    if (activity == null || cipher == null || !DeviceAuth.canUseStrongBiometric(context)) {
                        dialog = "no_fingerprint"
                    } else {
                        DeviceAuth.authenticate(activity, fingerprintTitle, cancel, cipher) { authenticated ->
                            authenticated?.let(viewModel::enableFingerprint)
                        }
                    }
                }
            },
            onLockNotesNow = viewModel::lockNotesNow,
        ),
    )

    when (dialog) {
        "setup" -> PassphraseSetupDialog(onConfirm = viewModel::setUpPassphrase, onDismiss = { dialog = null }, busy = busy)
        "unlock" -> PassphraseUnlockDialog(
            onUnlock = {
                wrong = false
                viewModel.unlockNotes(it)
            },
            onDismiss = { dialog = null },
            wrong = wrong,
            busy = busy,
        )
        "no_lock", "no_fingerprint" -> PlannerDialog(
            title = stringResource(R.string.security_title),
            message = if (dialog == "no_lock") noDeviceLock else noFingerprint,
            onDismiss = { dialog = null },
            confirmLabel = stringResource(R.string.security_ok),
            dismissLabel = "",
            onConfirm = { dialog = null },
        )
    }
}

@Composable
fun SecurityScreen(state: SecurityUiState, actions: SecurityActions) {
    val isPro = LocalProAccess.current.isPro
    val guard = rememberProGuard()
    // Things set up with Pro stay manageable (and can be switched off) if Pro ends.
    val showControls = isPro || state.lock.enabled || state.vaultSetUp
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.security_title), onBack = actions.onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (!state.loaded) return@LazyColumn
            if (!showControls) {
                item { ProTeaser(ProFeature.APP_LOCK) }
                return@LazyColumn
            }
            item { PlannerSectionHeader(stringResource(R.string.security_app_lock)) }
            item {
                SwitchRow(
                    stringResource(R.string.security_app_lock),
                    stringResource(R.string.security_app_lock_summary),
                    Icons.Rounded.Lock,
                    state.lock.enabled,
                    pro = !isPro && !state.lock.enabled,
                ) { enable -> if (enable) guard.run(ProFeature.APP_LOCK) { actions.onToggleLock(true) } else actions.onToggleLock(false) }
            }
            if (state.lock.enabled) {
                item { TimeoutRow(state.lock.timeout, actions.onTimeout) }
                item {
                    SwitchRow(
                        stringResource(R.string.security_hide_recents),
                        stringResource(R.string.security_hide_recents_summary),
                        Icons.Rounded.VisibilityOff,
                        state.lock.hideInRecents,
                        onChange = actions.onHideInRecents,
                    )
                }
            }
            item { PlannerSectionHeader(stringResource(R.string.security_notes)) }
            item {
                Text(
                    stringResource(R.string.security_notes_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!state.vaultSetUp) {
                item {
                    SettingsRow(
                        stringResource(R.string.security_set_passphrase),
                        icon = Icons.Rounded.Key,
                        subtitle = stringResource(R.string.security_set_passphrase_summary),
                        onClick = { guard.run(ProFeature.APP_LOCK, actions.onSetPassphrase) },
                        trailing = if (isPro) null else ({ ProBadge() }),
                    )
                }
            } else {
                item {
                    SettingsRow(
                        stringResource(R.string.security_passphrase),
                        icon = Icons.Rounded.Key,
                        subtitle = stringResource(if (state.vaultUnlocked) R.string.security_notes_open else R.string.security_notes_closed),
                    )
                }
                item {
                    SwitchRow(
                        stringResource(R.string.security_fingerprint),
                        stringResource(R.string.security_fingerprint_summary),
                        Icons.Rounded.Fingerprint,
                        state.fingerprintForNotes,
                        onChange = actions.onToggleFingerprint,
                    )
                }
                if (state.vaultUnlocked) {
                    item {
                        SettingsRow(
                            stringResource(R.string.security_lock_notes_now),
                            icon = Icons.Rounded.LockOpen,
                            subtitle = stringResource(R.string.security_lock_notes_now_summary),
                            onClick = actions.onLockNotesNow,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, icon: ImageVector, checked: Boolean, pro: Boolean = false, onChange: (Boolean) -> Unit) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        trailing = { if (pro) ProBadge() else Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun TimeoutRow(selected: AppLockTimeout, onSelect: (AppLockTimeout) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    SettingsRow(
        stringResource(R.string.security_timeout),
        icon = Icons.Rounded.LockClock,
        subtitle = timeoutLabel(selected),
        onClick = { open = true },
    )
    if (open) {
        PlannerDialog(
            title = stringResource(R.string.security_timeout),
            onDismiss = { open = false },
            confirmLabel = stringResource(com.behnamjalali.planb.core.ui.R.string.ui_cancel),
            dismissLabel = "",
            onConfirm = { open = false },
        ) {
            AppLockTimeout.entries.forEach { timeout ->
                SettingsRow(
                    timeoutLabel(timeout),
                    onClick = {
                        onSelect(timeout)
                        open = false
                    },
                    trailing = { androidx.compose.material3.RadioButton(selected = timeout == selected, onClick = null) },
                )
            }
        }
    }
}

@Composable
fun timeoutLabel(timeout: AppLockTimeout): String = when (timeout) {
    AppLockTimeout.IMMEDIATELY -> stringResource(R.string.security_timeout_now)
    else -> {
        val minutes = (timeout.millis / 60_000).toInt()
        LocalResources.current.getQuantityString(
            R.plurals.security_timeout_minutes, minutes,
            com.behnamjalali.planb.core.ui.PlannerLocals.numbers.format(minutes),
        )
    }
}
