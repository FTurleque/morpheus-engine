#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.2.0}"
EXPECTED_TAG="${2:-v$VERSION}"
OUTPUT_DIRECTORY="${3:-dist}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST="$REPO/$OUTPUT_DIRECTORY"
cd "$REPO"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Release build requires a clean Git workspace" >&2
  exit 1
fi

HEAD_SHA="$(git rev-parse HEAD)"
TAG_SHA="$(git rev-list -n 1 "$EXPECTED_TAG" 2>/dev/null || true)"
if [[ -z "$TAG_SHA" ]]; then
  echo "Release build requires tag '$EXPECTED_TAG' to exist" >&2
  exit 1
fi
if [[ "$TAG_SHA" != "$HEAD_SHA" ]]; then
  echo "Release tag '$EXPECTED_TAG' points to $TAG_SHA, but HEAD is $HEAD_SHA" >&2
  exit 1
fi

mkdir -p "$DIST"
"$SCRIPT_DIR/build-portable.sh" "$VERSION" "$OUTPUT_DIRECTORY"

ARCHIVE="$DIST/morpheus-$VERSION-linux-x64.tar.gz"
if [[ ! -s "$ARCHIVE" ]]; then
  echo "Linux portable archive missing or empty: $ARCHIVE" >&2
  exit 1
fi

(
  cd "$DIST"
  sha256sum "$(basename "$ARCHIVE")" > "$(basename "$ARCHIVE").sha256"
  sha256sum -c "$(basename "$ARCHIVE").sha256"
)

SHA256="$(sha256sum "$ARCHIVE" | awk '{print $1}')"
BYTES="$(stat -c '%s' "$ARCHIVE")"
MANIFEST="$DIST/morpheus-$VERSION-linux-x64-release-manifest.json"
python3 - "$MANIFEST" "$VERSION" "$EXPECTED_TAG" "$HEAD_SHA" "$(basename "$ARCHIVE")" "$BYTES" "$SHA256" <<'PY'
import json
import sys
from pathlib import Path

manifest, version, tag, sha, asset, size, digest = sys.argv[1:]
payload = {
    "schemaVersion": 1,
    "product": "MORPHEUS",
    "version": version,
    "tag": tag,
    "gitSha": sha,
    "platform": "linux-x64",
    "runtimeEmbedded": True,
    "userJdkRequired": False,
    "persistentLayout": {
        "data": "${XDG_DATA_HOME:-$HOME/.local/share}/morpheus",
        "config": "${XDG_CONFIG_HOME:-$HOME/.config}/morpheus",
        "logs": "${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/logs",
        "backups": "${XDG_STATE_HOME:-$HOME/.local/state}/morpheus/backups",
    },
    "assets": [{
        "name": asset,
        "bytes": int(size),
        "sha256": digest,
        "checksum": asset + ".sha256",
    }],
}
Path(manifest).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY

echo "Tagged Linux release build: PASS"
echo "Tag:      $EXPECTED_TAG"
echo "Git SHA:  $HEAD_SHA"
echo "Manifest: $MANIFEST"
echo "Asset: $(basename "$ARCHIVE") bytes=$BYTES sha256=$SHA256"
