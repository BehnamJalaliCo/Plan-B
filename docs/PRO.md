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
| `core:calendarsync` | #3: `DeviceCalendarStore` (CalendarContract behind an interface; `ContentResolverCalendarStore`, `FakeDeviceCalendarStore`), `CalendarSyncEngine`, `CalendarSyncRepository`, `CalendarSyncController` + `CalendarSyncWorker` |
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

`core:ai` holds the provider catalog (`AiProviders`: generic Iranian OpenAI-compatible gateway,
AvalAI, DeepSeek, Qwen/DashScope, OpenRouter, Groq, OpenAI, Anthropic, Gemini, custom), the
`AiSettingsRepository` (off by default, explicit consent timestamp, key encrypted with an
Android Keystore AES-GCM key in a device-only file) and `AiClient` (OpenAI chat-completions and
Anthropic Messages, HTTPS only, timeouts, `testConnection()`, `listModels()`). Errors map to
neutral categories (`INVALID_KEY`, `NETWORK_UNREACHABLE`, `RATE_LIMITED`, `PROVIDER_ERROR`);
the UI must only ever say to check the key or the internet connection. Only the text the user
chooses is sent, directly to their provider. Anthropic has no suggested model ids in the
catalog: the assistant UI should offer `listModels()` results.

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

**Engines and APK size.** Release APK (unsigned, R8): 4,709,567 → 17,294,258 bytes (+12.6 MB):
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

