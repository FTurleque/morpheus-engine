#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT="$REPO/validation-output/m20-linux"
LOGS="$OUTPUT/logs"
EXTRACT="$OUTPUT/portable"
XDG_DATA="$OUTPUT/xdg-data"
XDG_CONFIG="$OUTPUT/xdg-config"
XDG_STATE="$OUTPUT/xdg-state"
mkdir -p "$LOGS"
cd "$REPO"

VALIDATION_TAG=""
cleanup() {
  if [[ -n "$VALIDATION_TAG" ]]; then
    git tag -d "$VALIDATION_TAG" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

section() {
  printf '\n%s\n%s\n%s\n' '==============================================================================' "$1" '=============================================================================='
}

fail() {
  printf '%s\n' "M20 LINUX VALIDATION FAILURE: $*" >&2
  exit 1
}

section 'Workspace / SHA / version'
SHA="$(git rev-parse HEAD)"
[[ -z "$(git status --porcelain)" ]] || fail 'exact-head validation requires a clean Git workspace'
POM_VERSION="$(morpheus_python - <<'PY'
import xml.etree.ElementTree as ET
root = ET.parse('pom.xml').getroot()
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
print(root.find('m:version', ns).text)
PY
)"
[[ "$POM_VERSION" == "$VERSION" ]] || fail "pom.xml version is $POM_VERSION; expected $VERSION"
printf 'SHA:     %s\nVersion: %s\n' "$SHA" "$VERSION"

section 'Full Maven reactor'
# verify, not test: the architecture suite this reactor runs reads the JaCoCo reports and the packaged
# morpheus-provider-reference JAR, and neither exists after `clean test`. Every other validator uses verify.
./mvnw clean verify 2>&1 | tee "$LOGS/01-full-reactor.log"

section 'Tagged Linux release build'
VALIDATION_TAG="m20-validation-${SHA:0:12}"
git tag -d "$VALIDATION_TAG" >/dev/null 2>&1 || true
git tag "$VALIDATION_TAG" "$SHA"
bash distribution/build-release.sh "$VERSION" "$VALIDATION_TAG" dist 2>&1 | tee "$LOGS/02-release-build.log"

ARCHIVE="$REPO/dist/morpheus-$VERSION-linux-x64.tar.gz"
CHECKSUM="$ARCHIVE.sha256"
MANIFEST="$REPO/dist/morpheus-$VERSION-linux-x64-release-manifest.json"
[[ -s "$ARCHIVE" ]] || fail "missing Linux archive: $ARCHIVE"
[[ -s "$CHECKSUM" ]] || fail "missing Linux checksum: $CHECKSUM"
[[ -s "$MANIFEST" ]] || fail "missing Linux release manifest: $MANIFEST"
(
  cd "$(dirname "$ARCHIVE")"
  sha256sum -c "$(basename "$CHECKSUM")"
)
morpheus_python - "$MANIFEST" "$VERSION" "$VALIDATION_TAG" "$SHA" <<'PY'
import json
import sys
manifest, version, tag, sha = sys.argv[1:]
data = json.load(open(manifest, encoding='utf-8'))
assert data['version'] == version, data
assert data['tag'] == tag, data
assert data['gitSha'] == sha, data
assert data['runtimeEmbedded'] is True, data
assert data['userJdkRequired'] is False, data
PY

section 'Portable no-JDK + XDG layout smoke'
rm -rf "$EXTRACT" "$XDG_DATA" "$XDG_CONFIG" "$XDG_STATE"
mkdir -p "$EXTRACT" "$XDG_DATA" "$XDG_CONFIG" "$XDG_STATE"
tar -C "$EXTRACT" -xzf "$ARCHIVE"
LAUNCHER="$EXTRACT/morpheus/bin/morpheus"
[[ -x "$LAUNCHER" ]] || fail "portable launcher missing or not executable: $LAUNCHER"

VERSION_JSON="$(env -u JAVA_HOME PATH=/usr/bin:/bin "$LAUNCHER" --json version)"
morpheus_python - "$VERSION_JSON" "$VERSION" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
assert payload['version'] == sys.argv[2], payload
PY

PATHS_JSON="$(env -u JAVA_HOME PATH=/usr/bin:/bin XDG_DATA_HOME="$XDG_DATA" XDG_CONFIG_HOME="$XDG_CONFIG" XDG_STATE_HOME="$XDG_STATE" "$LAUNCHER" --json paths)"
morpheus_python - "$PATHS_JSON" "$XDG_DATA" "$XDG_CONFIG" "$XDG_STATE" <<'PY'
import json
import os
import sys
payload = json.loads(sys.argv[1])
data_root, config_root, state_root = map(os.path.abspath, sys.argv[2:])
assert os.path.abspath(payload['dataDirectory']) == os.path.join(data_root, 'morpheus'), payload
assert os.path.abspath(payload['configDirectory']) == os.path.join(config_root, 'morpheus'), payload
assert os.path.abspath(payload['logsDirectory']) == os.path.join(state_root, 'morpheus', 'logs'), payload
assert os.path.abspath(payload['databasePath']) == os.path.join(data_root, 'morpheus', 'morpheus.db'), payload
PY

env -u JAVA_HOME PATH=/usr/bin:/bin XDG_DATA_HOME="$XDG_DATA" XDG_CONFIG_HOME="$XDG_CONFIG" XDG_STATE_HOME="$XDG_STATE" "$LAUNCHER" --json projects list >/dev/null
[[ -f "$XDG_DATA/morpheus/morpheus.db" ]] || fail 'portable runtime did not create SQLite state under XDG data root'
MINOS="$(env -u JAVA_HOME PATH=/usr/bin:/bin "$LAUNCHER" --json minos-status)"
NEXUS="$(env -u JAVA_HOME PATH=/usr/bin:/bin "$LAUNCHER" --json nexus-status)"
[[ "$MINOS" == *'"state":"DISABLED"'* ]] || fail "MINOS must be opt-in: $MINOS"
[[ "$NEXUS" == *'"state":"DISABLED"'* ]] || fail "NEXUS must be opt-in: $NEXUS"

section 'Exact-head stability'
ENDING_SHA="$(git rev-parse HEAD)"
[[ "$ENDING_SHA" == "$SHA" ]] || fail "HEAD changed during validation: $SHA -> $ENDING_SHA"
[[ -z "$(git status --porcelain)" ]] || fail 'workspace changed during validation'

section 'M20 Linux summary'
printf '%s\n' \
  'M20 LINUX VALIDATION SUMMARY' \
  "SHA:       $SHA" \
  "Version:   $VERSION" \
  'Result:    PASS' \
  'Full Maven reactor: PASS' \
  'Tagged Linux release build: PASS' \
  'SHA-256 verification: PASS' \
  'Embedded runtime / no user JDK: PASS' \
  'XDG data/config/state layout: PASS' \
  'MINOS/NEXUS opt-in defaults: PASS' \
  'Exact-head stability: PASS' | tee "$OUTPUT/validation-summary.txt"
