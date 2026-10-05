package com.behnamjalali.planb.feature.assistant

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.behnamjalali.planb.core.ai.AiProvider
import com.behnamjalali.planb.core.designsystem.component.PlannerButton
import com.behnamjalali.planb.core.designsystem.component.PlannerButtonStyle
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerChip
import com.behnamjalali.planb.core.designsystem.component.PlannerDialog
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTextField
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.component.SettingsRow
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.ProGate

@Composable
fun AiSettingsDestination(onBack: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.ai_settings_title), onBack = onBack)
        ProGate(ProFeature.AI_ASSISTANT, teaser = { Column(Modifier.padding(horizontal = Spacing.screen)) { com.behnamjalali.planb.core.ui.ProTeaser(ProFeature.AI_ASSISTANT) } }) {
            if (!state.loaded) {
                PlannerLoadingState()
            } else {
                AiSettingsScreen(
                    state = state,
                    providers = viewModel.providers,
                    actions = AiSettingsActions(
                        onProvider = viewModel::selectProvider,
                        onBaseUrl = viewModel::setBaseUrl,
                        onModel = viewModel::setModel,
                        onKeyInput = viewModel::setKeyInput,
                        onSaveKey = viewModel::saveKey,
                        onRemoveKey = viewModel::removeKey,
                        onTest = viewModel::test,
                        onLoadModels = viewModel::loadModels,
                        onEnabled = viewModel::setEnabled,
                        onConfirmConsent = viewModel::confirmConsent,
                        onDismissConsent = viewModel::dismissConsent,
                        onForget = viewModel::forget,
                    ),
                )
            }
        }
    }
}

data class AiSettingsActions(
    val onProvider: (AiProvider) -> Unit = {},
    val onBaseUrl: (String) -> Unit = {},
    val onModel: (String) -> Unit = {},
    val onKeyInput: (String) -> Unit = {},
    val onSaveKey: () -> Unit = {},
    val onRemoveKey: () -> Unit = {},
    val onTest: () -> Unit = {},
    val onLoadModels: () -> Unit = {},
    val onEnabled: (Boolean) -> Unit = {},
    val onConfirmConsent: () -> Unit = {},
    val onDismissConsent: () -> Unit = {},
    val onForget: () -> Unit = {},
)

