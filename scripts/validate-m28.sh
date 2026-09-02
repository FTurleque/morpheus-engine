#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.1.0}"
BASE_REF="${MORPHEUS_M28_BASE_REF:-origin/develop}"
SKIP_PORTABLE="${MORPHEUS_M28_SKIP_PORTABLE:-false}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m28"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

printf '%s\n' "M28 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M28 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

MORPHEUS_R2_BASE_REF="$BASE_REF" MORPHEUS_R2_SKIP_PORTABLE=true \
  bash "$SCRIPT_DIR/validate-r2.sh" "$VERSION"

bash -n "$REPO/distribution/build-portable.sh"
bash -n "$SCRIPT_DIR/validate-m28.sh"

"$PYTHON" - "$REPO" <<'PY'
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
manager = (root / 'integration/configure-mcp-clients.ps1').read_text(encoding='utf-8')
installer = (root / 'distribution/windows/MORPHEUS.iss').read_text(encoding='utf-8')
required_manager = [
    'CopilotJetBrains', 'CopilotCli', 'ClaudeCode', 'ClaudeDesktop', 'Codex',
    "args = @('mcp', '--stdio')", 'MORPHEUS_DATA_DIR', 'MORPHEUS_CONFIG_DIR',
    'existing-unmanaged-morpheus-entry', 'managed-entry-modified', 'Uninstall is state-driven'
]
for token in required_manager:
    if token not in manager:
        raise SystemExit(f'M28 manager contract token is missing: {token}')
if 'docker' in manager.lower():
    raise SystemExit('M28 native MCP client manager must not require Docker')
for task in ('mcp_copilot_jetbrains', 'mcp_copilot_cli', 'mcp_claude_code', 'mcp_claude_desktop', 'mcp_codex'):
    if task not in installer:
        raise SystemExit(f'M28 installer task is missing: {task}')
for path in (
    'integration/configure-mcp-clients.ps1',
    'integration/configure-mcp-clients-setup.ps1',
    'integration/README.md',
    'scripts/verify-m28-mcp-client-integration.ps1',
    'docs/user/MCP_CLIENTS.md',
    'docs/roadmap/M28_EXECUTION.md',
    'docs/validation/VALIDATION_M28.md'):
    if not (root / path).is_file():
        raise SystemExit(f'M28 required file is missing: {path}')
print('M28 static integration contract: PASS')
PY

PORTABLE=false
if [[ "$SKIP_PORTABLE" != true ]]; then
  bash "$REPO/distribution/build-portable.sh" "$VERSION" 'validation-output/m28/dist'
  PACKAGED="$REPO/validation-output/m28/dist/.m20-linux/image/morpheus/integration"
  [[ -f "$PACKAGED/configure-mcp-clients.ps1" ]] || { echo 'Packaged manager is missing' >&2; exit 1; }
  [[ -f "$PACKAGED/configure-mcp-clients-setup.ps1" ]] || { echo 'Packaged setup wrapper is missing' >&2; exit 1; }
  [[ -f "$PACKAGED/README.md" ]] || { echo 'Packaged integration README is missing' >&2; exit 1; }
  printf '%s\n' 'M28 Linux portable integration payload: PASS'
  PORTABLE=true
fi

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during M28 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'Tracked workspace delta appeared during M28 validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi

R2_SUMMARY="$REPO/validation-output/r2/validation-summary.txt"
[[ -f "$R2_SUMMARY" ]] || { echo "Inherited R2 summary missing: $R2_SUMMARY" >&2; exit 1; }
value() { sed -n "s/^$1=//p" "$R2_SUMMARY" | tail -n 1; }

cat > "$OUTPUT/validation-summary.txt" <<EOF
M28 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$(value tests)
architectureTests=$(value architectureTests)
lineCoverage=$(value lineCoverage)
branchCoverage=$(value branchCoverage)
mcpClientManager=STATIC_PASS
clients=5
jsonMerge=WINDOWS_ONLY
cliRegistration=WINDOWS_ONLY
idempotency=WINDOWS_ONLY
foreignEntryPreservation=WINDOWS_ONLY
modifiedEntryPreservation=WINDOWS_ONLY
stateDrivenUninstall=WINDOWS_ONLY
invalidJsonProtection=WINDOWS_ONLY
portable=$PORTABLE
installer=NOT_APPLICABLE
dockerRequired=false
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
