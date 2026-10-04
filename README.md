# Plan-B

**Plan-B** is a calm, local-first planner and notebook for Android, in Persian (default) and
English. It brings Today, a calendar, tasks, projects, notebooks, habits, goals, focus sessions,
templates and a weekly review together in one app, with Jalali and Gregorian calendars.

Your data stays on your device. There are no accounts, ads, analytics or trackers.

**All of these features are free and stay free.** An optional upgrade, **Plan-B Pro** (monthly
or lifetime through Cafe Bazaar), adds new tools on top; see [docs/PRO.md](docs/PRO.md).

| Today | Calendar | Notes | Habits |
|---|---|---|---|
| <img src="artifacts/screenshots/today/today_fa_light.png" width="200"/> | <img src="artifacts/screenshots/calendar/calendar_month_fa_light.png" width="200"/> | <img src="artifacts/screenshots/notebooks/note_editor_en_light.png" width="200"/> | <img src="artifacts/screenshots/habits/habit_detail_en_dark.png" width="200"/> |

All 162 screen captures (Persian/English, light/dark, large font) and the icons are in
[docs/UI_GALLERY.md](docs/UI_GALLERY.md).

## Features

- **Today**: greeting, progress, a timeline of events and timed tasks, today's and upcoming tasks,
  habits, focus, projects and recent notes. Sections can be reordered or hidden.
- **Quick capture** from any main tab: task, note, event, habit or project in a few taps.
- **Tasks**: Inbox, Today, Upcoming, Scheduled, Completed, Archived and All views; subtasks,
  priorities, tags, projects, start/due dates and times, reminders, estimates, search, sorting,
  multi-select actions, swipe to complete/delete with undo, and manual ordering.
- **Recurrence**: daily, weekly (chosen weekdays), monthly and yearly rules with intervals, end
  dates or counts, evaluated in Jalali or Gregorian months.
- **Calendar**: Day, Week, Month and Agenda views with events and due tasks, all-day and timed
  events, reminders and recurring events.
- **Projects**: statuses, progress (by tasks, milestones or manual), milestones, notes and a board.
- **Notebooks**: notebooks, sections and notes with a block editor (text, headings, checklists,
  bullets, numbering, quotes, code, dividers), autosave, draft recovery, pin/favorite, tags, move,
  duplicate, archive and Markdown import/export.
- **Habits**: daily, chosen days, times per week or every N days; counts and units, reminders,
  streaks and a history heatmap.
- **Goals**: numeric targets with pace (ahead/on track/behind), deadlines, milestones and links
  to projects.
- **Focus**: Pomodoro-style sessions measured from timestamps (correct across process death),
  pause/resume, a linked task and history.
- **Search** across tasks, projects, notes, notebooks, habits, goals and events, tolerant of
  Persian/Arabic letter and digit variants.
- **Templates**: eight built-in templates (daily/weekly/monthly planners, meeting note, project
  plan and more) plus your own templates saved from notes.
- **Weekly review**: completed work, missed items, habits, focus time, notes, projects and goals.
- **Settings**: language, theme, calendar system, first day of week, digits, defaults,
  notifications, motion and haptics, Today layout.
- **Backup & data**: versioned ZIP backups validated before a transactional restore; export of
  tasks (CSV/JSON) and notes (Markdown ZIP/JSON); task import that never overwrites.

## Plan-B Pro

Plan-B Pro is optional and never takes anything away from the free app. It is sold through
Cafe Bazaar as a monthly subscription (399,000 toman) or a one-time lifetime purchase
(1,999,000 toman) and unlocks 40 new features as they ship — planning (Persian natural-language
quick add, Iranian holidays, calendar sync, time blocking, deadlines, multiple reminders…),
notes (attachments, links, scanning, journal…), habits and focus, reports and widgets, app lock
and automatic backups, and an AI assistant that uses your own provider key. Nothing is sent
anywhere unless you turn that assistant on. Details, gating rules and testing:
[docs/PRO.md](docs/PRO.md).

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Coroutines/Flow · Room (FTS4) · DataStore · Hilt ·
Poolakey (Cafe Bazaar billing) · OkHttp (optional AI assistant) ·
Navigation Compose (type-safe routes) · kotlinx.serialization · ICU (Jalali calendar) ·
AlarmManager notifications · Baseline Profiles · Robolectric · Roborazzi · Macrobenchmark.

