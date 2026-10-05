# Plan-B Backup & Data Transfer Format

Plan-B never uploads data. In the manifest, `android:allowBackup="false"` and
`android:fullBackupContent="false"` are set, and `res/xml/data_extraction_rules.xml` excludes
every domain from cloud backup and device transfer. Users make **explicit local backups**
from *Settings › Backup & data*. This document describes the file formats and the
restore logic in `core/backup`:

| File | Role |
|---|---|
| `core/backup/src/main/kotlin/com/behnamjalali/planb/core/backup/BackupModels.kt` | Manifest, metadata, per-table DTOs, `BackupFormat` constants, `BackupException` |
| `.../BackupCodec.kt` | ZIP read and write (`BackupCodec`) and referential validation (`BackupValidator`) |
| `.../BackupMapping.kt` | Entity ↔ DTO mapping |
| `.../BackupManager.kt` | Snapshot, export, inspect, transactional restore, delete-all |
| `.../DataTransfer.kt` | Separate task and note export/import (CSV, JSON, Markdown ZIP) |
| `feature/settings/.../SettingsViewModels.kt` (`DataViewModel`), `BackupScreen.kt` | UI flow and error messages |
| `core/backup/src/test/.../BackupTest.kt` | Round-trip, compatibility and failure tests |

---

## 1. Archive layout

A backup is a standard ZIP file (`application/zip`). The UI suggests the file name
`Plan-B-backup-<stamp>.zip`. The archive contains four **flat** JSON entries, all UTF-8
(compact, not pretty-printed), and, from format 2, one `attachments/` folder:

| Entry | Required on read | Content |
|---|---|---|
| `manifest.json` | **yes** | `BackupManifest`: format and app identity |
| `database.json` | **yes** | `BackupDatabase`: one array per table |
| `preferences.json` | no, defaults to `{}` | `Map<String, String>` of DataStore preference keys |
| `metadata.json` | no, and ignored if malformed | `BackupMetadata`: counts and language |
| `attachments/<fileName>` | only for attachment rows (format 2) | the raw bytes of each attachment file; one flat level |

Writers always emit the four JSON entries in this order, followed by one entry per attachment
row whose file exists. The JSON codec is configured as
`ignoreUnknownKeys = true`, `encodeDefaults = true` and `explicitNulls = false`, so `null`
fields are **omitted** from the output.

### 1.1 `manifest.json`

| Field | Type | Default | Meaning |
|---|---|---|---|
| `backupFormatVersion` | Int | — (required) | Archive format. Currently `BackupFormat.CURRENT = 2`. Format 2 (app schema v3) adds the Pro tables and columns and the `attachments/` folder; format 1 files still restore. |
| `appVersion` | String | — (required) | `versionName` of the app that wrote the file |
| `appVersionCode` | Int | `0` | `versionCode` |
| `createdAt` | Long | — (required) | Epoch milliseconds, UTC |
| `databaseSchemaVersion` | Int | `0` | `PlanBDatabase.VERSION` at write time (`3`). A value newer than the app's schema is rejected (`NewerDatabase`). |
| `application` | String | `"com.behnamjalali.planb"` | Must equal `BackupFormat.APPLICATION_ID` |

Example, consistent with the code (app 1.0.1, schema v3, created 4 Oct 2026 10:00 Tehran):

```json
{
  "backupFormatVersion": 2,
  "appVersion": "1.0.1",
  "appVersionCode": 2,
  "createdAt": 1791095400000,
  "databaseSchemaVersion": 3,
  "application": "com.behnamjalali.planb"
}
```

The application id stays `com.behnamjalali.planb` for the debug build too (the `.debug`
suffix is not used here), because the value comes from the constant and not from the
package name.

### 1.2 `metadata.json`

```json
{ "counts": { "tasks": 42, "projects": 3, "notebooks": 2, "notes": 17, "habits": 4,
              "goals": 1, "events": 9, "focusSessions": 12, "templates": 0, "tags": 6,
              "attachments": 2 },
  "language": "fa" }
```

