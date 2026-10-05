# Plan-B Localization

Plan-B ships in two languages: **Persian (`fa`), the default**, and **English (`en`)**. This
covers more than strings. It also includes right-to-left (RTL) layout, Persian digits, the
Jalali (Solar Hijri) calendar, first-day-of-week rules and Persian-aware search. Everything
below is derived from the code. File paths are relative to the repository root.

---

## 1. Language selection

### 1.1 Persian on a fresh install: `app/src/main/kotlin/com/behnamjalali/planb/AppLocales.kt`

`PlanBApplication.onCreate()` calls `AppLocales.applyDefaultIfUnset(this)` before anything
else:

```kotlin
const val DEFAULT_LANGUAGE = "fa"

fun applyDefaultIfUnset(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // AppCompatDelegate needs an attached activity on API 33+ …
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        if (manager.applicationLocales.isEmpty) manager.applicationLocales = LocaleList.forLanguageTags(DEFAULT_LANGUAGE)
    } else if (AppCompatDelegate.getApplicationLocales().isEmpty) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(DEFAULT_LANGUAGE))
    }
}
```

- **API 33+:** the platform `LocaleManager` is used directly. `AppCompatDelegate` needs an
  attached activity on these versions, and none exists in `Application.onCreate`.
- **API 26–32:** `AppCompatDelegate.setApplicationLocales` is used (minSdk is 26).
- The default is applied **only if no per-app language is set**. A language chosen in the app
  or in system *Settings › Apps › Language* is never overridden.
- `AppLocales.current(context)` returns the active tag. It is tested by
  `app/src/test/kotlin/com/behnamjalali/planb/AppLocalesTest.kt`
  (`freshInstall_defaultsToPersian`, `userChoice_isNotOverridden`).

### 1.2 Storing the locale on API < 33

`app/src/main/AndroidManifest.xml` declares the AppCompat holder service with automatic
storage:

```xml
<service android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"
         android:enabled="false" android:exported="false">
    <meta-data android:name="autoStoreLocales" android:value="true" />
</service>
```

With `autoStoreLocales`, AppCompat persists the chosen locale itself on pre-T devices. On
API 33+ the platform stores it. The app never stores the active UI locale on its own.

### 1.3 Switching in the app: `feature/settings/.../SettingsScreens.kt`

```kotlin
onLanguage = { language ->
    viewModel.update { it.copy(language = language) }
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}
```

1. The choice is saved in DataStore (`language` key, `AppLanguage.tag` = `fa` or `en`).
2. The per-app locale is applied, and AppCompat recreates the activity.
3. On every launch, `MainActivity.onCreate` reads `AppCompatDelegate.getApplicationLocales()[0]`
   and calls `MainViewModel.syncLanguage(...)`. This keeps the stored `UserSettings.language`
   **in sync with the platform locale**, even when the user changed the language from system
   settings. The platform locale is the source of truth.

`AppLanguage.fromTag(tag)` (`core/model/.../Settings.kt`) matches by prefix and falls back to
`PERSIAN`. The two language names (`settings_language_fa` = "فارسی", `settings_language_en` =
"English") are `translatable="false"` and are always shown in their own language.

### 1.4 First run: the language comes first

`app/.../ui/onboarding/` (`OnboardingFlow`, `OnboardingViewModel`, `OnboardingHost`). On the
first launch the very first screen asks for the language, before the welcome and the intro
slides, so they are never seen in the other language.

- The screen is **bilingual**: its texts are `translatable="false"` strings in both languages
  (`onboarding_language_*`), and each card is laid out in its own direction. The card matching
  the device language is suggested (`OnboardingFlow.suggestedLanguage`: Persian on Persian
  devices, English otherwise, Persian when the device does not say).
- Tapping a card fades the screen to the bare window background, then stores
  `language` and `language_chosen` in DataStore. Calendar system, first day of week and digits
  follow through their "auto" defaults. `MainActivity` applies the stored language to the
  platform as usual (§1.3), which recreates the activity behind that empty frame (the window
  background equals the theme background). The welcome is shown only once the activity's
  resources are in the chosen language.
- `language_chosen` is device state like `onboarding_completed`: never exported, kept on
  restore. Installs that finished onboarding before the screen existed count as having chosen.
  Onboarding is marked completed only after the last slide (or Skip).

### 1.5 Locale config and bundles: `app/build.gradle.kts`

```kotlin
androidResources {
    localeFilters += listOf("fa", "en")
    generateLocaleConfig = true
}
bundle {
    // The language can be switched inside the app, so every install needs both
    // Persian and English resources rather than only the device language split.
    language { enableSplit = false }
}
```