@Composable
internal fun providerName(provider: AiProvider): String = provider.brandName ?: provider.labelRes?.let { stringResource(it) }.orEmpty()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsScreen(state: AiSettingsUiState, providers: List<AiProvider>, actions: AiSettingsActions) {
    val context = LocalContext.current
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    val provider = state.provider
    LazyColumn(
        modifier = Modifier.imePadding(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item(key = "privacy") {
            PlannerCard(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, border = null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Icon(Icons.Rounded.Shield, contentDescription = null)
                    Text(stringResource(R.string.ai_settings_privacy), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "status") {
            val s = state.settings
            SettingsRow(
                title = stringResource(R.string.ai_settings_use),
                subtitle = stringResource(
                    when {
                        s.isReady -> R.string.ai_settings_on
                        s.isComplete -> R.string.ai_settings_off
                        else -> R.string.ai_settings_incomplete
                    },
                ),
                icon = Icons.Rounded.PowerSettingsNew,
                modifier = Modifier.toggleable(s.enabled, enabled = s.isComplete || s.enabled, role = Role.Switch, onValueChange = actions.onEnabled),
                trailing = { Switch(checked = s.enabled, onCheckedChange = null, enabled = s.isComplete || s.enabled) },
            )
        }

        item(key = "providers_header") { PlannerSectionHeader(stringResource(R.string.ai_settings_provider)) }
        items(providers, key = { "p_${it.id}" }) { p ->
            val selected = p.id == state.settings.providerId
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .selectable(selected = selected, role = Role.RadioButton) { actions.onProvider(p) }
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(providerName(p), style = MaterialTheme.typography.bodyLarge)
                    val hint = when (p.id) {
                        "iran_gateway" -> stringResource(R.string.ai_settings_provider_gateway_hint)
                        "avalai" -> stringResource(R.string.ai_settings_provider_iran_hint)
                        "custom" -> stringResource(R.string.ai_settings_provider_custom_hint)
                        else -> p.defaultBaseUrl?.let { runCatching { it.toUri().host }.getOrNull() }
                    }
                    hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }

        if (provider != null) {
            item(key = "connection_header") { PlannerSectionHeader(stringResource(R.string.ai_settings_connection)) }
            item(key = "base_url") {
                PlannerTextField(
                    value = state.baseUrl,
                    onValueChange = actions.onBaseUrl,
                    label = stringResource(if (provider.requiresBaseUrl) R.string.ai_settings_base_url else R.string.ai_settings_base_url_optional),
                    placeholder = provider.defaultBaseUrl ?: "https://…/v1",
                    leadingIcon = Icons.Rounded.Link,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    isError = state.baseUrl.isNotBlank() && !state.baseUrl.trim().startsWith("https://"),
                    supportingText = stringResource(R.string.ai_settings_https_only),
                )
            }
            item(key = "key") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (state.settings.hasKey) {
                        SettingsRow(
                            title = stringResource(R.string.ai_settings_key_saved),
                            subtitle = stringResource(R.string.ai_settings_key_saved_sub),
                            icon = Icons.Rounded.Lock,
                            trailing = { PlannerButton(stringResource(R.string.ai_settings_key_remove), actions.onRemoveKey, style = PlannerButtonStyle.Text) },
                        )
                    }
                    PlannerTextField(
                        value = state.keyInput,
                        onValueChange = actions.onKeyInput,
                        label = stringResource(if (state.settings.hasKey) R.string.ai_settings_key_replace else R.string.ai_settings_key),
                        leadingIcon = Icons.Rounded.Key,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        supportingText = stringResource(R.string.ai_settings_key_hint),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PlannerButton(stringResource(R.string.ai_settings_key_save), actions.onSaveKey, enabled = state.keyInput.isNotBlank())
                        provider.keyUrl?.let { url ->
                            PlannerButton(
                                stringResource(R.string.ai_settings_get_key),
                                {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (e: ActivityNotFoundException) {
                                        // No browser: nothing to open.
                                    }
                                },
                                style = PlannerButtonStyle.Text,
                            )
                        }
                    }
                }
            }
            item(key = "model_header") { PlannerSectionHeader(stringResource(R.string.ai_settings_model)) }
            item(key = "model") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PlannerTextField(
                        value = state.model,
                        onValueChange = actions.onModel,
                        label = stringResource(R.string.ai_settings_model_field),
                        leadingIcon = Icons.Rounded.Tune,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    )
                    val choices = (provider.suggestedModels + state.models).distinct()
                    if (choices.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            choices.take(MAX_CHIPS).forEach { m -> PlannerChip(m, selected = m == state.model, onClick = { actions.onModel(m) }) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlannerButton(
                            stringResource(R.string.ai_settings_load_models),
                            actions.onLoadModels,
                            style = PlannerButtonStyle.Tonal,
                            enabled = state.settings.hasKey && !state.loadingModels,
                        )
                        if (state.loadingModels) {
                            Spacer(Modifier.width(Spacing.md))
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    state.modelsError?.let { Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
            item(key = "test") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.md)) {
                    PlannerButton(
                        stringResource(R.string.ai_settings_test),
                        actions.onTest,
                        style = PlannerButtonStyle.Outlined,
                        icon = Icons.Rounded.NetworkCheck,
                        enabled = state.settings.isComplete && state.check != ConnectionCheck.Running,
                    )
                    when (val check = state.check) {
                        ConnectionCheck.Running -> Text(stringResource(R.string.ai_settings_testing), style = MaterialTheme.typography.bodySmall)
                        ConnectionCheck.Ok -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = PlanBTheme.colors.success, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.ai_settings_test_ok), style = MaterialTheme.typography.bodyMedium)
                        }
                        is ConnectionCheck.Failed -> Text(stringResource(check.error.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        null -> Unit
                    }
                }
            }
            item(key = "forget") {
                PlannerButton(
                    stringResource(R.string.ai_settings_forget),
                    { confirmForget = true },
                    style = PlannerButtonStyle.Text,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
            }
        }
    }

    if (state.consentDialog) {
        PlannerDialog(
            title = stringResource(R.string.ai_consent_title),
            onDismiss = actions.onDismissConsent,
            confirmLabel = stringResource(R.string.ai_consent_confirm),
            onConfirm = actions.onConfirmConsent,
        ) {
            val name = provider?.let { providerName(it) }.orEmpty()
            Text(stringResource(R.string.ai_consent_where, name, state.host), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.ai_consent_what), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.ai_consent_terms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmForget) {
        PlannerDialog(
            title = stringResource(R.string.ai_settings_forget),
            message = stringResource(R.string.ai_settings_forget_message),
            onDismiss = { confirmForget = false },
            confirmLabel = stringResource(R.string.ai_settings_forget_confirm),
            onConfirm = {
                confirmForget = false
                actions.onForget()
            },
            destructive = true,
        )
    }
}

private const val MAX_CHIPS = 24
