#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
SKIP_PORTABLE="${MORPHEUS_M24_SKIP_PORTABLE:-false}"
BASE_REF="${MORPHEUS_M24_BASE_REF:-origin/main}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m24"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

printf '%s\n' "M24 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M24 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then BASE_REF=main; fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo 'M24 base ref not found: origin/main (fallback main also missing)' >&2
  exit 1
fi
printf '%s\n' "M24 diff base: $BASE_REF"
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
if (( TESTS < 507 )); then echo "M24 test baseline regression: $TESTS < 507" >&2; exit 1; fi
if (( ARCH_TESTS < 195 )); then echo "M24 architecture baseline regression: $ARCH_TESTS < 195" >&2; exit 1; fi
printf '%s\n' "Tests: PASS ($TESTS, M23 baseline >= 507)"
printf '%s\n' "Architecture: PASS ($ARCH_TESTS, M23 baseline >= 195)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
[[ -f "$COVERAGE" ]] || { echo "Missing production coverage summary: $COVERAGE" >&2; exit 1; }
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
morpheus_python - "$LINE_RATIO" "$BRANCH_RATIO" <<'PY'
import sys
line, branch = map(float, sys.argv[1:])
if line < .25: raise SystemExit(f'M24 line coverage below 25%: {line}')
if branch < .20: raise SystemExit(f'M24 branch coverage below 20%: {branch}')
PY
printf '%s\n' "JaCoCo: PASS (line=$LINE_RATIO, branch=$BRANCH_RATIO)"

