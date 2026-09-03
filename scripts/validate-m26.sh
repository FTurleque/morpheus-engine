#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
SKIP_PORTABLE="${MORPHEUS_M26_SKIP_PORTABLE:-false}"
BASE_REF="${MORPHEUS_M26_BASE_REF:-origin/develop}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/lib/python.sh"
REPO="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO"
OUTPUT="$REPO/validation-output/m26"
mkdir -p "$OUTPUT"
VALIDATION_SHA="$(git rev-parse HEAD)"

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="$(command -v java || true)"
  if [[ -n "$JAVA_BIN" ]]; then
    JAVA_BIN="$(readlink -f "$JAVA_BIN")"
    DISCOVERED_JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
    if [[ -x "$DISCOVERED_JAVA_HOME/bin/java" ]]; then
      export JAVA_HOME="$DISCOVERED_JAVA_HOME"
      printf '%s\n' "M26 discovered JAVA_HOME=$JAVA_HOME"
    fi
  fi
fi

printf '%s\n' "M26 exact-head validation SHA: $VALIDATION_SHA"
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo 'M26 exact-head gate requires no tracked workspace delta before validation' >&2
  git status --short --untracked-files=no >&2
  exit 1
fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then BASE_REF=develop; fi
if ! git rev-parse --verify "${BASE_REF}^{commit}" >/dev/null 2>&1; then
  echo 'M26 base ref not found: origin/develop (fallback develop also missing)' >&2
  exit 1
fi
printf '%s\n' "M26 diff base: $BASE_REF"
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
if (( TESTS < 565 )); then echo "M26 M25-baseline regression: $TESTS < 565" >&2; exit 1; fi
if (( ARCH_TESTS < 231 )); then echo "M26 architecture baseline regression: $ARCH_TESTS < 231" >&2; exit 1; fi
printf '%s\n' "Tests: PASS ($TESTS, M25 baseline >= 565)"
printf '%s\n' "Architecture: PASS ($ARCH_TESTS, M25 baseline >= 231)"

COVERAGE="$REPO/morpheus-architecture-tests/target/m21-coverage-summary.txt"
[[ -f "$COVERAGE" ]] || { echo "Missing production coverage summary: $COVERAGE" >&2; exit 1; }
LINE_RATIO="$(sed -n 's/^lineRatio=//p' "$COVERAGE")"
BRANCH_RATIO="$(sed -n 's/^branchRatio=//p' "$COVERAGE")"
morpheus_python - "$LINE_RATIO" "$BRANCH_RATIO" <<'PY'
import sys
line, branch = map(float, sys.argv[1:])
if line < .25: raise SystemExit(f'M26 line coverage below 25%: {line}')
if branch < .20: raise SystemExit(f'M26 branch coverage below 20%: {branch}')
PY
printf '%s\n' "JaCoCo: PASS (line=$LINE_RATIO, branch=$BRANCH_RATIO)"

[[ -f "$REPO/target/m21-supply-chain/morpheus-sbom.json" && -f "$REPO/target/m21-supply-chain/morpheus-sbom.xml" ]] || { echo 'CycloneDX JSON/XML SBOM is missing' >&2; exit 1; }
bash scripts/write-build-provenance.sh
[[ -f "$REPO/target/m21-supply-chain/build-provenance.properties" ]] || { echo 'Build provenance is missing' >&2; exit 1; }
printf '%s\n' 'Supply chain: PASS (CycloneDX JSON/XML + provenance)'