`counts` comes from `BackupDatabase.counts()`. `language` is the `language` preference
value, if present. The restore preview **recomputes** counts from `database.json` and never
trusts this file.

### 1.3 `database.json`

`BackupDatabase` holds one array per table. A missing array decodes as empty. Field names
are the Kotlin property names (camelCase). All values use the canonical forms from
[`DATABASE.md`](../DATABASE.md):

- dates are **epoch days** (`Long`)
- times are **seconds of day** (`Int`, clamped to 0–86399 on read)
- instants are **epoch milliseconds UTC** (`Long`)

| Array | DTO | Fields (default when absent) |
|---|---|---|
| `tags` | `TagDto` | `id`, `name`, `color`=`"lavender"` |
| `projects` | `ProjectDto` | `id`, `title`, `description`=`""`, `color`=`"lavender"`, `icon`=`"folder"`, `status`=`"ACTIVE"`, `progressMode`=`"TASKS"`, `manualProgress`=0, `startDate`?, `dueDate`?, `sortOrder`=0, `createdAt`=0, `updatedAt`=0, `archived`=false |
| `projectTags` | `RefDto` | `a` = project id, `b` = tag id |
| `projectMilestones` | `MilestoneDto` | `id`, `parentId` (project), `title`, `date`?, `target`? (unused, always null), `completed`=false, `sortOrder`=0 |
| `tasks` | `TaskDto` | `id`, `title`, `description`=`""`, `status`=`"TODO"`, `completed`=false, `priority`=0, `startDate`?, `dueDate`?, `startTime`?, `dueTime`?, `reminderOffsetMinutes`?, `projectId`?, `parentTaskId`?, `recurrence`?, `recurrenceAnchor`?, `estimatedMinutes`?, `actualMinutes`?, `notes`=`""`, `sortOrder`=0, `createdAt`=0, `updatedAt`=0, `completedAt`?, `archived`=false |
| `taskTags` | `RefDto` | `a` = task id, `b` = tag id |
| `notebooks` | `NotebookDto` | `id`, `title`, `icon`=`"book"`, `color`=`"lavender"`, `sortOrder`, `createdAt`, `updatedAt`, `archived` |
| `sections` | `SectionDto` | `id`, `notebookId`, `title`, `sortOrder`=0 |
| `notes` | `NoteDto` | `id`, `notebookId`, `sectionId`?, `title`=`""`, `content`=`""` (JSON `NoteDocument`), `contentFormat`=`"blocks-v1"`, `pinned`, `favorite`, `sortOrder`, `createdAt`, `updatedAt`, `archived` |
| `noteTags` | `RefDto` | `a` = note id, `b` = tag id |
| `habits` | `HabitDto` | `id`, `title`, `icon`=`"star"`, `color`=`"mint"`, `schedule`=`"DAILY"`, `target`=1, `unit`=`""`, `reminderTime`?, **`startDate`** (required), `createdAt`, `updatedAt`, `archived` |
| `habitCompletions` | `HabitCompletionDto` | `id`, `habitId`, `date`, `amount`=1, `createdAt`=0 |
| `goals` | `GoalDto` | `id`, `title`, `description`=`""`, `target`=100.0, `currentValue`=0.0, `unit`=`""`, `deadline`?, `projectId`?, `notes`=`""`, `createdAt`, `updatedAt`, `archived` |
| `goalMilestones` | `MilestoneDto` | `id`, `parentId` (goal), `title`, `date`? (unused, always null), `target`?, `completed`, `sortOrder` |
| `events` | `EventDto` | `id`, `title`, `description`=`""`, **`date`** (required), `startTime`?, `endTime`?, `allDay`=true, `reminderOffsetMinutes`?, `recurrence`?, `color`=`"powder_blue"`, `notes`=`""`, `createdAt`, `updatedAt` |
| `focusSessions` | `FocusDto` | `id`, `linkedTaskId`?, **`startedAt`**, `endedAt`?, **`plannedDurationMillis`**, `actualDurationMillis`=0, `status`=`"COMPLETED"`, `runningSince`?, `accumulatedMillis`=0 |
| `templates` | `TemplateDto` | `id`, `title`, `type`, `payload`, `createdAt`=0, `updatedAt`=0 |

