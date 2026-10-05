# Changelog

All notable changes to Plan-B are documented here. The project follows
[Semantic Versioning](https://semver.org/); `versionCode` increases with every store upload.

## [Unreleased]

Foundation for **Plan-B Pro**. Every existing feature stays free.

### Added
- Plan-B Pro: a monthly subscription (`planb_pro_monthly`) or a lifetime purchase
  (`planb_pro_lifetime`) through Cafe Bazaar, verified on the device. Pro also works offline
  (lifetime always, monthly for 7 days after the last check). A Plan-B Pro screen with plans,
  restore purchases and the list of 40 upcoming Pro features; a Plan-B Pro row at the top of
  Settings and an entry in More. Pro features are gated without pop-ups or nagging.
- Database schema 3 for all Pro features (deadlines, time blocks, extra reminders,
  dependencies, smart lists, attachments, note links and versions, journal and mood, challenges
  and badges, activity history, calendar links, trash). Existing data is migrated unchanged.
- Backup format 2: includes the new data and attachment files; format 1 backups still restore.
- Infrastructure for an optional AI assistant that uses the user's own provider key (off by
  default; the key is stored encrypted and never backed up).
- Plan-B Pro reports: statistics for any week, month or year (Jalali or Gregorian) with
  accessible charts, a "My year" story and PDF export (statistics, My year, weekly review) in
  the app's Persian font, saved where the user chooses.
- Plan-B Pro home-screen widgets: Today, Quick add, Habits, Focus and a monthly calendar, with
  Material You colors; free users see a Pro placeholder.
- Plan-B Pro themes (Ocean, Forest, Sunset, Blossom, Midnight with true black) and four
  alternate app icons in Settings → Themes and app icon.
- Quick-settings tiles (Quick add, Focus) and launcher shortcuts: New task and New note are
  free; Today and Start focus are Pro.
- Wear OS companion app (Pro): today's tasks and habit check-ins on the watch, synced with the
  phone; the phone app keeps working without Google Play services.

