#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.2.1}"
SKIP_PORTABLE="${MORPHEUS_M21_SKIP_PORTABLE:-false}"
BASE_REF="${MORPHEUS_M21_BASE_REF:-origin/main}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m21"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"
RATCHETS="$REPO/config/m21-quality-ratchets.properties"

read_ratchet() {
  local key="$1"
  local value
  value="$(sed -n "s/^${key}=//p" "$RATCHETS")"
  if [[ -z "$value" ]]; then
    echo "Missing M21 quality ratchet: $key" >&2
    exit 1
  fi
  printf '%s' "$value"
}

if [[ ! -f "$RATCHETS" ]]; then
  echo "Missing M21 quality ratchet configuration: $RATCHETS" >&2
  exit 1
fi
TESTS_MINIMUM="$(read_ratchet testsMinimum)"
ARCH_TESTS_MINIMUM="$(read_ratchet architectureTestsMinimum)"
LINE_COVERAGE_MINIMUM="$(read_ratchet lineCoverageMinimum)"
BRANCH_COVERAGE_MINIMUM="$(read_ratchet branchCoverageMinimum)"

morpheus_python - "$TESTS_MINIMUM" "$ARCH_TESTS_MINIMUM" "$LINE_COVERAGE_MINIMUM" "$BRANCH_COVERAGE_MINIMUM" <<'PY'
import sys

tests, architecture = map(int, sys.argv[1:3])
line, branch = map(float, sys.argv[3:5])
if tests < 1 or architecture < 1:
    raise SystemExit('M21 test ratchets must be positive integers')
if not 0.0 < line <= 1.0 or not 0.0 < branch <= 1.0:
    raise SystemExit('M21 coverage ratchets must be ratios in (0, 1]')
PY

printf '%s\n' "M21 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M21 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  BASE_REF=main
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo "M21 base ref not found: ${MORPHEUS_M21_BASE_REF:-origin/main} (fallback main also missing)" >&2
  exit 1
fi
printf '%s\n' "M21 diff base: $BASE_REF"
git diff --check "$BASE_REF...HEAD"
./mvnw clean verify

read -r TESTS FAILURES ERRORS ARCH_TESTS < <(morpheus_python - "$REPO" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])

def totals(base):
    tests = failures = errors = 0
    for report in base.rglob('target/surefire-reports/TEST-*.xml'):
        suite = ET.parse(report).getroot()
        tests += int(suite.attrib.get('tests', 0))
        failures += int(suite.attrib.get('failures', 0))
        errors += int(suite.attrib.get('errors', 0))
    return tests, failures, errors
all_tests = totals(root)
arch = totals(root / 'morpheus-architecture-tests')
print(all_tests[0], all_tests[1], all_tests[2], arch[0])
PY
)
if (( FAILURES != 0 || ERRORS != 0 )); then
  echo "Surefire failures=$FAILURES errors=$ERRORS" >&2
  exit 1
fi
if (( TESTS < TESTS_MINIMUM )); then
  echo "M21 test baseline regression: $TESTS < $TESTS_MINIMUM" >&2
  exit 1
fi
if (( ARCH_TESTS < ARCH_TESTS_MINIMUM )); then
  echo "M21 architecture baseline regression: $ARCH_TESTS < $ARCH_TESTS_MINIMUM" >&2
  exit 1
fi
printf '%s\n' "Tests: PASS ($TESTS, baseline >= $TESTS_MINIMUM)"
printf '%s\n' "Architecture: PASS ($ARCH_TESTS, baseline >= $ARCH_TESTS_MINIMUM)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
if [[ ! -f "$COVERAGE" ]]; then
  echo "Missing M21 coverage summary: $COVERAGE" >&2
  exit 1
fi
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
morpheus_python - "$LINE_RATIO" "$BRANCH_RATIO" "$LINE_COVERAGE_MINIMUM" "$BRANCH_COVERAGE_MINIMUM" <<'PY'
import sys
line, branch, minimum_line, minimum_branch = map(float, sys.argv[1:])
if line < minimum_line:
    raise SystemExit(f'M21 line coverage below {minimum_line:.3f} ratchet: {line}')
