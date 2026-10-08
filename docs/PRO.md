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

**Planning & calendar** (#2–#4, #6, #7 and #9–#14 implemented, see [Planning](#planning-4-914) and [Calendar](#calendar-2-3-6-7))
1. `persian_quick_add` — Persian natural-language quick add
2. `iran_holidays` — official Iranian holidays, occasions and Hijri (lunar) dates ✅
3. `calendar_sync` — two-way sync with the device calendar (Google Calendar through Android's calendar provider) ✅
4. `advanced_recurrence` — advanced recurrence ("second Monday of every month", "3 days after completion") ✅
5. `auto_planning` — automatic day planning (auto-schedule tasks into free time)
6. `time_blocking` — time blocking (drag tasks onto calendar hours) ✅
7. `day_timeline` — vertical day timeline ✅
8. `daily_rituals` — morning planning and evening shutdown ritual
**Planning & calendar** (#1, #4, #5, #8 and #9–#14 implemented, see [Planning](#planning-4-914) and [Smart day](#smart-day-1-5-8))
1. `persian_quick_add` — Persian natural-language quick add ✅
2. `iran_holidays` — official Iranian holidays, occasions and Hijri (lunar) dates
3. `calendar_sync` — two-way sync with the device calendar (Google Calendar through Android's calendar provider)
4. `advanced_recurrence` — advanced recurrence ("second Monday of every month", "3 days after completion") ✅
5. `auto_planning` — automatic day planning (auto-schedule tasks into free time) ✅
6. `time_blocking` — time blocking (drag tasks onto calendar hours)
7. `day_timeline` — vertical day timeline
8. `daily_rituals` — morning planning and evening shutdown ritual ✅
9. `project_timeline` — project Gantt/timeline view ✅
10. `smart_lists` — custom smart lists and filters ✅
11. `deadlines` — separate deadline and planned date ✅
12. `multiple_reminders` — up to 5 reminders per task, nagging reminder until done ✅
13. `eisenhower` — Eisenhower matrix ✅
14. `dependencies` — task dependencies ✅

**Notes** (#15, #17–#20 and #23 implemented, see [Rich notes](#rich-notes-15-1720-23))
15. `rich_notes` — images, attachments, tables and pen drawing in notes ✅
16. `note_links` — links between notes and note version history
17. `document_scan` — document scan and text search inside images ✅
18. `handwriting` — handwriting to text ✅
19. `voice_notes` — voice recording in notes with transcription ✅
20. `note_databases` — simple database tables in notes ✅
21. `note_graph` — note graph view
22. `web_clipper` — web clipper (save from the browser via Android share)
23. `math_charts` — math formulas and charts in notes ✅
24. `focus_writing` — distraction-free writing with word goals
25. `journal` — daily journal with prompts and a mood calendar
**Notes** (#16, #21, #22, #24, #25 implemented, see [Notes knowledge](#notes-knowledge-16-21-22-24-25))
15. `rich_notes` — images, attachments, tables and pen drawing in notes
16. `note_links` — links between notes and note version history ✅
17. `document_scan` — document scan and text search inside images
18. `handwriting` — handwriting to text
19. `voice_notes` — voice recording in notes with transcription
20. `note_databases` — simple database tables in notes
21. `note_graph` — note graph view ✅
22. `web_clipper` — web clipper (save from the browser via Android share) ✅
23. `math_charts` — math formulas and charts in notes
24. `focus_writing` — distraction-free writing with word goals ✅
25. `journal` — daily journal with prompts and a mood calendar ✅

**Habits & focus** (implemented, see [Habits and focus](#habits-and-focus-2630))
26. `focus_pro` — Focus Pro (ambient sounds, strict mode with Do Not Disturb) ✅
27. `health_connect` — Health Connect sync (auto-check habits like steps, sleep, water) ✅
28. `habit_stats` — advanced habit statistics ✅
29. `challenges` — challenges and badges ✅
30. `mood_tracker` — mood and energy tracker ✅

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

**AI** (implemented, see [AI assistant and voice input](#ai-assistant-and-voice-input-39-40))
39. `ai_assistant` — AI assistant with your own provider key ✅
40. `voice_input` — Persian voice input ✅

## Modules

| Module | Contents |
|---|---|
| `core:billing` | `ProProduct`, `Entitlement`, `BillingClient`, `BazaarBillingClient` (Poolakey 2.2.0 from JitPack, group-filtered), `FakeBillingClient`, `PurchaseSignatureVerifier`, `EntitlementPolicy`, `EntitlementRepository` |
| `core:ui` | `ProFeature` (+ groups, strings, icons), `LocalProAccess`, `ProGate`, `rememberProGuard`, `ProTeaser`, `ProBadge` |
| `feature:pro` | `PaywallRoute(featureId)`, `PaywallViewModel`, `PaywallScreen` (paywall and "Your Pro") |
| `core:ai` | #39: provider catalog, encrypted key storage, `AiClient` (`AiApi`: chat, SSE streaming, test, model list), `AiAssistant`, `AssistantPrompts`, `AssistantParsing`, `AiContextBudget` |
| `core:speech` | #40 (and #19's recognizer plumbing): `VoiceDictation` / `PlatformVoiceDictation`, `SpeechIntents`, `VoiceInputViewModel`, `VoiceInputButton`, `FakeVoiceDictation` |
| `feature:assistant` | #39: `AssistantRoute` (chat, plan my day/week), `AiSettingsRoute`, `AiActionSheet` (contextual actions), `PlanValidator` |
| `core:calendarsync` | #3: `DeviceCalendarStore` (CalendarContract behind an interface; `ContentResolverCalendarStore`, `FakeDeviceCalendarStore`), `CalendarSyncEngine`, `CalendarSyncRepository`, `CalendarSyncController` + `CalendarSyncWorker` |
| `feature:journal` | #25: `JournalRoute`, `MoodCalendarRoute` and their ViewModels; #30: `MoodTrackerRoute`, `MoodTrackerViewModel` |
| `core:focus` | #26: `NoiseGenerator` (generated ambient sounds), `AmbientPlayer`, `FocusSoundService` (media-playback foreground service), `DndController`/`SystemDndController`, `StrictModeCoordinator`, `FocusProEffects` (`FocusSessionEffects` + `FocusProControls`) |
| `core:health` | #27: `HealthConnectDataSource` (Health Connect client 1.1.0, aggregated daily totals) and `HealthConnectPermissions` (per-metric read permission, request contract, install/open links) |
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
`BuildConfig.BAZAAR_RSA_KEY` from `core/billing/bazaar-rsa-public-key.txt` (it is a public key
and ships inside every APK anyway); the environment variable or Gradle property
`PLANB_BAZAAR_RSA_KEY` overrides it. Builds without any key report "not configured", and the Pro
screen says purchases are not available in this version.

How to get and set the key:

1. Sign in to [Pishkhan](https://pishkhan.cafebazaar.ir) → your app → **In-app payments /
   Poolakey** (the "RSA key" or "public key" of the app). Copy the Base64 text.
2. In Pishkhan, create the two products with the ids above (subscription and in-app product).
3. Put it in `core/billing/bazaar-rsa-public-key.txt` (done for Plan-B). To build with another
   key, set `PLANB_BAZAAR_RSA_KEY` (environment, `~/.gradle/gradle.properties` or the GitHub
   secret of the same name, which the Release workflow passes to the build).

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

## Planning (#4, #9–#14)

Data lives in the v3 schema (no schema change): `tasks.recurrence`, `tasks.deadline`,
`tasks.nag`, `task_reminders`, `task_dependencies`, `saved_filters`. Free users see each entry
point with a Pro badge or a `ProTeaser`; a tap opens the Pro screen for that feature. Data made
while Pro was active stays visible and working if Pro ends (lists stay selectable, reminders keep
firing, deadlines and locks keep showing).

| # | Where | How it works |
|---|---|---|
| 4 | Task editor › Repeat › Custom (`CustomRecurrenceDialog`, `allowAfterCompletion`) | `RecurrenceRule` gains `setPosition` (`BYSETPOS`, 1–5 or −1 = last), `basis` (`BASIS=COMPLETION`) and `weekStart` (`WKST`), written only when used, so older rules keep their text. `RecurrenceEngine` takes, in each month of the rule's calendar (Jalali or Gregorian), the days with the chosen weekday and picks the n-th; **a month without a fifth weekday is skipped** (never moved to the fourth/last). "Count from completion": completing a task plans the next one *interval* after today (`RecurrenceEngine.nextAfterCompletion`); its count is carried as "occurrences left" by each new occurrence (reopening gives it back). "Every N weeks" counts weeks from the user's first day of week (Pro; free rules keep the calendar's week). The dialog shows the rule in words (`recurrenceSummary`, fa/en, user's digits). |
| 9 | Project › Timeline tab (`ProjectTimeline`, `TimelineLayout`) | One Canvas scrolling both ways, drawing only the visible days and rows (smooth with hundreds of tasks). A bar runs from start date (or planned date, or deadline) to deadline (or planned date); milestones are diamonds; overdue bars use the error color, done ones are muted; a today line; dependency connectors from a blocker's end to the waiting task's start. Time flows from the reading start (right-to-left in Persian). Days/weeks zoom, sticky date header in the user's calendar, tap a bar to open the task, a spoken summary and "Open …" accessibility actions. Undated tasks are counted, not drawn. |
| 10 | Tasks › chips after the built-in views; "+ Smart list"; Smart lists screen | `SmartListRepository` over `saved_filters`; `query` is the versioned JSON document of `SmartFilterCodec` (version 1: `projects`, `noProject`, `tags`, `priorities`, `statuses`, `date` = `OVERDUE`/`TODAY`/`NEXT_7_DAYS`/`NO_DATE`/`CUSTOM` with `from`/`to`, `hasDeadline`, `text`, `sort`; readers ignore unknown keys and values). `SmartFilterEvaluator` filters live, top-level, non-archived tasks (open ones unless a status says otherwise; text matching uses `SearchNormalizer`). Lists have a name, icon and color, a live match count while editing, and can be reordered and deleted. |
| 11 | Task editor › Deadline; task rows; Today; sort menu | `tasks.deadline` is the hard deadline, `due_date` stays the planned date. Rows show "Deadline in 2 days" (error color when passed or today, warning within 2 days). **Overdue follows the deadline when there is one** (`Task.isOverdue`), otherwise the planned date as before. Today (and the Tasks Today view) also lists tasks whose deadline is at most 3 days away. Sort by deadline. Recurring tasks shift the deadline with the occurrence. |
| 12 | Task editor › More reminders / Nag until done; notifications | Up to four extra reminders in `task_reminders`: `OFFSET` (before the planned time), `DEADLINE` (before 09:00 of the deadline day) or `ABSOLUTE`; a `NAG` row stores a non-default nag interval (5/10/15/30 min, default 10). `ReminderPlanner.forTask` computes the next moment of all of them plus a snooze and the nag repetitions (at most 12 after each reminder), so **one alarm per task** (same request code, same intended-time stale check) covers everything. Reminders with Pro data get **Done**/«انجام شد» and **Snooze** buttons (explicit broadcasts with `FLAG_IMMUTABLE` to the non-exported `ReminderActionReceiver`, handled with `goAsync`; button request codes use free slots of the `id × 8 + code` scheme). Swiping a nagging reminder away stops the nag for the reminders that fired. Snooze/dismiss state is device-only (`planb_nag_state` DataStore). Relative rows follow a recurring task to its next occurrence. |
| 13 | Tasks › matrix icon (`EisenhowerRoute`) | **Important = priority High or Medium; urgent = overdue, or deadline or planned date within the chosen 1, 2, 3 or 7 days** (default 2). Hold a task and drag it to another quadrant (or use its accessibility actions): becoming important sets High, losing it sets Low; becoming urgent plans it for today, losing it plans it for the first day after the window. A deadline is never moved: if it alone keeps the task urgent, a message says so. Urgent quadrants sit on the reading-start side. |
| 14 | Task editor › Waits for; task rows; timeline | `task_dependencies` (task waits for blocker). The editor picks tasks with search; a choice that would close a circle (DFS over all dependencies, tolerant of cycles already in restored data) is refused with a message, and the repository rejects it too (`DependencyCycleException`). Task lists carry the number of open blockers: a lock badge, and checking off a blocked task (checkbox, swipe, editor status) asks "Complete anyway?". Completing or deleting a blocker unblocks at once. |


## Smart day (#1, #5, #8)

No schema change: time blocks are `tasks.scheduled_start`/`scheduled_end`, ritual reflections
are journal pages (`journal_entries` + a note), working hours, ritual reminder times and the
top 3 are user preferences (`day_plan_*`, `ritual_*` keys, part of the exported preferences).
Free users see a teaser (Quick Capture hint, Today card and header button, Settings row with a
Pro badge); each opens the Pro screen only when tapped. If Pro ends, blocks and journal pages
stay, ritual reminders that were switched on keep firing and can be switched off.

### #1 Natural-language quick add (`core:nlp`, `QuickAddParser`)

Fully on the device, deterministic, table-tested (231 Persian/English cases). Quick Capture
reads the text while typing (tasks: every kind; events: date, time, repeat, duration,
reminder), highlights recognized parts inside the field and lists them as chips; tapping a chip
dismisses that interpretation (its words stay in the title; the dismissal is keyed by kind and
words, so it survives further typing). The date/time/priority chips show what will be saved; a
date, time or priority picked by hand dismisses the matching part. Rules:

- **Normalization** for matching only: Persian/Arabic-Indic digits → ASCII, Arabic ي/ك → ی/ک,
  half-spaces, diacritics and the ezafe «ی»/«ٔ» after «ه» ignored, so «پس‌فردا», «پس فردا» and
  «پسفردا» are the same. Ordinal endings («۱۵ام», "15th") are dropped.
- **Reading order**: word by word from the start; at each word the first matching rule wins in
  this order: #tag, @project, `!`/`!!`/`p1`, reminder, deadline, repeat, relative time, date,
  time, duration, priority word. Each kind is taken once (later mentions stay in the title),
  tags repeat.
- **Dates**: امروز/today, فردا/tomorrow, پس‌فردا/"day after tomorrow", امشب/tonight (today,
  evening hours). A weekday alone is the next one *after* today; «این/همین …»/"this …" may be
  today; «… بعد/آینده/دیگه» and "next …" are that weekday of next week (by the user's first
  day of week). «آخر هفته»/"weekend" is the weekend day on or after today (Friday for weeks that
  start on Saturday, otherwise Saturday); «هفته بعد»/"next week" is the first day of next week;
  «اول ماه (بعد)»/«ماه بعد»/"next month" the 1st of next month and «آخر ماه»/"end of month" the
  last day of this month, in the user's calendar; «سال بعد» the first day of next year.
  «۳ روز دیگه/دیگر/بعد», "in 3 days", "3 days from now" (days, weeks, months, years; months in
  the user's calendar). «۱۵ مهر» (Jalali month names always Jalali), «۲۵ دسامبر», "oct 15",
  "15th of october", with an optional year; without a year the next such day (a day that
  doesn't exist, «۳۱ مهر», is not a date). Numeric «۱۵/۷» is day/month in the user's calendar
  (month/day when the second number can't be a month, "10/15"); «۱۴۰۵/۸/۱», "2026-12-01" choose
  the calendar by the year (1300–1699 Jalali, 1900–2299 Gregorian).
- **Times**: «ساعت ۵»/"at 5" (a bare number counts only after «ساعت»/"at"), «۵ عصر», «۸ صبح»,
  «۱۰ شب», «۱ ظهر», «۵ و نیم», "5pm", "17:30". Time-of-day words alone (ظهر 12:00, صبح 09:00,
  بعدازظهر 15:00, عصر 17:00, شب 20:00, نیمه‌شب 00:00; "noon", "this evening" 18:00) count after a
  date, at the end of the text or next to another part («مهمانی شب یلدا» stays text).
  **Ambiguous hours** — 1–11 with no صبح/عصر/am/pm and no leading zero: without a date or
  with today, the next upcoming of h:mm and (h+12):mm (at 18:00 «ساعت ۵» is tomorrow 05:00;
  with «امروز» it stays today 17:00); on another day 1–6 means the afternoon and 7–11 the
  morning; with «امشب»/"tonight" the evening. A time alone puts the task on today, or tomorrow
  when that time has passed.
- **Repeats**: «هر روز/هفته/ماه/سال», «هر ۳ روز (یکبار)», «هر دوشنبه (و پنجشنبه)», «یک روز در
  میان», «روزهای کاری» (Saturday–Wednesday for Saturday weeks, else Monday–Friday), "every day",
  "every other day", "every mon and thu", "every weekday", with «تا …»/"until …" as the end.
  Adjectives («روزانه», "weekly") count only next to another part (English also at the end), so
  «گزارش هفتگی» stays a title. A repeat without a date starts today or on its first weekday.
- **Priority**: فوری, مهم, خیلی مهم, «اولویت بالا», `!!`/`!!!`, `p1`, urgent, important → high;
  `!`, `p2`, «اولویت متوسط» → medium; `p3`, «اولویت پایین/کم», "low priority" → low.
- **Tags** `#کار`, **project** `@name` (existing projects, whole name, half-spaces and spaces
  ignored, or a unique prefix; otherwise the word stays), **deadline** «تا جمعه», «مهلت ۲۰
  مهر», «ددلاین …», "by friday", "deadline oct 20", **duration** «۴۵ دقیقه», «۲ ساعت», «یک ساعت و
  نیم», «نیم ساعت», "for 45 min", "1.5h", **reminder** «یادم بنداز ۱۰ دقیقه قبل», «۱۵ دقیقه قبل
  یادم بنداز», "remind me 1 hour before" (alone: at the time). «۲ ساعت دیگه»/"in 2 hours" is a
  relative time (now + 2 h).
- Numbers that are not part of a pattern stay text: «۳ کتاب بخرم», «سه تا نان», «اتاق ۱۲».
- The rest is the title (connectors and punctuation left at either end removed). A text made
  only of parts is saved with its words as the title.

### #5 Automatic day planning (`DayPlanner` in `core:model`, `DayPlanRepository` in `core:data`)

Today › **Plan my day** (and the morning ritual) builds a preview from a snapshot; nothing is
written until **Accept** (all or some blocks, one transaction). Deterministic rules:

- The day is the working hours (Settings › Day planning and rituals, default 09:00–18:00); on
  the current day it starts at the next 5-minute mark. An optional lunch break is never used.
- Busy: timed events of the day (an end before the start runs to midnight; all-day events don't
  block), tasks with a due time today (for their estimate, default 30 min) and existing time
  blocks; each busy range gets the buffer (default 10 min, 0–30) on both sides, and every placed
  block keeps the buffer before the next.
- Candidates: today's list (planned today or earlier, or deadline within 3 days), open, without
  a time block or a fixed time. Tasks waiting for others (#14) are never scheduled and listed as
  such.
- Order: deadline passed or today, then deadline within 2 days, then the rest; within each,
  higher priority, older planned date, nearer deadline, manual order, id.
- Each task gets its estimate (default 30, minimum 5 min) in the **earliest gap that holds it
  whole** (first fit, never split, blocks start on 5-minute marks); tasks that fit nowhere are
  listed as "didn't fit".
- All maths is on instants with the device zone, so DST days have 23/25 hours and a working-hour
  boundary in a skipped hour moves forward.
- **Replan** (a button on Today when blocks were missed, only on tap): unfinished blocks of the
  last 14 days that started before now, lie on an earlier day or now overlap something busy are
  moved, in their order and length, into the free time left; blocks still ahead stay.
- Today's timeline shows tasks at their block start; the calendar package shows blocks too.

### #8 Morning planning and evening shutdown (`feature:today` `ritual/`)

Today's header button (or a reminder) opens a full-screen step flow. **Morning**: unfinished
tasks from earlier → Today / Tomorrow / Drop (archive, restorable); pick today's top 3 (shown on
Today); today's calendar and free time in the working hours; optional Plan my day; an intention.
**Evening**: what was completed today (a calm celebration); leftovers → Tomorrow / Next week /
Drop; one line about the day; tomorrow's top 3. Moving a task clears its old time block.
Finishing marks the ritual done for the day (`ritual_*_done`). The intention and the reflection
are appended (heading + text) to the **journal page** of the day: a note in the "Journal"
notebook («دفتر روزانه», created when missing) linked from `journal_entries` (`prompt_id`
`ritual_morning`/`ritual_evening`), exactly where the daily journal (#25) reads its pages; a
locked page is never touched. **Reminders** (`RitualReminders` in `core:notifications`): off by
default; one inexact daily alarm per ritual at the chosen time (request codes and notification
ids 8 and 16, the free slot 0 of the `id × 8 + code` scheme), skipped on a day the ritual was
done, opening `planb://open/ritual/morning|evening`; re-armed after delivery, boot, clock or zone
changes and whenever the settings change (also after a restore). Switching one on asks for the
notification permission (Android 13+) in context.

## Notes knowledge (#16, #21, #22, #24, #25)

No schema change: `note_links`, `note_versions`, `journal_entries` and `mood_entries` (v3) hold the
data; writing goals, the journal reminder and custom prompts are user preferences (`writing_*`,
`journal_*` keys, part of the exported preferences). Read queries live in `NoteKnowledgeDao` (no
tables of its own). Pure rules are in `core:model` (`NoteLinks`, `NoteDiff`, `WordCount`,
`HtmlToBlocks`, `GraphLayout`, `JournalPrompts`, `MoodCalendar`), repositories in `core:data`
(`NoteLinkRepository`, `NoteHistoryRepository`, `JournalRepository`), screens in
`feature:notebooks` (`knowledge/`, `history/`, `graph/`, `clipper/`, `writing/`) and the new
`feature:journal`. Free users keep every note feature; the entry points (the `[[` hint, the
editor menu items, the Notebooks chips with a Pro badge, the share sheet) show a teaser or open
the Pro screen only when tapped. Data made with Pro stays: links keep working and showing,
journal pages are ordinary notes, versions stay in backups.

| # | Where | How it works |
|---|---|---|
| 16 links | Note editor: type `[[` | A picker above the block toolbar searches live notes by title (`SearchNormalizer`: Arabic/Persian letters, half-spaces, digits; every typed word must start a title word, titles starting with the search first). Choosing one replaces `[[search` with the token **`[[note:ID\|Title]]`** inside the block text. The editor draws tokens as the target's **current** title (underlined; a trashed or deleted target is struck through in the muted color) with a `VisualTransformation` whose caret never stops inside a link, and a deletion that cuts into a token removes the whole token. With the caret on a link, "Open …" opens it on top of the current note. The end of a note lists **Links in this note** and **Linked from** (backlinks of live notes). `NoteRepository` rewrites `note_links` from the tokens on every save (`saveNote`, `updateContent`, duplicate) in the same transaction: self-links and links to notes deleted for good are dropped (the token stays as text and shows its stored title), a locked note's links are left as they were. Search indexes a link as its title (`NoteDocument.plainText`). Exports: plain text uses the title, Markdown a relative link `[Title](<Title.md>)` (single note) or `[Title](<../Notebook/Title.md>)` in the notes ZIP. |
| 16 history | Note editor › Version history | `NoteHistoryRepository.snapshot` stores the note's **stored** state in `note_versions` (Pro only; never for locked, encrypted or trashed notes; never a duplicate of the newest version). The editor takes one before the first save of an editing session (forced), at most one every **10 minutes** while saving, and one when a changed note is closed. Retention per note: the newest **50**, none older than **90 days**. The screen lists versions with relative times, word counts and "N added, M removed", previews a version and compares it with the note now (`NoteDiff`: block lines with type markers, LCS line diff, linear head/tail trim, above 1,500 lines everything counts as changed); **Restore** first saves the current state as a version, then writes the version back and reopens the editor. Versions are never indexed for search. |
| 21 graph | Notebooks › Note graph | `NoteLinkRepository.observeGraph` (live notes, links between live notes, note tags). `GraphBuilder` filters by notebook and tag, can hide unlinked notes and, above **2,000** notes, keeps the best-connected ones. `GraphLayout` (Fruchterman–Reingold, Barnes–Hut quadtree with θ = 0.8, centre gravity, seeded random start) runs on `Dispatchers.Default`; the same notes always give the same picture. One Canvas draws lines and dots (size by links) with pan and pinch zoom around the fingers; labels for small graphs, when zoomed in, or for the selection. Tap a dot to highlight it with its neighbours (others fade), tap again or "Open" to open it. A list view (and the canvas's spoken summary) serve screen readers. |
| 22 clipper | Android share sheet › **Save to Plan-B** | `ClipperActivity` (exported, `ACTION_SEND` of `text/plain` and `text/html` only, translucent, excluded from recents, behind `AppLockGate`). `ClipIntentInput` rejects other actions and types and reads only the text extras, capped before parsing; streams are never opened. `ClipParser` (offline, no page is fetched): HTML through the sanitizing `HtmlToBlocks` (only text survives; `script`/`style`/`iframe`/`svg`/forms and comments dropped whole; links keep their text plus an `http`/`https`/`mailto` address; entities decoded; control and bidi-override characters removed; at most 500,000 characters in, 2,000 blocks of 20,000 characters out), plain text through the Markdown importer (paragraphs, lists, quotes), the first `http(s)` address as the source link, the title from the sharing app (`EXTRA_TITLE`/`EXTRA_SUBJECT`), the text or the host. The sheet edits the title, picks the notebook, adds tags and opens the note (`planb://open/note/<id>`). Without Pro it shows the teaser; "Unlock" opens the Pro screen in the app. |
| 24 writing mode | Note editor › Writing mode | A full-screen editor over the same editor state (autosave, drafts and history unchanged): no toolbars, a centred column of at most 640 dp, larger type, rich blocks of other kinds shown as a quiet placeholder. `WordCount`: a word is a run of letters and digits; ZWNJ/ZWJ and in-word apostrophes and hyphens keep it together («می‌روم» is one word); links count as their titles; characters exclude invisible marks. Words the note gains during a session count towards the **daily goal** (0, 100…2,000; default 300) shown as a ring; reaching it extends the **streak** (broken after a day without the goal). A session timer (minutes, monotonic clock) and optional **typewriter scrolling** (the focused block is kept mid-screen, without animation when motion is reduced). Back leaves writing mode. |
| 25 journal | Notebooks › Journal (also `planb://open/journal`) | A page per day: a note in the notebook named «دفتر روزانه» / "Journal" (created when missing) linked from `journal_entries` — the same page the morning intention and evening reflection (#8) are appended to, so ritual reflections appear in the journal automatically. Prompts: 60 built-in (fa/en string arrays, keys `p01`…`p60`, append only) plus the user's own (`custom:<hash>`), rotated by date with a stride coprime with the count (every prompt once before any repeats, neighbours never on neighbouring days); "Another prompt" before the page exists; the page starts with the prompt as a quote and records its key. Mood and energy (1–5, icons) are one `mood_entries` row per page (`note_id`), other check-ins of the day are left alone; tags are the page note's tags. Streak = days in a row with a live page (today or up to yesterday). **Mood calendar**: the month in the user's calendar (Jalali or Gregorian, `MonthGrid`), days colored by their average mood, a dot for days with a page; insights over 90 days: average mood per weekday, averages, current and longest streak. Optional **daily reminder** (`JournalReminders`, inexact alarm, request code and notification id **24** = slot 0 of id 3 in the `id × 8 + code` scheme), skipped on a day with a page, re-armed after delivery, boot, clock changes and settings changes; switching it on asks for the notification permission (Android 13+). |

## Habits and focus (#26–#30)

No schema change: v3's `focus_sessions.sound_id`/`strict`, `habits.health_metric`/
`health_threshold`, `challenges`, `badges` and `mood_entries` hold the data; read queries live
in the new `WellbeingDao` (no tables of its own). Pure rules are in `core:model`
(`FocusPro.kt`: `AmbientSound`, `FocusProSettings`, `FocusCycle`, `StrictMode`;
`HealthHabits`; `HabitAnalytics`; `Achievements.kt`: `ChallengeRules`, `BadgeRules`;
`MoodInsights`), repositories in `core:data` (`AchievementRepository`, `MoodRepository`,
`wellbeing/HealthHabitSync`, `wellbeing/WellbeingState`), platform code in the new `core:focus`
and `core:health`, screens in `feature:focus`, `feature:habits` and `feature:journal`, and the
background glue in `app` (`WellbeingSync`). Free users keep every existing habit, focus and
journal feature; each new entry point shows a Pro badge or a `ProTeaser` and opens the Pro
screen only when tapped. If Pro ends: sessions keep their sound and strict flag (the running one
finishes as it started), linked habits keep their metric (shown, no more auto-checks), challenges,
badges and check-ins stay readable.

| # | Where | How it works |
|---|---|---|
| 26 | Focus › Focus Pro card; the running session | **Ambient sounds** (rain, ocean waves, brown, pink and white noise) are **synthesized on the device** by `NoiseGenerator` (22.05 kHz mono: white noise, Paul Kellet's pinking filter, leaky-integrated brown noise, rain = soft pink bed with a slow intensity drift plus ~40 bright decaying drops a second, ocean = brown noise swelling in 7–12 s waves with pink "foam" on the crests) — no audio file, 0 bytes of assets. `AmbientPlayer` streams it to an `AudioTrack` (media usage) with 0.6 s fades (`GainRamp`, squared volume curve), crossfading through silence when the sound changes. Playback runs in `FocusSoundService`, a non-exported foreground service of type **`mediaPlayback`** (normal permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, no prompt) so it continues with the screen off; it follows the active session in Room and stops itself as soon as no running session has a sound (pause, finish, cancel, end alarm). Quiet notification (channel `focus_sound`, id 32 = slot 0 of id 4 in the `id × 8 + code` scheme) with **Stop sound**; the audio focus is respected (muted while another app plays). Picking a sound while idle plays a 6-second preview. **Strict mode** turns on Do Not Disturb ("priority only", so the user's own exceptions still ring) while a strict session **runs**, and puts the previous filter back on pause, finish, cancel or the end alarm: `StrictMode.decide` (pure, tested) + `StrictModeCoordinator` remember the previous and applied filters in the device-only `planb_wellbeing_state` DataStore, so the restore also happens after process death (app start runs `FocusProControls.refresh()`). Do Not Disturb that was already on is never touched; a filter the user changed during the session is left alone. It needs `ACCESS_NOTIFICATION_POLICY` plus the user's "Do Not Disturb access": turning strict mode on without it shows an explanation and opens `Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`; access is re-checked when the screen resumes. **Daily goal** (0–5 h) with progress, and **long breaks** after every 2–6 completed sessions of the day (10–30 min; `FocusCycle`). Defaults are preferences (`focus_sound`, `focus_volume`, `focus_strict`, `focus_daily_goal`, `focus_long_break_every`, `focus_long_break_minutes`, exported) and are copied onto each session started with Pro (`FocusRepository.start(…, soundId, strict)`); the running session's sound and strict mode can be changed (`setSound`, `setStrict`). Every session change, whoever makes it (screen, tile, widget, end alarm), reaches `FocusSessionEffects`. The sound service is started only from the foreground; a start the system refuses (Android 12+ background start) just means no sound. |
| 27 | Habit editor › Check off with Health Connect; habit screen | A habit links to **steps, sleep, water, exercise (active minutes) or distance** with a daily goal in friendly units (steps, hours, ml, minutes, km; stored in the base unit, `HealthUnits`). Access is asked **in context and per metric**: an explanation, then Health Connect's own permission screen (`PermissionController.createRequestPermissionResultContract`) for only that metric's read permission. Not available (Android 8, or no provider) → a plain note; needs install/update (Android 9–13) → a button to the store page. Reads are **aggregated daily totals** (Health Connect de-duplicates sources): the local day, or for sleep the night ending that day (18:00 → 18:00). `HealthHabitSync` checks the scheduled days of the last **7 days**: a day that reached the goal gets enough check-ins to meet the habit's target and is remembered (device-only) so it is never checked again — unchecking it by hand sticks, manual check-ins are never removed. It runs only for Pro users with access, **while Plan-B is in use** (app comes to the foreground, the habits list opens, "Sync now"), at most every 15 minutes unless forced; background reads would need `READ_HEALTH_DATA_IN_BACKGROUND`, which Plan-B does not ask for — days are filled in on the next open instead. The habit screen shows today's amount against the goal. Health Connect's required rationale screen is `HealthPermissionsActivity` (`ACTION_SHOW_PERMISSIONS_RATIONALE`; on Android 14+ the `VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS` alias guarded by `START_VIEW_PERMISSION_USAGE`). Nothing is written to Health Connect, stored or backed up except the check-ins themselves. |
| 28 | Habit screen › Advanced statistics (`HabitStatsRoute`) | `HabitAnalytics`: current and best streak (days, or weeks for "N times per week"), days done, rate since the start, this week/month/year and the **last 12 weeks and 12 months** in the user's calendar (`StatsPeriods`: weeks from the user's first day, Jalali or Gregorian months and years), the **weekday pattern** over the last year (best and hardest weekday, at least 2 due days each), the **30-day trend** against the 30 days before (up/down beyond 5 points, needs 60 days of history) and a **year heatmap** of twelve small months (`MonthGrid`, Jalali or Gregorian) that can be browsed back to the habit's start. Today counts once it is done and is never a miss. Bars run from the reading start; every chart has a spoken summary. The habit screen keeps its free 30-day rate, best streak and 20-week heatmap. |
| 29 | Habits › trophy; habit screen › Start a challenge; More › Challenges and badges; a dialog over any screen | **Challenges** of 7, 21, 30 or 66 days on a habit (one running per habit). `ChallengeRules` (deterministic from check-ins): day schedules need every due day in the window; a due day before today that was missed fails it (today never does); it completes on its last due day. "N times per week" habits count 7-day blocks needing N done days (a shorter last block proportionally fewer). Checking in a missed day afterwards revives the challenge; given-up challenges and those whose habit was deleted stay abandoned. **24 badges** (`BadgeDefinition`, keys stored in `badges.key`, never renamed): habit streaks of 7/21/30/66/100/365 days, 1/10/50/100/500 hours of focus, 10/100/500/1,000 tasks done, journal runs of 7/30/100 days, 10/100 mood check-ins, completed challenges of at least 7/21/30/66 days. `BadgeRules` derives each badge **and the day it was reached** from the data alone (so re-evaluating after a restore or on another device gives the same result); `earned_at` is that day's local midnight; a badge stays once awarded. `AchievementRepository.evaluate()` writes challenge statuses and new badges; `WellbeingSync` runs it while the user has Pro, at start and after changes to tasks, habits and their check-ins, focus sessions, journal pages, notes, mood check-ins and challenges (debounced 1.5 s); the screen also runs it when opened. **Celebration**: badges awarded within the last 2 days and not yet shown appear one at a time in a dialog with the medal popping in and a confetti burst (just the medal with reduced motion); the first evaluation on a device (existing history) is never celebrated. Gallery with progress ("12 of 21") for locked badges. |
| 30 | Today › How are you feeling? (Pro); Habits › face; More › Mood and energy (`MoodTrackerRoute`) | Quick check-ins of mood and/or energy (1–5) with optional tags, **several a day** (`mood_entries` rows with `note_id` null; `MoodRepository`). The journal's one check-in per page (#25) keeps its `note_id` and stays the journal's; it appears in the tracker too (labelled "Journal"), and the journal's behaviour and tests are unchanged. The screen: today's check-ins, a 90-day overview (averages, last 7 days against the 7 before), the last 30 days as a mood/energy chart (mirrored in RTL), mood by energy level and by time of day, and **patterns**: Pearson correlations of the daily mood with habits completed, focus minutes and — when the user allows sleep in Health Connect from this card — hours of sleep (≥ 7 days with variation; strength none/weak/moderate/strong), plus the habits with the largest mood difference between done and not-done due days (≥ 3 days each). Wording says these are patterns, not causes. History of the last 30 days with edit, delete and undo. Today's face row opens the check-in sheet with that mood chosen. |

**Device-only state.** `planb_wellbeing_state` (DataStore, never exported or restored): the Do
Not Disturb filter to restore, the days Health Connect already checked off per habit (14 days
kept), the last Health Connect sync time and the last celebrated badge id. Device tests replace
it (`TestWellbeingStoreModule`).

**Backups.** Challenges, badges, check-ins and the sessions' sound/strict flags were already part
of backup format 2 (see [BACKUP_FORMAT.md](BACKUP_FORMAT.md)); the Focus Pro preferences are
exported keys. Health Connect data itself is never backed up.

**APK size.** Release APK (unsigned, R8): 17,228,170 → 17,688,429 bytes (+460 KB, +2.7 %).
No audio assets: the sounds are generated. The Health Connect client is
pure Kotlin/Java (its Guava dependency is mostly removed by R8).

## Calendar (#2, #3, #6, #7)

No schema change: #6/#7 use `tasks.scheduled_start`/`scheduled_end`, #3 uses `calendar_links`.
Free users keep the Month, Week, Day and Agenda views as they were; the new "Timeline" segment
shows a `ProTeaser`, the free Day view ends with one "Plan your day hour by hour" line (Pro
badge), and Settings › Holidays and device calendars shows teasers. Blocks made with Pro keep
their times if Pro ends (the free views list the tasks as before).

| # | Where | How it works |
|---|---|---|
| 2 | Month grid, week header, day/agenda headers; Settings › Holidays and device calendars | `IranCalendar` (`core:datetime/iran`): the 26 official holidays — 10 solar from a fixed table (1–4, 12, 13 Farvardin; 14, 15 Khordad; 22 Bahman; 29 Esfand) and 16 lunar by Hijri date (the last day of Safar for Imam Reza) — plus a curated list of occasions, names in fa/en resources. Holidays and, in the Jalali calendar, Fridays are drawn in the error color; occasion names and the Hijri date («۲۲ ربیع‌الثانی ۱۴۴۸») sit under the selected day with the note «تاریخ‌های قمری ممکن است یک روز جابه‌جا شوند». Three toggles (`CalendarDecorations`, backed-up preferences). Works in English and in the Gregorian calendar (holidays still marked; Fridays only in Jalali). |
| 3 | Settings › Holidays and device calendars; all calendar views | Opt-in: a rationale dialog, then `READ_CALENDAR`/`WRITE_CALENDAR`. The user picks the calendars to **show** (read only, outlined in their color, never stored in Plan-B unless "Copy into Plan-B" makes an unlinked copy) and one **target** calendar for Plan-B's events (or a new local "Plan-B" calendar, `ACCOUNT_TYPE_LOCAL`, no account). See "Sync rules" below. |
| 6 | Calendar › Day and Week (Pro) | Hour grids. Long-press a task in the "Unscheduled" tray (open tasks of the day/week without a block or time) and drop it on an hour: a block of its estimate or 30 minutes (a task without a date is planned for that day). Long-press drag a block to move it (to another day in the week), drag its top or bottom edge to resize; everything snaps to 15 minutes with haptic ticks and auto-scrolls near the edges. Overlaps sit side by side (`TimeBlocks.layout`). RTL-aware hit testing; hour height and gutter grow with the font scale. TalkBack: "Schedule at…", "Move…", "Change length…", "Remove from schedule". Tap opens the task/event; a device event opens its details. |
| 7 | Calendar › Timeline (Pro) | `TimelineBuilder`: events, time blocks, timed tasks and device events in time order on one rail with colored icons, free gaps of 15 minutes or more ("1 hr 30 min free"), a "now" marker on today (ticks every minute) and inline check-off; all-day and untimed items under "Anytime". Only in Calendar (Today's timeline belongs to `feature:today`, not changed here). |

**Holiday data and accuracy.** Solar holidays are fixed by law and computed exactly from the
Jalali calendar. Lunar holidays follow the Hijri calendar, which Iran fixes by **sighting the
new moon**, so a date is only certain shortly before it and can differ by a day from any
calculation. Plan-B uses ICU's `islamic-civil` calendar, corrected for the solar years
**1405 and 1406** by the month starts of the published Iranian calendar
(`core/datetime/src/main/resources/iran_calendar/hijri_month_starts.json`, from holidayapi.ir,
which republishes time.ir's calendar; retrieved 2026-10-05; not checked against the printed
official calendar). Against that calendar `islamic-civil` matched 13 of the 25 month starts and
was one day off for the others; `islamic-umalqura` matched 6. Tests check that 1405's and 1406's
official holidays equal the published lists and that the calculation alone stays within a day.
time.ir and the Iranian Calendar Center's site were not reachable from the build environment.
To add a year, append its month starts to the JSON (with the source) and extend the tests.

**Sync rules (#3).** `CalendarSyncEngine` mirrors Plan-B events (`calendar_events`) into the
target calendar and records each pair in `calendar_links` (`local_version` = the event's
`updated_at`, `remote_version` = a fingerprint of the device event). On each sync:
- a Plan-B edit updates the device event; a Plan-B deletion deletes it;
- a device edit (title, notes, times) comes back into Plan-B; color, reminder and the Plan-B
  recurrence stay Plan-B's; a device deletion deletes the Plan-B event unless it was edited in
  Plan-B since the last sync (then it is written again);
- **conflicts: last writer wins** — Plan-B's `updated_at` against the time Plan-B noticed the
  device change (provider notification, or the sync); a tie keeps Plan-B's version;
- only device events carrying `CUSTOM_APP_PACKAGE` = Plan-B are ever updated or deleted;
  links whose calendar is missing on this device (for example restored from another phone's
  backup) or whose event is not Plan-B's are dropped, never followed;
- recurrences without a standard RRULE (Jalali months/years, "after completion") stay in
  Plan-B only; imported copies (`remote_version` = `import:…`) are never written back;
- turning sync off stops it and leaves the device events in place.
Sync runs at app start, after provider change notifications and `calendar_events`
invalidations (debounced 2 s), and hourly via WorkManager (`planb_calendar_sync`), only while
it is on, the user has Pro and the permission is granted. Settings (enabled, shown calendars,
target, last sync) are device-only keys of the user preferences file: never exported, never
overwritten by a restore.

## AI assistant and voice input (#39, #40)

No schema change and no new DataStore file: assistant settings stay in the device-only
`planb_ai_settings` file of `core:ai` (never exported or backed up); chats live only in memory.
Free users see the More entry, the Settings row (Pro badge) and the editor actions; each shows a
`ProTeaser` or opens the Pro screen only when tapped.

**#39 AI assistant (BYOK).** More › Assistant; Settings › AI assistant; the note editor's menu
(AI assistant) and the task editor's top bar.
- *Settings* (`AiSettingsRoute`): providers from `AiProviders.all` — Iranian gateways first (a
  generic OpenAI-compatible gateway, then AvalAI), DeepSeek, Qwen, OpenRouter, Groq, OpenAI,
  Anthropic, Gemini, custom. Address (only `https://`; also enforced by `AiClient` and
  `usesCleartextTraffic="false"`), key (typed once, stored encrypted with the Keystore key, never
  shown again; "Remove"), model (suggestions plus the provider's `listModels()`; a provider without
  suggestions such as Anthropic loads the list right after the key is checked), "Check the
  connection", "Forget provider and key". "Use the assistant" needs a complete setup and an
  explicit consent dialog that names the provider and host and says what is sent
  (`enableWithConsent()` records the time).
- *Transport*: `AiClient.stream` sends `"stream": true` and reads server-sent events
  (`SseParser`, `AiStreamDecoder`: OpenAI `choices[0].delta.content` … `[DONE]`; Anthropic
  `content_block_delta`/`text_delta` … `message_stop`, `error` events) and falls back to a one-piece
  JSON answer when a gateway ignores streaming. Cancelling stops the request. Errors are only the
  neutral `AiError` categories (`INVALID_KEY`, `NETWORK_UNREACHABLE`, `RATE_LIMITED`,
  `PROVIDER_ERROR`, `NOT_CONFIGURED`): the UI says to check the key, the model or the internet
  connection, nothing else. Neither the key nor any text is logged; no analytics.
- *Prompts* (`AssistantPrompts`): English instructions, replies in the app language; the user's
  text is wrapped in `<<<PLANB_CONTEXT … PLANB_CONTEXT>>>` and declared data. *Parsing*
  (`AssistantParsing`): the first JSON object/array inside Markdown fences or prose (string-aware
  bracket matching, lenient JSON), else bulleted/numbered lines; plans accept `id`/`task_id` and
  Persian digits.
- *Size*: `AiContextBudget` cuts context to 12,000 characters at a paragraph, line or sentence
  break (marked "shortened to fit") and shows "N characters · about M tokens" (≈4 Latin or 2
  Persian characters per token). Chat history: the last 10 messages, 4,000 characters each.
- *Chat* (`AssistantViewModel`): context chips Nothing / Today / This week / A note (recent notes,
  locked notes never offered); "Sent with your question" expands to the exact text; "Sent only to
  <host>". Streaming replies with Stop; Clear. Dictation in the question field (#40).
- *Plan my day / week*: open tasks of Today (and Upcoming for 7 days) without a time block, fixed
  time or open blockers (at most 40), working hours, events and existing blocks as busy times.
  `PlanValidator` drops proposals for tasks not offered or repeated, days outside the range, bad
  times, outside working hours, before now (next 5-minute mark), shorter than 5 min or longer than
  8 h, or overlapping busy time or each other ("N blocks were left out"). Accept writes the chosen
  blocks in one transaction (`DayPlanRepository.applyBlocks`); the snackbar's Undo restores the
  previous blocks.
- *Contextual actions* (`AiActionSheet` through `LocalAssistant` in `core:ui`, so editors don't
  depend on the assistant module): note editor — summarize, rewrite, translate (to the other app
  language), continue, find tasks, suggest a title — on the selection of the focused block or the
  whole note; task editor — break into subtasks, suggest a title. Text results: Replace the
  selection / Insert below / Add to the note / Copy; lists are checkable: "Add N tasks" (created in
  the sheet, with Undo), "Add as a checklist", "Add N subtasks" (pending for a new task, saved for
  an existing one), "Use this title". Every editor change shows a snackbar with Undo.

**#40 Persian voice input** (`core:speech`). A microphone in Quick Capture's field, the task
title, the note editor's block toolbar (inserts at the caret) and the assistant's question field.
`PlatformVoiceDictation` runs `SpeechRecognizer` on the main thread with partial results,
`EXTRA_PREFER_OFFLINE` and `fa-IR` or `en-US` by the app language; the on-device recognizer is
tried first and the regular speech service takes over when it lacks the language. The listening
dialog shows a level-driven microphone, the words heard so far (polite live region), Done (keeps
what was heard) and Cancel. `RECORD_AUDIO` is asked after an explanation, with "Open settings" when
denied for good; no speech service → a calm explanation (install/enable one, add Persian for
offline use); errors map to neutral reasons (nothing heard, network/offline language, busy,
language missing). In Quick Capture the text joins the title and goes through `QuickAddParser`
(#1), so «فردا ساعت ۵ عصر جلسه با علی» becomes "جلسه با علی" tomorrow at 17:00 (a note's dictation
goes to its body once it has a title). Voice-note transcription (#19) shares `SpeechIntents` and
`RecognitionListenerAdapter` and dictates through `VoiceDictation` on Android 12 and older.

**Size and permissions.** No new dependency (OkHttp, kotlinx.serialization and the platform
`SpeechRecognizer` were already in the app) and no new permission (`INTERNET`, `RECORD_AUDIO`,
`ACCESS_NETWORK_STATE` were already allowlisted). Release APK (unsigned, R8): 17,228,170 →
17,485,074 bytes (+0.25 MB, code and strings of the two packages).

**Limitations.** Recognition quality, offline Persian and whether audio leaves the device depend on
the installed speech service. The assistant needs network access to the user's provider; replies
are only as good as the chosen model. Week plans propose time blocks, not new planned dates.

## Rich notes (#15, #17–#20, #23)

No schema change: block content lives in the note's JSON (`NoteBlock.data`, `attachmentId`),
files in `attachments` (owner `NOTE`) and `files/attachments/`. The note editor's toolbar has an
**Insert** button (Photo from gallery, Take a photo, Scan document, File, Table, Drawing, Record
voice, Database, Formula, Chart); each item shows a Pro badge to free users and opens the Pro
screen for its feature. **Everyone can read every block**; without Pro the blocks are read-only
(edit buttons open the Pro screen). Code: `core:model` `rich/` (payloads, table and database
operations, chart mapping, the formula parser), `core:data` `AttachmentRepository`,
`feature:notebooks` `rich/` (block UIs, `RichEditor`) and `media/` (recognition engines).

| # | Block | How it works |
|---|---|---|
| 15 | Photo, File, Table, Drawing | Photos from the system Photo Picker (no storage permission) or the camera app (`ACTION_IMAGE_CAPTURE` into a FileProvider URI; no `CAMERA` permission), scaled to 2560 px on the longer side (EXIF rotation applied) and stored as JPEG 85 (PNG when transparent); captions. Files through SAF (`OpenDocument`), copied as they are; open/share through the `<package>.attachments` FileProvider (read grant for that one file). Tables: editable cells, add/insert/delete/move rows and columns, header row, reading-order columns (mirrored in Persian), cells announced as "row r, column c" with TalkBack actions. Drawing: full-screen pen canvas with pressure-sensitive width, six colors, width slider, stroke eraser, undo/redo/clear (also TalkBack actions); strokes are stored as a compact vector file (`x,y,pressure` integer triples, max 60,000 points) plus a PNG preview on paper. |
| 17 | Scanned document | ML Kit Document Scanner (Play services, up to 10 pages, gallery import) when Google Play services exist; otherwise the camera app and a manual crop (drag the corners). Every photo and page is read for text in the background. **OCR**: Latin text with ML Kit text recognition through Play services (`play-services-mlkit-text-recognition`, no model in the APK; skipped without Play services), Persian/Arabic text with Tesseract (Tesseract4Android 4.9.0) and the bundled `fas.traineddata` from tessdata_fast (431,500 bytes, SHA-256 `db1c0a91…a4505`, Apache 2.0) — small enough to bundle, so there is no optional download. Tesseract's output is kept only at ≥55 % confidence and ≥60 % Arabic-script letters. Text is stored in `attachments.ocr_text`, shown under the image (selectable) and indexed on the note's search row; search marks such hits "Found in an image". |
| 18 | Drawing → Convert to text | ML Kit Digital Ink Recognition with the `fa` or `en-US` model (chosen in a menu). A missing model (≈20 MB) is downloaded only after a consent dialog with an "Only over Wi-Fi" option (default on), with a progress note; failures show a neutral message. The text is inserted as a text block below the drawing. |
| 19 | Voice recording | Recorded with `MediaRecorder` (AAC in `.m4a`, mono 16 kHz, 48 kbps, stops at 31 MB); `RECORD_AUDIO` is asked in context after an explanation, with a path to Settings when denied for good. Live waveform while recording; 48-bar waveform stored in the block; play/pause/seek and duration. **Transcribe**: on Android 13+ the file is decoded to PCM and streamed to `SpeechRecognizer` (`EXTRA_AUDIO_SOURCE`, segmented session, `EXTRA_PREFER_OFFLINE`, the on-device recognizer first), in `fa-IR` or `en-US` by the app language. Android 12 and older cannot give a file to the speech service and the microphone cannot feed a recorder and the recognizer at once, so there the transcript is dictated live after a short explanation. Stored in `attachments.transcript`, indexed ("Found in a recording"). |
| 20 | Database | Typed columns (text, number, checkbox, date, select with options), rows, sort by a column (empty cells last; Persian collation), one filter (contains; `>`, `<`, `>=`, `<=`, `=` for numbers; ISO prefix for dates), footer with sums of number columns, checked counts and the row count. Numbers are stored with Latin digits and shown in the user's digits; dates are ISO days shown in the user's calendar (Jalali or Gregorian) with the app's date picker. |
| 23 | Formula, Chart | Formula: LaTeX-like source parsed by `MathParser` (fractions, scripts, roots with index, Greek letters, ∑/∏/∫ with limits, `\left…\right`, `matrix`/`pmatrix`/`bmatrix`/`vmatrix`/`cases`, `\text`, functions, common symbols; never fails) and laid out natively with Compose text (no WebView), always left to right, with a source/preview toggle and a spoken description. Chart: bar, line or pie on a Canvas from inline label/value pairs or a database block of the same note (label column + first number column, rows as that database shows them); bars run in reading order, pie slices use the accent palette with a legend; the chart's spoken summary lists every value. |

**Storage and limits.** Attachments follow the backup limits (`AttachmentLimits` = `BackupFormat`):
32 MB per file, 1 GB and 10,000 files in total; a larger pick shows a calm message. Unused files
are removed when the editor closes (after a 10-minute grace for blocks not saved yet) and at app
start (`AttachmentMaintenance.sweep`); a file stays while the note's draft or a kept version still
uses it, while the note is in the trash, and for locked notes (their body cannot be read). A
permanent delete removes the files at once. Duplicating a note copies its files. Decoded previews
are cached in memory (`AttachmentBitmaps`, ⅛ of the heap at most).

**Search and locked notes.** Recognized text and transcripts are added to the owning note's
search row (`SearchIndexer.note(…, attachmentText)`, also on rebuild/restore) and never for
locked notes. Attachment files themselves are not encrypted for locked notes (documented in
PRIVACY.md).

**Compatibility.** Unknown block kinds (a note from a newer version) are read as text blocks and
keep their text; old notes decode unchanged. Note that app versions before this one cannot read
notes that contain the new block kinds (they show the note's raw text). Markdown export: tables
and databases → GFM tables, formulas → `$$…$$`, charts → a table with the title as caption,
photos/scans/drawings → `![caption](attachments/<file>)` and files/recordings → links, with scan
text and transcripts quoted; the Markdown ZIP contains the files in each notebook's
`attachments/` folder. Markdown import reads GFM tables and `$$` blocks back.

**Engines and APK size.** Release APK (unsigned, R8): 4,709,567 → 17,228,170 bytes (+12.5 MB):
Tesseract and its libraries ≈ 6.1 MB, ML Kit Digital Ink ≈ 5.3 MB (compressed native code),
code ≈ 0.9 MB, the Persian OCR model 0.3 MB. Release builds keep only ARM native code
(`armeabi-v7a`, `arm64-v8a`; benchmark builds keep all ABIs) and store native libraries
compressed (`useLegacyPackaging`); with the App Bundle each phone downloads one ABI (≈7 MB more
than before). Document scanning and Latin OCR run in Google Play services (unbundled, a few
hundred KB of code). ML Kit's usage logging (Google data transport) is disabled by removing its
transport backend from the manifest, so nothing is reported.

**Limitations.** Without Google Play services there is no ML Kit scanner (camera + manual
rectangular crop instead) and no Latin OCR (Persian OCR still works). Handwriting needs the model
download (network) once per language. Transcription quality and whether it runs on the device
depend on the installed speech service; Android 12 and older use live dictation. Tables and
databases scroll sideways on narrow screens. Templates made from a note keep its tables,
databases, formulas and charts but not its files.

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