| | |
|---|---|
| Application id | `com.behnamjalali.planb` |
| Version | 1.0.1 (code 2) |
| minSdk / targetSdk / compileSdk | 26 / 37 / 37 |
| Build | Gradle 9.8 (wrapper), AGP 9.4, Kotlin 2.4, JDK 21 |

Exact versions are pinned in [gradle/libs.versions.toml](gradle/libs.versions.toml) and listed in
[docs/DEPENDENCIES.md](docs/DEPENDENCIES.md).

## Building

Requirements: JDK 21 and the Android SDK (platform 37). Point `local.properties` at the SDK
(`sdk.dir=/path/to/android-sdk`) or set `ANDROID_HOME`.

```bash
./gradlew assembleDebug                 # debug APK (app id suffix .debug)
./gradlew testDebugUnitTest             # unit, database, end-to-end and screenshot tests (JVM)
./gradlew verifyRoborazziDebug          # compare screenshots with artifacts/screenshots
./gradlew lintDebug                     # Android Lint, all modules (must be clean)
./gradlew :app:connectedDebugAndroidTest   # device/emulator tests
./gradlew assembleRelease bundleRelease    # R8-optimized release (signed when credentials exist)
```

Release signing never uses files from the repository; see [RELEASE.md](RELEASE.md).

## Project structure

```
app/                  Application, activity, navigation, onboarding, app-level tests
core/model            Domain models, recurrence/habit/goal rules, Markdown
core/common           Clock, dispatchers, digits, search normalization, result helpers
core/datetime         Calendar engines (Jalali via ICU, Gregorian), recurrence engine, formatting
core/database         Room entities, DAOs, FTS index, migrations, exported schemas
core/datastore        User preferences (DataStore)
core/data             Repositories, search indexing, reminder scheduling contract, file I/O
core/notifications    Alarm scheduling, reminder receivers, notification channels
core/backup           Backup/restore and export/import
core/billing          Plan-B Pro: Cafe Bazaar billing and the offline entitlement
core/ai               Optional AI assistant: providers, encrypted key, HTTP client
core/designsystem     Theme, tokens, typography and components
core/ui               Shared composables (cards, pickers, editors, formatting)
core/testing          Test utilities (fake clock)
feature/*             today, tasks, calendar, projects, notebooks, habits, goals, focus,
                      search, templates, review, settings, pro (Plan-B Pro screen)
baselineprofile/      Baseline Profile generator (Macrobenchmark)
benchmark/            Start-up and frame-timing benchmarks
build-logic/          Gradle convention plugins
```

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, modules, data flow, navigation, background work |
| [DATABASE.md](DATABASE.md) | Schema, search index, migrations |
| [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) | Colors, typography, tokens, components |
| [TESTING.md](TESTING.md) | Test suites and how to run them |
| [RELEASE.md](RELEASE.md) | Versioning, signing, release builds, CI release workflow |
| [PRIVACY.md](PRIVACY.md) | Privacy policy |
| [docs/PRO.md](docs/PRO.md) | Plan-B Pro: products, the 40 features, gating, billing key, testing |
| [CHANGELOG.md](CHANGELOG.md) | Release notes |
| [docs/UI_GALLERY.md](docs/UI_GALLERY.md) | Screenshots of every major screen |
| [docs/LOCALIZATION.md](docs/LOCALIZATION.md) | Languages, RTL, calendars, digits |
| [docs/BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md) | Backup archive and export formats |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Performance work and measurements |
| [docs/SECURITY_REVIEW.md](docs/SECURITY_REVIEW.md) | Security review |
| [docs/CAFE_BAZAAR_RELEASE.md](docs/CAFE_BAZAAR_RELEASE.md) | Cafe Bazaar packaging and publishing |
| [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) | Dependencies and licenses |
| [docs/FONTS.md](docs/FONTS.md) | Licensed typeface: how it is supplied to builds |
| [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) | What is implemented and verified |

## Privacy

Plan-B stores everything in its private app storage on your device. Backups and exports are
written only to files you choose. See [PRIVACY.md](PRIVACY.md).

## License

No open-source license has been chosen for this repository yet; all rights are reserved by the
owner. Bundled third-party components keep their own licenses (see
[docs/DEPENDENCIES.md](docs/DEPENDENCIES.md)). The Anjoman Max typeface is proprietary
(fontiran.com) and used under license; it is not part of this repository in usable form — see
[docs/FONTS.md](docs/FONTS.md).

## Developer and support

Designed and developed by **Behnam Jalali**. Feedback, ideas and bug reports:
[behnamjalali88@gmail.com](mailto:behnamjalali88@gmail.com) (also available from **Settings →
About** in the app).
