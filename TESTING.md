# Testing

Every quality gate runs on the JVM (Robolectric) except the device suites, so the full check
needs no emulator. CI also runs the device suite on an Android emulator.

## Quick reference

| Command | What it runs |
|---|---|
| `./gradlew testDebugUnitTest` | All JVM tests: unit, Room/DAO/migration, repositories, ViewModel and editor logic, end-to-end flows and screenshot tests (in verify-free mode) |
| `./gradlew verifyRoborazziDebug` | The same tests, failing if any screenshot differs from `artifacts/screenshots` |
| `./gradlew recordRoborazziDebug` | Re-records screenshots (then run `python3 tools/generate_ui_gallery.py`) |
| `./gradlew lintDebug` | Android Lint for every module; must report no errors (warnings are fixed too) |
| `./gradlew :app:connectedDebugAndroidTest` | Instrumentation tests on a device/emulator |
| `./gradlew :benchmark:pixel6Api34BenchmarkReleaseAndroidTest` | Macrobenchmarks on a Gradle-managed emulator |
| `./gradlew :app:generateBaselineProfile` | Regenerates the baseline profile on a Gradle-managed emulator |

## Suites

| Module | Tests | Focus |
|---|---|---|
| `core:model` | 24 | Recurrence/habit encodings, habit streaks and rates, goal pace, Markdown import/export |
| `core:common` | 11 | Persian/Latin digits, search normalization (Arabic ي/ك, ZWNJ, diacritics, digits) |
| `core:datetime` | 27 | Jalali conversion and month lengths (incl. leap years), month grids, recurrence (intervals, weekdays, month-end clamping, Jalali months, counts/until, DST) |
| `core:database` | 8 | DAOs, cascades and constraints, FTS queries, migration 1→2 against the exported schema |
| `core:datastore` | 6 | Defaults, round-trips, tolerance of malformed values |
| `core:data` | 26 | Task filtering/sorting/views, recurrence spawning, reminders scheduling, notes hierarchy, drafts, habits, goals, events validation, focus timing, search indexing, templates, **large-dataset performance** |
| `core:notifications` | 4 | Reminder planning for tasks, recurring events and habits |
| `core:backup` | 15 | Backup round trip, validation of corrupt/foreign/newer archives, limits, transactional restore, CSV/JSON/Markdown export and non-overwriting import |
| `feature:*` | 40 | ViewModels of Today, Tasks, Habits, Focus and Search on real repositories (30), block editing, task form validation |
| `app` | 123 | Persian default locale, smoke test, 9 end-to-end flows, 108 screenshot tests, launcher/store icon rendering |

Total: **284 JVM tests**, all passing locally and in CI.

### End-to-end flows (`app/src/test/.../e2e`)

The real Hilt graph, Room (in-memory), DataStore and navigation run inside Robolectric with a
frozen clock (`TestClockModule`): quick capture → task list → complete; task editor edits
persist; a new note autosaves while typing (mixed Persian/English); projects open detail and
board; habit check-in from Today; focus start/finish with goals intact; search finds Persian
text typed with Arabic keyboard letters; theme and language changes persist; backup snapshot
and restore round trip.

### Screenshot tests (`app/src/test/.../screenshots`)

`AppScreenshotTest` launches the real app with seeded data and captures 26 screens in six
variants (Persian/English × light/dark, plus 150% font in both languages): 156 images in
`artifacts/screenshots/<feature>/`, shown in [docs/UI_GALLERY.md](docs/UI_GALLERY.md). Clicks
are dispatched through semantics actions (no touch ripples) and animations settle on the test
clock, so images are pixel-stable. `AppIconTest` renders the adaptive icon, the themed icon and
the 512×512 store icon.

### Instrumentation tests (`app/src/androidTest`)

`AppFlowsTest` runs with `HiltTestRunner` and an in-memory database: onboarding skip, all
top-level tabs, quick capture creating a task.

### Benchmarks

`:benchmark` measures cold start-up with and without the baseline profile and frame timing
while switching tabs and scrolling; `:baselineprofile` generates the profile from the same
journeys. Both target the `benchmarkRelease` variant (R8-optimized, debug-signed) on a
Gradle-managed Pixel 6 API 34 emulator. They need hardware virtualization and are not part of
the regular CI gate.

## Conventions

- Tests never depend on the current date: the clock is injected (`FakeTimeProvider`).
- Robolectric runs SDK 36 (`robolectric.properties`) with `user.timezone=Asia/Tehran` for the
  app module; screenshot tests use native graphics with hardware rendering.
- Failing tests are fixed at the root cause; tests are never skipped or weakened.