Fields and arrays added in **format 2** (schema v3). All have defaults, so a format 1 file
restores with neutral values (no deadline, no time block, not nagging, not in the trash, not
locked, no health metric, no sound, not strict, empty new tables):

| Array / DTO | Added fields or content |
|---|---|
| `tasks` (`TaskDto`) | `deadline`? (epoch day), `scheduledStart`?, `scheduledEnd`? (epoch ms), `nag`=false, `deletedAt`? |
| `notes` (`NoteDto`) | `deletedAt`?, `locked`=false, `encryptedPayload`? (Base64, standard alphabet, padded) |
| `habits` (`HabitDto`) | `healthMetric`?, `healthThreshold`? |
| `focusSessions` (`FocusDto`) | `soundId`?, `strict`=false |
| `taskReminders` (`TaskReminderDto`) | `id`, `taskId`, `kind`=`"OFFSET"`, `offsetMinutes`?, `at`? |
| `taskDependencies` (`RefDto`) | `a` = task id, `b` = the task it depends on |
| `savedFilters` (`SavedFilterDto`) | `id`, `name`, `icon`=`"star"`, `color`=`"lavender"`, `query`=`"{}"`, `sortOrder`, `createdAt`, `updatedAt` |
| `noteVersions` (`NoteVersionDto`) | `id`, `noteId`, `createdAt`, `title`, `content`, `size` |
| `noteLinks` (`RefDto`) | `a` = linking note, `b` = linked note |
| `attachments` (`AttachmentDto`) | `id`, `ownerType`, `ownerId`, `kind`=`"FILE"`, `fileName`, `displayName`=`""`, `mimeType`, `sizeBytes`, `durationMillis`?, `width`?, `height`?, `ocrText`?, `transcript`?, `sortOrder`, `createdAt` |
| `journalEntries` (`JournalEntryDto`) | `id`, `date`, `noteId`, `promptId`?, `createdAt`, `updatedAt` |
| `moodEntries` (`MoodEntryDto`) | `id`, `date`, `time`?, `mood`?, `energy`?, `tags`=`""`, `noteId`?, `createdAt`, `updatedAt` |
| `challenges` (`ChallengeDto`) | `id`, `kind`, `title`, `targetDays`, `startDate`, `habitId`?, `status`=`"ACTIVE"`, `completedAt`?, `createdAt`, `updatedAt` |
| `badges` (`BadgeDto`) | `id`, `key`, `earnedAt` |
| `activityLog` (`ActivityDto`) | `id`, `entityType`, `entityId`, `action`, `at`, `summary` |
| `calendarLinks` (`CalendarLinkDto`) | `id`, `localType`, `localId`, `calendarId`, `externalEventId`, `lastSyncedAt`, `localVersion`?, `remoteVersion`? |

Items in the trash are part of a backup. Only attachment rows whose owner exists and whose file
is on disk are written, so every row in a backup has its bytes. Rich note blocks (Plan-B Pro)
add no new array: tables, databases, formulas and charts are part of the note's `content`;
photos, scans, files, recordings (`AUDIO`, `.m4a`) and drawings (two `DRAWING` rows: a PNG
preview and a `application/vnd.planb.drawing+json` vector file) are ordinary attachment rows
whose `ocrText`/`transcript` hold recognized text. The app never stores an attachment above the
per-file or total limits below (`AttachmentLimits` equals them), so every one fits a backup.

`?` marks an optional, nullable field (omitted when null). These are **not** in a backup:

- `note_drafts`: transient editor state.
- `search_index`: rebuilt on restore.
- `planner_templates.built_in`: built-in templates are not stored in the database, and
  restored templates always get `built_in = 0`.
- The Plan-B Pro purchase state (it is re-verified with Cafe Bazaar; a backup can never
  grant Pro) and the AI assistant settings, **including the provider key** (stored encrypted
  with a key that never leaves the device). Neither lives in the preferences file that is
  exported.

### 1.4 `preferences.json`

This is a flat string→string map produced by `UserPreferencesDataSource.export()`. Every
DataStore key is written, with its value converted by `toString()`. The keys come from
`core/datastore/.../UserPreferencesDataSource.kt`:

| Key | Value | Restored? |
|---|---|---|
| `language` | `fa` or `en` | yes (see note below) |
| `theme` | `SYSTEM`, `LIGHT` or `DARK` | yes |
| `calendar_system` | `JALALI`, `GREGORIAN`, or `AUTO` (follows the language; stored as an absent key, exported explicitly) | yes |
| `first_day_of_week` | `1`–`7` (ISO), or `0` (follows the calendar; stored as an absent key, exported explicitly) | yes |
| `number_format` | `AUTO`, `PERSIAN` or `LATIN` | yes |
| `default_reminder_minutes` | Int, clamped to 0..10080 | yes |
| `animations_enabled`, `haptics_enabled` | `true` or `false` | yes |
| `dashboard_order`, `dashboard_hidden` | comma-separated `DashboardSection` keys | yes |
| `default_task_view` | `TaskView` name | yes |
| `default_calendar_view` | `CalendarView` name | yes |
| `focus_minutes` (1..180), `short_break_minutes` (1..60) | Int | yes |
| `onboarding_completed` | not exported | **no.** It is device-specific, so the current device value is kept. |

Import (`UserPreferencesDataSource.import`) merges the backup into the current settings:
values present in the backup win; a key that is **missing** from the backup, or whose value
is unknown or unparsable, keeps the device's current value. Numbers are clamped to their valid
ranges. Onboarding state is never changed by a restore. If `preferences.json` is absent or
empty, current preferences are left alone.

> **Language.** The UI language is owned by the platform per-app locale
> (`AppCompatDelegate` / `LocaleManager`). After a successful restore the backup screen applies
> the restored `language` with `AppCompatDelegate.setApplicationLocales`, so the app switches
> to it immediately and `MainActivity` keeps it on the next start.

## 2. Automatic backups (Plan-B Pro #37)

`AutoBackupRunner` (`core/backup/.../AutoBackup.kt`) writes the same archive as a manual backup
(`BackupManager.snapshot` + `BackupCodec.write`) into a folder picked with
`ACTION_OPEN_DOCUMENT_TREE` (persisted read/write permission; Google Drive and other providers
work). Files are named `Plan-B-auto-yyyyMMdd-HHmmss.zip` (local time, Latin digits). After a
successful write, `AutoBackupNaming.toPrune` deletes our files beyond the newest 21; files that
do not match the exact pattern are never listed for deletion. A failed write deletes its partial
file. `AutoBackupWorker` runs it daily or weekly (optionally only while charging, never on low
storage) and notifies only on failure; settings and the last result live in the device-only
`planb_auto_backup` DataStore, which is not part of any backup. Locked notes stay encrypted in
every backup and open after a restore with their passphrase.

## 3. Limits and reading safeguards (`BackupCodec.read`)