- `localeFilters` drops library translations for any other language.
- `generateLocaleConfig = true` makes AGP generate `locale_config.xml` from the resource
  folders. Android 13+ uses it to offer the per-app language picker in system settings.
- AGP requires `app/src/main/res/resources.properties` for this. It declares the language of
  the unqualified `values/` folder: `unqualifiedResLocale=en-US`. **`values/` is English** and
  **`values-fa/` is Persian**. Persian is still the runtime default through `AppLocales`.
- **The bundle language split is disabled.** Play must install both languages on every
  device, otherwise switching in the app would show missing resources.

---

## 2. RTL

- `android:supportsRtl="true"` is set in the manifest. Compose mirrors layouts automatically
  for `fa`.
- Layouts use start and end semantics. The lint rule `RtlHardcoded` is an **error** (see §9).
- Direction-dependent icons use `Icons.AutoMirrored.*` (back arrow in `PlannerTopBar`,
  chevrons in `SettingsRow`, `MonthHeader`, review and project navigation, `MenuBook`,
  `Sort`, `NoteAdd`, …), so they flip in RTL.
- Custom canvas drawing checks `layoutDirection` explicitly:
  - `PlannerProgressBar` (`core/designsystem/.../component/Feedback.kt`) grows from the start
    edge.
  - `PlannerHeatmap` (`core/ui/.../Cards.kt`) lays out weeks oldest → newest in reading
    direction.
  - Charts in `feature/goals/.../GoalsScreens.kt` and `feature/review/.../Review.kt` also
    check direction.
- Some content is deliberately kept LTR:
  - The Material `TimePicker` in `PlannerTimePickerDialog` (`core/ui/.../Pickers.kt`) is
    wrapped in `LocalLayoutDirection provides Ltr`, so the clock face digits stay in clock
    order.
  - The focus timer text (`feature/focus/.../FocusScreen.kt`) uses
    `TextDirection.Ltr`: "A clock reads left-to-right in both languages."
- Typography uses **no letter spacing**, because tracking breaks Persian cursive joining
  (see `DESIGN_SYSTEM.md`).

---

## 3. Digits and number formatting: `core/common/.../Digits.kt`

| API | Behaviour |
|---|---|
| `Digits.toPersian(s)` | ASCII `0-9` → Persian `۰-۹` |
| `Digits.toLatin(s)` | Persian `۰-۹` **and** Arabic-Indic `٠-٩` → ASCII |
| `Digits.localize(s, persian)` | applies `toPersian` when `persian` is true |
| `NumberFormatter(persianDigits).format(Int/Long)` | localized integer |
| `NumberFormatter.format(Double, maxFractionDigits = 1)` | `HALF_UP` rounding with trailing zeros stripped. Uses the Persian decimal separator `٫` when Persian. |
| `NumberFormatter.percent(fraction)` | clamps to 0..1. `۴۰٪` in Persian, `40%` in Latin. |
| `NumberFormatter.twoDigits(v)` | zero-padded, for clocks |
| `NumberFormatter.localize(text)` | localizes the digits inside arbitrary text |

Rules:

- Apply digit conversion **only to display strings**, never to stored values, identifiers,
  URLs or file names (KDoc on `Digits`).
- Inputs accept any digit style. For example, `DataTransfer` runs `Digits.toLatin` before
  parsing dates and numbers on import, and the search normalizer converts digits to ASCII.
- **Digit preference:** `UserSettings.numberFormat` is `AUTO`, `PERSIAN` or `LATIN`.
  `usePersianDigits` is true for `PERSIAN`, or for `AUTO` when the language is Persian.
- Composables get the formatter through `PlannerLocals.numbers`, which is
  `LocalDateFormatter.current.numbers` (`core/ui/.../Formatting.kt`).
- Tests: `core/common/src/test/.../DigitsTest.kt`.

---

## 4. Calendars: `core/datetime`

### 4.1 Calendar engines (`CalendarEngine.kt`, `JalaliEngine.kt`)

User data is always stored as `LocalDate` (epoch day). A `CalendarEngine` converts dates only
for **display and calendar-aware arithmetic**:

```kotlin
interface CalendarEngine {
    val system: CalendarSystem
    fun toCalendarDate(date: LocalDate): CalendarDate      // (year, month 1-based, day)
    fun toLocalDate(year: Int, month: Int, day: Int): LocalDate  // day clamped to month length
    fun monthLength(year: Int, month: Int): Int
    fun isLeapYear(year: Int): Boolean
    // defaults: monthOf, firstDayOfMonth, lastDayOfMonth, plusMonths (clamps day), plusYears
}
```

