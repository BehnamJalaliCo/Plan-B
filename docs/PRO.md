# Plan-B Pro

Plan-B Pro is an optional upgrade sold through Cafe Bazaar. **Every feature that exists today
stays free, forever.** Pro only unlocks *new* features, which are built in work packages on top
of the foundation described here.

## Products and prices

| Product | Cafe Bazaar product id | Type | Price |
|---|---|---|---|
| Monthly | `planb_pro_monthly` | Subscription (renews monthly) | 399,000 toman |
| Lifetime (recommended) | `planb_pro_lifetime` | In-app product, non-consumable — **never consumed** | 1,999,000 toman |

Prices are set in the Cafe Bazaar developer panel (Pishkhan). The Pro screen shows the store's
localized price from the SKU details; when Bazaar gives none (offline, debug builds) it shows the
amounts above (`FallbackPrices` in `feature/pro`), formatted with the user's digit setting.

## The 40 Pro features

The numbers and ids are the ones in `core/ui/.../ProFeature.kt` (ids are stable; never rename).

**Planning & calendar**
1. `persian_quick_add` — Persian natural-language quick add
2. `iran_holidays` — official Iranian holidays, occasions and Hijri (lunar) dates
3. `calendar_sync` — two-way sync with the device calendar (Google Calendar through Android's calendar provider)
4. `advanced_recurrence` — advanced recurrence ("second Monday of every month", "3 days after completion")
5. `auto_planning` — automatic day planning (auto-schedule tasks into free time)
6. `time_blocking` — time blocking (drag tasks onto calendar hours)
7. `day_timeline` — vertical day timeline
8. `daily_rituals` — morning planning and evening shutdown ritual
9. `project_timeline` — project Gantt/timeline view
10. `smart_lists` — custom smart lists and filters
11. `deadlines` — separate deadline and planned date
12. `multiple_reminders` — up to 5 reminders per task, nagging reminder until done
13. `eisenhower` — Eisenhower matrix
14. `dependencies` — task dependencies

**Notes**
15. `rich_notes` — images, attachments, tables and pen drawing in notes
16. `note_links` — links between notes and note version history
17. `document_scan` — document scan and text search inside images
18. `handwriting` — handwriting to text
19. `voice_notes` — voice recording in notes with transcription
20. `note_databases` — simple database tables in notes
21. `note_graph` — note graph view
22. `web_clipper` — web clipper (save from the browser via Android share)
23. `math_charts` — math formulas and charts in notes
24. `focus_writing` — distraction-free writing with word goals
25. `journal` — daily journal with prompts and a mood calendar

**Habits & focus**
26. `focus_pro` — Focus Pro (ambient sounds, strict mode with Do Not Disturb)
27. `health_connect` — Health Connect sync (auto-check habits like steps, sleep, water)
28. `habit_stats` — advanced habit statistics
29. `challenges` — challenges and badges
30. `mood_tracker` — mood and energy tracker

**Reports & personalization** (implemented, see [Reports and personalization](#reports-and-personalization-3135))
31. `reports` — statistics, yearly report ("my year") and PDF export ✅
32. `widgets` — home-screen widgets ✅
33. `themes` — premium themes and app icons ✅
34. `quick_tiles` — quick-settings tile and launcher shortcuts ✅
35. `wear_os` — Wear OS companion ✅ (see limitations)

**Security & data**
36. `app_lock` — app lock with fingerprint and encrypted notes
37. `auto_backup` — automatic scheduled backups keeping the last 21 versions
38. `trash_history` — 30-day trash and activity history

**AI**
39. `ai_assistant` — AI assistant with your own provider key
40. `voice_input` — Persian voice input

## Modules

| Module | Contents |
|---|---|
| `core:billing` | `ProProduct`, `Entitlement`, `BillingClient`, `BazaarBillingClient` (Poolakey 2.2.0 from JitPack, group-filtered), `FakeBillingClient`, `PurchaseSignatureVerifier`, `EntitlementPolicy`, `EntitlementRepository` |
| `core:ui` | `ProFeature` (+ groups, strings, icons), `LocalProAccess`, `ProGate`, `rememberProGuard`, `ProTeaser`, `ProBadge` |
| `feature:pro` | `PaywallRoute(featureId)`, `PaywallViewModel`, `PaywallScreen` (paywall and "Your Pro") |
| `core:ai` | AI provider catalog, encrypted key storage, `AiClient` (infrastructure for #39) |
| `app` | `BillingModule` (Bazaar in release, fake in debug), `ProStatusViewModel`, provides `LocalProAccess`, Pro routes, More entry |

## Gating rules (for work packages)

- **Free stays free.** Never gate an existing feature, and never reduce what free users have.
- Gate a Pro *screen or section* with `ProGate(ProFeature.X) { … }`: Pro users see the content,
  others see a `ProTeaser` that explains it and offers "Unlock with Pro".
- Gate a Pro *action* (a button, a menu item) with `val guard = rememberProGuard()` and
  `guard.run(ProFeature.X) { doIt() }`. For non-Pro users this opens the Pro screen focused on
  that feature. Mark such actions with `ProBadge()`.
- **No nagging.** The Pro screen opens only in reply to the user's tap. No pop-ups on start,
  no timers, no repeated prompts, no interruptions of free flows.
- Feature modules depend only on `core:ui` for gating (`LocalProAccess`), never on
  `core:billing` and never on `feature:pro`.
- Data created with a Pro feature stays readable and exportable if Pro ends (for example after
  a subscription lapses): show it read-only rather than hiding it.

## Entitlement and offline use

`EntitlementRepository` is the single source of truth (`entitlement`, `isPro`):

- Every store answer (purchase, query, restore) is cached in a device-only DataStore file
  (`planb_entitlement`), which is never part of a backup, so a backup cannot grant Pro.
- **Lifetime** never expires offline.
- **Monthly** stays active for **7 days after the last successful check** with Bazaar (or until
  a known expiry, whichever is later). A verification time far in the future (clock moved back)
  does not extend it. When Bazaar answers again, its answer wins (a cancelled subscription or a
  refund ends Pro).
- The app re-checks at start (`PlanBApplication`) and when the Pro screen opens.
- "Restore purchases" asks Bazaar for the purchases of the signed-in Bazaar account.

## Purchase verification and `PLANB_BAZAAR_RSA_KEY`

Purchases are verified **on the device** with the app's Cafe Bazaar RSA public key: by
Poolakey (`SecurityCheck.Enable`) and again by `PurchaseSignatureVerifier` (SHA1withRSA over the
purchase JSON) plus a package-name check. The key is injected at build time as
`BuildConfig.BAZAAR_RSA_KEY` from the environment variable or Gradle property
`PLANB_BAZAAR_RSA_KEY` and is never committed. Open-source builds without it report "not
configured", and the Pro screen says purchases are not available in this version.

How to get and set the key:

1. Sign in to [Pishkhan](https://pishkhan.cafebazaar.ir) → your app → **In-app payments /
   Poolakey** (the "RSA key" or "public key" of the app). Copy the Base64 text.
2. In Pishkhan, create the two products with the ids above (subscription and in-app product).
3. GitHub: add the repository (or `release` environment) secret `PLANB_BAZAAR_RSA_KEY`. The
   Release workflow passes it to the build; it is optional (a warning is shown when missing).
4. Local release builds: `export PLANB_BAZAAR_RSA_KEY=…` or add
   `PLANB_BAZAAR_RSA_KEY=…` to `~/.gradle/gradle.properties` (never to the repository).

## Testing purchases

- **Debug builds** use `FakeBillingClient` (`BuildConfig.FAKE_BILLING`): purchases succeed at
  once and no money is involved. To switch Pro on or off, open **Settings → About** and
  **long-press the version line**; a hidden developer dialog offers Not Pro / Monthly /
  Lifetime. The choice is kept in a private file of the debug app. Release builds have no such
  section (`DeveloperBilling.Disabled`).
- **Unit tests**: `core/billing` (policy, grace period, lifetime, restore, signature
  verification), `feature/pro` (paywall states, purchase, restore, gating) use the fake client.
- **Real Bazaar purchases** need a release-signed build with the release package name
  (`com.behnamjalali.planb`, not the `.debug` one), `PLANB_BAZAAR_RSA_KEY` set, the products
  published in Pishkhan, and the Cafe Bazaar app signed in on the device. Use Pishkhan's
  in-app billing test options (test accounts) if your account has them, otherwise make a real
  purchase with a developer account and refund it from Pishkhan. Check: buy monthly, buy
  lifetime, cancel, "Restore purchases" after reinstalling, and airplane mode (Pro stays).

## Schema overview (v3)

All Pro data lives in schema v3, added in one migration; see [DATABASE.md](../DATABASE.md)
§3.18–3.30 and [BACKUP_FORMAT.md](BACKUP_FORMAT.md) (format 2). Work packages must not change
the schema.

| Feature(s) | Storage |
|---|---|
| 4 advanced recurrence | `tasks.recurrence` text: reserved `BASIS=COMPLETION`, `BYSETPOS` |
| 5, 6, 7 auto planning, time blocking, timeline | `tasks.scheduled_start`, `scheduled_end`, `estimated_minutes` |
| 10 smart lists | `saved_filters` |
| 11 deadlines | `tasks.deadline` (`due_date` is the planned date) |
| 12 reminders | `task_reminders` (up to 4 extra) + `tasks.reminder_offset_minutes`, `tasks.nag` |
| 14 dependencies | `task_dependencies` |
| 15, 17, 18, 19 attachments, scan, OCR, voice | `attachments` (+ files in `files/attachments/`, included in backups) |
| 16, 21 links, history, graph | `note_links`, `note_versions` |
| 25, 30 journal, mood | `journal_entries`, `mood_entries` |
| 26 Focus Pro | `focus_sessions.sound_id`, `strict` |
| 27 Health Connect | `habits.health_metric`, `health_threshold` |
| 29 challenges and badges | `challenges`, `badges` |
| 3 calendar sync | `calendar_links` |
| 36 encrypted notes | `notes.locked`, `encrypted_payload` |
| 38 trash and history | `tasks.deleted_at`, `notes.deleted_at`, `activity_log` |

Preferences-only features (themes, widgets configuration, app lock settings, backup schedule)
use DataStore; secrets never go into the exported preferences file.

## AI assistant (#39) infrastructure

`core:ai` holds the provider catalog (`AiProviders`: generic Iranian OpenAI-compatible gateway,
AvalAI, DeepSeek, Qwen/DashScope, OpenRouter, Groq, OpenAI, Anthropic, Gemini, custom), the
`AiSettingsRepository` (off by default, explicit consent timestamp, key encrypted with an
Android Keystore AES-GCM key in a device-only file) and `AiClient` (OpenAI chat-completions and
Anthropic Messages, HTTPS only, timeouts, `testConnection()`, `listModels()`). Errors map to
neutral categories (`INVALID_KEY`, `NETWORK_UNREACHABLE`, `RATE_LIMITED`, `PROVIDER_ERROR`);
the UI must only ever say to check the key or the internet connection. Only the text the user
chooses is sent, directly to their provider. Anthropic has no suggested model ids in the
catalog: the assistant UI should offer `listModels()` results.

## Reports and personalization (#31–35)

| # | Where | What |
|---|---|---|
| 31 | `feature:reports` (`StatisticsRoute`, `YearReportRoute`), More → Statistics | Week/month/year statistics in the user's calendar (Jalali or Gregorian, `StatsPeriods` in `core:datetime`): tasks done per day/month, completion rate, on time vs late, busiest weekday and hour, focus minutes, habit success, notes written, project progress, top tags and projects. Charts are drawn on a Canvas, mirrored for RTL, with a spoken summary. "My year" is a story-like yearly summary. Aggregation is pure Kotlin (`StatisticsCalculator` in `core:model`) over a read-only `StatisticsDao` (no schema change). |
| 31 | `core:ui` `pdf/` | `PdfReportWriter` renders reports on A4 `PdfDocument` pages with `StaticLayout` in the app typeface (Persian shaping, RTL alignment, mirrored bars); `rememberPdfExport` saves through `ACTION_CREATE_DOCUMENT` (no permission). Statistics, My year and the weekly review (button at the end of the review) export PDFs. |
| 32 | `app` `widget/` | Glance widgets: Today (progress, next tasks, tap to complete), Quick add (opens Quick Capture), Habits (tap to check in), Focus (start/pause, a platform countdown), Monthly calendar (dots on busy days). Material You colors on Android 12+, the Plan-B palette before; light/dark; resizable; texts and digits follow the app's language, calendar and digit settings. Updated after any write to the shown tables (`DataChangeWatcher` observes Room's invalidation tracker and calls the `WidgetUpdater` bound in the app), at midnight (inexact alarm, only while widgets exist) and on clock/time-zone changes. |
| 33 | `core:designsystem` `ColorThemes.kt`, Settings → Themes and app icon (`AppearanceRoute`) | Color themes Ocean, Forest, Sunset, Blossom and Midnight (true black in dark mode), stored as `color_theme`; contrast is unit-tested. Four alternate launcher icons (Ocean, Sunset, Forest, Midnight) derived from the B mark, each with the monochrome themed-icon layer, switched with activity aliases (`LauncherIconSwitcher`, `DONT_KILL_APP`). |
| 34 | `app` `quick/` | Quick-settings tiles "Quick add" and "Focus"; launcher shortcuts. |
| 35 | `wear` module + `app` `wear/` | Wear OS app (Compose for Wear OS, minSdk 30) with today's tasks (tap to complete) and habit check-ins, synced through the Wearable Data Layer. |

**Gating decisions.**
- Statistics, My year and PDF export: the screens show a calm `ProTeaser` to free users; the
  weekly review stays free and only its new PDF button is Pro (`rememberProGuard`).
- Widgets: every widget can be added by anyone, but without Pro it shows a "Plan-B Pro"
  placeholder that opens the Pro screen when tapped (widgets cannot be hidden from the picker
  per user).
- Themes and icons: free users see the choices with a Pro badge; tapping a premium one opens the
  Pro screen. If Pro ends, the classic palette is drawn again (the choice is kept); the launcher
  icon stays as chosen until changed.
- **#34 decision:** the static launcher shortcuts **New task** and **New note** are basic UX and
  stay **free**. The **quick-settings tiles** and the **dynamic shortcuts** (Today, Start focus)
  are **Pro**: anyone may add a tile, but without Pro a tap opens the Pro screen; dynamic
  shortcuts are only published while the user has Pro and removed when Pro ends.
- Wear OS: the phone sends data only while the user has Pro; otherwise the watch says Pro is
  needed on the phone.

**Wear OS notes and limitations.**
- The watch app uses the phone's application id (`com.behnamjalali.planb`, `.debug` for debug
  builds), which the Data Layer requires, and must be **signed with the same key** as the phone
  app. It is a separate APK/AAB (`./gradlew :wear:bundleRelease`); CI builds it, but
  `tools/package_cafebazaar.sh` ships only the phone app. Wear apps are distributed through
  Google Play (Cafe Bazaar has no Wear OS store); publishing it is the owner's decision.
- The phone app works unchanged without Google Play services: every Wearable call is behind a
  `GoogleApiAvailability` check and `runCatching`, and nothing is sent without a connected watch.
  `play-services-wearable` adds no permission to the phone's release manifest.
- The watch shows today's open tasks and scheduled habits (up to 20 each) and works while the
  phone is reachable; it keeps the last synced copy, but changes made offline on the watch are
  not queued.

**Platform notes.**
- Glance runs widget updates through AndroidX WorkManager, which adds the normal (install-time,
  no user prompt) permissions `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` to
  the merged manifest; they are allowlisted in `tools/allowed-permissions.txt`. No runtime
  permission was added.
- The launcher entry is now the `.LauncherClassic` alias instead of `MainActivity` itself. Some
  launchers drop a home-screen shortcut once when the app updates to this version or when the
  icon is switched; the Appearance screen says so, and the app is reachable from the app list.