| Rule | Constant or behaviour |
|---|---|
| Max entries iterated in the ZIP | `MAX_ENTRIES = 16 + MAX_ATTACHMENTS` (10 016). More raises `Corrupt("too many entries")`. Ignored entries count too. |
| Attachment files | Streamed to a private staging folder (`files/backup-staging/<random>/`), never held in memory. At most `MAX_ATTACHMENTS = 10 000` files, `MAX_ATTACHMENT_BYTES = 32 MiB` each and `MAX_ATTACHMENTS_TOTAL_BYTES = 1 GiB` together, counted while streaming; more raises `Corrupt("too many attachments")` / `Corrupt("attachment too large")`. Only `attachments/<name>` entries whose name passes `AttachmentFiles.isSafeName` (one flat level of ASCII letters, digits, `.`, `-`, `_`, no `..`, not starting with a dot) are written; others are ignored, so no entry can land outside the folder. An I/O failure (for example a full disk) raises `Corrupt`. |
| Max uncompressed bytes | `MAX_ENTRY_BYTES = 32 MiB` per entry and `MAX_TOTAL_BYTES = 40 MiB` for all entries together, counted while streaming (header sizes are never trusted). More raises `Corrupt("entry too large")`. This keeps a zip bomb from exhausting memory. Running out of memory anyway while reading raises `Corrupt("too large to open")` instead of crashing. |
| Entry names | Apart from `attachments/`, directories and names containing `/`, `\` or `..` are skipped. Only the four known names are read. Everything else is ignored. |
| Invalid ZIP or truncated stream | `ZipException` or `EOFException` raises `Corrupt` |
| No known entries | `NotABackup("empty or not a ZIP archive")` |
| `manifest.json` missing | `NotABackup` |
| `application` ≠ `com.behnamjalali.planb` | `NotABackup("created by another application")` |
| `database.json` missing | `Corrupt` |
| JSON that fails to decode (manifest, database, preferences) | `Corrupt("<name> is not valid (…)")` |
| `databaseSchemaVersion` > this app's schema version | `NewerDatabase(schemaVersion)`: the backup was made by a newer app |
| `metadata.json` that fails to decode | silently replaced by empty metadata |

---

## 4. Validation before restore (`BackupValidator.validate`)

Validation runs over the decoded `BackupDatabase` **before anything is written**, and again
defensively inside `restore()`. Any failure raises `BackupException.Invalid`.

1. **Ids** in every table are unique and `> 0`.
2. **References** resolve within the archive:
   - Each task's `projectId` is a project. Each task's `parentTaskId` is another task, not
     itself.
   - `taskTags`, `projectTags` and `noteTags` point at existing rows on both sides.
   - Project and goal milestones point at their parent.
   - Sections point at a notebook. Notes point at a notebook, and at a section if one is set.
   - Habit completions point at a habit. Goals point at a project, if set. Focus sessions
     point at a task, if set.
3. **One habit check-in per habit and day** (mirrors the unique index on
   `habit_completions(habit_id, date)`).
4. **Unique tag names**, compared like the unique `COLLATE NOCASE` index on `tags.name`
   (ASCII letters case-insensitively, everything else exactly), and no duplicate
   task/project/note tag links.
5. **No cycles** in the task parent hierarchy.
6. **Format 2 tables:** unique ids; reminders, versions, links, journal and mood entries,
   challenges and dependencies point at existing rows; a task never depends on itself; no
   duplicate dependencies, note links, badge keys, journal dates or calendar links; encrypted
   note bodies are valid Base64; every attachment has a safe, unique file name and an existing
   owner (`TASK`, `NOTE`, `EVENT` or `MOOD`), and — on restore — its file in the archive.

---

## 5. Backup and restore flow

### 5.1 Create (`BackupManager.snapshot` / `exportTo`)

1. Read every table inside **one** `withTransaction`, so the snapshot is consistent. Leave
   out attachment rows whose owner or file is missing.
2. Export preferences.
3. Build the manifest (format `CURRENT`, app version, `createdAt = now`, schema version) and
   the metadata (counts and language).
4. Write the ZIP to the user-chosen `Uri` (Storage Access Framework `CreateDocument`),
   streaming each attachment file from `files/attachments/`.

### 5.2 Restore: validate → confirm → replace → reminders → preferences

```
DataViewModel.inspect(uri) ── BackupManager.inspect ── BackupCodec.read + BackupValidator.validate
        │  (nothing changed yet; on error → RestoreError message)
        ▼
