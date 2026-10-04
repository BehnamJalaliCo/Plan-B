package com.behnamjalali.planb.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.behnamjalali.planb.core.data.platform.AppIconSwitcher
import com.behnamjalali.planb.core.data.repository.SettingsRepository
import com.behnamjalali.planb.core.designsystem.component.PlannerCard
import com.behnamjalali.planb.core.designsystem.component.PlannerLoadingState
import com.behnamjalali.planb.core.designsystem.component.PlannerSectionHeader
import com.behnamjalali.planb.core.designsystem.component.PlannerTopBar
import com.behnamjalali.planb.core.designsystem.theme.MinTouchTarget
import com.behnamjalali.planb.core.designsystem.theme.Radius
import com.behnamjalali.planb.core.designsystem.theme.Spacing
import com.behnamjalali.planb.core.designsystem.theme.colorThemeSwatch
import com.behnamjalali.planb.core.model.AppIcon
import com.behnamjalali.planb.core.model.ColorTheme
import com.behnamjalali.planb.core.model.ThemeMode
import com.behnamjalali.planb.core.model.UserSettings
import com.behnamjalali.planb.core.ui.LocalProAccess
import com.behnamjalali.planb.core.ui.ProBadge
import com.behnamjalali.planb.core.ui.ProFeature
import com.behnamjalali.planb.core.ui.rememberProGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.behnamjalali.planb.core.designsystem.R as DesignR

/** Settings → Appearance: theme mode, color themes and app icons (Plan-B Pro #33). */
@Serializable data object AppearanceRoute

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val icons: AppIconSwitcher,
) : ViewModel() {
    val state: StateFlow<UserSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _icon = MutableStateFlow(runCatching { icons.current() }.getOrDefault(AppIcon.CLASSIC))
    val icon: StateFlow<AppIcon> = _icon.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.update { it.copy(themeMode = mode) } }

    fun setColorTheme(theme: ColorTheme) = viewModelScope.launch { settings.update { it.copy(colorTheme = theme) } }

    fun setIcon(icon: AppIcon) {
        if (runCatching { icons.apply(icon) }.isSuccess) _icon.value = icon
    }
}

@Composable
fun AppearanceDestination(onBack: () -> Unit, viewModel: AppearanceViewModel = hiltViewModel()) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val icon by viewModel.icon.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        PlannerLoadingState()
        return
    }
    AppearanceScreen(
        settings = s,
        icon = icon,
        onBack = onBack,
        onThemeMode = viewModel::setThemeMode,
        onColorTheme = viewModel::setColorTheme,
        onIcon = viewModel::setIcon,
    )
}

