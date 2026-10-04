#!/usr/bin/env bash
# Uploads a signed APK to Cafe Bazaar through the Pishkhan developer API and commits it as a
# new release with the Persian and English "what's new" texts.
#
#   tools/publish_cafebazaar.sh release/cafebazaar/Plan-B-v1.0.0-release.apk
#
# Requires CAFEBAZAAR_PISHKHAN_API_SECRET or PISHKHAN_SECRET in the environment (the API
# secret from Pishkhan → API settings). The secret is sent only as a request header and is
# never printed. By default the release is left for review without automatic publishing; set
# AUTO_PUBLISH=true to request publishing after review.
#
# Endpoints (Cafe Bazaar developer API v1):
#   POST https://api.pishkhan.cafebazaar.ir/v1/apps/releases/upload/   multipart "apk"
#   POST https://api.pishkhan.cafebazaar.ir/v1/apps/releases/commit/   JSON release details
set -euo pipefail
set +x

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:?usage: publish_cafebazaar.sh <signed.apk>}"
SECRET="${CAFEBAZAAR_PISHKHAN_API_SECRET:-${PISHKHAN_SECRET:-}}"
API="https://api.pishkhan.cafebazaar.ir/v1/apps/releases"
AUTO_PUBLISH="${AUTO_PUBLISH:-false}"

[[ -n "$SECRET" ]] || { echo "error: CAFEBAZAAR_PISHKHAN_API_SECRET is not set" >&2; exit 1; }
[[ -f "$APK" ]] || { echo "error: $APK not found" >&2; exit 1; }
[[ "$AUTO_PUBLISH" == "true" ]] || AUTO_PUBLISH=false

# Keep the header out of the process list: curl reads it from a private temporary file.
HEADER_FILE="$(mktemp)"
trap 'rm -f "$HEADER_FILE"' EXIT
chmod 600 "$HEADER_FILE"
printf 'CAFEBAZAAR-PISHKHAN-API-SECRET: %s\n' "$SECRET" > "$HEADER_FILE"

request() { # curl args...; prints the response body and fails on HTTP errors
  local response status
  response="$(curl -sS --retry 3 -w '\n%{http_code}' -H "@$HEADER_FILE" "$@")"
  status="${response##*$'\n'}"
  response="${response%$'\n'*}"
  echo "$response"
  [[ "$status" =~ ^2 ]] || { echo "error: Cafe Bazaar API returned HTTP $status" >&2; return 1; }
}

echo "Uploading $(basename "$APK") to Cafe Bazaar…"
request -X POST "$API/upload/" -F "apk=@${APK}"

CHANGELOG_FA="$(cat "$ROOT/store/cafebazaar/whats_new_fa.txt")"
CHANGELOG_EN="$(cat "$ROOT/store/cafebazaar/whats_new_en.txt")"
BODY="$(CHANGELOG_FA="$CHANGELOG_FA" CHANGELOG_EN="$CHANGELOG_EN" AUTO_PUBLISH="$AUTO_PUBLISH" python3 -c '
import json, os
print(json.dumps({
    "changelog_fa": os.environ["CHANGELOG_FA"],
    "changelog_en": os.environ["CHANGELOG_EN"],
    "developer_note": "",
    "staged_rollout_percentage": 100,
    "auto_publish": os.environ["AUTO_PUBLISH"] == "true",
}, ensure_ascii=False))')"

echo "Committing the release (auto_publish=$AUTO_PUBLISH)…"
request -X POST "$API/commit/" -H 'Content-Type: application/json; charset=utf-8' --data-binary "$BODY"
echo "Done. Check the release in Pishkhan."
