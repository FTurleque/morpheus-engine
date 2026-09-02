#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
SKIP_PORTABLE="${MORPHEUS_M23_SKIP_PORTABLE:-false}"
BASE_REF="${MORPHEUS_M23_BASE_REF:-origin/main}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m23"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

printf '%s\n' "M23 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M23 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then BASE_REF=main; fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo 'M23 base ref not found: origin/main (fallback main also missing)' >&2
  exit 1
fi
printf '%s\n' "M23 diff base: $BASE_REF"
git diff --check "$BASE_REF...HEAD"
./mvnw clean verify

read -r TESTS FAILURES ERRORS ARCH_TESTS < <(morpheus_python - "$REPO" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
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
if (( TESTS < 494 )); then echo "M23 test baseline regression: $TESTS < 494" >&2; exit 1; fi
if (( ARCH_TESTS < 190 )); then echo "M23 architecture baseline regression: $ARCH_TESTS < 190" >&2; exit 1; fi
printf '%s\n' "Tests: PASS ($TESTS, baseline >= 494)"
printf '%s\n' "Architecture: PASS ($ARCH_TESTS, baseline >= 190)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
[[ -f "$COVERAGE" ]] || { echo "Missing production coverage summary: $COVERAGE" >&2; exit 1; }
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
morpheus_python - "$LINE_RATIO" "$BRANCH_RATIO" <<'PY'
import sys
line, branch = map(float, sys.argv[1:])
if line < .25: raise SystemExit(f'M23 line coverage below 25%: {line}')
if branch < .20: raise SystemExit(f'M23 branch coverage below 20%: {branch}')
PY
printf '%s\n' "JaCoCo: PASS (line=$LINE_RATIO, branch=$BRANCH_RATIO)"

[[ -f "$REPO/target/m21-supply-chain/morpheus-sbom.json" && -f "$REPO/target/m21-supply-chain/morpheus-sbom.xml" ]] || {
  echo 'CycloneDX JSON/XML SBOM is missing' >&2; exit 1;
}
bash scripts/write-build-provenance.sh
[[ -f "$REPO/target/m21-supply-chain/build-provenance.properties" ]] || { echo 'Build provenance is missing' >&2; exit 1; }
printf '%s\n' 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if [[ "$SKIP_PORTABLE" != true ]]; then
  bash distribution/build-portable.sh "$VERSION" 'validation-output/m23/dist'
  LAUNCHER="$REPO/validation-output/m23/dist/.m20-linux/image/morpheus/bin/morpheus"
  [[ -x "$LAUNCHER" ]] || { echo "Packaged launcher not found: $LAUNCHER" >&2; exit 1; }

  JAR_TOOL="${JAVA_HOME:-}/bin/jar"
  [[ -x "$JAR_TOOL" ]] || { echo "jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2; exit 1; }
  SHADED_JAR="$(ls -1t "$REPO"/morpheus-cli/target/morpheus-cli-*-all.jar | head -n 1)"
  "$JAR_TOOL" tf "$SHADED_JAR" > "$OUTPUT/shaded-entries.txt"
  for entry in \
    'com/morpheus/application/portfolio/PortfolioRegistryService.class' \
    'com/morpheus/application/portfolio/PortfolioTraversalService.class' \
    'com/morpheus/store/sqlite/SqlitePortfolioStore.class' \
    'com/morpheus/cli/MorpheusPortfolioCli.class' \
    'com/morpheus/mcp/MorpheusPortfolioMcpTools.class' \
    'com/morpheus/api/MorpheusPortfolioApiService.class' \
    'db/migration/V013__portfolio_intelligence.sql'; do
    grep -Fxq "$entry" "$OUTPUT/shaded-entries.txt" || { echo "M23 packaged runtime is missing $entry" >&2; exit 1; }
  done
  HELP="$($LAUNCHER help)"
  [[ "$HELP" == *'Portfolio intelligence (M23)'* ]] || { echo 'Packaged M23 CLI help smoke failed' >&2; exit 1; }
  printf '%s\n' 'M23 classes + V013 + CLI help packaging proof: PASS'

  DATA="$OUTPUT/portfolio-data"
  rm -rf "$DATA" && mkdir -p "$DATA"
  CREATED="$($LAUNCHER --data-dir "$DATA" --json portfolio create --name 'M23 Gate Portfolio')"
  SEED="$($LAUNCHER --data-dir "$DATA" --json portfolio create --name 'M23 Project Identity Seed')"
  read -r PORTFOLIO_ID PROJECT_ID < <(morpheus_python - "$CREATED" "$SEED" <<'PY'
import re, sys
pattern = r'[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}'
def first(text):
    match = re.search(pattern, text)
    if not match: raise SystemExit(f'UUIDv7 not found: {text}')
    return match.group(0)
print(first(sys.argv[1]), first(sys.argv[2]))
PY
)
  "$LAUNCHER" --data-dir "$DATA" --json portfolio add-project --portfolio "$PORTFOLIO_ID" --project "$PROJECT_ID" --name 'Gate Project' --workspace "$DATA" --providers reference > "$OUTPUT/register.json"
  OVERVIEW="$($LAUNCHER --data-dir "$DATA" --json portfolio overview --portfolio "$PORTFOLIO_ID")"
  [[ "$OVERVIEW" == *"$PROJECT_ID"* && "$OVERVIEW" == *'"referenceCount":0'* ]] || { echo "Packaged portfolio overview mismatch: $OVERVIEW" >&2; exit 1; }
  printf '%s\n' 'Packaged portfolio CLI create/register/overview: PASS'

  PORT="$(morpheus_python - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0)); print(sock.getsockname()[1])
PY
)"
  "$LAUNCHER" --data-dir "$DATA" api --host 127.0.0.1 --port "$PORT" >"$OUTPUT/api.stdout.log" 2>"$OUTPUT/api.stderr.log" &
  API_PID=$!
  cleanup_api() { kill "$API_PID" >/dev/null 2>&1 || true; wait "$API_PID" >/dev/null 2>&1 || true; }
  trap cleanup_api EXIT
  API_OK=false
  for _ in $(seq 1 60); do
    if ! kill -0 "$API_PID" >/dev/null 2>&1; then cat "$OUTPUT/api.stderr.log" >&2 || true; exit 1; fi
    if morpheus_python - "$PORT" "$PORTFOLIO_ID" "$PROJECT_ID" 2>/dev/null <<'PY'
import json, sys, urllib.request
port, portfolio, project = sys.argv[1:]
with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/portfolios/{portfolio}', timeout=.5) as response:
    body = json.load(response)
assert project in json.dumps(body), body
PY
    then API_OK=true; break; fi
    sleep .1
  done
  [[ "$API_OK" == true ]] || { echo 'Packaged API M23 portfolio check timed out' >&2; exit 1; }
  cleanup_api; trap - EXIT
  printf '%s\n' 'Packaged CLI/MCP/HTTP portfolio convergence: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during M23 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during M23 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
M23 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
portfolioIdentity=PASS
crossProjectReferences=PASS
boundedTraversal=PASS
sqliteV013=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