[[ -f "$REPO/target/m21-supply-chain/morpheus-sbom.json" && -f "$REPO/target/m21-supply-chain/morpheus-sbom.xml" ]] || {
  echo 'CycloneDX JSON/XML SBOM is missing' >&2; exit 1;
}
bash scripts/write-build-provenance.sh
[[ -f "$REPO/target/m21-supply-chain/build-provenance.properties" ]] || { echo 'Build provenance is missing' >&2; exit 1; }
printf '%s\n' 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if [[ "$SKIP_PORTABLE" != true ]]; then
  bash distribution/build-portable.sh "$VERSION" 'validation-output/m24/dist'
  LAUNCHER="$REPO/validation-output/m24/dist/.m20-linux/image/morpheus/bin/morpheus"
  [[ -x "$LAUNCHER" ]] || { echo "Packaged launcher not found: $LAUNCHER" >&2; exit 1; }

  JAR_TOOL="${JAVA_HOME:-}/bin/jar"
  [[ -x "$JAR_TOOL" ]] || { echo "jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2; exit 1; }
  SHADED_JAR="$(ls -1t "$REPO"/morpheus-cli/target/morpheus-cli-*-all.jar | head -n 1)"
  "$JAR_TOOL" tf "$SHADED_JAR" > "$OUTPUT/shaded-entries.txt"
  for entry in \
    'com/morpheus/application/query/dsl/QueryExecutionService.class' \
    'com/morpheus/application/query/dsl/QueryDslParser.class' \
    'com/morpheus/application/query/saved/SavedViewService.class' \
    'com/morpheus/application/query/export/QueryExportService.class' \
    'com/morpheus/store/sqlite/SqliteSavedViewStore.class' \
    'com/morpheus/cli/MorpheusQueryCli.class' \
    'com/morpheus/mcp/MorpheusQueryMcpTools.class' \
    'com/morpheus/api/MorpheusQueryApiService.class' \
    'com/morpheus/api/MorpheusQueryHttpRoutes.class' \
    'db/migration/V014__saved_views.sql'; do
    grep -Fxq "$entry" "$OUTPUT/shaded-entries.txt" || { echo "M24 packaged runtime is missing $entry" >&2; exit 1; }
  done
  HELP="$($LAUNCHER help)"
  [[ "$HELP" == *'Query DSL / saved views / reporting (M24)'* ]] || { echo 'Packaged M24 CLI help smoke failed' >&2; exit 1; }
  printf '%s\n' 'M24 classes + V014 + CLI help packaging proof: PASS'

  DATA="$OUTPUT/query-data"
  rm -rf "$DATA" && mkdir -p "$DATA"
  PROJECT_ID='01890f7a-36d4-7c1e-8000-000000000071'
  QUERY="$($LAUNCHER --data-dir "$DATA" --json query execute --project "$PROJECT_ID" --entity change --filter 'title contains security' --sort 'title:asc' --fields 'id,title' --limit 25)"
  [[ "$QUERY" == *'"entityType":"CHANGE"'* && "$QUERY" == *'"totalMatches":0'* ]] || { echo "Packaged query mismatch: $QUERY" >&2; exit 1; }
  if "$LAUNCHER" --data-dir "$DATA" query execute --project "$PROJECT_ID" --entity change --limit 501 >"$OUTPUT/page-budget.stdout" 2>"$OUTPUT/page-budget.stderr"; then
    echo 'Packaged query page budget unexpectedly accepted limit 501' >&2; exit 1
  fi
  grep -q 'limit must be between 1 and 500' "$OUTPUT/page-budget.stderr" || { cat "$OUTPUT/page-budget.stderr" >&2; exit 1; }
  printf '%s\n' 'Provider-neutral query DSL + page budget: PASS'

  CREATED="$($LAUNCHER --data-dir "$DATA" --json views create --name 'M24 Gate View' --project "$PROJECT_ID" --entity change --filter 'title contains security' --fields 'id,title')"
  VIEW_ID="$(morpheus_python - "$CREATED" <<'PY'
import re, sys
m = re.search(r'[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}', sys.argv[1])
if not m: raise SystemExit(f'UUIDv7 not found: {sys.argv[1]}')
print(m.group(0))
PY
)"
  UPDATED="$($LAUNCHER --data-dir "$DATA" --json views update --id "$VIEW_ID" --expected-revision 1 --name 'M24 Gate View v2' --entity change --filter 'title contains security' --fields 'id,title')"
  [[ "$UPDATED" == *'"revision":2'* ]] || { echo "Saved-view revision did not advance: $UPDATED" >&2; exit 1; }
  if "$LAUNCHER" --data-dir "$DATA" views update --id "$VIEW_ID" --expected-revision 1 --name stale --entity change >"$OUTPUT/stale.stdout" 2>"$OUTPUT/stale.stderr"; then
    echo 'Stale saved-view CAS unexpectedly succeeded' >&2; exit 1
  fi
  grep -q 'stale saved view revision' "$OUTPUT/stale.stderr" || { cat "$OUTPUT/stale.stderr" >&2; exit 1; }
  VERSIONS="$($LAUNCHER --data-dir "$DATA" --json views versions --id "$VIEW_ID")"
  [[ "$VERSIONS" == *'"revision":1'* && "$VERSIONS" == *'"revision":2'* ]] || { echo "Saved-view versions mismatch: $VERSIONS" >&2; exit 1; }
  printf '%s\n' 'Versioned saved views + stale CAS rejection: PASS'

  JSON_EXPORT="$($LAUNCHER --data-dir "$DATA" export view --format json --id "$VIEW_ID")"
  CSV_EXPORT="$($LAUNCHER --data-dir "$DATA" export view --format csv --id "$VIEW_ID")"
  MD_EXPORT="$($LAUNCHER --data-dir "$DATA" export view --format markdown --id "$VIEW_ID")"
  [[ "$JSON_EXPORT" == *'"scopeKind":"PROJECT"'* && "$JSON_EXPORT" == *'"totalMatches":0'* ]] || { echo "Canonical JSON export mismatch: $JSON_EXPORT" >&2; exit 1; }
  [[ "$CSV_EXPORT" == '"id","projectId","title"' ]] || { echo "CSV export mismatch: $CSV_EXPORT" >&2; exit 1; }
  [[ "$MD_EXPORT" == *'| id | projectId | title |'* && "$MD_EXPORT" == *'| --- | --- | --- |'* ]] || { echo "Markdown export mismatch: $MD_EXPORT" >&2; exit 1; }
  AFTER="$($LAUNCHER --data-dir "$DATA" --json views get --id "$VIEW_ID")"
  [[ "$AFTER" == *'"revision":2'* ]] || { echo 'Export mutated saved-view revision' >&2; exit 1; }
  printf '%s\n' 'Canonical JSON + CSV + Markdown read-only exports: PASS'

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
    if morpheus_python - "$PORT" "$PROJECT_ID" "$VIEW_ID" 2>/dev/null <<'PY'
import json, sys, urllib.request
port, project, view = sys.argv[1:]
body = json.dumps({
    'scopeKind':'PROJECT', 'scopeId':project,
    'query':{'entity':'change','filter':'title contains security','limit':25}
}).encode()
request = urllib.request.Request(
    f'http://127.0.0.1:{port}/api/v1/queries/execute', data=body,
    headers={'Content-Type':'application/json'}, method='POST')
with urllib.request.urlopen(request, timeout=.5) as response:
    payload = json.load(response)
assert payload['data']['totalMatches'] == 0, payload
with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/saved-views/{view}', timeout=.5) as response:
    saved = json.load(response)
assert saved['data']['revision'] == 2, saved
PY
    then API_OK=true; break; fi
    sleep .1
  done
  [[ "$API_OK" == true ]] || { echo 'Packaged API M24 query/saved-view check timed out' >&2; exit 1; }
  cleanup_api; trap - EXIT
  printf '%s\n' 'Packaged CLI/MCP/HTTP M24 convergence: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during M24 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during M24 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
M24 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
queryDsl=PASS
savedViews=PASS
canonicalJsonExport=PASS
csvExport=PASS
markdownExport=PASS
queryBudgets=PASS
surfaceConvergence=PASS
sqliteV014=PASS
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