pendingRestore = archive  →  confirmation dialog shows createdAt, appVersion and counts
        │  user taps "Replace and restore" (or cancels → cancelRestore)
        ▼
DataViewModel.confirmRestore() ── BackupManager.restore(archive)
```

`BackupManager.restore(archive)`:

1. Validate again (attachment files must have been unpacked by `inspect`).
2. Record the ids of tasks, events and habits that currently have reminders, so their
   alarms can be cancelled later.
3. **Swap the attachment folder:** unreferenced unpacked files are deleted, the current
   `files/attachments/` is renamed to `attachments-previous`, and the staging folder is renamed
   to `files/attachments/` (renames on one file system are atomic).
4. **In one `db.withTransaction`:**
   1. `BackupDao.clearAll()` (children first, search index last).
   2. Insert, in FK-safe order: tags → projects → project tags → project milestones → tasks
      (**topologically ordered, parents first**, by `orderParentsFirst`) → task tags →
      notebooks → sections → notes → note tags → habits → habit completions → goals → goal
      milestones → events → focus sessions → templates → task reminders → task dependencies →
      saved filters → note versions → note links → journal entries → mood entries →
      attachments → challenges → badges → activity log → calendar links.
   3. `SearchIndexMaintenance.rebuild()`.
5. If any exception occurs, the transaction **rolls back**, the previous attachment folder
   is put back, and current data is untouched. The exception (or an `OutOfMemoryError`) is
   wrapped as `BackupException.RestoreFailed`. `CancellationException` is re-thrown unchanged.
   After a commit, `attachments-previous` is deleted.
6. After commit, cancel the old alarms and their posted notifications (each one is
   best-effort).
7. **Preferences** are imported only after the data has committed, and only if the map is
   non-empty. This step is best-effort.
8. `ReminderScheduler.rescheduleAll()` re-creates alarms from the restored data
   (best-effort).

Cancelling the confirmation, or a failed restore, deletes the staging folder
(`BackupManager.discard`); the next `inspect` also clears any abandoned staging folder.

`deleteAllData()` (*Settings › Data management*) records the items with reminders, runs
`clearAll()` in a transaction, deletes every attachment file, then cancels their alarms and posted notifications and the
focus end alarm, and reschedules reminders. It does not touch preferences. Alarms also carry
their planned trigger time, and the receiver drops one that no longer matches the item, so a
leftover alarm can never notify for a restored item that reuses an id.

---

## 6. Errors and their user-facing meaning

`DataViewModel` maps exceptions with `restoreErrorMessage()`. Strings are in
`feature/settings/src/main/res/values{,-fa}/settings_strings.xml`.

| Exception | Raised when | Message shown (English resource) |
|---|---|---|
| `NotABackup` | Not a ZIP, no known entries, no manifest, or a foreign `application` | `backup_error_not_backup`: "This file is not a Plan-B backup." |
| `Corrupt` | Invalid or truncated ZIP, too many entries, an entry too large, bad JSON, `database.json` missing, format < 1 | `backup_error_corrupt`: "This backup is damaged and cannot be restored." |
| `UnsupportedVersion(version)` | `backupFormatVersion > CURRENT` | `backup_error_version`: "This backup was made by a newer version of Plan-B. Please update the app." |
| `NewerDatabase(schemaVersion)` | `databaseSchemaVersion` newer than this app's database | `backup_error_version` (same message) |
| `Invalid` | Validator failure (duplicate ids, broken references, duplicate check-ins, cycles) | `backup_error_invalid`: "This backup contains inconsistent data and was not restored." |
| `RestoreFailed(cause)` or anything else | A database error during the replace transaction (rolled back) | `backup_error_restore`: "Restore failed. Your current data was kept." |

Other messages:

| Outcome | Message |
|---|---|
| Backup created | `backup_created` |
| Backup failed | `backup_failed`: "Backup failed. Nothing was changed." |
| Restore succeeded | `backup_restored` |
| Export finished | `export_done` (plural, with the count of exported items) |
| Export failed | `export_failed` |
| Import finished | `import_done` (imported and skipped counts) |
| Import failed | `import_failed` |
| All data deleted | `data_deleted` |

The English exception messages in `BackupException` are for logs and tests only. The UI
always shows the localized resource.

---

## 7. Export and import formats (`DataTransfer`)

These formats are portable and are **not** backups. They contain no ids and cannot be
restored as a backup.

### 7.1 Tasks: CSV export

- RFC 4180 CSV, UTF-8 **with BOM** (`U+FEFF`) so spreadsheet apps detect Persian text, and
  `\r\n` line endings.
- A field is quoted when it contains `,`, `"`, CR or LF. Quotes are doubled.
- A field that a spreadsheet would treat as a formula (starting with `=`, `+`, `-`, `@`, tab
  or CR) gets a leading apostrophe, as are fields that already start with an apostrophe
  followed by such a character. Import removes exactly that apostrophe, so exports round-trip.