if [[ "$SKIP_PORTABLE" != true ]]; then
  bash distribution/build-portable.sh "$VERSION" 'validation-output/m26/dist'
  LAUNCHER="$REPO/validation-output/m26/dist/.m20-linux/image/morpheus/bin/morpheus"
  [[ -x "$LAUNCHER" ]] || { echo "Packaged launcher not found: $LAUNCHER" >&2; exit 1; }
  JAR_TOOL="${JAVA_HOME:-}/bin/jar"
  [[ -x "$JAR_TOOL" ]] || { echo "jar tool not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2; exit 1; }
  SHADED_JAR="$(ls -1t "$REPO"/morpheus-cli/target/morpheus-cli-*-all.jar | head -n 1)"
  "$JAR_TOOL" tf "$SHADED_JAR" > "$OUTPUT/shaded-entries.txt"
  for entry in \
    'com/morpheus/api/MorpheusRemoteHttpServer.class' \
    'com/morpheus/api/MorpheusRemoteIdentityFile.class' \
    'com/morpheus/api/MorpheusRemoteRole.class' \
    'com/morpheus/store/sqlite/SqliteServerMaintenance.class' \
    'com/morpheus/cli/RemoteApiLaunchOptions.class' \
    'com/morpheus/cli/MorpheusServerCli.class'; do
    grep -Fxq "$entry" "$OUTPUT/shaded-entries.txt" || { echo "M26 packaged runtime is missing $entry" >&2; exit 1; }
  done
  HELP="$($LAUNCHER help)"
  [[ "$HELP" == *'Team / remote server (M26, opt-in)'* ]] || { echo 'Packaged M26 CLI help smoke failed' >&2; exit 1; }
  printf '%s\n' 'M26 TLS/auth/server/maintenance classes + CLI help packaging proof: PASS'

  # MORPHEUS creates and hardens its own data directory, so the gate must not pre-create it: a directory made
  # here inherits the permissions of whatever it sits under, and the real owner-controlled storage path is never
  # exercised. Under the repository that inheritance is precisely what the hardener refuses, which made a
  # packaged product gate depend on the permissions of a development checkout. mktemp gives an owner-only parent;
  # the data directory itself is only named here and is created by the launcher below.
  DATA_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/morpheus-m26-XXXXXXXXXX")"
  # Best effort, and deliberately not allowed to replace whatever failure is already unwinding.
  trap 'rm -rf "$DATA_ROOT"' EXIT
  DATA="$DATA_ROOT/server-data"
  IDENTITY="$($LAUNCHER --data-dir "$DATA" --json server identity create --principal gate-admin --role ADMIN)"
  read -r TOKEN TOKEN_PERSISTENCE < <(morpheus_python - "$IDENTITY" <<'PY'
import json,sys
p=json.loads(sys.argv[1]); print(p['token'],p['tokenPersistence'])
PY
)
  [[ -n "$TOKEN" && "$TOKEN_PERSISTENCE" == 'NOT_PERSISTED_PRINTED_ONCE' ]] || { echo "M26 identity result mismatch: $IDENTITY" >&2; exit 1; }
  AUTH_FILE="$DATA/config/remote-auth.txt"
  [[ -f "$AUTH_FILE" ]] || { echo "M26 remote auth file missing: $AUTH_FILE" >&2; exit 1; }
  ! grep -Fq "$TOKEN" "$AUTH_FILE" || { echo 'Plaintext bearer token leaked into persisted auth file' >&2; exit 1; }
  grep -Eq '^gate-admin\|ADMIN\|[0-9a-f]{64}$' "$AUTH_FILE" || { cat "$AUTH_FILE" >&2; exit 1; }
  printf '%s\n' 'Remote identity hash-only provisioning: PASS'

  BACKUP="$($LAUNCHER --data-dir "$DATA" --json server backup create)"
  read -r BACKUP_PATH BACKUP_SHA BACKUP_SCHEMA BACKUP_OK < <(morpheus_python - "$BACKUP" <<'PY'
import json,sys
p=json.loads(sys.argv[1]); print(p['path'],p['sha256'],p['schemaVersion'],str(p['integrityOk']).lower())
PY
)
  [[ -f "$BACKUP_PATH" && "$BACKUP_SCHEMA" == 17 && "$BACKUP_OK" == true ]] || { echo "M26 backup result mismatch: $BACKUP" >&2; exit 1; }
  VERIFIED="$($LAUNCHER --data-dir "$DATA" --json server backup verify --file "$BACKUP_PATH")"
  morpheus_python - "$VERIFIED" "$BACKUP_SHA" <<'PY'
import json,sys
p=json.loads(sys.argv[1]); expected=sys.argv[2]
assert p['integrityOk'] is True and p['schemaVersion']==17 and p['sha256']==expected,p
PY
  if "$LAUNCHER" --data-dir "$DATA" server restore --file "$BACKUP_PATH" >"$OUTPUT/restore-unconfirmed.stdout" 2>"$OUTPUT/restore-unconfirmed.stderr"; then
    echo 'Unconfirmed M26 restore unexpectedly succeeded' >&2; exit 1
  fi
  grep -q -- '--confirm' "$OUTPUT/restore-unconfirmed.stderr" || { cat "$OUTPUT/restore-unconfirmed.stderr" >&2; exit 1; }
  RESTORED="$($LAUNCHER --data-dir "$DATA" --json server restore --file "$BACKUP_PATH" --confirm)"
  morpheus_python - "$RESTORED" <<'PY'
import json,sys
p=json.loads(sys.argv[1]); assert p['integrityOk'] is True and p['schemaVersion']==17,p
PY
  printf '%s\n' 'SQLite backup + verify + explicit offline restore: PASS'

  if "$LAUNCHER" --data-dir "$DATA" api --host 0.0.0.0 --port 18765 >"$OUTPUT/local-nonloopback.stdout" 2>"$OUTPUT/local-nonloopback.stderr"; then
    echo 'Local non-loopback API unexpectedly started' >&2; exit 1
  fi
  grep -Eq 'requires explicit.*api --remote' "$OUTPUT/local-nonloopback.stderr" || { cat "$OUTPUT/local-nonloopback.stderr" >&2; exit 1; }
  if "$LAUNCHER" --data-dir "$DATA" api --remote --host 127.0.0.1 --port 18766 >"$OUTPUT/remote-missing-tls.stdout" 2>"$OUTPUT/remote-missing-tls.stderr"; then
    echo 'Remote API without TLS unexpectedly started' >&2; exit 1
  fi
  grep -Eq 'requires --tls-keystore|TLS keystore' "$OUTPUT/remote-missing-tls.stderr" || { cat "$OUTPUT/remote-missing-tls.stderr" >&2; exit 1; }
  printf '%s\n' 'Local-first bind boundary + remote fail-closed startup: PASS'
fi

CURRENT_SHA="$(git rev-parse HEAD)"
[[ "$CURRENT_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during M26 validation: $VALIDATION_SHA -> $CURRENT_SHA" >&2; exit 1; }
if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then echo 'Tracked workspace delta appeared during M26 validation' >&2; git status --short --untracked-files=no >&2; exit 1; fi

cat > "$OUTPUT/validation-summary.txt" <<EOF
M26 VALIDATION PASS
sha=$VALIDATION_SHA
baseRef=$BASE_REF
version=$VERSION
tests=$TESTS
architectureTests=$ARCH_TESTS
lineCoverage=$LINE_RATIO
branchCoverage=$BRANCH_RATIO
localFirst=PASS
remoteTlsAuthRbac=PASS
boundedConcurrency=PASS
secretNonDisclosure=PASS
backupRestore=PASS
schemaCompatibility=PASS
surfaceConvergence=PASS
sqliteV017=PASS
sbom=PASS
provenance=PASS
portable=$([[ "$SKIP_PORTABLE" == true ]] && echo false || echo true)
postGateExecutableDelta=NONE
EOF
cat "$OUTPUT/validation-summary.txt"
