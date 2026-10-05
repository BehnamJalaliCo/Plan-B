package com.behnamjalali.planb.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.behnamjalali.planb.core.designsystem.R
import com.behnamjalali.planb.core.designsystem.theme.AccentTones
import com.behnamjalali.planb.core.designsystem.theme.IconSize
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.PlanBTheme
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing

/** Selectable filter/option chip. */
@Composable
fun PlannerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: AccentTones? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = PlanBTheme.motion
    val container by animateColorAsState(
        when {
            selected && accent != null -> accent.container
            selected -> scheme.primaryContainer
            else -> scheme.surfaceContainerLowest
        },
        motion.standard(),
        label = "chipContainer",
    )
    val content = when {
        selected && accent != null -> accent.onContainer
        selected -> scheme.onPrimaryContainer
        else -> scheme.onSurfaceVariant
    }
    Surface(
        // 40dp visual height inside a 48dp touch target.
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = 40.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        shape = RoundedCornerShape(Radius.pill),
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(Dp.Hairline, scheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.xs))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}


/** Small non-interactive label, e.g. a tag or status. */
@Composable
fun PlannerPill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(Radius.pill), color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(Spacing.xxs))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun PlannerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onSearch: () -> Unit = {},
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                PlannerIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.ds_clear_search),
                    onClick = { onQueryChange("") },
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Radius.pill),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/** Outlined text field with planner styling. Text direction follows the content. */
@Composable
fun PlannerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = imeAction,
    ),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    /** An action at the end of the field, such as voice input; null leaves the end empty. */
    trailingContent: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        trailingIcon = trailingContent,
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(Radius.md),
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
    )
}

/** Segmented single-choice control (e.g. Day/Week/Month). */
@Composable
fun <T> PlannerSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = PlanBTheme.motion
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.pill),
        color = scheme.surfaceContainer,
    ) {
        Row(
            Modifier
                .padding(Spacing.xs)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val bg by animateColorAsState(
                    if (isSelected) scheme.surfaceContainerLowest else Color.Transparent,
                    motion.standard(),
                    label = "segment",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = MinTouchTarget)
                        .selectable(selected = isSelected, onClick = { onSelect(option) }, role = Role.Tab),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = bg,
                        shadowElevation = if (isSelected) 1.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            label(option),
                            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
