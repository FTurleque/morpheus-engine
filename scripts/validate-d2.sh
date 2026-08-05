#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.2.0}"
BASE_REF="${MORPHEUS_D2_BASE_REF:-origin/develop}"
SKIP_SECURITY_SCAN="${MORPHEUS_D2_SKIP_SECURITY_SCAN:-false}"
SKIP_PORTABLE="${MORPHEUS_D2_SKIP_PORTABLE:-false}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/d2"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

printf '%s\n' "D2 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'D2 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  BASE_REF=develop
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo 'D2 base ref not found and fallback develop is unavailable' >&2
  exit 1
fi
printf '%s\n' "D2 diff base: $BASE_REF"

git diff --check "$BASE_REF...HEAD"
if git diff --name-only "$BASE_REF...HEAD" -- .github/workflows | grep -q .; then
  echo 'D2 is local-only and must not modify GitHub Actions workflows' >&2
  exit 1
fi
printf '%s\n' 'D2 no-CI scope: PASS (.github/workflows delta NONE)'

python3 - "$REPO" "$VERSION" <<'PY'
import pathlib
import sys
root = pathlib.Path(sys.argv[1])
version = sys.argv[2]
poms = sorted(p for p in root.rglob('pom.xml') if 'target' not in p.parts)
if len(poms) != 17:
    raise SystemExit(f'unexpected Maven reactor POM count: {len(poms)}, expected 17')
for pom in poms:
    text = pom.read_text(encoding='utf-8')
    if f'<version>{version}</version>' not in text:
        raise SystemExit(f'MORPHEUS {version} version missing from {pom}')
root_pom = (root / 'pom.xml').read_text(encoding='utf-8')
for token in (
    '<jackson.version>3.1.5</jackson.version>',
    '<sqlite-jdbc.version>3.53.2.0</sqlite-jdbc.version>',
    '<dependency-check.maven.plugin.version>12.2.2</dependency-check.maven.plugin.version>',
    '<failOnWarning>true</failOnWarning>',
    '<id>d2-security</id>',
    '<failBuildOnCVSS>7.0</failBuildOnCVSS>',
):
    if token not in root_pom:
        raise SystemExit(f'D2 dependency/quality token missing from pom.xml: {token}')
PY
printf '%s\n' 'D2 dependency baseline: PASS'

./mvnw clean verify

read -r TESTS FAILURES ERRORS SKIPPED ARCH_TESTS < <(python3 - "$REPO" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
tests = failures = errors = skipped = arch = 0
for report in root.rglob('target/surefire-reports/TEST-*.xml'):
    suite = ET.parse(report).getroot()
    current = int(suite.attrib.get('tests', 0))
    tests += current
    failures += int(suite.attrib.get('failures', 0))
    errors += int(suite.attrib.get('errors', 0))
    skipped += int(suite.attrib.get('skipped', 0))
    if 'morpheus-architecture-tests' in report.parts:
        arch += current
print(tests, failures, errors, skipped, arch)
PY
)
if (( FAILURES != 0 || ERRORS != 0 )); then
  echo "D2 Surefire failures=$FAILURES errors=$ERRORS" >&2
  exit 1
fi
if (( TESTS < 613 )); then
  echo "D2 test baseline regression: $TESTS < 613" >&2
  exit 1
fi
if (( ARCH_TESTS < 247 )); then
  echo "D2 architecture baseline regression: $ARCH_TESTS < 247" >&2
  exit 1
fi
printf '%s\n' "D2 tests: PASS ($TESTS tests, architecture=$ARCH_TESTS, skipped=$SKIPPED)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
if [[ ! -f "$COVERAGE" ]]; then
  echo "D2 coverage summary missing: $COVERAGE" >&2
  exit 1
fi
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
python3 - "$LINE_RATIO" "$BRANCH_RATIO" <<'PY'
import sys
line = float(sys.argv[1])
branch = float(sys.argv[2])
if line < 0.40:
    raise SystemExit(f'D2 line coverage below 0.40: {line}')
if branch < 0.35:
    raise SystemExit(f'D2 branch coverage below 0.35: {branch}')
PY
printf '%s\n' "D2 coverage: PASS (line=$LINE_RATIO branch=$BRANCH_RATIO)"

SBOM_JSON="$REPO/target/m21-supply-chain/morpheus-sbom.json"
SBOM_XML="$REPO/target/m21-supply-chain/morpheus-sbom.xml"
if [[ ! -f "$SBOM_JSON" || ! -f "$SBOM_XML" ]]; then
  echo 'D2 CycloneDX aggregate SBOM JSON/XML missing after clean verify' >&2
  exit 1
fi
printf '%s\n' 'D2 SBOM: PASS'

SECURITY_SCAN=SKIPPED
if [[ "$SKIP_SECURITY_SCAN" != true ]]; then
  ./mvnw -Pd2-security org.owasp:dependency-check-maven:12.2.2:aggregate
  SECURITY_REPORT="$REPO/target/d2-security/dependency-check-report.json"
  if [[ ! -f "$SECURITY_REPORT" ]]; then
    echo "D2 dependency-check JSON report missing: $SECURITY_REPORT" >&2
    exit 1
  fi
  SECURITY_SCAN=PASS
  printf '%s\n' 'D2 SCA: PASS (OWASP Dependency-Check, CVSS >= 7 fails the gate)'
fi

PORTABLE=SKIPPED
if [[ "$SKIP_PORTABLE" != true ]]; then
  chmod +x distribution/build-portable.sh
  ./distribution/build-portable.sh "$VERSION" 'validation-output/d2/dist'
  LAUNCHER="$REPO/validation-output/d2/dist/.m20-linux/image/morpheus/bin/morpheus"
  if [[ ! -x "$LAUNCHER" ]]; then
    echo "D2 packaged launcher missing: $LAUNCHER" >&2
    exit 1
  fi
  PRODUCT_INFO="$($LAUNCHER --json product-info)"
  python3 - "$PRODUCT_INFO" "$VERSION" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
if payload.get('version') != sys.argv[2]:
    raise SystemExit(f"D2 packaged version mismatch: {payload.get('version')} != {sys.argv[2]}")
PY
  PORTABLE=PASS
  printf '%s\n' 'D2 Linux portable: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
if [[ "$CURRENT_SHA" != "$VALIDATION_SHA" ]]; then
  echo "HEAD changed during D2 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2
  exit 1
fi
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during D2 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
D2 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
dependencyHygiene=PASS
securityScan=$SECURITY_SCAN
sbom=PASS
portable=$PORTABLE
ciUsed=false
ciWorkflowDelta=NONE
workspaceTrackedClean=PASS
EOF
cat "$OUTPUT/validation-summary.txt"
