#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.1.0}"
BASE_REF="${MORPHEUS_R2_BASE_REF:-origin/develop}"
SKIP_PORTABLE="${MORPHEUS_R2_SKIP_PORTABLE:-false}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
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

"$PYTHON" - "$REPO" "$VERSION" <<'PY'
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
"$PYTHON" - "$UPGRADE_REPORT" <<'PY'
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

PACKAGED_M25_M26=SKIPPED
if [[ "$SKIP_PORTABLE" != true ]]; then
  LAUNCHER="$REPO/validation-output/m27/dist/.m20-linux/image/morpheus/bin/morpheus"
  [[ -x "$LAUNCHER" ]] || { echo "R2 packaged launcher is missing: $LAUNCHER" >&2; exit 1; }

  JAR_TOOL="${JAVA_HOME:-}/bin/jar"
  if [[ ! -x "$JAR_TOOL" ]]; then JAR_TOOL="$(command -v jar || true)"; fi
  [[ -x "$JAR_TOOL" ]] || { echo 'jar tool is unavailable' >&2; exit 1; }
  SHADED_JAR="$(find "$REPO/morpheus-cli/target" -maxdepth 1 -type f -name 'morpheus-cli-*-all.jar' -print | sort | tail -n 1)"
  [[ -n "$SHADED_JAR" ]] || { echo 'Shaded MORPHEUS CLI JAR not found' >&2; exit 1; }
  "$JAR_TOOL" tf "$SHADED_JAR" > "$OUTPUT/shaded-entries.txt"

  for entry in \
    'com/morpheus/application/policy/PolicyPackService.class' \
    'com/morpheus/application/policy/PolicyEvaluationService.class' \
    'com/morpheus/store/sqlite/SqlitePolicyPackStore.class' \
    'com/morpheus/cli/MorpheusPolicyCli.class' \
    'com/morpheus/mcp/MorpheusPolicyMcpTools.class' \
    'com/morpheus/api/MorpheusPolicyApiService.class' \
    'com/morpheus/api/MorpheusPolicyHttpRoutes.class' \
    'db/migration/V015__policy_packs.sql' \
    'com/morpheus/api/MorpheusRemoteHttpServer.class' \
    'com/morpheus/api/MorpheusRemoteIdentityFile.class' \
    'com/morpheus/api/MorpheusRemoteRole.class' \
    'com/morpheus/store/sqlite/SqliteServerMaintenance.class' \
    'com/morpheus/cli/RemoteApiLaunchOptions.class' \
    'com/morpheus/cli/MorpheusServerCli.class'; do
    grep -Fxq "$entry" "$OUTPUT/shaded-entries.txt" || { echo "R2 packaged runtime is missing $entry" >&2; exit 1; }
  done

  HELP="$($LAUNCHER help)"
  [[ "$HELP" == *'Policy packs / governance automation (M25)'* ]] || { echo 'Packaged M25 help surface is missing' >&2; exit 1; }
  [[ "$HELP" == *'Team / remote server (M26, opt-in)'* ]] || { echo 'Packaged M26 help surface is missing' >&2; exit 1; }
  printf '%s\n' 'Packaged M25 policy + M26 remote classes, migration and CLI surfaces: PASS'
  PACKAGED_M25_M26=PASS
fi

grep -Fq "<version>$VERSION</version>" "$REPO/pom.xml" || { echo "Root POM is not $VERSION" >&2; exit 1; }
grep -Fq "[string]\$Version = '$VERSION'" "$REPO/distribution/build-release.ps1" || { echo 'Windows release default version is incoherent' >&2; exit 1; }
grep -Fq 'VERSION="${1:-'"$VERSION"'}"' "$REPO/distribution/build-release.sh" || { echo 'Linux release default version is incoherent' >&2; exit 1; }
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
packagedM25M26=$PACKAGED_M25_M26
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
installer=NOT_APPLICABLE
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
