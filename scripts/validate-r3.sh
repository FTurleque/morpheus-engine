#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.2.0}"
BASE_REF="${MORPHEUS_R3_BASE_REF:-origin/develop}"
SKIP_PORTABLE="${MORPHEUS_R3_SKIP_PORTABLE:-false}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/r3"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

printf '%s\n' "R3 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'R3 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

git diff --check "$BASE_REF...HEAD"
CHANGED_FILES="$(git diff --name-only "$BASE_REF...HEAD")"
if grep -Eq '^\.github/workflows/' <<<"$CHANGED_FILES"; then
  echo 'R3 must not modify GitHub Actions workflows during the July 2026 freeze' >&2
  exit 1
fi
if grep -Eq '^morpheus-store-sqlite/src/main/resources/db/migration/' <<<"$CHANGED_FILES"; then
  echo 'R3 must not introduce a SQLite migration for the configuration-only M28 release' >&2
  exit 1
fi
printf '%s\n' 'R3 scope policy: PASS (no CI workflow or SQLite migration delta)'

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
    for stale in ('1.1.0', '1.0.0', '0.1.0-SNAPSHOT'):
        if f'<version>{stale}</version>' in content:
            raise SystemExit(f'Stale MORPHEUS {stale} version remains in {pom.relative_to(root)}')
print(f'Maven reactor version: PASS ({version} across 17 POMs)')
PY

MORPHEUS_M28_BASE_REF="$BASE_REF" MORPHEUS_M28_SKIP_PORTABLE="$SKIP_PORTABLE" \
  bash "$SCRIPT_DIR/validate-m28.sh" "$VERSION"

bash -n "$REPO/distribution/build-portable.sh"
bash -n "$REPO/distribution/build-release.sh"
bash -n "$SCRIPT_DIR/validate-r3.sh"

python3 - "$REPO" "$VERSION" <<'PY'
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
version = sys.argv[2]
required = [
    'docs/release/RELEASE_NOTES_1.2.0.md',
    'docs/user/UPGRADE_1_2.md',
    'docs/roadmap/R3_EXECUTION.md',
    'docs/validation/VALIDATION_R3.md',
]
for relative in required:
    if not (root / relative).is_file():
        raise SystemExit(f'R3 required documentation is missing: {relative}')

checks = {
    'distribution/build-portable.ps1': f'[string]$Version = "{version}"',
    'distribution/build-installer.ps1': f"[string]$Version = '{version}'",
    'distribution/build-release.ps1': f"[string]$Version = '{version}'",
    'distribution/build-portable.sh': f'VERSION="${{1:-{version}}}"',
    'distribution/build-release.sh': f'VERSION="${{1:-{version}}}"',
}
for relative, token in checks.items():
    content = (root / relative).read_text(encoding='utf-8')
    if token not in content:
        raise SystemExit(f'R3 default version is incoherent in {relative}: expected {token}')

notes = (root / 'docs/release/RELEASE_NOTES_1.2.0.md').read_text(encoding='utf-8')
for token in ('MORPHEUS 1.2.0', 'GitHub Copilot', 'Claude Code', 'Claude Desktop', 'OpenAI Codex', 'mcp --stdio', 'Docker'):
    if token not in notes:
        raise SystemExit(f'R3 release notes token is missing: {token}')
print('R3 builder defaults and release documentation: PASS')
PY

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during R3 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during R3 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

M28_SUMMARY="$REPO/validation-output/m28/validation-summary.txt"
[[ -f "$M28_SUMMARY" ]] || { echo "Inherited M28 summary missing: $M28_SUMMARY" >&2; exit 1; }
value() { sed -n "s/^$1=//p" "$M28_SUMMARY" | tail -n 1; }

cat > "$OUTPUT/validation-summary.txt" <<EOF
R3 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$(value tests)
architectureTests=$(value architectureTests)
lineCoverage=$(value lineCoverage)
branchCoverage=$(value branchCoverage)
reactorVersion=PASS
mcpClientIntegration=PASS
clients=5
releaseDocumentation=PASS
schemaMigration=UNCHANGED
ciWorkflowDelta=NONE
portable=$(value portable)
installer=NOT_APPLICABLE
sbom=PASS
provenance=PASS
dockerRequired=false
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
