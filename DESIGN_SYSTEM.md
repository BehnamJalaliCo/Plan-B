# Plan-B Design System

The design system is in `core/designsystem`, a Compose library module. It re-exports Compose
Foundation, Material 3, Animation and Material Icons Extended as `api`. It contains the
theme, tokens and planner-specific components. Feature modules build screens from these
pieces and from the shared building blocks in `core/ui`.

```
core/designsystem/src/main/kotlin/com/behnamjalali/planb/core/designsystem/
├── theme/   Color.kt · Accents.kt · Type.kt · Tokens.kt · Theme.kt
└── component/   Surfaces.kt · Buttons.kt · Inputs.kt · Feedback.kt · Chrome.kt
core/designsystem/src/main/res/font/   vazirmatn_{regular,medium,semibold,bold}.ttf
core/designsystem/src/main/res/values{,-fa}/designsystem_strings.xml   (ds_… strings)
```

Screenshots of every screen in Persian and English, light and dark, and at 150% font are in
**[docs/UI_GALLERY.md](docs/UI_GALLERY.md)**.

---

## 1. Color

### 1.1 Palette and roles (`theme/Color.kt`)

`Palette` is `internal`. Screens use `MaterialTheme.colorScheme` roles or `PlanBTheme.colors`
and never raw palette values.

| Family | Swatches | Used for |
|---|---|---|
| Cream (light neutrals) | `Cream50 #FFFDF9`, `Cream100 #FBF7F2`, `Cream200 #F5EFE8`, `Cream300 #EFE8E0`, `Cream400 #E7DFD6` | light background and surfaces |
| Ink (light text) | `Ink900 #1F1B26`, `Ink700 #4A4455`, `Ink500 #6A6476`, `Ink300 #CDC6D3`, `Ink200 #E4DEE8` | on-surface, outline |
| Night (dark neutrals) | `Night950 #0F1118` … `Night600 #3A4157` | dark background and surfaces |
| Mist (dark text) | `Mist100 #ECE9F3`, `Mist300 #B4AFC2`, `Mist500 #8A859A` | on-surface, outline |
| Lavender: **primary** | `Lavender600 #6B5BD2` (light primary), `Lavender200 #C9C0FF` (dark primary), containers `Lavender100`, `LavenderDarkContainer #3A3178` | |
| Mint: **secondary** | `Mint700 #23705F`, `Mint200 #9EE0CC`, containers `Mint100`, `MintDarkContainer` | |
| Peach: **tertiary** | `Peach700 #A9512C`, `Peach200 #FFBE9F`, containers `Peach100`, `PeachDarkContainer` | |
| Error | `Error600 #B3261E`, `Error200 #FFB4AB`, `Error100`, `ErrorDarkContainer` | |

### 1.2 Light and dark schemes

`LightColors` and `DarkColors` are full Material 3 `ColorScheme`s. Highlights:

| Role | Light | Dark |
|---|---|---|
| `primary` / `onPrimary` | Lavender600 / white | Lavender200 / Lavender900 |
| `secondary` | Mint700 | Mint200 |
| `tertiary` | Peach700 | Peach200 |
| `background` = `surface` | Cream100 | Night900 |
| `surfaceContainerLowest` | white (cards) | Night950 |
| `surfaceContainerLow` | Cream50 (nav bar, sheets, dialogs) | Night850 |
| `surfaceContainer` / `High` / `Highest` | Cream200 / Cream300 / Cream400 | Night800 / Night750 / Night700 |
| `outline` / `outlineVariant` | Ink500 / Ink200 | Mist500 / Night600 |
| `error` | Error600 | Error200 |

There is **no dynamic color**. The brand palette is always used. The theme mode (`SYSTEM`,
`LIGHT` or `DARK`) comes from user settings and is resolved in `PlanBProviders`
(`app/.../ui/PlanBRoot.kt`).

### 1.3 Extended colors (`PlannerColors` in `theme/Theme.kt`)

| Field | Light | Dark |
|---|---|---|
| `heroGradient` | linear `#EDE7FF → #E3EEFA → #DDF4EC` | `#2A2557 → #1C2D45 → #1B3A35` |
| `cardBorder` | `outlineVariant` at 90% alpha | `outlineVariant` at 60% alpha |
| `success` | `#1F7A63` | `#86D9C0` |
| `warning` | `#9A5B00` | `#FFC27A` |
| `priorityHigh` / `Medium` / `Low` | `#C0392B` / `#B86E00` / `#3B74AE` | `#FF9E9E` / `#FFC27A` / `#9CC4EE` |
| `isDark` | `false` | `true` |

