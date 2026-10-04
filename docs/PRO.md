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

**Reports & personalization**
31. `reports` — statistics, yearly report ("my year") and PDF export
32. `widgets` — home-screen widgets
33. `themes` — premium themes and app icons
34. `quick_tiles` — quick-settings tile and launcher shortcuts
35. `wear_os` — Wear OS companion

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

## Implemented: Security & data (#36–#38)

All three are built (module `feature:security`, data layer in `core:data` and `core:backup`).
Things set up while Pro was active stay manageable if Pro ends: App lock and locked notes can
still be opened and switched off, the trash can still be restored or emptied (and is purged
after 30 days), automatic backups can be switched off.

**#36 App lock and locked notes** — Settings › Security.
- *App lock* (`AppLockController`, `AppLockGate`): BiometricPrompt with the device's own
  fingerprint/face or screen lock (`BIOMETRIC_WEAK | DEVICE_CREDENTIAL`); turning it on asks
  once to confirm. Locks on a cold start and after the chosen time in the background
  (immediately, 1, 5 or 15 minutes; `AppLockPolicy`, wall and monotonic clocks, rotation and the
  prompt itself don't count). "Hide in recent apps" (default on) uses
  `setRecentsScreenshotEnabled(false)` on Android 13+, `FLAG_SECURE` before. Settings live in
  the device-only `planb_security` DataStore.
- *Locked notes* (`NoteCrypto`, `NoteVault`, `NoteRepository.lockNote/removeLock`): the user
  sets a passphrase once (confirmed, with a warning that it cannot be recovered). The key is
  PBKDF2-HMAC-SHA256 (600,000 iterations, random salt kept in DataStore with a check value);
  bodies are AES-256-GCM envelopes in `notes.encrypted_payload` that carry their own salt, so a
  restored backup opens on another device with the same passphrase (that device adopts the
  salt). The key stays in memory until the vault locks (App lock, 5 minutes in the background,
  "Close locked notes now"). Optional fingerprint unlock wraps the key with a biometric-bound
  Keystore key (`BiometricKeyStore`). Locked bodies never reach search (title only), drafts,
  versions, exports, templates or logs.

**#37 Automatic backups** — Settings › Backup & restore › Automatic backup. `AutoBackupWorker`
(WorkManager, daily/weekly, optionally charging only) runs `AutoBackupRunner`: the regular
backup ZIP into a SAF folder picked with `ACTION_OPEN_DOCUMENT_TREE` (persisted permission,
works with Google Drive), named `Plan-B-auto-yyyyMMdd-HHmmss.zip`, then only our own files
beyond the newest 21 are deleted (`AutoBackupNaming`). Last run, last success and a plain-words
error are shown; "Back up now"; a notification only on failure. See
[BACKUP_FORMAT.md](BACKUP_FORMAT.md) §2.

**#38 Trash and activity history** — More › Trash, More › Activity, and History from the task
and note editors. For Pro users task/note deletions set `deleted_at` (subtasks share the
parent's time); free users keep today's undo-then-permanent delete. Restore re-indexes search
and reschedules reminders; "Delete forever", "Empty trash" and the 30-day purge
(`MaintenanceWorker`, daily, and at start) delete permanently. `DataHistory` writes
`activity_log` rows (created, edited, completed, reopened, deleted, restored, archived; title
only) inside the repositories' transactions for tasks, notes, notebooks, projects, events,
habits and goals, merges edits within 10 minutes and caps the table at 5,000 rows. The data
layer learns about Pro through `ProStatusSource` (bound to the entitlement in `app`).

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
