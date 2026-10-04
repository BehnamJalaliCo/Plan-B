#!/usr/bin/env bash
# Fails when a merged manifest requests a permission that is not in tools/allowed-permissions.txt.
# Usage: tools/check_permissions.sh [path/to/AndroidManifest.xml]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MANIFEST="${1:-$ROOT/app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml}"
ALLOWLIST="$ROOT/tools/allowed-permissions.txt"
[[ -f "$MANIFEST" ]] || { echo "error: manifest not found: $MANIFEST" >&2; exit 1; }

allowed="$(grep -v '^[[:space:]]*#' "$ALLOWLIST" | sed 's/[[:space:]]//g' | grep -v '^$' | sort -u)"
# <uses-permission> and <uses-permission-sdk-23>, attributes possibly on following lines.
requested="$(tr '\n' ' ' < "$MANIFEST" | grep -oE '<uses-permission(-sdk-23)?[^>]*>' |
  grep -oE 'android:name="[^"]+"' | sed 's/android:name="//; s/"$//' | sort -u)"

unexpected="$(comm -23 <(echo "$requested") <(echo "$allowed"))"
echo "Requested permissions:"
echo "$requested" | sed 's/^/  /'
if [[ -n "$unexpected" ]]; then
  echo "error: the manifest requests permissions that are not in tools/allowed-permissions.txt:" >&2
  echo "$unexpected" | sed 's/^/  /' >&2
  exit 1
fi
echo "All requested permissions are on the allowlist."
