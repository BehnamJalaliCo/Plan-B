# Dependencies

All versions are pinned in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Only
stable releases are used (no alpha, beta or RC artifacts). Nothing in the app talks to the
network; no library adds the `INTERNET` permission to the merged release manifest.

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
3. Check the merged release manifest still has no `INTERNET` permission.
4. Update this file.
