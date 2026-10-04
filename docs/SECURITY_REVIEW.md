# Security Review — Plan-B 1.0.0

Scope: the Android app (all modules), build configuration, release signing and CI workflows.
Reviewed on 4 October 2026 against the release build (`app-release`, R8 enabled).

## Threat model

Plan-B is an offline, single-user app. The assets are the user's private notes, tasks and
habits. Relevant threats: other apps on the device, people with physical access to an unlocked
or locked device, malicious files given to import/restore, and leakage of release signing
credentials from the repository or CI.

## Findings and controls

| Area | Status | Details |
|---|---|---|
| Network | ✅ | No `INTERNET` permission in the merged release manifest (verified with `processReleaseMainManifest`). No networking libraries. No analytics, ads, crash reporting or accounts. |
| Permissions | ✅ | Only `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED` (plus AndroidX's signature-level dynamic-receiver permission). Notification permission is requested in context, only when a reminder is set. |
| Data at rest | ✅ | Room database and DataStore live in app-private storage. `allowBackup=false`, `fullBackupContent=false` and `data_extraction_rules.xml` exclude everything from cloud backup and device transfer. |
| Exported components | ✅ | `MainActivity` (launcher). `RescheduleReceiver` is exported only for protected system broadcasts and ignores any other action. `ReminderReceiver` is not exported. No content providers, services or file providers are exported. |
| Deep links | ✅ | No `VIEW` intent filters. Links inside the app's own notifications (`planb://open/<type>/<id>`) are validated (scheme, host, numeric id) and only navigate to an existing screen; nothing is modified. |
| PendingIntents | ✅ | All are explicit and `FLAG_IMMUTABLE`. |
| Lock screen | ✅ | Notifications use `VISIBILITY_PRIVATE` with a generic public version, so titles are hidden on a secure lock screen. |
| SQL | ✅ | Room DAOs with bound parameters. The one dynamic query (task filters) is assembled from fixed fragments with all values bound as arguments. FTS queries are built from normalized tokens and bound. |
| Backup restore | ✅ | Archives are read fully in memory with limits on entry count, per-entry size and total size (zip-bomb protection); entry names are never used as file paths (no zip-slip). Manifest, format version and every record are validated before anything is replaced; the replacement runs in a single database transaction, so a bad file cannot leave partial data. |
| Import | ✅ | CSV/JSON task import validates every row, skips invalid rows, never overwrites existing data and enforces size limits. |
| File access | ✅ | Only the Storage Access Framework (user-chosen documents); no storage permissions. |
| Logging | ✅ | The app does not log; in particular no note or task content is ever written to Logcat. |
| WebView / dynamic code | ✅ | None. |
| Release build | ✅ | R8 minification and resource shrinking; debuggable=false; the debug build uses a separate application id (`.debug`). |
| Signing secrets | ✅ | No keystore, password or key material in the repository. `.gitignore` excludes `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `keystore.properties`, `local.properties`, `.env*` and `release-signing/`. Signing reads `PLANB_KEYSTORE_PATH`, `PLANB_KEYSTORE_PASSWORD`, `PLANB_KEY_ALIAS`, `PLANB_KEY_PASSWORD` from the environment (CI decodes `PLANB_KEYSTORE_BASE64` into a temporary file that is deleted after the build) or from an untracked `keystore.properties`. |
| CI | ✅ | Workflows use least-privilege `permissions`, never echo secrets, publish only on manual `workflow_dispatch` with an explicit input, and never attach signing material to releases. |

## Residual risks and recommendations

- **Unencrypted exports.** Backup and export files are plain ZIP/CSV/JSON so that users can
  read and move them. The UI states where they are saved; users should store them safely.
- **Device compromise.** A rooted or compromised device can read app-private storage. Database
  encryption was not added because it would require storing a key on the same device without a
  user secret; an optional app lock with a key derived from the user's credential is a
  candidate for a later version.
- **Exact alarm permission.** Users may deny exact alarms; reminders then fall back to inexact
  delivery. This is a reliability, not a security, concern.

## How this was checked

- Merged manifest inspection of the release variant.
- Code search for logging, PendingIntent flags, exported components, raw SQL and file I/O.
- Unit tests for corrupt, foreign, oversized and newer-version backups (`core/backup` tests).
- Android Lint (all modules, zero findings), including security checks such as
  `UnsafeProtectedBroadcastReceiver` and `ExportedReceiver`.
- Repository scan for committed secrets and signing files.