@Composable
fun AppearanceScreen(
    settings: UserSettings,
    icon: AppIcon,
    onBack: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onColorTheme: (ColorTheme) -> Unit,
    onIcon: (AppIcon) -> Unit,
) {
    val guard = rememberProGuard()
    val isPro = LocalProAccess.current.isPro
    // Without Pro the classic palette is drawn, so that is what is shown as selected.
    val activeTheme = settings.effectiveColorTheme(isPro)
    Column(Modifier.fillMaxSize()) {
        PlannerTopBar(stringResource(R.string.settings_appearance), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "mode_h") { PlannerSectionHeader(stringResource(R.string.appearance_mode)) }
            item(key = "mode") {
                PlannerCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs, horizontal = Spacing.md)) {
                    Column(Modifier.selectableGroup()) {
                        listOf(
                            ThemeMode.SYSTEM to R.string.settings_theme_system,
                            ThemeMode.LIGHT to R.string.settings_theme_light,
                            ThemeMode.DARK to R.string.settings_theme_dark,
                        ).forEach { (mode, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MinTouchTarget)
                                    .selectable(settings.themeMode == mode, role = Role.RadioButton) { onThemeMode(mode) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = settings.themeMode == mode, onClick = null)
                                Spacer(Modifier.width(Spacing.md))
                                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            item(key = "themes_h") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlannerSectionHeader(stringResource(R.string.appearance_color_theme), Modifier.weight(1f))
                    if (!isPro) ProBadge()
                }
            }
            item(key = "themes") {
                ChoiceGrid(ColorTheme.entries, label = { colorThemeName(it) }, selected = activeTheme, onSelect = { theme ->
                    if (theme.isPremium) guard.run(ProFeature.THEMES) { onColorTheme(theme) } else onColorTheme(theme)
                }) { theme -> ThemeSwatch(theme) }
            }
            item(key = "icons_h") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlannerSectionHeader(stringResource(R.string.appearance_app_icon), Modifier.weight(1f))
                    if (!isPro) ProBadge()
                }
            }
            item(key = "icons") {
                ChoiceGrid(AppIcon.entries, label = { appIconName(it) }, selected = icon, onSelect = { choice ->
                    if (choice == AppIcon.CLASSIC) onIcon(choice) else guard.run(ProFeature.THEMES) { onIcon(choice) }
                }) { choice -> IconPreview(choice) }
            }
            item(key = "icons_note") {
                Text(
                    stringResource(R.string.appearance_icon_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGrid(
    options: List<T>,
    label: @Composable (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    preview: @Composable (T) -> Unit,
) {
    val selectedText = stringResource(R.string.appearance_selected)
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        maxItemsInEachRow = 3,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val name = label(option)
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.md))
                    .selectable(isSelected, role = Role.RadioButton) { onSelect(option) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = name
                        if (isSelected) stateDescription = selectedText
                    },
            ) {
                Column(Modifier.padding(Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        preview(option)
                        if (isSelected) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        // Keep the last row's cells the same width as the others.
        repeat((3 - options.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ThemeSwatch(theme: ColorTheme) {
    val colors = colorThemeSwatch(theme)
    Canvas(Modifier.size(56.dp).clip(CircleShape)) {
        drawRect(colors[0], Offset.Zero, Size(size.width / 2, size.height))
        drawRect(colors[1], Offset(size.width / 2, 0f), Size(size.width / 2, size.height / 2))
        drawRect(colors[2], Offset(size.width / 2, size.height / 2), Size(size.width / 2, size.height / 2))
    }
}

/** The real launcher artwork (adaptive icon layers), clipped like a round launcher icon. */
@Composable
private fun IconPreview(icon: AppIcon) {
    val (bg, fg) = when (icon) {
        AppIcon.CLASSIC -> DesignR.drawable.planb_icon_classic_background to DesignR.drawable.planb_icon_classic_foreground
        AppIcon.OCEAN -> DesignR.drawable.planb_icon_ocean_background to DesignR.drawable.planb_icon_ocean_foreground
        AppIcon.SUNSET -> DesignR.drawable.planb_icon_sunset_background to DesignR.drawable.planb_icon_sunset_foreground
        AppIcon.FOREST -> DesignR.drawable.planb_icon_forest_background to DesignR.drawable.planb_icon_forest_foreground
        AppIcon.MIDNIGHT -> DesignR.drawable.planb_icon_midnight_background to DesignR.drawable.planb_icon_midnight_foreground
    }
    Box(Modifier.size(56.dp).clip(CircleShape)) {
        Image(painterResource(bg), contentDescription = null, modifier = Modifier.fillMaxSize())
        Image(painterResource(fg), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun colorThemeName(theme: ColorTheme): String = stringResource(
    when (theme) {
        ColorTheme.CLASSIC -> R.string.appearance_theme_classic
        ColorTheme.OCEAN -> R.string.appearance_theme_ocean
        ColorTheme.FOREST -> R.string.appearance_theme_forest
        ColorTheme.SUNSET -> R.string.appearance_theme_sunset
        ColorTheme.BLOSSOM -> R.string.appearance_theme_blossom
        ColorTheme.MIDNIGHT -> R.string.appearance_theme_midnight
    },
)

@Composable
fun appIconName(icon: AppIcon): String = stringResource(
    when (icon) {
        AppIcon.CLASSIC -> R.string.appearance_theme_classic
        AppIcon.OCEAN -> R.string.appearance_theme_ocean
        AppIcon.SUNSET -> R.string.appearance_theme_sunset
        AppIcon.FOREST -> R.string.appearance_theme_forest
        AppIcon.MIDNIGHT -> R.string.appearance_theme_midnight
    },
)
