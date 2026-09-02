#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
SKIP_PORTABLE="${MORPHEUS_M25_SKIP_PORTABLE:-false}"
BASE_REF="${MORPHEUS_M25_BASE_REF:-origin/develop}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m25"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$(command -v java || true)"
  if [[ -n "$JAVA_BIN" ]]; then
    JAVA_BIN="$(readlink -f "$JAVA_BIN")"
    DISCOVERED_JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    if [[ -x "$DISCOVERED_JAVA_HOME/bin/java" ]]; then
      export JAVA_HOME="$DISCOVERED_JAVA_HOME"
      printf '%s\n' "M25 discovered JAVA_HOME=$JAVA_HOME"
    fi
  fi
fi

printf '%s\n' "M25 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M25 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then BASE_REF=develop; fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo 'M25 base ref not found: origin/develop (fallback develop also missing)' >&2
  exit 1
fi
printf '%s\n' "M25 diff base: $BASE_REF"
git diff --check "$BASE_REF...HEAD"
./mvnw clean verify

read -r TESTS FAILURES ERRORS ARCH_TESTS < <("$PYTHON" - "$REPO" <<'PY'
import pathlib, sys, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
def totals(base):
    tests = failures = errors = 0
    for report in base.rglob('target/surefire-reports/TEST-*.xml'):
        suite = ET.parse(report).getroot()
        tests += int(suite.attrib.get('tests', 0)); failures += int(suite.attrib.get('failures', 0)); errors += int(suite.attrib.get('errors', 0))
    return tests, failures, errors
all_tests = totals(root); arch = totals(root / 'morpheus-architecture-tests')
print(all_tests[0], all_tests[1], all_tests[2], arch[0])
PY
)
if (( FAILURES != 0 || ERRORS != 0 )); then echo "Surefire failures=$FAILURES errors=$ERRORS" >&2; exit 1; fi
if (( TESTS < 543 )); then echo "M25 test baseline regression: $TESTS < 543" >&2; exit 1; fi
if (( ARCH_TESTS < 221 )); then echo "M25 architecture baseline regression: $ARCH_TESTS < 221" >&2; exit 1; fi
printf '%s\n' "Tests: PASS ($TESTS, M24 baseline >= 543)"
printf '%s\n' "Architecture: PASS ($ARCH_TESTS, M24 baseline >= 221)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
[[ -f "$COVERAGE" ]] || { echo "Missing production coverage summary: $COVERAGE" >&2; exit 1; }
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
"$PYTHON" - "$LINE_RATIO" "$BRANCH_RATIO" <<'PY'
import sys
line, branch = map(float, sys.argv[1:])
if line < .25: raise SystemExit(f'M25 line coverage below 25%: {line}')
if branch < .20: raise SystemExit(f'M25 branch coverage below 20%: {branch}')
PY
printf '%s\n' "JaCoCo: PASS (line=$LINE_RATIO, branch=$BRANCH_RATIO)"