- Plan-B Pro **App lock and locked notes** (#36): lock Plan-B with the device's fingerprint,
  face or screen lock (immediately or after 1, 5 or 15 minutes in the background, optionally
  hidden in recent apps), and lock single notes with a passphrase (PBKDF2-HMAC-SHA256 +
  AES-256-GCM, optional fingerprint unlock). Locked notes are searchable by title only and stay
  encrypted in backups. Settings › Security.
- Plan-B Pro **automatic backups** (#37): daily or weekly (optionally only while charging) into a
  folder you choose, also on Google Drive, keeping the newest 21; last result and "Back up now"
  in Settings › Backup & restore; a notification only when a backup fails.
- Plan-B Pro **30-day trash and activity history** (#38): deleted tasks and notes can be restored
  for 30 days (More › Trash); a history of changes to tasks, notes, projects, events, habits and
  goals (More › Activity, and from the task and note editors). Free users' deletions stay as before.
- Plan-B Pro **planning** (#4, #9–#14):
  - Advanced repeats: "the second Monday" or "the last Friday" of every month (Jalali or
    Gregorian; months without a fifth weekday are skipped), "3 days after completion", and
    "every 2 weeks" counted from your own first day of the week, with the rule shown in words.
  - Project **timeline**: a Gantt-style view of tasks and milestones with a today line and
    dependency arrows, right-to-left in Persian; tap a bar to open the task.
  - **Smart lists**: save filters (projects, tags, priority, status, dates, deadline, text,
    sort) with an icon and color, shown as chips in Tasks; reorder and delete them.
  - **Deadlines** separate from the planned date, with "deadline in 2 days" badges, near
    deadlines on Today, sorting by deadline and overdue based on the deadline.
  - **Up to five reminders** per task (before the planned time, before the deadline or at a set
    time) and **nag until done** every 5–30 minutes, with Done and Snooze buttons.
  - **Eisenhower matrix** with long-press drag between quadrants.
  - **Dependencies**: tasks can wait for other tasks (cycles are refused), show a lock and ask
    before being completed early.

### Changed
- Overdue tasks: a task with a deadline is late only after its deadline.
- The custom repeat dialog uses −/+ buttons for "repeat every" and the number of occurrences.
- A restore also clears alarms of tasks that only had extra reminders.
- The app may now use the Internet, only for the optional AI assistant. The release build may
  request only the permissions listed in `tools/allowed-permissions.txt` (checked in CI).
- Items in the trash are left out of every list, count, reminder, search and export.
- The launcher entry is now an activity alias (to allow alternate icons); widgets add the
  normal WorkManager permissions `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE`
  (no runtime permission).

## [1.0.1] — 2026-10-04

Reliability update after a full code and screenshot review.

### Fixed
- One-time event reminders were dismissed as soon as they were posted; habits repeating every
  15+ days got no reminders; stale alarms could fire after delete-all/restore; the focus end
  alarm was not restored after a reboot; reminder failures could crash the app.
- On Android 8–12 the app switched back to Persian on every cold start.
- Recurring tasks: saving as Done from the editor ended the series; changing the date or rule
  kept the old anchor; reopening a completed occurrence left a duplicate; subtasks of the next
  occurrence kept stale dates and had no reminders; archiving a parent left subtask reminders.
- Undo of a swipe-delete affected other pending deletes; deletes could stay half-done after
  leaving the screen; multi-select acted on items hidden by a filter; manual reorder in a
  filtered view broke the global order.
- Project tags were erased on edit and on notes autosave; project notes lost the last edits.
- Notes editor: stale field values while typing, multi-line blocks split on the first keystroke,
  Enter at the start of a block moved its text, toolbar acted on a block while the title was
  focused, a new note could be created twice after process death; added "join with the block
  above". Notes in archived notebooks no longer show in recent/pinned lists.
- Search: words with and without the half-space (ZWNJ) and hamza variants now match (the index
  is rebuilt once after updating).
- A corrupt preferences file crashed the app; restore/delete-all could be cancelled halfway;
  safer backup limits, case-insensitive tag validation, safe export file names, CSV formula
  guard, and backups from newer versions are rejected clearly.
- Today: completed count included subtasks and other days; greeting went stale. Calendar: fast
  paging skipped taps; completing a task can now be undone. Focus: minutes recorded when a
  session ends in the background, midnight rollover, no duplicate session on double tap.
- Goals: "10,000" was saved as 10, Infinity crashed, pace was wrong on the deadline day, very
  large goals showed as done early, rapid +/- taps were lost. Number and percent rounding fixed.
- Habits: weekly-review rate ignored the start date and counted today as missed; best streak for
  "times per week" habits was always 0 and the streak unit now says weeks.
- Markdown export now escapes block markers so notes round-trip.
- Display: Persian separators next to digits looked like a zero (now "،"); status-bar icons
  follow the app theme; 48dp touch targets; 12-hour time picker in English when the device uses
  12-hour time; bottom navigation labels fit at large font sizes; numbered-list icon mirrored in
  RTL; board column counts sit next to their titles; templates use the week/month start date.
- Double taps on Save or Back no longer create duplicates or leave a blank screen.

## [1.0.0] — 2026-10-04

First public release.

### Planning
- **Today** dashboard: greeting, daily progress, summary tiles, a timeline of events and timed
  tasks, today's and upcoming tasks, habits, focus, projects and recent notes. Sections can be
  hidden and reordered.
- **Quick capture** for tasks, notes, events, habits and projects, with quick dates, time and
  priority.
- **Tasks** with Inbox, Today, Upcoming, Scheduled, Completed, Archived and All views, subtasks,
  priorities, statuses, tags, projects, dates and times, reminders, estimates, filtering,
  sorting, multi-select, swipe actions with undo and manual ordering.
- **Recurring tasks and events**: daily, weekly on chosen days, monthly and yearly, with
  intervals, end dates or counts, in Jalali or Gregorian months.
- **Calendar** with Day, Week, Month and Agenda views.
- **Projects** with status, progress modes, milestones, notes and a board.
- **Goals** with numeric targets, pace, deadlines, milestones and project links.
- **Habits** with flexible schedules, targets and units, reminders, streaks and a heatmap.
- **Focus** sessions (Pomodoro presets) that keep accurate time across app restarts.
- **Weekly review** of completed and missed work, habits, focus, notes, projects and goals.

### Notes
- Notebooks, sections and notes with a block editor (text, headings, checklists, bullet and
  numbered lists, quotes, code, dividers).
- Autosave, draft recovery, pin and favorite, tags, move, duplicate, archive.
- Markdown import and export of single notes.

### Search and templates
- Global search across all content, tolerant of Persian/Arabic letter and digit variants.
- Eight built-in templates plus custom templates saved from notes.

### Data
- Versioned backup files (ZIP) with validation before restore and a transactional restore.
- Export tasks as CSV or JSON and notes as Markdown (ZIP) or JSON; import tasks from CSV or
  JSON without overwriting existing data.
- Delete all data.

### Experience
- Persian (default) and English with right-to-left layout, switchable in the app.
- Jalali and Gregorian calendars, configurable first day of week and digit style.
- Light and dark themes, Material You themed icon, reduced-motion and haptics settings.
- Accessible labels, 48dp touch targets and layouts that adapt to large fonts.
- Private reminder notifications that restore themselves after a restart.

### Privacy
- No Internet permission, accounts, ads, analytics or tracking. Data never leaves the device
  unless you export it.
