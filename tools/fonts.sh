#!/usr/bin/env bash
# Licensed app fonts (Anjoman Max) are never committed in plain form: the repository is public
# and the font license forbids redistribution. Only an AES-256 encrypted archive is committed;
# the passphrase is the PLANB_FONTS_PASSPHRASE secret (CI) / environment variable (local).
#
#   tools/fonts.sh encrypt   # private-fonts/*.ttf -> fonts/planb-fonts.tar.gz.gpg (maintainers)
#   tools/fonts.sh decrypt   # fonts/planb-fonts.tar.gz.gpg -> $PLANB_FONTS_DIR or private-fonts/
#
# The passphrase is read from PLANB_FONTS_PASSPHRASE and passed to gpg on a file descriptor,
# never on the command line.
set -euo pipefail
set +x

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ARCHIVE="$ROOT/fonts/planb-fonts.tar.gz.gpg"
DIR="${PLANB_FONTS_DIR:-$ROOT/private-fonts}"
FILES=(AnjomanMax-Regular.ttf AnjomanMax-Medium.ttf AnjomanMax-SemiBold.ttf AnjomanMax-Bold.ttf)

[[ -n "${PLANB_FONTS_PASSPHRASE:-}" ]] || { echo "error: PLANB_FONTS_PASSPHRASE is not set" >&2; exit 1; }

case "${1:-}" in
  encrypt)
    for f in "${FILES[@]}"; do [[ -f "$DIR/$f" ]] || { echo "error: $DIR/$f missing" >&2; exit 1; }; done
    mkdir -p "$(dirname "$ARCHIVE")"
    tar -C "$DIR" -czf - "${FILES[@]}" |
      gpg --batch --yes --quiet --symmetric --cipher-algo AES256 --pinentry-mode loopback \
        --passphrase-fd 3 --output "$ARCHIVE" 3<<<"$PLANB_FONTS_PASSPHRASE"
    echo "Encrypted ${#FILES[@]} fonts into ${ARCHIVE#$ROOT/}"
    ;;
  decrypt)
    mkdir -p "$DIR"
    gpg --batch --quiet --decrypt --pinentry-mode loopback --passphrase-fd 3 "$ARCHIVE" 3<<<"$PLANB_FONTS_PASSPHRASE" |
      tar -C "$DIR" -xzf -
    for f in "${FILES[@]}"; do [[ -f "$DIR/$f" ]] || { echo "error: $f missing after decryption" >&2; exit 1; }; done
    echo "Fonts available in $DIR"
    ;;
  *)
    echo "usage: tools/fonts.sh encrypt|decrypt" >&2
    exit 2
    ;;
esac