### 1.4 Accent colors (`theme/Accents.kt`)

Users pick a pastel accent for projects, notebooks, habits, events and tags. It is stored as
`AccentColor.key`. There are eight accents: `LAVENDER`, `MINT`, `PEACH`, `POWDER_BLUE`,
`ROSE`, `SAND`, `SAGE` and `SLATE`. Each one resolves to an `AccentTones(container,
onContainer, strong)` for light or dark:

- `container`: a tinted background.
- `onContainer`: text and icons on that container. It meets the contrast requirement.
- `strong`: small marks (dots, rings, bars) on regular surfaces.

Access accents with `PlanBTheme.colors.accent(AccentColor.MINT)`. Because the color is stored
as a key, the palette can be retuned, dark mode included, without migrating any data.

---

## 2. Typography (`theme/Type.kt`)

- One family, **Anjoman Max** (`PlanBFont`), covers both Persian and Latin. It ships in four
  weights — Regular (400), Medium (500), SemiBold (600) and Bold (700) — as `R.font.planb_*`,
  generated at build time from the licensed font files (see [docs/FONTS.md](docs/FONTS.md)).
- Sizes are in **sp**, so they follow the system font scale. The gallery includes 150%
  screenshots.
- **Letter spacing is always `0.sp`, on purpose. Tracking breaks Persian cursive joining.**
  Never add `letterSpacing` to a style.
- Line heights are generous to make room for Persian ascenders and descenders.

| Style | Size / line (sp) | Weight |
|---|---|---|
| `displayLarge` | 40 / 52 | Bold |
| `displayMedium` | 34 / 44 | Bold |
| `displaySmall` | 30 / 40 | SemiBold |
| `headlineLarge` | 28 / 38 | SemiBold |
| `headlineMedium` | 24 / 34 | SemiBold |
| `headlineSmall` | 21 / 30 | SemiBold |
| `titleLarge` | 19 / 28 | SemiBold |
| `titleMedium` | 16 / 24 | Medium |
| `titleSmall` | 14 / 22 | Medium |
| `bodyLarge` | 16 / 26 | Normal |
| `bodyMedium` | 14 / 22 | Normal |
| `bodySmall` | 12 / 18 | Normal |
| `labelLarge` | 14 / 20 | Medium |
| `labelMedium` | 12 / 18 | Medium |
| `labelSmall` | 11 / 16 | Medium |

`PlannerType.caption` is an alias for `bodySmall`.

**Font license:** Anjoman Max is proprietary software by Hirbod Lotfian / fontiran.com, used in
Plan-B under the owner's registered license. The font files are never committed in plain form
because the repository is public; see [docs/FONTS.md](docs/FONTS.md). The open-source Vazirmatn
(SIL OFL 1.1, `licenses/Vazirmatn-OFL.txt`) is kept only as a build fallback for checkouts
without the licensed files and is never used for releases.

---

## 3. Tokens (`theme/Tokens.kt`)

| Token | Values |
|---|---|
| `Spacing` (4dp grid) | `xxs 2` · `xs 4` · `sm 8` · `md 12` · `lg 16` · `xl 20` · `xxl 24` · `xxxl 32` · `huge 48`. `screen = 20dp` is the horizontal gutter for screen content. |
| `Radius` | `xs 8` · `sm 12` · `md 16` · `lg 22` · `xl 28` · `pill 999` |
| `PlanBShapes` (Material `Shapes`) | extraSmall = `xs` · small = `sm` · medium = `md` · large = `lg` · extraLarge = `xl` |
| `Elevation` | `none 0` · `card 1` · `raised 3` · `floating 8` (dp) |
| `IconSize` | `xs 16` · `sm 18` · `md 22` · `lg 28` · `xl 40` · `hero 64` (dp) |
| `MinTouchTarget` | **48dp** |
| `Opacity` | `disabled 0.38` · `muted 0.64` · `subtle 0.12` · `hairline 0.08` |
| `Motion.Duration` | `short 140` · `medium 240` · `long 380` (ms) |

### Motion

