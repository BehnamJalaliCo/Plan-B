#!/usr/bin/env bash
# Builds the signed release and the Cafe Bazaar upload files:
#
#   release/cafebazaar/Plan-B-v<version>-release.apk    signed universal APK
#   release/cafebazaar/Plan-B-v<version>-release.aab    signed Android App Bundle
#   release/cafebazaar/Plan-B-v<version>-bazaar.bin     signed bundle digest for Cafe Bazaar
#   release/cafebazaar/Plan-B-v<version>-mapping.txt    R8 mapping (deobfuscating crash traces)
#   release/cafebazaar/SHA256SUMS.txt                   checksums of the files above
#
# The .bin is produced by the official Cafe Bazaar bundle-signer
# (https://github.com/cafebazaar/bundle-signer), downloaded at a pinned version and verified by
# SHA-256 before use.
#
# Signing credentials are read from the environment only and are never printed:
#   PLANB_KEYSTORE_PATH       path to the release keystore (CI decodes PLANB_KEYSTORE_BASE64
#                             into a temporary file)
#   PLANB_KEYSTORE_PASSWORD   keystore password
#   PLANB_KEY_ALIAS           key alias
#   PLANB_KEY_PASSWORD        key password
#
# Optional:
#   PLANB_BAZAAR_RSA_KEY      Cafe Bazaar RSA public key for Plan-B Pro purchases (docs/PRO.md);
#                             without it the build cannot sell Pro
#   PLANB_RELEASE_DIR         output directory (default: release/cafebazaar)
#   PLANB_SKIP_GRADLE=1       reuse already-built outputs in app/build/outputs
set -euo pipefail
set +x # never trace: commands below reference secret environment variables

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BUNDLESIGNER_VERSION="0.1.13"
BUNDLESIGNER_SHA256="1a28844a32a16953eabe99e81400db681e844e11bb3f3bb649b8243cdd4ed3eb"
BUNDLESIGNER_URL="https://github.com/cafebazaar/bundle-signer/releases/download/v${BUNDLESIGNER_VERSION}/bundlesigner-${BUNDLESIGNER_VERSION}.jar"

fail() { echo "error: $*" >&2; exit 1; }

missing=()
for name in PLANB_KEYSTORE_PATH PLANB_KEYSTORE_PASSWORD PLANB_KEY_ALIAS PLANB_KEY_PASSWORD; do
  # Secrets pasted into a web form often end with a newline; strip surrounding whitespace.
  value="${!name:-}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  export "$name=$value"
  [[ -n "$value" ]] || missing+=("$name")
done
unset value
if ((${#missing[@]})); then
  fail "release signing is not configured; missing: ${missing[*]}. See RELEASE.md."
fi
[[ -f "$PLANB_KEYSTORE_PATH" ]] || fail "PLANB_KEYSTORE_PATH does not point to a file"
keytool -list -keystore "$PLANB_KEYSTORE_PATH" -storepass:env PLANB_KEYSTORE_PASSWORD \
  -alias "$PLANB_KEY_ALIAS" >/dev/null 2>&1 ||
  fail "the keystore cannot be opened with PLANB_KEYSTORE_PASSWORD or has no key named PLANB_KEY_ALIAS"

VERSION="$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts | head -1)"
[[ -n "$VERSION" ]] || fail "could not read versionName from app/build.gradle.kts"
OUT="${PLANB_RELEASE_DIR:-$ROOT/release/cafebazaar}"
PREFIX="Plan-B-v${VERSION}"
mkdir -p "$OUT"

if [[ "${PLANB_SKIP_GRADLE:-0}" != "1" ]]; then
  ./gradlew --no-configuration-cache :app:assembleRelease :app:bundleRelease
fi

APK="app/build/outputs/apk/release/app-release.apk"
AAB="app/build/outputs/bundle/release/app-release.aab"
MAPPING="app/build/outputs/mapping/release/mapping.txt"
[[ -f "$APK" ]] || fail "signed APK not found at $APK (was the release signed?)"
[[ -f "$AAB" ]] || fail "AAB not found at $AAB"
[[ -f "$MAPPING" ]] || fail "R8 mapping not found at $MAPPING"

# Only the permissions in tools/allowed-permissions.txt may ship.
"$ROOT/tools/check_permissions.sh" \
  "app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml" ||
  fail "the release manifest requests a permission that is not allowlisted"

# Locate apksigner from the newest installed build-tools.
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f local.properties ]]; then
  SDK="$(sed -n 's/^sdk.dir=//p' local.properties)"
fi
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
[[ -x "$APKSIGNER" ]] || fail "apksigner not found in the Android SDK build-tools"

echo "Verifying APK signature…"
"$APKSIGNER" verify --verbose "$APK" | grep -E "^Verified using|^Number of signers" || true
"$APKSIGNER" verify "$APK"
echo "Signing certificate (public):"
"$APKSIGNER" verify --print-certs "$APK" | grep -E "certificate SHA-256 digest|certificate DN" || true

# Fetch and verify the official bundle-signer.
TOOLS="$ROOT/tools/bundle-signer"
JAR="$TOOLS/bundlesigner-${BUNDLESIGNER_VERSION}.jar"
mkdir -p "$TOOLS"
if [[ ! -f "$JAR" ]]; then
  echo "Downloading bundle-signer ${BUNDLESIGNER_VERSION}…"
  curl -fsSL --retry 4 -o "$JAR.part" "$BUNDLESIGNER_URL"
  mv "$JAR.part" "$JAR"
fi
echo "${BUNDLESIGNER_SHA256}  ${JAR}" | sha256sum -c - >/dev/null || {
  rm -f "$JAR"
  fail "bundle-signer checksum mismatch; refusing to use it"
}

BIN_DIR="$(mktemp -d)"
trap 'rm -rf "$BIN_DIR"' EXIT
echo "Generating the Cafe Bazaar .bin…"
java -jar "$JAR" genbin \
  --bundle "$AAB" \
  --bin "$BIN_DIR" \
  --v2-signing-enabled true \
  --v3-signing-enabled false \
  --ks "$PLANB_KEYSTORE_PATH" \
  --ks-key-alias "$PLANB_KEY_ALIAS" \
  --ks-pass env:PLANB_KEYSTORE_PASSWORD \
  --key-pass env:PLANB_KEY_PASSWORD
BIN="$(ls "$BIN_DIR"/*.bin 2>/dev/null | head -1 || true)"
[[ -n "$BIN" && -s "$BIN" ]] || fail "bundle-signer did not produce a .bin file"

cp "$APK" "$OUT/${PREFIX}-release.apk"
cp "$AAB" "$OUT/${PREFIX}-release.aab"
cp "$BIN" "$OUT/${PREFIX}-bazaar.bin"
cp "$MAPPING" "$OUT/${PREFIX}-mapping.txt"
(
  cd "$OUT"
  sha256sum "${PREFIX}-release.apk" "${PREFIX}-release.aab" "${PREFIX}-bazaar.bin" "${PREFIX}-mapping.txt" > SHA256SUMS.txt
)

echo
echo "Cafe Bazaar release files in $OUT:"
ls -l "$OUT"
cat "$OUT/SHA256SUMS.txt"