| Engine | Implementation |
|---|---|
| `GregorianEngine` | `java.time` (`YearMonth`, `Year.isLeap`) |
| `JalaliEngine` | **Android ICU** `android.icu.util.Calendar` with `ULocale("fa_IR@calendar=persian")` in UTC. ICU is queried **once per Jalali year** for the 13 month boundaries (12 month starts plus the next Farvardin 1). The table is cached in a `ConcurrentHashMap`. All later conversions are arithmetic on that table. Leap year means Esfand has 30 days. |

- `CalendarEngines.of(system)` picks the engine.
- `plusMonths` clamps the day. For example, 31 Shahrivar + 1 month = 30 Mehr.
- `MonthGrid` builds full-week month grids for any engine and first day of week.
- Recurrence rules carry `CAL=JALALI|GREGORIAN`, so "monthly" and "yearly" repeat in the
  calendar the rule was created with (`RecurrenceEngine.kt`).
- Tests: `JalaliEngineTest.kt`, `RecurrenceEngineTest.kt`.

### 4.2 Defaults per language (`core/model/.../Settings.kt`)

| Setting | Default when there is no override |
|---|---|
| `calendarSystem` | `JALALI` if the language is Persian, otherwise `GREGORIAN` |
| `firstDayOfWeek` | `SATURDAY` if the calendar is Jalali, otherwise `MONDAY` |
| Persian digits | follows the language (`NumberFormatMode.AUTO`) |

Users can override the calendar system and the first day of week (*Settings*). Their "auto"
choices show the resolved default, for example `settings_calendar_auto` and
`settings_first_day_auto`. `workWeek(system)` (`core/ui/.../EditorComponents.kt`) gives the
"weekdays" recurrence preset as Saturday–Wednesday for Jalali and Monday–Friday for
Gregorian.

### 4.3 `PlannerDateFormatter` (`core/datetime/.../PlannerDateFormatter.kt`)

`PlannerDateFormatter` is built by `rememberDateFormatter(calendarSystem, firstDayOfWeek,
persianDigits)` in `core/ui/.../Formatting.kt`. The app root (`PlanBProviders` in
`app/.../ui/PlanBRoot.kt`) provides it as `LocalDateFormatter`.

- All words come from resources: month names (`jalali_month_names`,
  `gregorian_month_names`), weekday names (full, short and narrow, ordered **Monday first**
  to match `DayOfWeek`), today, tomorrow and yesterday, AM and PM.
- Word order comes from **pattern strings** (`date_pattern_full_jalali`,
  `date_pattern_medium_gregorian`, …). Each language orders the parts itself. For example,
  the English Gregorian pattern is `%1$s, %3$s %2$s, %4$s` (weekday, month, day, year) and
  the Persian one is `%1$s %2$s %3$s %4$s`.
- The class only shapes digits, through `NumberFormatter`.
- API: `fullDate`, `dayMonth`, `mediumDate`, `shortDate` (omits the current year),
  `relativeDate`, `monthYear`, `weekdayDate`, `time`, `timeRange`, `duration` (plurals),
  `timer` (`mm:ss` / `h:mm:ss`), `weekdays()`.
- **24-hour clock:** `use24Hour = persianDigits || DateFormat.is24HourFormat(context)`. When
  Persian digits are on, times are always 24-hour. Otherwise the device setting decides, and
  12-hour times use `time_am` and `time_pm` (`ق.ظ` and `ب.ظ` in Persian).

---

## 5. Search normalization: `core/common/.../SearchNormalizer.kt`

Search must find Persian text no matter which keyboard or digit style was used. Normalization
is applied **to the index and the query only**. Stored text is never changed.

| Step | Effect |
|---|---|
| NFKD, then drop diacritics | removes harakat (U+064B–U+065F), superscript alef, tatweel and non-spacing marks |
| Letter folding | Arabic Yeh ي and Alef maksura ى → Persian Yeh ی. Arabic Kaf ك → Persian Kaf ک. ة, ۀ and ە → ه. أ, إ, آ and ٱ → ا. ؤ → و. |
| Zero-width characters | ZWNJ (half-space), ZWJ, ZWSP, LRM, RLM, WJ and BOM → space (word separator) |
| Whitespace | collapsed to single spaces and trailing whitespace trimmed |
| Case | lowercased |
| Digits | Persian and Arabic-Indic digits → ASCII |
| `tokens()` | splits on spaces and keeps only letters and digits in each token |

Queries become FTS4 prefix terms (`tok*`, at most 8 tokens). See `DATABASE.md` §4. Tests:
`core/common/src/test/.../SearchNormalizerTest.kt`.