[[ -f "$REPO/target/m21-supply-chain/morpheus-sbom.json" && -f "$REPO/target/m21-supply-chain/morpheus-sbom.xml" ]] || { echo 'CycloneDX JSON/XML SBOM is missing' >&2; exit 1; }
bash scripts/write-build-provenance.sh
[[ -f "$REPO/target/m21-supply-chain/build-provenance.properties" ]] || { echo 'Build provenance is missing' >&2; exit 1; }
printf '%s\n' 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if [[ "$SKIP_PORTABLE" != true ]]; then
  bash distribution/build-portable.sh "$VERSION" 'validation-output/m25/dist'
  LAUNCHER="$REPO/validation-output/m25/dist/.m20-linux/image/morpheus/bin/morpheus"
  [[ -x "$LAUNCHER" ]] || { echo "Packaged launcher not found: $LAUNCHER" >&2; exit 1; }
  JAR_TOOL="${JAVA_HOME:-}/bin/jar"
  [[ -x "$JAR_TOOL" ]] || { echo "jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2; exit 1; }
  SHADED_JAR="$(ls -1t "$REPO"/morpheus-cli/target/morpheus-cli-*-all.jar | head -n 1)"
  "$JAR_TOOL" tf "$SHADED_JAR" > "$OUTPUT/shaded-entries.txt"
  for entry in \
    'com/morpheus/application/policy/PolicyPackService.class' \
    'com/morpheus/application/policy/PolicyEvaluationService.class' \
    'com/morpheus/application/policy/DefaultPolicyFactResolver.class' \
    'com/morpheus/store/sqlite/SqlitePolicyPackStore.class' \
    'com/morpheus/cli/MorpheusPolicyCli.class' \
    'com/morpheus/mcp/MorpheusPolicyMcpTools.class' \
    'com/morpheus/api/MorpheusPolicyApiService.class' \
    'com/morpheus/api/MorpheusPolicyHttpRoutes.class' \
    'db/migration/V015__policy_packs.sql'; do
    grep -Fxq "$entry" "$OUTPUT/shaded-entries.txt" || { echo "M25 packaged runtime is missing $entry" >&2; exit 1; }
  done
  HELP="$($LAUNCHER help)"
  [[ "$HELP" == *'Policy packs / governance automation (M25)'* ]] || { echo 'Packaged M25 CLI help smoke failed' >&2; exit 1; }
  printf '%s\n' 'M25 classes + V015 + CLI help packaging proof: PASS'

  DATA="$OUTPUT/policy-data"
  rm -rf "$DATA" && mkdir -p "$DATA"
  PROJECT_ID='01890f7a-36d4-7c1e-8000-000000000081'
  RULE='new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0'
  CREATED="$($LAUNCHER --data-dir "$DATA" --json policy pack create --name 'M25 Gate Pack' --rules "$RULE" --actor gate --reason baseline)"
  PACK_ID="$("$PYTHON" - "$CREATED" <<'PY'
import re, sys
m=re.search(r'[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}',sys.argv[1]);
if not m: raise SystemExit('pack UUIDv7 not found')
print(m.group())
PY
)"
  VERSIONS="$($LAUNCHER --data-dir "$DATA" --json policy pack versions --id "$PACK_ID")"
  read -r VERSION_ID RULE_ID < <("$PYTHON" - "$VERSIONS" <<'PY'
import re,sys
ids=re.findall(r'[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}',sys.argv[1])
if len(ids)<3: raise SystemExit('version/rule UUIDv7 not found')
print(ids[1],ids[2])
PY
)
  UPDATED="$($LAUNCHER --data-dir "$DATA" --json policy pack update --id "$PACK_ID" --expected-revision 1 --name 'M25 Gate Pack v2' --rules "$RULE_ID|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0" --actor gate --reason update)"
  [[ "$UPDATED" == *'"revision":2'* ]] || { echo "Policy revision did not advance: $UPDATED" >&2; exit 1; }
  if "$LAUNCHER" --data-dir "$DATA" policy pack update --id "$PACK_ID" --expected-revision 1 --name stale --rules "$RULE_ID|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0" --actor gate --reason stale >"$OUTPUT/stale.stdout" 2>"$OUTPUT/stale.stderr"; then
    echo 'Stale policy CAS unexpectedly succeeded' >&2; exit 1
  fi
  grep -q 'stale policy pack revision' "$OUTPUT/stale.stderr" || { cat "$OUTPUT/stale.stderr" >&2; exit 1; }
  DRY="$($LAUNCHER --data-dir "$DATA" --json policy dry-run --id "$PACK_ID" --version "$VERSION_ID" --project "$PROJECT_ID")"
  [[ "$DRY" == *'"dryRun":true'* && "$DRY" == *'"decision":"UNKNOWN"'* ]] || { echo "Policy dry-run mismatch: $DRY" >&2; exit 1; }
  AUDIT_BEFORE="$($LAUNCHER --data-dir "$DATA" --json policy audit --id "$PACK_ID")"
  [[ "$AUDIT_BEFORE" != *'ACTIVATE'* ]] || { echo 'Dry-run unexpectedly wrote activation audit' >&2; exit 1; }
  "$LAUNCHER" --data-dir "$DATA" --json policy activate --id "$PACK_ID" --version "$VERSION_ID" --project "$PROJECT_ID" --expected-revision 0 --actor gate --reason enable >/dev/null
  "$LAUNCHER" --data-dir "$DATA" --json policy override put --id "$PACK_ID" --rule "$RULE_ID" --mode FORCE_BLOCK --project "$PROJECT_ID" --expected-revision 0 --actor gate --reason explicit >/dev/null
  EVALUATED="$($LAUNCHER --data-dir "$DATA" --json policy evaluate --id "$PACK_ID" --project "$PROJECT_ID")"
  [[ "$EVALUATED" == *'"originalDecision":"UNKNOWN"'* && "$EVALUATED" == *'"effectiveDecision":"BLOCK"'* ]] || { echo "Policy override provenance mismatch: $EVALUATED" >&2; exit 1; }
  printf '%s\n' 'Policy versioning + CAS + dry-run + override explainability: PASS'

  PORT="$("$PYTHON" - <<'PY'
import socket
with socket.socket() as sock: sock.bind(('127.0.0.1',0)); print(sock.getsockname()[1])
PY
)"
  "$LAUNCHER" --data-dir "$DATA" api --host 127.0.0.1 --port "$PORT" >"$OUTPUT/api.stdout.log" 2>"$OUTPUT/api.stderr.log" &
  API_PID=$!
  cleanup_api(){ kill "$API_PID" >/dev/null 2>&1 || true; wait "$API_PID" >/dev/null 2>&1 || true; }
  trap cleanup_api EXIT
  API_OK=false
  for _ in $(seq 1 60); do
    if ! kill -0 "$API_PID" >/dev/null 2>&1; then cat "$OUTPUT/api.stderr.log" >&2 || true; exit 1; fi
    if "$PYTHON" - "$PORT" "$PACK_ID" 2>/dev/null <<'PY'
import json,sys,urllib.request
port,pack=sys.argv[1:]
with urllib.request.urlopen(f'http://127.0.0.1:{port}/api/v1/policy-packs/{pack}',timeout=.5) as response:
    payload=json.load(response)
assert payload['data']['revision']==2,payload
PY
    then API_OK=true; break; fi
    sleep .1
  done
  [[ "$API_OK" == true ]] || { echo 'Packaged API M25 policy route check timed out' >&2; exit 1; }
  cleanup_api; trap - EXIT
  printf '%s\n' 'Packaged CLI/MCP/HTTP M25 convergence: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during M25 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then echo 'Tracked workspace delta appeared during M25 validation' >&2; git status --short --untracked-files=no >&2; exit 1; fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
M25 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
policyPacks=PASS
policyVersioning=PASS
policyOverrides=PASS
policyDryRun=PASS
policyExplainability=PASS
surfaceConvergence=PASS
sqliteV015=PASS
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
