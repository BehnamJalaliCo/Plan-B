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
| `core:model` | 158 | Plan-B Pro habits and focus rules (#26–#30): strict mode Do Not Disturb decisions, long-break cycles, Health Connect thresholds and day windows, habit analytics (period rates, weekdays, trend, streak milestones), challenge rules (day and weekly schedules, failure and revival), deterministic badge rules, mood insights and correlations; Notes knowledge rules (Pro #16, #21, #22, #24, #25): link tokens, pending `[[` search, insert and whole-link deletion, Markdown/plain export of links, line diff (incl. huge notes), Persian-aware word and character counts, writing goal streaks, sanitizing HTML → blocks (malicious and malformed input, limits, tables), seeded graph layout (determinism, bounds, 2,000 notes within a time bound), prompt rotation, mood calendar and streaks; Recurrence/habit encodings, Pro planning rules (deadline-aware overdue, Eisenhower quadrants and moves, dependency cycle detection, smart-list JSON), habit streaks and rates, goal pace, Markdown import/export, statistics aggregation (Pro reports) |
| `core:common` | 16 | Persian/Latin digits, search normalization (Arabic ي/ك, ZWNJ, diacritics, digits) |
| `core:datetime` | 50 | Iran's official holidays for 1405 and 1406 against the published calendar, known solar holidays, computed lunar dates within a day, Hijri round trips and published month starts (Pro #2); Jalali conversion and month lengths (incl. leap years), month grids, recurrence (intervals, weekdays, month-end clamping, Jalali months, counts/until, DST; n-th/last weekday in Jalali and Gregorian months, skipped fifth weekdays, Esfand, after-completion, week start), Jalali/Gregorian statistics periods |
| `core:database` | 16 | DAOs, cascades and constraints, FTS queries, migrations 1→2, 2→3 and 1→3 against the exported schemas (existing data survives), soft-delete filters of every list query, the v3 DAOs |
| `core:datastore` | 14 | Focus Pro preferences round trip; Defaults, round-trips, tolerance of malformed values, calendar decorations backed up while device calendar sync settings are not |
| `core:data` | 119 | Plan-B Pro habits and focus: focus session effects on every change (also the end alarm), device-only strict mode state, Health Connect sync with a fake source (Pro and permission only, threshold, unchecked days stay unchecked, throttling), challenges following the data, badges awarded once with their day and celebrated only when new, mood check-ins beside the journal's, focus minutes per day; Notes knowledge (Pro): links maintained on save, backlinks, renamed/trashed/deleted targets, link titles searchable, normalized title search, graph data, version snapshots (interval, force, no duplicates, Pro only, never locked notes), retention (50 per note, 90 days), restore saving the current state first, journal pages shared with ritual reflections, trashed pages replaced, one mood per page, previews; Time-blocked tasks found by their block's day, Pro planning (after-completion spawning and count, near deadlines on Today and deadline sort, extra reminders and nag rows, dependencies and blocker counts, smart lists and filter evaluation), Locked-note crypto (round trip, wrong passphrase, tamper detection), note vault and restore on a new device, App lock timeout policy and controller, trash (Pro only, subtasks, restore re-indexes and reschedules, 30-day purge), activity history (merging, cap, no note bodies), Pro schema fields round-trip, trash excluded from lists and search, locked notes indexed by title only; task filtering/sorting/views, recurrence spawning, reminders scheduling, notes hierarchy, drafts, habits, goals, events validation, focus timing, search indexing, templates, **large-dataset performance** |
| `core:notifications` | 27 | The daily journal reminder (arming, skipping a day with a page, collision-free request code); Reminder planning for tasks, recurring events and habits; up to five reminders, nagging with its cap, snooze and dismiss, Done/Snooze buttons, collision-free request codes |
| `core:calendarsync` | 24 | Two-way device calendar sync on an in-memory provider (Pro #3): mirroring and links, edits and deletions both ways, last writer wins, missing calendars and stale links, never touching foreign events, Pro/permission/off, recurrences, reading and importing device events, device-only settings; event mapping and RRULEs |
| `core:backup` | 42 | Notes knowledge round trip in format 2 (links, versions, journal entries, mood entries, writing-goal and journal preferences) and relative `.md` links in the Markdown ZIP; Planning data round trip (deadlines, nag, reminder kinds, dependencies, advanced recurrence, smart-list JSON), Automatic backups (file names, pruning to 21 without touching other files, scheduling config, runner success/failure/skip), format 2 with v3 tables and attachment files (round trip, missing files, zip-slip names, size limits, rollback of files), backup round trip, validation of corrupt/foreign/newer archives, limits, transactional restore, CSV/JSON/Markdown export and non-overwriting import |
| `core:billing` | 15 | Entitlement policy (lifetime, 7-day grace, clock moved back), offline cache, purchase, restore, revocation, on-device signature verification |
| `core:ai` | 35 | Both wire formats against MockWebServer, SSE streaming (OpenAI deltas and `[DONE]`, Anthropic `content_block_delta`/`message_stop`/`error`, one-piece JSON fallback, cancellation), the SSE parser, error mapping, timeouts, HTTPS-only URLs, provider catalog, encrypted key storage and consent; prompt building (context marked as data, history, plan input), tolerant parsing of lists and plans (Markdown fences, prose, bullets, Persian digits), context truncation and token estimates, `AiAssistant` when not configured |
| `core:speech` | 8 | Voice input ViewModel with a fake recognizer (partial text, final text delivered once, Done keeps what was heard, errors, cancel), recognizer error mapping, levels, language tags, joining dictated text |
| `feature:assistant` | 14 | Plan proposals checked against working hours, busy time, now and each other; planner context text; chat with the chosen context (locked notes never offered), streaming, history and neutral errors; plan my day as a preview, accept and undo; contextual actions (fenceless text, extracted tasks created on confirm with undo, not configured); settings (key check, model list, consent before turning on, forget) |
| `feature:pro` | 9 | Paywall states, purchase, cancel, restore, store unavailable, price formatting, `ProGate` and `rememberProGuard` |
| `core:focus` | 7 | Focus Pro: generated noise (bounded, audible, deterministic, spectral tilt of white/pink/brown, ocean swells), fades, strict mode applying and restoring Do Not Disturb across process death, never touching a filter the user set |
| `feature:*` (others) | 179 | Plan-B Pro habits and focus ViewModels: Focus Pro (default sound and strict mode only for Pro starts, preview, running-session changes, Do Not Disturb access on resume, long breaks), habit statistics in the user's calendar with year browsing across process death, challenges and badges, the celebration, Health Connect on the habit screen, list and editor (friendly units, Persian digits), the mood tracker (check-in sheet once, several check-ins, edit, delete and undo, the journal's check-in, patterns with habits, focus and sleep); Plan-B Pro AI and voice in the editors (assistant changes and undo in the note and task editors, dictation at the caret, assistant text as blocks, Quick Capture dictation through the quick-add parser into a dated task); Notes knowledge in feature:notebooks (link insertion and repair in the editor, session versions, the link `VisualTransformation` offsets, graph filtering and capping, clipper parsing and share-intent validation) and the journal ViewModel (prompt rotation across process death, page creation, mood/energy/tags, ritual pages and streak, settings); Calendar time blocking (snapping, side-by-side layout, clipping, scheduling and moving blocks), the day timeline's gaps and now marker, device events and holidays in the calendar state, Task editor planning fields, smart lists and Eisenhower ViewModels, the advanced repeat dialog, project timeline layout (300+ tasks, RTL), ViewModels of Today, Tasks, Habits, Focus and Search on real repositories (30), Trash and Activity, automatic backup settings, locking notes in the editor, block editing, task form validation |
| `app` | 468 | Persian default locale, smoke test, 9 end-to-end flows, screenshot tests (including the Pro assistant: setup, chat, plan my day, settings, the note editor's actions and Quick Capture voice input; the Pro habits and focus screens: Focus Pro idle and running, the habit screen with Health Connect and a challenge, habit statistics and the year heatmap, the Health Connect section of the habit editor, challenges, the badge gallery, the badge celebration, the mood tracker, its patterns and the check-in from Today; the Pro notes knowledge screens: note links and backlinks, version history, note graph, web clipper, writing mode, journal and mood calendar; the Pro calendar: month with holidays, holiday day with the Hijri date, time blocking, timeline and calendar sync settings, the Plan-B Pro screen, Trash, Activity, Security, the lock screen, Statistics, My year, Appearance, the smart list builder, Eisenhower matrix, project timeline, task editor planning section and advanced repeat dialog), launcher/store icon rendering, Glance widget content and the launcher icon switcher |
| `wear` | 2 | Watch state updates (optimistic task completion and habit check-in) |

Total: **1,456 JVM tests**, all passing locally and in CI.

### End-to-end flows (`app/src/test/.../e2e`)

The real Hilt graph, Room (in-memory), DataStore and navigation run inside Robolectric with a
frozen clock (`TestClockModule`): quick capture → task list → complete; task editor edits
persist; a new note autosaves while typing (mixed Persian/English); projects open detail and
board; habit check-in from Today; focus start/finish with goals intact; search finds Persian
text typed with Arabic keyboard letters; theme and language changes persist; backup snapshot
and restore round trip; the first run (choosing English on the language screen stores and
applies it, the recreated activity continues in English through welcome and slides to Today;
an upgrade that already chose starts at the welcome; existing users never see onboarding).

### Screenshot tests (`app/src/test/.../screenshots`)

`AppScreenshotTest` launches the real app with seeded data and captures 86 screens in six
variants (Persian/English × light/dark, plus 150% font in both languages): 506 images in
`artifacts/screenshots/<feature>/`, shown in [docs/UI_GALLERY.md](docs/UI_GALLERY.md). Clicks
are dispatched through semantics actions (no touch ripples) and animations settle on the test
clock, so images are pixel-stable. `AppIconTest` renders the adaptive icon, the themed icon and
the 512×512 store icon.

### Instrumentation tests (`app/src/androidTest`)

`AppFlowsTest` runs with `HiltTestRunner` and an in-memory database: the first run (language
screen, welcome, skipping the slides), all
top-level tabs, quick capture creating a task.

JVM app tests never reach a network or a microphone: `TestAiModules.kt` replaces the provider client with a scripted `FakeAiApi`, the Keystore cipher with an in-memory one, and the speech recognizer with `FakeVoiceDictation`.

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
