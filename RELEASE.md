# Release Guide

## Versioning

| Field | Value | Where |
|---|---|---|
| `applicationId` | `com.behnamjalali.planb` (never changes) | `app/build.gradle.kts` |
| `versionName` | `1.1.1` (Semantic Versioning) | `app/build.gradle.kts` |
| `versionCode` | `4` (increase by one for every store upload; the Wear OS build uses 1,000,000 + this) | `app/build.gradle.kts` |
| Tag | `v<versionName>`, created by the release workflow | Git |

Record user-visible changes in [CHANGELOG.md](CHANGELOG.md) and in
`release/cafebazaar/RELEASE_NOTES_{fa,en}.md` and `store/cafebazaar/whats_new_{fa,en}.txt`.

## SDK levels

- **minSdk 26** (Android 8.0): adaptive icons, notification channels and `java.time` are
  available natively, and it covers practically all active devices in the target market.
- **targetSdk / compileSdk 37**: the current stable platform, so the app runs with the latest
  behaviour changes (per-app languages, exact-alarm and notification permissions, predictive
  back) and meets store targeting requirements.

## Build types

| Type | Purpose | Notes |
|---|---|---|
| `debug` | Development | App id `com.behnamjalali.planb.debug`, version suffix `-debug`, not minified |
| `release` | Store builds | R8 minification + resource shrinking, `proguard-rules.pro`, baseline profile, signed with the release key when configured |
| `benchmarkRelease`, `nonMinifiedRelease` | Macrobenchmarks / baseline profiles | Added by the baseline-profile plugin; signed with the debug key; never distributed |

The App Bundle keeps both languages in the base module (language splits are disabled) because
the language can be switched inside the app.

## Signing

The repository contains **no signing material**. `.gitignore` excludes keystores
(`*.jks`, `*.keystore`, `*.p12`), keys, `keystore.properties`, `local.properties` and `.env`
files.

The release signing config is created only when all four values are available, from the
environment first and otherwise from an untracked `keystore.properties` in the project root:

| Environment variable | `keystore.properties` key | Meaning |
|---|---|---|
| `PLANB_KEYSTORE_PATH` | `storeFile` | Path to the keystore file |
| `PLANB_KEYSTORE_PASSWORD` | `storePassword` | Keystore password |
| `PLANB_KEY_ALIAS` | `keyAlias` | Key alias |
| `PLANB_KEY_PASSWORD` | `keyPassword` | Key password |

Without them `assembleRelease` produces an unsigned APK (`app-release-unsigned.apk`); nothing
falls back to a debug or throwaway key.

### Creating the upload/release key (once, by the owner)

```bash
keytool -genkeypair -v -keystore planb-release.jks -alias planb \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep the keystore and passwords in a password manager and an offline backup. Losing the key
means the app can no longer be updated in stores.

### GitHub secrets

Add these **repository secrets** (Settings → Secrets and variables → Actions), or put them on
the `release` environment to require approval:

| Secret | Value |
|---|---|
| `PLANB_KEYSTORE_BASE64` | `base64 -w0 planb-release.jks` |
| `PLANB_KEYSTORE_PASSWORD` | keystore password |
| `PLANB_KEY_ALIAS` | key alias |
| `PLANB_KEY_PASSWORD` | key password |
| `PLANB_FONTS_PASSPHRASE` | passphrase of the encrypted licensed fonts (see [docs/FONTS.md](docs/FONTS.md)); also needed by CI to verify screenshots |
| `CAFEBAZAAR_PISHKHAN_API_SECRET` | optional: Pishkhan API secret for automated upload |
| `PLANB_BAZAAR_RSA_KEY` | optional: overrides the Cafe Bazaar RSA public key in `core/billing/bazaar-rsa-public-key.txt` (Pishkhan → in-app billing), used to verify Plan-B Pro purchases on the device (see [docs/PRO.md](docs/PRO.md)). |

The workflow decodes the keystore into the runner's temporary directory with mode 600, passes
passwords only through environment variables (never on a command line or in logs) and deletes
the file at the end of the job, even on failure.

## Building a release locally

```bash
export PLANB_KEYSTORE_PATH=/secure/planb-release.jks
export PLANB_KEYSTORE_PASSWORD=…  PLANB_KEY_ALIAS=planb  PLANB_KEY_PASSWORD=…
./gradlew testDebugUnitTest -Proborazzi.test.verify=true lintDebug
tools/package_cafebazaar.sh
```

`tools/package_cafebazaar.sh` builds the signed APK and AAB, verifies the APK signature with
`apksigner`, downloads the official Cafe Bazaar bundle-signer (pinned version and SHA-256),
generates the `.bin`, and writes everything plus checksums to `release/cafebazaar/` (binaries
are git-ignored). See [docs/CAFE_BAZAAR_RELEASE.md](docs/CAFE_BAZAAR_RELEASE.md).

## Release workflow (GitHub Actions)

`.github/workflows/release.yml` runs only on manual **workflow_dispatch**:

1. Fails immediately with a clear message if any signing secret is missing.
2. Runs all JVM tests with screenshot verification, and lint.
3. Decodes the keystore, runs `tools/package_cafebazaar.sh`, deletes the keystore.
4. Uploads `release/cafebazaar/*` (including the R8 mapping) as a workflow artifact.
5. If `create_github_release` is checked (default): creates tag `v<versionName>` and a GitHub
   Release with the signed APK, AAB, Bazaar `.bin`, `SHA256SUMS.txt` and release notes.
   Keystores, passwords, keys and API secrets are never attached.
6. If `publish_to_cafebazaar` is checked (default off): uploads the signed APK through the
   Pishkhan API (`tools/publish_cafebazaar.sh`) and commits a release with the Persian and
   English change notes. Automatic publishing after review happens only when
   `cafebazaar_auto_publish` is also checked. A normal push never publishes anything.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes to `main` and on pull requests:

- **Tests, screenshots, lint and builds**: all JVM tests with Roborazzi verification, Android
  Lint for every module, debug/release/test/benchmark builds, and a check that the release
  manifest requests only the permissions listed in `tools/allowed-permissions.txt` (`tools/check_permissions.sh`; the release script runs the same check). Reports and screenshot diffs are uploaded on failure.
- **Device tests**: instrumentation tests on an Android 14 (API 34) emulator.

## Checklist for a new version

1. Bump `versionCode` (+1) and `versionName`.
2. Update CHANGELOG, release notes and what's-new texts (both languages).
3. `./gradlew recordRoborazziDebug` if the UI changed, review the diffs, then
   `python3 tools/generate_ui_gallery.py` and `python3 tools/generate_store_graphics.py`.
4. Push to `main`; wait for CI to be green.
5. Run the **Release** workflow; download the artifact; verify `sha256sum -c SHA256SUMS.txt`.
6. Upload to Cafe Bazaar (manually or with the Pishkhan option).
