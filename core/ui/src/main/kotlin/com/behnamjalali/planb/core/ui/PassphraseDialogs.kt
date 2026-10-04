package com.behnamjalali.planb.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.theme.Spacing

/** A passphrase must have at least this many characters. */
const val MIN_PASSPHRASE_LENGTH = 8

/**
 * The note passphrase is never kept in saved state (it would be written to disk on process
 * death): plain `remember`, handed over as a CharArray.
 */
@Composable
private fun PassphraseField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false, supporting: String? = null) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (visible) R.string.ui_passphrase_hide else R.string.ui_passphrase_show),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Sets the passphrase of locked notes, with a clear warning that it cannot be recovered. */
@Composable
fun PassphraseSetupDialog(onConfirm: (CharArray) -> Unit, onDismiss: () -> Unit, busy: Boolean = false) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val tooShort = first.isNotEmpty() && first.length < MIN_PASSPHRASE_LENGTH
    val mismatch = second.isNotEmpty() && second != first
    PlannerDialog(
        title = stringResource(R.string.ui_passphrase_setup_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.ui_passphrase_setup_confirm),
        confirmEnabled = !busy && first.length >= MIN_PASSPHRASE_LENGTH && first == second,
        onConfirm = { onConfirm(first.toCharArray()) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.ui_passphrase_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            PassphraseField(
                first, { first = it }, stringResource(R.string.ui_passphrase),
                isError = tooShort,
                supporting = stringResource(R.string.ui_passphrase_min, PlannerLocals.numbers.format(MIN_PASSPHRASE_LENGTH)),
            )
            PassphraseField(
                second, { second = it }, stringResource(R.string.ui_passphrase_repeat),
                isError = mismatch,
                supporting = if (mismatch) stringResource(R.string.ui_passphrase_mismatch) else null,
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

/** Asks for the passphrase of locked notes; [onFingerprint] offers the biometric shortcut. */
@Composable
fun PassphraseUnlockDialog(
    onUnlock: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    wrong: Boolean = false,
    busy: Boolean = false,
    message: String? = null,
    onFingerprint: (() -> Unit)? = null,
) {
    var value by remember { mutableStateOf("") }
    PlannerDialog(
        title = stringResource(R.string.ui_passphrase_unlock_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.ui_passphrase_unlock),
        confirmEnabled = !busy && value.isNotEmpty(),
        onConfirm = { onUnlock(value.toCharArray()) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            PassphraseField(
                value, { value = it }, stringResource(R.string.ui_passphrase),
                isError = wrong,
                supporting = if (wrong) stringResource(R.string.ui_passphrase_wrong) else null,
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (onFingerprint != null) {
                TextButton(onClick = onFingerprint) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                    Text(stringResource(R.string.ui_passphrase_fingerprint), Modifier.padding(start = Spacing.sm))
                }
            }
        }
    }
}
