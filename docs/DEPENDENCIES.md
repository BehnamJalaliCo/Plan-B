# Dependencies

All versions are pinned in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Only
stable releases are used (no alpha, beta or RC artifacts). The only network code is the
optional AI assistant in `core:ai` (OkHttp); `INTERNET` is declared there, and ML Kit's
handwriting model download (Plan-B Pro, after consent) uses it too.
The merged release manifest must request only the permissions in
[`tools/allowed-permissions.txt`](../tools/allowed-permissions.txt) (checked by CI).

## Build tooling

| Tool | Version | Notes |
|---|---|---|
| Gradle (wrapper) | 9.8.0 | Configuration cache enabled |
| Android Gradle Plugin | 9.4.1 | Built-in Kotlin support |
| Kotlin / Compose compiler plugin | 2.4.20 | |
| KSP | 2.3.12 | Room and Hilt code generation |
| JDK | 21 (bytecode target 17) | |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 | |

## Runtime libraries (shipped in the app)

| Library | Version | License | Used for |
|---|---|---|---|
| Kotlin stdlib | 2.4.20 | Apache 2.0 | Language runtime |
| kotlinx-coroutines | 1.11.0 | Apache 2.0 | Concurrency, Flow |
| kotlinx-serialization-json | 1.11.0 | Apache 2.0 | Navigation routes, note documents, backups, templates |
| Compose BOM | 2026.09.00 | Apache 2.0 | UI toolkit (foundation, material3 1.4, animation, ui) |
| Material Icons Extended | via BOM | Apache 2.0 | Icons |
| androidx.activity:activity-compose | 1.13.0 | Apache 2.0 | Compose activity integration, SAF launchers |
| androidx.appcompat | 1.8.0 | Apache 2.0 | Per-app language API (`AppCompatDelegate`) |
| androidx.core:core-ktx | 1.19.1 | Apache 2.0 | Notifications, KTX helpers |
| androidx.core:core-splashscreen | 1.2.0 | Apache 2.0 | Splash screen on all API levels |
| androidx.lifecycle (runtime-compose, viewmodel-compose) | 2.11.0 | Apache 2.0 | ViewModels, lifecycle-aware collection |
| androidx.navigation:navigation-compose | 2.10.2 | Apache 2.0 | Type-safe navigation |
| androidx.room (runtime, ktx) | 2.8.5 | Apache 2.0 | Database, FTS4 search |
| androidx.datastore:datastore-preferences | 1.2.1 | Apache 2.0 | Settings |
| androidx.hilt:hilt-lifecycle-viewmodel-compose | 1.4.0 | Apache 2.0 | `hiltViewModel()` |
| androidx.profileinstaller | 1.4.1 | Apache 2.0 | Installs the baseline profile |
| Dagger Hilt | 2.60.1 | Apache 2.0 | Dependency injection |
| androidx.work:work-runtime-ktx | 2.12.0 | Apache 2.0 | Plan-B Pro automatic backups and the daily trash purge (`ACCESS_NETWORK_STATE` and `FOREGROUND_SERVICE` are removed from the manifest; `WAKE_LOCK` stays) |
| androidx.biometric:biometric | 1.1.0 | Apache 2.0 | Plan-B Pro App lock and fingerprint unlock of locked notes (`USE_BIOMETRIC`, `USE_FINGERPRINT`) |
| Poolakey (`com.github.cafebazaar.Poolakey:poolakey`) | 2.2.0 | Apache 2.0 | Cafe Bazaar in-app billing (Plan-B Pro). From JitPack, restricted to this group by an `exclusiveContent` filter in `settings.gradle.kts`; brings `androidx.fragment` and adds only `PAY_THROUGH_BAZAAR` |
| ML Kit Document Scanner (`com.google.android.gms:play-services-mlkit-document-scanner`) | 16.0.0 | ML Kit terms | Plan-B Pro document scan (#17); runs in Google Play services, the camera + crop fallback works without them |
| ML Kit Text Recognition, unbundled (`com.google.android.gms:play-services-mlkit-text-recognition`) | 19.0.1 | ML Kit terms | Plan-B Pro OCR of Latin text (#17); the model lives in Play services, nothing bundled |
| ML Kit Digital Ink Recognition (`com.google.mlkit:digital-ink-recognition`) | 19.0.0 | ML Kit terms | Plan-B Pro handwriting to text (#18); native recognizer in the APK (≈5 MB compressed, ARM), language models downloaded on first use after consent. Its data-transport usage logging is disabled (backend removed in the manifest) |
| Tesseract4Android (`cz.adaptech.tesseract4android:tesseract4android`) | 4.9.0 | Apache 2.0 (Tesseract), BSD-2 (Leptonica), libjpeg/libpng licenses | Plan-B Pro Persian OCR (#17), with `fas.traineddata` from tessdata_fast (Apache 2.0) in `feature/notebooks/src/main/assets/tessdata/`. From JitPack, restricted to this group; ships no R8 rules, so `app/proguard-rules.pro` keeps its JNI classes |
| OkHttp (+ Okio) | 5.5.0 | Apache 2.0 | HTTP client of the optional AI assistant (`core:ai`) |
| Anjoman Max font (Regular, Medium, SemiBold, Bold) | 3.000 | Proprietary, fontiran.com — used under the owner's license | App typeface (supplied at build time, see [FONTS.md](FONTS.md)) |
| Vazirmatn font (build fallback only) | 33.003 | SIL OFL 1.1 | Used only when the licensed fonts are absent; never in releases (`licenses/Vazirmatn-OFL.txt`) |

The Jalali calendar uses the platform ICU implementation (`android.icu`), so no calendar
library is bundled.

## Test and tooling libraries (not shipped)

| Library | Version | License |
|---|---|---|
| JUnit 4 | 4.13.2 | EPL 1.0 |
| Robolectric | 4.17 | MIT |
| Roborazzi (core, compose, junit rule) | 1.76.0 | Apache 2.0 |
| Google Truth | 1.4.5 | Apache 2.0 |
| OkHttp MockWebServer (`mockwebserver3`) | 5.5.0 | Apache 2.0 |
| Turbine | 1.2.1 | Apache 2.0 |
| kotlinx-coroutines-test | 1.11.0 | Apache 2.0 |
| androidx.test (core, runner, rules, ext-junit) | 1.7.0 / 1.3.0 | Apache 2.0 |
| androidx.room:room-testing | 2.8.5 | Apache 2.0 |
| Compose UI test (ui-test-junit4, ui-test-manifest) | via BOM | Apache 2.0 |
| Hilt testing | 2.60.1 | Apache 2.0 |
| androidx.benchmark (macro-junit4, baseline profile plugin) | 1.5.0 | Apache 2.0 |
| androidx.test.uiautomator | 2.4.0 | Apache 2.0 |

## Release tooling

| Tool | Source | Used for |
|---|---|---|
| Cafe Bazaar bundle-signer | https://github.com/cafebazaar/bundle-signer (official) | Producing the `.bin` upload file from the signed AAB; fetched at release time, never committed |

## Updating dependencies

1. Change the version in `gradle/libs.versions.toml` (stable versions only).
2. Run `./gradlew testDebugUnitTest verifyRoborazziDebug lintDebug assembleRelease`.
3. Run `tools/check_permissions.sh` after `:app:assembleRelease`: the merged release manifest
   may request only the permissions in `tools/allowed-permissions.txt`.
4. Update this file.