- Every task is exported except tasks in the trash. Each top-level task is followed by its subtasks, which carry the
  parent's title in the `parent` column.
- Header and column order:

| Column | Content |
|---|---|
| `title` | |
| `description` | |
| `status` | `TODO`, `IN_PROGRESS` or `DONE` |
| `priority` | `NONE`, `LOW`, `MEDIUM` or `HIGH` |
| `start_date` | ISO-8601 Gregorian `yyyy-MM-dd`, or empty |
| `due_date` | ISO-8601 Gregorian, or empty |
| `due_time` | ISO `HH:mm[:ss]`, or empty |
| `project` | project title, or empty |
| `tags` | tag names joined with `;` |
| `notes` | |
| `estimated_minutes` | Int, or empty |
| `parent` | title of the parent task for subtasks, or empty |

### 7.2 Tasks: JSON export

A JSON array of `TaskExport` objects with the same information: `title`, `description`,
`status`, `priority` (name), `startDate`, `dueDate`, `dueTime` (ISO strings), `project`
(title), `tags` (array of names), `notes`, `estimatedMinutes` and `parent` (parent task
title, subtasks only). Null fields are omitted.

### 7.3 Notes: Markdown ZIP export

- One `.md` file per note (archived notes included, notes in the trash left out), in a folder named after its notebook:
  `<Notebook>/<Note title>.md`.
- In folder and file names, `\ / : * ? " < > |` and control characters become spaces, runs
  of dots become one dot and leading/trailing dots are removed (so no entry name contains
  `..`). Whitespace is collapsed and names are cut to 60 characters. A blank name becomes
  `untitled`. A blank note title becomes `Note <id>`. A name collision gets ` (<id>)`
  appended.
- The body comes from `Markdown.export(title, document)` in `core/model`:
  - the title becomes `# Title`
  - block types map as follows:

| Block type | Markdown |
|---|---|
| heading | `## …` |
| checklist | `- [ ]` or `- [x]` |
| bullet | `- ` |
| numbered | `1.`, `2.`, … |
| quote | `> ` |
| divider | `---` |
| code | fenced with three backticks |
| table, database (Pro) | GFM table (`\|` and `<br>` escaped; database checkboxes as `[x]`/`[ ]`, all rows in its sort order) |
| formula (Pro) | `$$` block with the LaTeX-like source |
| chart (Pro) | GFM table of its labels and values, then the title in italics |
| photo, scan, drawing (Pro) | `![caption](attachments/<file>)` (a drawing's PNG); a scan's recognized text follows as a quote |
| file, voice recording (Pro) | `[name](attachments/<file>)`; a transcript follows as a quote |

  In the ZIP, the files of these blocks are written to `<Notebook>/attachments/<file name>`, so
  the links work when the ZIP is unpacked; a locked note's blocks (and files) are not exported.
  A single note exported from the editor as `.md` names the files instead (no folder). Markdown
  import reads GFM tables and `$$` blocks back as table and formula blocks; image links stay
  text.

### 7.4 Notes: JSON export

A JSON array of `NoteExport` objects: `{ "title", "notebook" (title), "markdown", "pinned",
"favorite" }`.

### 7.5 Task import (CSV or JSON)

**Rule: import never overwrites or merges existing data. Every valid row becomes a NEW task**
through `TaskRepository.save`, which also indexes it for search. The settings string
`import_tasks_summary` states this to the user.

- **Format detection:** after stripping a BOM and leading whitespace, text that starts with
  `[` is parsed as a JSON `TaskExport` array (a decode failure raises
  `ImportException("Invalid JSON")`). Anything else is parsed as CSV.
- **CSV:** the header is matched case-insensitively and columns may be in any order. A
  `title` column is required (`ImportException("CSV needs a 'title' column")`). The columns
  read are `description`, `status`, `priority`, `start_date`, `due_date`, `due_time`,
  `project`, `tags` (split on `;` or `,`), `notes`, `estimated_minutes` and `parent`. Persian and Arabic digits
  are accepted in numbers, dates and times.
- **Row conversion:**
  - Rows with a blank title are skipped.
  - A row with an unparsable date or time is **skipped**, not imported without the date.
  - Status and priority are parsed case-insensitively. Unknown values become `TODO` and
    `NONE`.
  - Titles are trimmed and cut to 500 characters.
  - `dueTime` is kept only when a due date is present.
  - `estimatedMinutes` must be in 0..100000, otherwise it is dropped.
  - A leading `#` is removed from tag names. Tags are matched to existing tags by name, and
    new ones are created.
  - `project` links the task to an existing project with the same title
    (case-insensitive); an unknown title creates a new project. Existing projects are not
    changed.
  - `parent` links a subtask to the most recent top-level task with that title imported
    from the same file; if there is none, the row becomes a top-level task.
- If no rows parse, `ImportException("No tasks found")` is raised. Otherwise the result is
  `ImportResult(imported, skipped)`.
- Markdown **note** import is separate. It lives in *Notebooks*
  (`NotebooksViewModels.importMarkdown`, `Markdown.import`), and also only creates new notes.

---

## 8. Tests (`core/backup/src/test/.../BackupTest.kt`)

`emptyDatabase_roundTrips`, `normalDatabase_withPersianAndMixedText_restoresExactly`,
`largeDatabase_roundTrips`, `oldFormat_withMissingOptionalFieldsAndTables_imports`,
`corruptJson_isRejected`, `corruptZip_isRejected`, `futureFormat_isRejected`,
`otherApplication_isRejected`, `duplicateIds_andBrokenReferences_failValidation`,
`restoreFailure_rollsBack_andKeepsCurrentData`, `subtasksBeforeParents_inArchive_areOrdered`,
`csv_roundTripsPersianAndQuotes`, `oversizedArchive_isRejectedBeforeExhaustingMemory`,
`taskExport_thenImport_addsCopiesLinkedToProjects_withoutOverwriting`,
`csvImport_unknownProject_createsIt_andSkipsInvalidRows`, and for format 2:
`v3TablesAndAttachments_roundTripThroughTheZip`, `attachmentRowWithoutItsFile_failsValidation`,
`snapshot_leavesOutAttachmentsWithoutFileOrOwner`, `attachmentEntryNames_cannotEscapeTheStagingFolder`,
`oversizedAttachments_areRejected`, `failedRestore_keepsCurrentDataAndAttachments`,
`v3References_areValidated`, `deleteAllData_removesAttachmentFiles`,
`format1Backup_restoresWithNeutralValuesForNewFields`. `RichNotesBackupTest` (Plan-B Pro rich
notes): `everyAttachmentKind_roundTripsThroughABackup` (photo, scan with text, file, recording
with transcript, drawing preview and vector file; searchable again after restore),
`markdownZip_includesTheFilesAndLinksThem`, `attachmentLimits_matchTheBackupLimits`.
