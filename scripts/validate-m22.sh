#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
SKIP_PORTABLE="${MORPHEUS_M22_SKIP_PORTABLE:-false}"
BASE_REF="${MORPHEUS_M22_BASE_REF:-origin/main}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m22"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

printf '%s\n' "M22 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M22 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  BASE_REF=main
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo "M22 base ref not found: ${MORPHEUS_M22_BASE_REF:-origin/main} (fallback main also missing)" >&2
  exit 1
fi
printf '%s\n' "M22 diff base: $BASE_REF"
git diff --check "$BASE_REF...HEAD"
./mvnw clean verify

read -r TESTS FAILURES ERRORS ARCH_TESTS < <("$PYTHON" - "$REPO" <<'PY'
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
if (( TESTS < 473 )); then
  echo "M22 test baseline regression: $TESTS < 473" >&2
  exit 1
fi
if (( ARCH_TESTS < 187 )); then
  echo "M22 architecture baseline regression: $ARCH_TESTS < 187" >&2
  exit 1
fi
printf '%s\n' "Tests: PASS ($TESTS, baseline >= 473)"
printf '%s\n' "Architecture: PASS ($ARCH_TESTS, baseline >= 187)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
if [[ ! -f "$COVERAGE" ]]; then
  echo "Missing production coverage summary: $COVERAGE" >&2
  exit 1
fi
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
"$PYTHON" - "$LINE_RATIO" "$BRANCH_RATIO" <<'PY'
import sys
line = float(sys.argv[1])
branch = float(sys.argv[2])
if line < 0.25:
    raise SystemExit(f'M22 line coverage below 25%: {line}')
if branch < 0.20:
    raise SystemExit(f'M22 branch coverage below 20%: {branch}')
PY
printf '%s\n' "JaCoCo: PASS (line=$LINE_RATIO, branch=$BRANCH_RATIO)"

SBOM_JSON="$REPO/target/m21-supply-chain/morpheus-sbom.json"
SBOM_XML="$REPO/target/m21-supply-chain/morpheus-sbom.xml"
if [[ ! -f "$SBOM_JSON" || ! -f "$SBOM_XML" ]]; then
  echo 'CycloneDX JSON/XML SBOM is missing' >&2
  exit 1
fi
bash scripts/write-build-provenance.sh
if [[ ! -f "$REPO/target/m21-supply-chain/build-provenance.properties" ]]; then
  echo 'Build provenance is missing' >&2
  exit 1
fi
printf '%s\n' 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

REFERENCE_JAR="$REPO/morpheus-provider-reference/target/morpheus-provider-reference-$VERSION.jar"
if [[ ! -f "$REFERENCE_JAR" ]]; then
  echo "M22 reference provider JAR missing after reactor verify: $REFERENCE_JAR" >&2
  exit 1
fi

if [[ "$SKIP_PORTABLE" != true ]]; then
  bash distribution/build-portable.sh "$VERSION" 'validation-output/m22/dist'
  LAUNCHER="$REPO/validation-output/m22/dist/.m20-linux/image/morpheus/bin/morpheus"
  if [[ ! -x "$LAUNCHER" ]]; then
    echo "Packaged launcher not found: $LAUNCHER" >&2
    exit 1
  fi

  JAR_TOOL="${JAVA_HOME:-}/bin/jar"
  if [[ -z "${JAVA_HOME:-}" || ! -x "$JAR_TOOL" ]]; then
    echo "jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
    exit 1
  fi
  SHADED_JAR="$(ls -1t "$REPO"/morpheus-cli/target/morpheus-cli-*-all.jar | head -n 1)"
  "$JAR_TOOL" tf "$SHADED_JAR" > "$OUTPUT/shaded-entries.txt"
  grep -Fxq 'com/morpheus/sdk/provider/ProviderPluginService.class' "$OUTPUT/shaded-entries.txt" || {
    echo 'M22 packaged runtime is missing ProviderPluginService' >&2
    exit 1
  }
  if grep -Fxq 'com/morpheus/provider/reference/ReferenceProviderPlugin.class' "$OUTPUT/shaded-entries.txt"; then
    echo 'M22 reference provider must remain external and must not be embedded in the MORPHEUS launcher' >&2
    exit 1
  fi
  printf '%s\n' 'Provider SDK embedded / reference provider external: PASS'

  PLUGIN_DIR="$OUTPUT/plugins"
  WORKSPACE="$OUTPUT/reference-workspace"
  rm -rf "$PLUGIN_DIR" "$WORKSPACE"
  mkdir -p "$PLUGIN_DIR" "$WORKSPACE"
  cp "$REFERENCE_JAR" "$PLUGIN_DIR/reference-provider.jar"
  printf '%s\n' reference > "$WORKSPACE/morpheus-reference.spec"

  DISCOVERY="$($LAUNCHER --json provider-plugins discover --directory "$PLUGIN_DIR")"
  "$PYTHON" - "$DISCOVERY" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
assert payload['compatibleCount'] == 1, payload
assert payload['candidates'][0]['status'] == 'COMPATIBLE', payload
PY

  PROBE="$($LAUNCHER --json provider-plugins probe --directory "$PLUGIN_DIR" --plugin reference-provider-plugin --workspace "$WORKSPACE")"
  "$PYTHON" - "$PROBE" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
assert payload['probe']['status'] == 'SUPPORTED', payload
assert payload['probe']['providerId']['value'] == 'reference-plugin', payload
PY
  printf '%s\n' 'External reference provider discovery + isolated activation + probe: PASS'

  PORT="$("$PYTHON" - <<'PY'
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
      echo 'Packaged API exited before M22 check' >&2
      exit 1
    fi
    if "$PYTHON" - "$PORT" "$VERSION" "$PLUGIN_DIR" 2>/dev/null <<'PY'
import json
import sys
import urllib.parse
import urllib.request
port, expected, plugin_dir = sys.argv[1:]
with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/version', timeout=0.5) as response:
    version = json.load(response)
assert version['data']['version'] == expected, version
query = urllib.parse.urlencode({'directory': plugin_dir})
with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/provider-plugins/discover?{query}', timeout=0.5) as response:
    plugins = json.load(response)
assert plugins['data']['compatibleCount'] == 1, plugins
PY
    then
      API_OK=true
      break
    fi
    sleep 0.1
  done
  if [[ "$API_OK" != true ]]; then
    echo 'Packaged API M22 check timed out' >&2
    cat "$OUTPUT/api.stderr.log" >&2 || true
    exit 1
  fi
  cleanup_api
  trap - EXIT
  printf '%s\n' 'Packaged CLI/MCP/HTTP provider platform convergence: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
if [[ "$CURRENT_SHA" != "$VALIDATION_SHA" ]]; then
  echo "HEAD changed during M22 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during M22 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
M22 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
sdkApiVersion=1
externalReferenceProvider=PASS
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
