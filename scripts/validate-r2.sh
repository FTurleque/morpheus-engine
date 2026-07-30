#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.1.0}"
BASE_REF="${MORPHEUS_R2_BASE_REF:-origin/develop}"
SKIP_PORTABLE="${MORPHEUS_R2_SKIP_PORTABLE:-false}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/r2"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

printf '%s\n' "R2 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'R2 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

python3 - "$REPO" "$VERSION" <<'PY'
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
version = sys.argv[2]
poms = sorted(path for path in root.rglob('pom.xml') if 'target' not in path.parts)
if len(poms) != 17:
    raise SystemExit(f'Unexpected Maven reactor POM count: {len(poms)}, expected 17')
for pom in poms:
    content = pom.read_text(encoding='utf-8')
    if f'<version>{version}</version>' not in content:
        raise SystemExit(f'MORPHEUS {version} version missing from {pom.relative_to(root)}')
    if '<version>1.0.0</version>' in content:
        raise SystemExit(f'Stale MORPHEUS 1.0.0 version remains in {pom.relative_to(root)}')
print(f'Maven reactor version: PASS ({version} across 17 POMs)')
PY

MORPHEUS_M27_BASE_REF="$BASE_REF" \
MORPHEUS_M27_SKIP_PORTABLE="$SKIP_PORTABLE" \
  bash "$SCRIPT_DIR/validate-m27.sh" "$VERSION"

UPGRADE_REPORT="$REPO/morpheus-store-sqlite/target/surefire-reports/TEST-com.morpheus.store.sqlite.R2UpgradeCompatibilityTest.xml"
[[ -f "$UPGRADE_REPORT" ]] || { echo "R2 upgrade report is missing: $UPGRADE_REPORT" >&2; exit 1; }
python3 - "$UPGRADE_REPORT" <<'PY'
import sys
import xml.etree.ElementTree as ET

suite = ET.parse(sys.argv[1]).getroot()
tests = int(suite.attrib.get('tests', 0))
failures = int(suite.attrib.get('failures', 0))
errors = int(suite.attrib.get('errors', 0))
if tests < 1 or failures or errors:
    raise SystemExit(f'R2 upgrade compatibility test failed: tests={tests} failures={failures} errors={errors}')
print('SQLite V012 -> V015 upgrade compatibility: PASS')
PY

grep -Fq '<version>1.1.0</version>' "$REPO/pom.xml" || { echo 'Root POM is not 1.1.0' >&2; exit 1; }
grep -Fq "[string]\$Version = '1.1.0'" "$REPO/distribution/build-release.ps1" || { echo 'Windows release default version is incoherent' >&2; exit 1; }
grep -Fq 'VERSION="${1:-1.1.0}"' "$REPO/distribution/build-release.sh" || { echo 'Linux release default version is incoherent' >&2; exit 1; }
printf '%s\n' 'Release script default versions: PASS'

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during R2 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during R2 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

M27_SUMMARY="$REPO/validation-output/m27/validation-summary.txt"
[[ -f "$M27_SUMMARY" ]] || { echo "Inherited M27 summary missing: $M27_SUMMARY" >&2; exit 1; }
value() { sed -n "s/^$1=//p" "$M27_SUMMARY" | tail -n 1; }

cat > "$OUTPUT/validation-summary.txt" <<EOF
R2 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$(value tests)
architectureTests=$(value architectureTests)
lineCoverage=$(value lineCoverage)
branchCoverage=$(value branchCoverage)
reactorVersion=PASS
sqliteV012ToV015Upgrade=PASS
policyPacks=PASS
remoteServer=PASS
assistedReasoning=PASS
surfaceConvergence=PASS
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
installer=NOT_APPLICABLE
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"