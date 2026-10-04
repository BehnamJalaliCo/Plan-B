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
| Permissions | ✅ | CI and the release script compare the merged release manifest with `tools/allowed-permissions.txt` (`tools/check_permissions.sh`): `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR` and AndroidX's signature-level dynamic-receiver permission. Anything else fails the build. Notification permission is requested in context, only when a reminder is set. |
| AI provider key | ✅ | Encrypted with AES-256-GCM using a non-exportable Android Keystore key, stored in a separate device-only DataStore file that is not part of backups or exports (and Android backup is disabled). `AiEndpoint.toString()` omits the key; nothing about requests or answers is logged. Errors are mapped to neutral categories. |
| Purchases | ✅ | Cafe Bazaar purchases (Poolakey) are verified on the device: Poolakey's RSA check plus Plan-B's own `PurchaseSignatureVerifier` (SHA1withRSA with the Bazaar public key) and a package-name check. Poolakey's `BillingReceiver` is exported (Bazaar's broadcast fallback); forged broadcasts fail signature verification. The Bazaar key comes from `PLANB_BAZAAR_RSA_KEY` (CI secret), never from git; builds without it report "not configured". The cached entitlement is a device-only file; a rooted device could edit it (accepted risk, as with any offline entitlement). The lifetime product is never consumed. |
| Data at rest | ✅ | Room database and DataStore live in app-private storage. `allowBackup=false`, `fullBackupContent=false` and `data_extraction_rules.xml` exclude everything from cloud backup and device transfer. |
| Exported components | ✅ | `MainActivity` (launcher). `RescheduleReceiver` is exported only for protected system broadcasts and ignores any other action. `ReminderReceiver` is not exported. Poolakey's `BillingReceiver` is exported for Cafe Bazaar (see Purchases). No content providers, services or file providers are exported. |
| Deep links | ✅ | No `VIEW` intent filters. Links inside the app's own notifications (`planb://open/<type>/<id>`) are validated (scheme, host, numeric id) and only navigate to an existing screen; nothing is modified. |
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