if branch < minimum_branch:
    raise SystemExit(f'M21 branch coverage below {minimum_branch:.3f} ratchet: {branch}')
PY
printf '%s\n' "JaCoCo: PASS (line=$LINE_RATIO, branch=$BRANCH_RATIO, ratchet=$LINE_COVERAGE_MINIMUM/$BRANCH_COVERAGE_MINIMUM)"

SBOM_JSON="$REPO/target/m21-supply-chain/morpheus-sbom.json"
SBOM_XML="$REPO/target/m21-supply-chain/morpheus-sbom.xml"
if [[ ! -f "$SBOM_JSON" || ! -f "$SBOM_XML" ]]; then
  echo 'M21 CycloneDX JSON/XML SBOM is missing' >&2
  exit 1
fi
chmod +x scripts/write-build-provenance.sh distribution/build-portable.sh
./scripts/write-build-provenance.sh
if [[ ! -f "$REPO/target/m21-supply-chain/build-provenance.properties" ]]; then
  echo 'M21 build provenance is missing' >&2
  exit 1
fi
printf '%s\n' 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if [[ "$SKIP_PORTABLE" != true ]]; then
  ./distribution/build-portable.sh "$VERSION" 'validation-output/m21/dist'
  LAUNCHER="$REPO/validation-output/m21/dist/.m20-linux/image/morpheus/bin/morpheus"
  if [[ ! -x "$LAUNCHER" ]]; then
    echo "Packaged launcher not found: $LAUNCHER" >&2
    exit 1
  fi

  PRODUCT_INFO="$($LAUNCHER --json product-info)"
  morpheus_python - "$PRODUCT_INFO" "$VERSION" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
if payload.get('version') != sys.argv[2] or payload.get('updateChannel') != 'stable':
    raise SystemExit(f'packaged product metadata mismatch: {payload!r}')
PY

  MANIFEST="$OUTPUT/update.properties"
  cat > "$MANIFEST" <<EOF
version=$VERSION
channel=stable
artifactUri=https://example.invalid/morpheus.zip
sha256=dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
EOF
  UPDATE="$($LAUNCHER --json update-check --manifest "$MANIFEST")"
  morpheus_python - "$UPDATE" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
if payload.get('updateAvailable') is not False:
    raise SystemExit(f'same-version manifest must not report an update: {payload!r}')
PY

  PORT="$(morpheus_python - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    print(sock.getsockname()[1])
PY
)"
  API_DATA="$OUTPUT/api-data"
  mkdir -p "$API_DATA"
  "$LAUNCHER" --data-dir "$API_DATA" api --host 127.0.0.1 --port "$PORT" >"$OUTPUT/api.stdout.log" 2>"$OUTPUT/api.stderr.log" &
  API_PID=$!
  cleanup_api() { kill "$API_PID" >/dev/null 2>&1 || true; wait "$API_PID" >/dev/null 2>&1 || true; }
  trap cleanup_api EXIT
  API_OK=false
  for _ in $(seq 1 60); do
    if ! kill -0 "$API_PID" >/dev/null 2>&1; then
      cat "$OUTPUT/api.stderr.log" >&2 || true
      echo 'Packaged API exited before version check' >&2
      exit 1
    fi
    if morpheus_python - "$PORT" "$VERSION" 2>/dev/null <<'PY'
import json
import sys
import urllib.request
port, expected = sys.argv[1:]
with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/version', timeout=0.5) as response:
    payload = json.load(response)
assert payload['data']['version'] == expected, payload
PY
    then
      API_OK=true
      break
    fi
    sleep 0.1
  done
  if [[ "$API_OK" != true ]]; then
    echo 'Packaged API version check timed out' >&2
    cat "$OUTPUT/api.stderr.log" >&2 || true
    exit 1
  fi
  cleanup_api
  trap - EXIT
  printf '%s\n' 'Packaged CLI/update/API convergence: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
if [[ "$CURRENT_SHA" != "$VALIDATION_SHA" ]]; then
  echo "HEAD changed during M21 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during M21 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
M21 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
qualityRatchets=$TESTS_MINIMUM/$ARCH_TESTS_MINIMUM/$LINE_COVERAGE_MINIMUM/$BRANCH_COVERAGE_MINIMUM
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