`Motion(enabled)` exposes specs that collapse to `snap()` when motion is disabled:

| Member | Enabled | Disabled |
|---|---|---|
| `pressedScale` | `0.975f` | `1f` |
| `press<T>()` | `spring(dampingRatio 0.7, StiffnessMediumLow)` | `snap()` |
| `emphasized<T>()` | `spring(dampingRatio 0.82, StiffnessLow)` | `snap()` |
| `standard<T>(duration = 240)` | `tween(duration)` | `snap()` |

When motion is disabled, decorative motion stops, but state changes stay visible.

---

## 4. `PlanBTheme` (`theme/Theme.kt`)

```kotlin
@Composable
fun PlanBTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    animationsEnabled: Boolean = true,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
)
```

The theme does the following:

- Chooses `LightColors` or `DarkColors` and builds the matching `PlannerColors`.
- Computes `motionEnabled = animationsEnabled && !systemAnimationsDisabled()`. The second
  part reads `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (the system "Remove animations"
  option) and is ignored in inspection mode.
- Provides these CompositionLocals:
  - `LocalPlannerColors` → `PlannerColors`. It has no default and errors with "PlanBTheme not
    applied".
  - `LocalPlannerExperience` → `PlannerExperience(motion: Motion, hapticsEnabled: Boolean)`.
- Wraps `MaterialTheme(colorScheme, typography = PlanBTypography, shapes = PlanBShapes)`.

Accessors: `PlanBTheme.colors`, `PlanBTheme.motion` and `PlanBTheme.experience`.

The app root feeds the theme from `UserSettings` (`themeMode`, `animationsEnabled`,
`hapticsEnabled`). Inside the theme it also provides `LocalDateFormatter` and `LocalToday`
from `core/ui` (see `docs/LOCALIZATION.md`).

---

## 5. Components (`component/*.kt`)

All component names start with `Planner` (except `AnimatedTaskCheckbox` and `SettingsRow`).
Every component takes a `modifier`.

### 5.1 Surfaces and helpers (`Surfaces.kt`)

| API | Purpose and key parameters |
|---|---|
| `Modifier.pressScale(interactionSource)` | Scales down slightly while pressed, through `graphicsLayer` so it never triggers relayout. Uses `motion.pressedScale` and `motion.press()`. |
| `PlannerHaptics` / `rememberPlannerHaptics()` | `success()` (Confirm), `longPress()` and `tick()` (SegmentTick). Each one does nothing when the user disabled haptics. |
| `PlannerCard` | The main card: `surfaceContainerLowest`, a hairline `cardBorder`, `Radius.lg` and shadow `Elevation.card`. Parameters: `onClick`, `onLongClick` (uses `combinedClickable`), `onClickLabel`, `containerColor`, `contentColor`, `shape`, `border`, `tonalElevation`, `shadowElevation`, `contentPadding` (default `Spacing.lg`). Clickable cards get `Role.Button`, a ripple and press scale. |
| `PlannerSurface` | A flat tinted grouping container: `color` (default `surfaceContainer`), `shape` (`Radius.md`), `contentPadding` (`Spacing.md`). |
| `PlannerHeroSurface` | A hero block filled with `heroGradient`: `brush`, `shape` (`Radius.xl`), `contentPadding` (`Spacing.xl`), hairline border. |

### 5.2 Buttons (`Buttons.kt`)

| API | Purpose and key parameters |
|---|---|
| `PlannerButton(text, onClick, style, icon, enabled)` | A pill-shaped button with a minimum height of `MinTouchTarget` and press scale. `PlannerButtonStyle` is `Primary`, `Tonal`, `Outlined` (hairline border), `Text` or `Destructive` (error colors). The optional leading `icon` is decorative. |
| `PlannerIconButton(icon, contentDescription, onClick, enabled, tint, containerColor)` | A 48×48dp icon button with a `md` icon. `contentDescription` is the accessibility label. |
| `PlannerFAB(icon, contentDescription, onClick)` | A circular primary FAB with `Elevation.floating`, an `lg` icon and press scale. `contentDescription` is required. |

### 5.3 Inputs (`Inputs.kt`)

| API | Purpose and key parameters |
|---|---|
| `PlannerChip(label, selected, onClick, icon, accent: AccentTones?)` | A selectable pill used for filters and options. `Role.Tab`; 40dp visual height inside a 48dp touch target (`minimumInteractiveComponentSize`). The container color animates. When selected it uses `accent` colors if given, otherwise `primaryContainer`. |
| `PlannerPill(text, container, content, icon)` | A small non-interactive label for tags and status (`labelSmall`). |
| `PlannerSearchBar(query, onQueryChange, placeholder, focusRequester, onSearch)` | A pill-shaped `TextField`, at least 52dp tall. It has a search icon, a clear button (`ds_clear_search`) and the IME action Search. |
| `PlannerTextField(value, onValueChange, label, singleLine, minLines, maxLines, imeAction, keyboardOptions, keyboardActions, isError, supportingText, leadingIcon, placeholder)` | An outlined field with `Radius.md` and sentence capitalization. Text direction follows the content. |
| `PlannerSegmentedControl(options, selected, onSelect, label)` | A single-choice pill segmented control (for example Day, Week, Month). Uses `selectableGroup` and `Role.Tab`. Each segment is at least `MinTouchTarget` (48dp) tall, and the selected segment is raised. |

### 5.4 Feedback (`Feedback.kt`)

| API | Purpose and key parameters |
|---|---|
| `PlannerProgressRing(progress, size = 56dp, strokeWidth = 6dp, color, trackColor, contentDescription, content)` | A ring drawn on a single Canvas that starts at 12 o'clock. Progress animates with `emphasized()`. A localized `contentDescription` replaces child semantics. A `content` slot sits in the center. |
| `PlannerProgressBar(progress, color, trackColor, height = 8dp)` | A rounded linear bar that grows from the **start edge** (RTL-aware). |
| `PlannerSectionHeader(title, actionLabel, onAction, trailing)` | A row at least 48dp tall. The title is marked as a **heading** for accessibility. It has optional trailing text and a text-button action. |
| `PlannerEmptyState(icon, title, message, actionLabel, onAction)` | A centered illustration icon in a `primaryContainer` circle, with title, message and an optional tonal action. |
| `PlannerErrorState(message, onRetry)` | An error icon with the localized "Something went wrong" (`ds_something_went_wrong`), the message, and an optional "Try again" (`ds_retry`). |
| `PlannerLoadingState()` | A centered circular indicator with a localized "Loading" (`ds_loading`) description. |

### 5.5 Chrome (`Chrome.kt`)

| API | Purpose and key parameters |
|---|---|
| `PlannerTopBar(title, subtitle, onBack, actions)` | A Material `TopAppBar` with a one-line `titleLarge` title and an optional subtitle. The back button uses an **auto-mirrored** arrow (`ds_back`). Container colors: `background`, or `surfaceContainer` when scrolled. |
| `PlannerNavItem(label, icon, selectedIcon, testTag)` | Model for one bottom-navigation entry. `testTag` is a stable id for UI tests and benchmarks. |
| `PlannerNavigationBar(items, selectedIndex, onSelect)` | Bottom navigation on `surfaceContainerLow` with a `primaryContainer` indicator. Icons are decorative because the label carries the meaning. |
| `PlannerDialog(title, onDismiss, confirmLabel, onConfirm, message, dismissLabel = "Cancel", destructive, confirmEnabled, content)` | An `AlertDialog` with `Radius.xl`. `destructive` colors the confirm button with `error`. An **empty `dismissLabel` hides** the second button. An optional custom `content` slot is available. |
| `PlannerBottomSheet(onDismiss, content)` | A `ModalBottomSheet` that skips the half-expanded state. Top corners use `Radius.xl`. Content is padded for the navigation bar. |
| `AnimatedTaskCheckbox(checked, onCheckedChange, color, contentDescription)` | A circular 24dp checkbox inside a 48dp target. The fill springs in and the checkmark is drawn progressively. It is `toggleable` with `Role.Checkbox` and a localized `stateDescription` (`ds_completed` or `ds_not_completed`). It plays success haptics when checked. |
| `SettingsRow(title, icon, subtitle, onClick, trailing)` | A settings list row at least 56dp tall, with a tinted circular icon. Shows `trailing` content, or an auto-mirrored chevron when it is clickable. It is disabled when `onClick` is null. |

---

## 6. Shared UI building blocks (`core/ui`)

These sit on top of the design system and know about the domain model and formatting:

| File | Contents |
|---|---|
| `Cards.kt` | Domain cards `PlannerTaskCard`, `PlannerEventCard`, `PlannerHabitCard`, `PlannerProjectCard`, `PlannerGoalCard` and `PlannerNoteCard`. Helpers `priorityLabel`, `priorityColor`, `accentName` and `projectStatusLabel`. **`PlannerHeatmap`** is a completion heatmap drawn on one Canvas: week columns follow reading direction, rows start at the user's first day of week, and it takes a `contentDescription`. |
| `Pickers.kt` | `MonthGridView` and `MonthHeader` (calendar-system aware, with auto-mirrored arrows), `PlannerDatePickerDialog` (Jalali or Gregorian), `PlannerTimePickerDialog` (24-hour Material picker kept LTR), `AccentColorPicker` and `PlannerIconPicker`. |
| `EditorComponents.kt` | `EditorRow`, `ReminderMenu` / `reminderLabel`, `RecurrenceMenu`, `CustomRecurrenceDialog`, `recurrenceSummary`, `presetRule`, `workWeek`, `calendarSystemLabel`, `DiscardChangesDialog` and `ConfirmDeleteDialog`. |
| `Formatting.kt` | `LocalDateFormatter`, `LocalToday`, `PlannerLocals` (`formatter`, `numbers`, `today`) and `rememberDateFormatter`. |
| `Icons.kt` | Maps `PlannerIcon` keys to Material icons, with localized labels. |
| `Permissions.kt` | `rememberNotificationPermissionRequest()`. |

---

## 7. Accessibility conventions visible in code

- **Touch targets:** `MinTouchTarget = 48.dp` applies to `PlannerButton` (minimum height),
  `PlannerIconButton` (fixed size), `AnimatedTaskCheckbox` (target size) and
  `PlannerSectionHeader` (row height). Segmented-control segments are at least 48dp.
  `PlannerChip` draws at 40dp inside a 48dp touch target. `SettingsRow` is at least 56dp.
- **Content descriptions:**
  - Icon-only controls take a localized description (`PlannerIconButton`, and `PlannerFAB`
    where it is required).
  - Icons next to visible text pass `null` because they are decorative (button icons, chip
    icons, navigation icons, settings-row icons, empty-state icons).
  - Canvas visuals (`PlannerProgressRing`, `PlannerHeatmap`) take a localized description,
    and the ring uses `clearAndSetSemantics`.
  - `PlannerLoadingState` announces "Loading".
- **Roles and states:**
  - `Role.Button` on clickable cards.
  - `Role.Tab` and `selectable` / `selectableGroup` on chips and segments.
  - `Role.Checkbox` plus `stateDescription` on the task checkbox.
  - `heading()` on section headers.
  - `onClickLabel` support on `PlannerCard`.
- **Live regions:** the focus timer text is a `LiveRegionMode.Polite` region
  (`feature/focus/.../FocusScreen.kt`).
- **Reduced motion:** every animation goes through `PlanBTheme.motion`. It becomes `snap()`
  and press scale is turned off when the in-app *Animations* setting is off **or** the system
  animator duration scale is 0.
- **Haptics:** `rememberPlannerHaptics()` follows the in-app *Haptics* setting.
- **Text scaling:** all type is in sp, one-line labels use ellipsis, and the 150% font
  screenshots in `docs/UI_GALLERY.md` guard against regressions.
- **RTL:** auto-mirrored directional icons and start-edge drawing (see
  `docs/LOCALIZATION.md` §2).
- **Strings:** every accessibility string is a resource in both `values/` and `values-fa/`
  (`ds_…` in `designsystem_strings.xml`).

---

## 8. Rules for contributors

1. Use `MaterialTheme.colorScheme` roles, `PlanBTheme.colors` or accent tones. Never use raw
   hex values or `Palette`.
2. Use `Spacing`, `Radius`, `IconSize` and `Elevation` tokens instead of literal dp values
   where a token exists.
3. Never set `letterSpacing`.
4. Drive animations from `PlanBTheme.motion` so reduced motion is respected.
5. Give every interactive element a 48dp target and every icon-only control a localized
   description.
6. Check new UI in `fa` and `en`, in light and dark, and at large font. Re-record the gallery
   with `./gradlew recordRoborazziDebug && python3 tools/generate_ui_gallery.py`.
