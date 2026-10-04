# Implementation Status — Plan-B 1.0.0

Legend: ✅ implemented and verified by automated tests · ☑️ implemented, verified manually or
by build only · ⏳ needs an external step (credentials, device lab).

## Product features

| Area | Status | Verification |
|---|---|---|
| Today dashboard (customizable sections) | ✅ | ViewModel tests, E2E (habit check-in), screenshots |
| Quick capture (task/note/event/habit/project) | ✅ | E2E, device test, screenshots |
| Tasks: views, subtasks, tags, projects, priorities, reminders, multi-select, swipe + undo, reorder | ✅ | Repository + ViewModel tests, E2E, screenshots |
| Recurrence engine (Jalali/Gregorian, intervals, weekdays, counts, until, DST) | ✅ | 17 engine tests, repository tests |
| Reminders and notifications (exact/inexact, reboot restore, private) | ✅ | Planner tests; receivers verified by build and lint |
| Calendar Day/Week/Month/Agenda, events with reminders and repeats | ✅ | Repository tests, screenshots |
| Projects: statuses, progress modes, milestones, notes, board | ✅ | Repository tests, E2E, screenshots |
| Notebooks, sections, notes, block editor, autosave, drafts, Markdown | ✅ | Block-editing tests, repository tests, E2E (autosave), screenshots |
| Habits: schedules, targets, streaks, heatmap | ✅ | Habit statistics tests, ViewModel tests, screenshots |
| Goals: pace, deadlines, milestones, project links | ✅ | Goal pace tests, screenshots |
| Focus sessions (timestamp-based, pause/resume, history) | ✅ | Repository and ViewModel tests, E2E |
| Global search with Persian normalization (FTS) | ✅ | Normalizer tests, DAO tests, E2E, ViewModel tests |
| Templates (8 built-in + custom) | ✅ | Repository tests, screenshots |
| Weekly review | ✅ | Repository tests, screenshots |
| Settings (language, theme, calendar, first day, digits, defaults, motion, haptics, dashboard) | ✅ | DataStore tests, E2E (theme/language), screenshots |
| Backup/restore (versioned ZIP, validation, transactional restore) | ✅ | 15 backup tests incl. corrupt/foreign/future/oversized archives and rollback |
| Export/import (tasks CSV/JSON, notes Markdown ZIP/JSON) | ✅ | Export→import round trips, invalid rows, project/subtask linking |
| Onboarding (optional, skippable) | ✅ | Smoke test, device test, screenshots |
| Empty, loading and error states | ✅ | Screenshots (empty Today), code review of every screen |

## Platform and quality

| Area | Status | Notes |
|---|---|---|
| Persian default + RTL, English, in-app switching | ✅ | Locale tests (incl. API 33+ LocaleManager path), E2E |
| Jalali/Gregorian calendars, Persian digits | ✅ | Engine and formatter tests, screenshots |
| Accessibility (labels, 48dp targets, large fonts, TalkBack semantics) | ✅ | 150% font screenshots, semantics-based tests |
| Large-data performance (5k tasks, 1k notes, 2k events) | ✅ | `LargeDataTest` with time bounds |
| Screenshot suite (26 screens × 6 variants + icons) | ✅ | Roborazzi verify in CI, [UI_GALLERY.md](UI_GALLERY.md) |
| Room schema export + tested migration, no destructive fallback | ✅ | Migration test against exported schemas |
| Android Lint, all modules | ✅ | Zero errors, warnings and hints |
| Release build with R8, resource shrinking, baseline profile | ✅ | Built in CI; release APK ≈ 3 MB |
| Macrobenchmarks and baseline-profile generator | ☑️ | Modules build in CI; running them needs a KVM device (not available in the dev container) |
| Instrumentation tests | ✅ | Run on an API 34 emulator in CI |
| App icon (adaptive + themed), splash screen | ✅ | Rendered by `AppIconTest` |
| Pro #31 statistics, My year, PDF export | ✅ | `StatisticsCalculatorTest`, `StatsPeriodsTest`, `StatisticsRepositoryTest`, `PdfReportWriterTest` (layout on the JVM), `PdfExportDeviceTest` (real PDF on a device), screenshots |
| Pro #32 home-screen widgets | ✅ | `WidgetContentTest` (Glance unit tests); live widgets checked on a device only |
| Pro #33 themes and app icons | ✅ | `ColorThemesTest` (contrast), DataStore test, icon switcher test, Appearance screenshots |
| Pro #34 quick-settings tiles and shortcuts | ☑️ | Built and lint-checked; tiles and shortcuts need a device/launcher to try |
| Pro #35 Wear OS companion | ☑️ | `wear` module built in CI, `WearStateTest`; Data Layer sync needs a phone + watch with Google Play services (see docs/PRO.md) |
| Privacy: no tracking; network only for the opt-in AI assistant; permission allowlist | ✅ | CI checks the merged release manifest against `tools/allowed-permissions.txt` |
| Security review | ✅ | [SECURITY_REVIEW.md](SECURITY_REVIEW.md) |

## Release

| Item | Status | Notes |
|---|---|---|
| CI workflow (tests, screenshots, lint, builds, device tests) | ✅ | `.github/workflows/ci.yml` — green on `main` ([run #2](https://github.com/BehnamJalaliCo/Plan-B/actions/runs/37180478313)) |
| Manual release workflow (signing from secrets, GitHub Release, optional Pishkhan) | ☑️ | `.github/workflows/release.yml`; packaging script dry-run verified locally with a throwaway key that was deleted afterwards |
| Cafe Bazaar packaging with the official bundle-signer | ☑️ | `tools/package_cafebazaar.sh` (pinned version + SHA-256) |
| Store material (listing fa/en, icon, covers, screenshots, what's new) | ✅ | `store/cafebazaar/` |
| Signed v1.0.0 binaries, tag and GitHub Release | ⏳ | Needs the owner's release keystore as GitHub secrets (`PLANB_KEYSTORE_BASE64`, `PLANB_KEYSTORE_PASSWORD`, `PLANB_KEY_ALIAS`, `PLANB_KEY_PASSWORD`) plus the fonts passphrase `PLANB_FONTS_PASSPHRASE`, then running the Release workflow |
| Upload to Cafe Bazaar | ⏳ | Manual upload of the release artifact, or the workflow's Pishkhan option with `CAFEBAZAAR_PISHKHAN_API_SECRET` |

## Known limitations

- Exports and backups are not encrypted (by design, so they stay portable).
- Device benchmark numbers have not been collected yet (no virtualization in the development
  container); JVM performance bounds are enforced in CI.
- Exact alarms depend on the user granting the permission; otherwise reminders may arrive a
  few minutes late.
