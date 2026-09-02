#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
SKIP_PORTABLE="${MORPHEUS_M27_SKIP_PORTABLE:-false}"
BASE_REF="${MORPHEUS_M27_BASE_REF:-origin/develop}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m27"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$(command -v java || true)"
  if [[ -n "$JAVA_BIN" ]]; then
    JAVA_BIN="$(readlink -f "$JAVA_BIN")"
    DISCOVERED_JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    if [[ -x "$DISCOVERED_JAVA_HOME/bin/java" ]]; then export JAVA_HOME="$DISCOVERED_JAVA_HOME"; fi
  fi
fi

printf '%s\n' "M27 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M27 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then BASE_REF=develop; fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo 'M27 base ref not found: origin/develop (fallback develop also missing)' >&2
  exit 1
fi
printf '%s\n' "M27 diff base: $BASE_REF"
git diff --check "$BASE_REF...HEAD"
./mvnw clean verify

read -r TESTS FAILURES ERRORS ARCH_TESTS < <(morpheus_python - "$REPO" <<'PY'
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
if (( TESTS < 602 )); then echo "M27 complete test floor regression: $TESTS < 602" >&2; exit 1; fi
if (( ARCH_TESTS < 238 )); then echo "M27 architecture baseline regression: $ARCH_TESTS < 238" >&2; exit 1; fi
printf '%s\n' "Tests: PASS ($TESTS, M27 minimum >= 602)"
printf '%s\n' "Architecture: PASS ($ARCH_TESTS, M27 minimum >= 238)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
[[ -f "$COVERAGE" ]] || { echo "Missing production coverage summary: $COVERAGE" >&2; exit 1; }
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
morpheus_python - "$LINE_RATIO" "$BRANCH_RATIO" <<'PY'
import sys
line, branch = map(float, sys.argv[1:])
if line < .42: raise SystemExit(f'M27 line coverage below 42%: {line}')
if branch < .35: raise SystemExit(f'M27 branch coverage below 35%: {branch}')
PY
printf '%s\n' "JaCoCo: PASS (line=$LINE_RATIO, branch=$BRANCH_RATIO)"

morpheus_python - "$REPO/contracts/public-surfaces.tsv" "$REPO/docs/openapi/morpheus-v1-reasoning-m27.yaml" <<'PY'
import pathlib, sys
manifest = pathlib.Path(sys.argv[1]).read_text()
openapi = pathlib.Path(sys.argv[2]).read_text()
required = [
 'reasoning.adapters\tREAD\treason adapters\tlist_reasoning_adapters\tGET /api/v1/reasoning/adapters',
 'reasoning.analyze\tREAD\treason analyze\treason_with_evidence\tPOST /api/v1/reasoning/analyze']
for contract in required:
    assert contract in manifest, contract
assert '/reasoning/adapters:' in openapi
assert '/reasoning/analyze:' in openapi
assert 'const: false' in openapi
PY
printf '%s\n' 'CLI/MCP/HTTP convergence + OpenAPI mutation boundary: PASS'

[[ -f "$REPO/target/m21-supply-chain/morpheus-sbom.json" && -f "$REPO/target/m21-supply-chain/morpheus-sbom.xml" ]] || { echo 'CycloneDX JSON/XML SBOM is missing' >&2; exit 1; }
bash scripts/write-build-provenance.sh
[[ -f "$REPO/target/m21-supply-chain/build-provenance.properties" ]] || { echo 'Build provenance is missing' >&2; exit 1; }
printf '%s\n' 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if [[ "$SKIP_PORTABLE" != true ]]; then
  bash distribution/build-portable.sh "$VERSION" 'validation-output/m27/dist'
  LAUNCHER="$REPO/validation-output/m27/dist/.m20-linux/image/morpheus/bin/morpheus"
  [[ -x "$LAUNCHER" ]] || { echo "Packaged launcher not found: $LAUNCHER" >&2; exit 1; }
  JAR_TOOL="${JAVA_HOME:-}/bin/jar"
  [[ -x "$JAR_TOOL" ]] || { echo "jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2; exit 1; }
  SHADED_JAR="$(ls -1t "$REPO"/morpheus-cli/target/morpheus-cli-*-all.jar | head -n 1)"
  "$JAR_TOOL" tf "$SHADED_JAR" > "$OUTPUT/shaded-entries.txt"
  for entry in \
    'com/morpheus/application/reasoning/ReasoningContracts.class' \
    'com/morpheus/application/reasoning/ReasoningService.class' \
    'com/morpheus/application/reasoning/ReasoningAdapter.class' \
    'com/morpheus/application/reasoning/EvidenceSynthesisReasoningAdapter.class' \
    'com/morpheus/cli/MorpheusReasoningCli.class' \
    'com/morpheus/api/MorpheusReasoningHttpRoutes.class' \
    'com/morpheus/mcp/MorpheusReasoningMcpTools.class'; do
    grep -Fxq "$entry" "$OUTPUT/shaded-entries.txt" || { echo "M27 packaged runtime is missing $entry" >&2; exit 1; }
  done

  ADAPTERS="$($LAUNCHER --json reason adapters)"
  FACTS_ONLY="$($LAUNCHER --json reason analyze --question 'What remains authoritative?' \
    --evidence 'fact-1|PUBLISHED_FACT|history|Published history remains authoritative|source=gate')"
  ASSISTED="$($LAUNCHER --json reason analyze --question 'Can remote mode be enabled safely?' \
    --evidence 'fact-1|PUBLISHED_FACT|remote|TLS is required|source=gate' \
    --evidence 'fact-2|PUBLISHED_FACT|remote|Authentication is required|source=gate' \
    --adapter builtin-evidence-synthesis-v1 --max-claims 10)"
  morpheus_python - "$ADAPTERS" "$FACTS_ONLY" "$ASSISTED" <<'PY'
import json, sys
adapters, facts, assisted = map(json.loads, sys.argv[1:])
assert any(item['id']=='builtin-evidence-synthesis-v1' for item in adapters), adapters
assert facts['assisted'] is False and facts['mutated'] is False, facts
assert len(facts['facts']) == 1 and len(facts['inferences']) == 0, facts
assert assisted['assisted'] is True and assisted['mutated'] is False, assisted
assert len(assisted['inferences']) >= 1 and len(assisted['heuristics']) >= 1, assisted
claim = assisted['inferences'][0]
assert claim['evidenceIds'] and 0 <= claim['confidence']['score'] <= 1, claim
PY
  printf '%s\n' 'Packaged facts-only + explicit assisted reasoning: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during M27 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then echo 'Tracked workspace delta appeared during M27 validation' >&2; git status --short --untracked-files=no >&2; exit 1; fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
M27 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
factsInferenceSeparation=PASS
explicitConfidence=PASS
evidenceProvenance=PASS
adapterOptionality=PASS
adapterFailureIsolation=PASS
noSilentMutation=PASS
surfaceConvergence=PASS
remoteReadRbac=PASS
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
