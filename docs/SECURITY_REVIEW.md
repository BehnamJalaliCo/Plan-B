# Security Review — Plan-B (with the Plan-B Pro foundation)

Scope: the Android app (all modules), build configuration, release signing and CI workflows.
Reviewed on 4 October 2026 against the release build (`app-release`, R8 enabled).

## Threat model

Plan-B is an offline, single-user app. The assets are the user's private notes, tasks and
habits, the user's AI provider key (if they add one) and the Pro entitlement. Relevant threats:
other apps on the device, people with physical access to an unlocked or locked device,
malicious files given to import/restore, forged purchase data, and leakage of release signing
credentials or the Bazaar key from the repository or CI.

## Findings and controls

| Area | Status | Details |
|---|---|---|
| Network | ✅ | `INTERNET` is declared only by `core:ai` and used only by the optional AI assistant: off by default, enabled only after explicit consent (timestamp stored), with the user's own key, HTTPS only, requests go directly to the provider the user chose. No other code opens connections; no analytics, ads, crash reporting or accounts. |
| Permissions | ✅ | CI and the release script compare the merged release manifest with `tools/allowed-permissions.txt` (`tools/check_permissions.sh`): `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR`, `USE_BIOMETRIC`/`USE_FINGERPRINT` (Pro App lock), `WAKE_LOCK` (WorkManager, used by automatic backups, the trash purge and the Pro widgets; WorkManager's own foreground service is never used) `READ_CALENDAR`/`WRITE_CALENDAR` (Pro device calendar sync, asked only when the user turns it on; only events carrying Plan-B's package marker are ever changed or deleted), `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Focus Pro's ambient sound: a non-exported `mediaPlayback` service that runs only while a session with a sound runs and is started only from the foreground), `ACCESS_NOTIFICATION_POLICY` (Focus Pro strict mode; effective only after the user allows Do Not Disturb access; the previous interruption filter is kept device-only and restored, never one the user changed), the five Health Connect read permissions (Pro #27; read-only aggregated daily totals, asked per metric in context; the rationale activity is exported only for Health Connect's rationale action and, on Android 14+, through an alias guarded by `START_VIEW_PERMISSION_USAGE`) and AndroidX's signature-level dynamic-receiver permission. Anything else fails the build. Notification permission is requested in context, only when a reminder is set. |
| AI provider key | ✅ | Encrypted with AES-256-GCM using a non-exportable Android Keystore key, stored in a separate device-only DataStore file that is not part of backups or exports (and Android backup is disabled). `AiEndpoint.toString()` omits the key; nothing about requests or answers is logged. Errors are mapped to neutral categories. |
| Purchases | ✅ | Cafe Bazaar purchases (Poolakey) are verified on the device: Poolakey's RSA check plus Plan-B's own `PurchaseSignatureVerifier` (SHA1withRSA with the Bazaar public key) and a package-name check. Poolakey's `BillingReceiver` is exported (Bazaar's broadcast fallback); forged broadcasts fail signature verification. The Bazaar key comes from `PLANB_BAZAAR_RSA_KEY` (CI secret), never from git; builds without it report "not configured". The cached entitlement is a device-only file; a rooted device could edit it (accepted risk, as with any offline entitlement). The lifetime product is never consumed. |
| Locked notes (Pro #36) | ✅ | Bodies are sealed with AES-256-GCM under a key derived from the user's passphrase (PBKDF2-HMAC-SHA256, 600,000 iterations, random 16-byte salt). The envelope (version, iterations, salt, IV) is authenticated as associated data; tampering or a wrong passphrase fails. Only salt, work factor and a check value are stored (device-only `planb_security` DataStore); the passphrase and key never touch disk and stay in memory only until the vault locks (App lock, 5 minutes in the background, process death). Optional fingerprint unlock wraps the key with a non-exportable Keystore key that requires strong biometric authentication and is invalidated by new enrolments. Locked bodies are left out of search, drafts, versions, exports, templates and logs; backups carry the ciphertext. Passphrases are never kept in saved instance state. |
| App lock (Pro #36) | ✅ | BiometricPrompt with device-credential fallback; locks on cold start and after the chosen time in the background (wall and monotonic clocks, a clock moved back locks). The lock screen covers the whole UI and hides it from accessibility; "hide in recents" uses `setRecentsScreenshotEnabled(false)` (API 33+) or `FLAG_SECURE`. Without any device screen lock the app cannot verify the user, so enabling App lock requires one. |
| Automatic backups (Pro #37) | ✅ | Written only into a SAF folder the user picked (persisted URI permission); only files matching `Plan-B-auto-yyyyMMdd-HHmmss.zip` are ever deleted. Settings and results live in the device-only `planb_auto_backup` DataStore. |
| Data at rest | ✅ | Room database and DataStore live in app-private storage. `allowBackup=false`, `fullBackupContent=false` and `data_extraction_rules.xml` exclude everything from cloud backup and device transfer. |
| Exported components | ✅ | `MainActivity` and its launcher aliases (`.LauncherClassic` and the Pro icon aliases). Plan-B Pro adds: the home-screen widget receivers (only `APPWIDGET_UPDATE`; actions run through Glance's own non-exported receiver), the quick-settings tile services (protected by `BIND_QUICK_SETTINGS_TILE`, only the system can bind) and `PhoneWearListenerService` (bound only by Google Play services, which delivers messages only from the same package and signing key; a message can only complete a task or check in a habit, never read data). `QuickActionActivity` and `WidgetRefreshReceiver` are not exported. `RescheduleReceiver` is exported only for protected system broadcasts and ignores any other action. `ReminderReceiver` is not exported. Poolakey's `BillingReceiver` is exported for Cafe Bazaar (see Purchases). No content providers, services or file providers are exported. |
| Deep links | ✅ | No `VIEW` intent filters. Links from the app's own notifications, widgets, tiles and shortcuts (`planb://open/<type>/<id>`, also `capture`, `new-task`, `new-note`, `today`, `calendar`, `habits`, `pro/<feature>`) are validated (scheme, host, numeric id) and only navigate to an existing screen or open an empty editor; nothing is modified. |
| PendingIntents | ✅ | All are explicit and `FLAG_IMMUTABLE`. |
| Lock screen | ✅ | Notifications use `VISIBILITY_PRIVATE` with a generic public version, so titles are hidden on a secure lock screen. |
| SQL | ✅ | Room DAOs with bound parameters. The one dynamic query (task filters) is assembled from fixed fragments with all values bound as arguments. FTS queries are built from normalized tokens and bound. |
| Backup restore | ✅ | JSON entries are read in memory with limits on entry count, per-entry size and total size (zip-bomb protection). Attachment files are streamed to a private staging folder with per-file, total and count limits; only flat names matching a strict pattern are written, so no entry can escape the folder (no zip-slip). The attachment folder is swapped atomically and restored if the database transaction fails. Manifest, format version and every record are validated before anything is replaced; the replacement runs in a single database transaction, so a bad file cannot leave partial data. |
| Import | ✅ | CSV/JSON task import validates every row, skips invalid rows, never overwrites existing data and enforces size limits. |
| File access | ✅ | Only the Storage Access Framework (user-chosen documents); no storage permissions. |
| Logging | ✅ | The app logs only failure class names at startup; no note or task content, purchase data, AI text or key is ever written to Logcat. The activity log table stores titles, never note bodies. |
| WebView / dynamic code | ✅ | None. |
| Release build | ✅ | R8 minification and resource shrinking; debuggable=false; the debug build uses a separate application id (`.debug`). |
| Signing secrets | ✅ | No keystore, password or key material in the repository. `.gitignore` excludes `*.jks`, `*.keystore`, `*.p12`, `*.pem`, `*.key`, `keystore.properties`, `local.properties`, `.env*` and `release-signing/`. Signing reads `PLANB_KEYSTORE_PATH`, `PLANB_KEYSTORE_PASSWORD`, `PLANB_KEY_ALIAS`, `PLANB_KEY_PASSWORD` from the environment (CI decodes `PLANB_KEYSTORE_BASE64` into a temporary file that is deleted after the build) or from an untracked `keystore.properties`. |
| CI | ✅ | Workflows use least-privilege `permissions`, never echo secrets, publish only on manual `workflow_dispatch` with an explicit input, and never attach signing material to releases. |

## Residual risks and recommendations

- **Unencrypted exports.** Backup and export files are plain ZIP/CSV/JSON so that users can
  read and move them. The UI states where they are saved; users should store them safely.
- **Device compromise.** A rooted or compromised device can read app-private storage. Database
  encryption was not added because it would require storing a key on the same device without a
  user secret. Plan-B Pro adds App lock and passphrase-encrypted locked notes; the rest of the
  database stays unencrypted (accepted).
- **Exact alarm permission.** Users may deny exact alarms; reminders then fall back to inexact
  delivery. This is a reliability, not a security, concern.

## How this was checked

- Merged manifest inspection of the release variant.
- Code search for logging, PendingIntent flags, exported components, raw SQL and file I/O.
- Unit tests for corrupt, foreign, oversized and newer-version backups (`core/backup` tests).
- Android Lint (all modules, zero findings), including security checks such as
  `UnsafeProtectedBroadcastReceiver` and `ExportedReceiver`.
- Repository scan for committed secrets and signing files.
