# Fonts

Plan-B uses **Anjoman Max** (designed by Hirbod Lotfian, published by
[fontiran.com](https://fontiran.com)) in four weights: Regular, Medium, SemiBold and Bold.
It is proprietary software used under the owner's registered license. The license allows the
font to be embedded in the app but forbids redistributing the font files, and this repository
is public, so:

- the plain font files are **never committed**;
- the license code is **not** shown anywhere in the app and is kept only in the private
  `FontLicense.txt` next to the font files, as Fontiran requires (that folder is git-ignored);
- the repository contains only `fonts/planb-fonts.tar.gz.gpg`, an AES-256 encrypted archive of
  the four `.ttf` files that cannot be used without the passphrase.

## How the build finds the fonts

`core/designsystem/build.gradle.kts` copies the four files into generated resources
(`R.font.planb_regular/medium/semibold/bold`) from, in order:

1. `$PLANB_FONTS_DIR`, or
2. `private-fonts/` in the project root (git-ignored).

The expected names are `AnjomanMax-Regular.ttf`, `AnjomanMax-Medium.ttf`,
`AnjomanMax-SemiBold.ttf` and `AnjomanMax-Bold.ttf` (from the `01- Base` folder of the
purchased package).

If they are missing, the open-source Vazirmatn (`core/designsystem/fonts-fallback/`) is used so
a public checkout still builds. Release builds and screenshot verification refuse the fallback
(`-Pplanb.requirePrivateFonts=true` or `PLANB_REQUIRE_PRIVATE_FONTS=true`), so a published
APK/AAB always contains the licensed typeface and screenshots always show it.

## Local setup

Either copy the four `.ttf` files (and your `FontLicense.txt`) into `private-fonts/`, or decrypt
the committed archive:

```bash
export PLANB_FONTS_PASSPHRASE=…   # from your password manager
tools/fonts.sh decrypt            # → private-fonts/
```

## CI and releases

Add the repository secret **`PLANB_FONTS_PASSPHRASE`**. The CI and Release workflows run
`tools/fonts.sh decrypt` before building; the passphrase is passed to gpg through a file
descriptor and never appears on a command line or in logs. Without the secret, CI still runs
all tests but fails the screenshot check with a clear message (pull requests from forks, which
never receive secrets, skip only the screenshot comparison), and the Release workflow stops.

## Updating or replacing the fonts

```bash
# put the new AnjomanMax-*.ttf files in private-fonts/, then:
export PLANB_FONTS_PASSPHRASE=…   # same secret, or a new one (update the GitHub secret)
tools/fonts.sh encrypt            # rewrites fonts/planb-fonts.tar.gz.gpg
./gradlew recordRoborazziDebug    # screenshots change with the typeface
```

Commit only the encrypted archive and the re-recorded screenshots.