---

## 6. Plurals

- Plural resources (`<plurals>`) are used for counts and durations, for example
  `duration_minutes` and `duration_hours` in `core/datetime`, `export_done` in settings, and
  plurals in `core/ui`, calendar, goals, habits, notebooks and templates.
- **Convention:** the quantity is the raw `Int`. The displayed number is passed **already
  localized** as a string argument (`%1$s`), so Persian digits show up:

  ```kotlin
  resources.getQuantityString(R.plurals.duration_minutes, minutes, numbers.format(minutes))
  ```

- Persian has no grammatical plural for counted nouns. Persian plurals therefore define
  `one` and `other` with the same text, and English defines both forms.

---

## 7. String organisation

Each module owns its strings in a prefixed file. Every file exists in both `values/`
(English) and `values-fa/` (Persian):

| Module | File | Key prefix (typical) |
|---|---|---|
| `app` | `strings.xml` | `nav_…`, onboarding |
| `core/data` | `data_strings.xml` | |
| `core/datetime` | `datetime_strings.xml` | `date_…`, `time_…`, `duration_…` |
| `core/designsystem` | `designsystem_strings.xml` | `ds_…` |
| `core/notifications` | `notification_strings.xml` | |
| `core/ui` | `ui_strings.xml`, `ui_editor_strings.xml` | `ui_…` |
| `feature/<name>` | `<name>_strings.xml` (calendar, focus, goals, habits, notebooks, projects, review, search, settings, tasks, templates, today) | `<feature>_…` |

- The key sets in `values/` and `values-fa/` match one to one. The only English-only keys are
  marked `translatable="false"`: `app_name`, `app_display_name`, `app_label_fallback` (all
  "Plan-B") and the two language names.
- Format arguments are always positional (`%1$s`, `%2$s`), so translations can reorder them.
- Built-in planner templates are localized JSON files in `core/data/src/main/res/raw/` and
  `raw-fa/` (`template_daily_planner`, …). `TemplateRepository.builtInTemplates()` reads them
  on every call, so a language change takes effect immediately. Lint's `MissingTranslation`
  check does not cover `raw` resources, so keep both folders in sync by hand.

---

## 8. Other locale-sensitive behaviour

- Exports use **ISO-8601 Gregorian** dates and **ASCII** digits in CSV and JSON, regardless
  of the UI language (`docs/BACKUP_FORMAT.md` §7).
- CSV export includes a UTF-8 BOM so spreadsheet apps detect Persian text.
- Screenshot and end-to-end tests run with `user.timezone=Asia/Tehran` and a frozen clock
  (`app/build.gradle.kts`). The UI gallery (`docs/UI_GALLERY.md`) captures `fa` and `en`, in
  light and dark, and at 150% font scale.

---

## 9. Lint enforcement: `config/lint/lint.xml`

The convention plugin (`build-logic/convention/src/main/kotlin/KotlinAndroid.kt`) applies
this config to every Android module, with `abortOnError = true`:

| Issue | Severity | Why |
|---|---|---|
| `MissingTranslation` | **error** | every user-facing string must exist in `values-fa` |
| `HardcodedText` | **error** | no literal UI text in XML |
| `RtlHardcoded` | **error** | use start and end, not left and right |
| `GradleDependency`, `NewerVersionAvailable`, `AndroidGradlePluginVersion` | ignore | dependency freshness is managed through the version catalog |

---

## 10. Checklist: adding a string

1. Add the English text to the module's `src/main/res/values/<module>_strings.xml`, with the
   module prefix (`tasks_…`, `ds_…`, …).
2. Add the Persian translation **with the same key** to `values-fa/<module>_strings.xml`.
   Lint fails the build otherwise.
3. Use positional arguments (`%1$s`). Pass numbers pre-formatted with
   `PlannerLocals.numbers` / `NumberFormatter`, not `%d`, so Persian digits appear.
4. For counts, use `<plurals>` (`one` and `other` in both languages) and
   `getQuantityString(id, count, numbers.format(count))`.
5. For dates and times, use `PlannerLocals.formatter` (`PlannerDateFormatter`). Never use
   `java.time` formatting or `SimpleDateFormat` for display.
6. Mark a string `translatable="false"` only if it is truly language-neutral (brand names,
   language self-names).
7. Check RTL: use start and end padding and alignment, `AutoMirrored` icons for anything
   directional, and `layoutDirection` checks in custom `Canvas` drawing.
8. Accessibility text (content descriptions, state descriptions) is a string too. Translate
   it.
9. Re-record screenshots (`./gradlew recordRoborazziDebug`) when visible text changes.
