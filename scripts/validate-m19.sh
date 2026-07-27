#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

OUTPUT_ROOT="$REPO_ROOT/validation-output/m19-linux"
LOG_ROOT="$OUTPUT_ROOT/logs"
EXTRACT_ROOT="$OUTPUT_ROOT/portable-smoke"
mkdir -p "$LOG_ROOT"

CURRENT_STAGE=""
CURRENT_LOG=""
VALIDATION_SHA=""
FULL_TEST_SUMMARY=""
ARCHITECTURE_TEST_SUMMARY=""
declare -a RESULTS=()

section() {
  printf '\n%s\n%s\n%s\n' "==============================================================================" "$1" "=============================================================================="
}

record() {
  RESULTS+=("$1|$2")
}

failure_summary() {
  local error="$1"
  local summary="$OUTPUT_ROOT/failure-summary.txt"
  {
    echo "M19 VALIDATION FAILURE"
    echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    [[ -n "$VALIDATION_SHA" ]] && echo "SHA:       $VALIDATION_SHA"
    [[ -n "$CURRENT_STAGE" ]] && echo "Stage:     $CURRENT_STAGE"
    echo "Error:     $error"
    if [[ -n "$CURRENT_LOG" && -f "$CURRENT_LOG" ]]; then
      echo "Log:       $CURRENT_LOG"
      echo
      echo "Relevant log lines:"
      grep -Ei 'COMPILATION ERROR|\[ERROR\]|FAILURE|Failures:|Errors:|Tests run:|M19_METRIC' "$CURRENT_LOG" | tail -80 || tail -80 "$CURRENT_LOG"
    fi
  } | tee "$summary"
  echo "Failure summary: $summary" >&2
}

final_summary() {
  local outcome="$1"
  local summary="$OUTPUT_ROOT/validation-summary.txt"
  {
    echo "M19 VALIDATION SUMMARY"
    echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    [[ -n "$VALIDATION_SHA" ]] && echo "SHA:       $VALIDATION_SHA"
    echo "Result:    $outcome"
    echo "Windows proof: NOT EXECUTED BY THIS LINUX VALIDATOR"
    [[ -n "$FULL_TEST_SUMMARY" ]] && echo "Full reactor tests: $FULL_TEST_SUMMARY"
    [[ -n "$ARCHITECTURE_TEST_SUMMARY" ]] && echo "Architecture tests: $ARCHITECTURE_TEST_SUMMARY"
    echo
    for entry in "${RESULTS[@]:-}"; do
      IFS='|' read -r name result <<< "$entry"
      printf '%-34s %s\n' "$name" "$result"
    done
    if grep -Rh 'M19_METRIC' "$LOG_ROOT" --include='*.log' >/dev/null 2>&1; then
      echo
      echo "Measured M19 metrics:"
      grep -Rh 'M19_METRIC' "$LOG_ROOT" --include='*.log' | sort -u
    fi
  } | tee "$summary"
  echo "Summary file: $summary"
}

surefire_totals() {
  python3 - "$1" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

root = pathlib.Path(sys.argv[1])
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "suites": 0}
for report in root.rglob("target/surefire-reports/TEST-*.xml"):
    suite = ET.parse(report).getroot()
    for key in ("tests", "failures", "errors", "skipped"):
        totals[key] += int(suite.attrib.get(key, "0"))
    totals["suites"] += 1
print("; ".join(f"{key}: {value}" for key, value in totals.items()))
PY
}

run_stage() {
  local name="$1"
  local log_name="$2"
  shift 2
  CURRENT_STAGE="$name"
  CURRENT_LOG="$LOG_ROOT/$log_name"
  section "$name"
  printf 'Command:'
  printf ' %q' "$@"
  printf '\nLog:     %s\n' "$CURRENT_LOG"
  local start end
  start=$(date +%s)
  if "$@" 2>&1 | tee "$CURRENT_LOG"; then
    end=$(date +%s)
    record "$name" "PASS ($((end-start))s)"
  else
    local code=${PIPESTATUS[0]}
    record "$name" "FAIL ($code)"
    return "$code"
  fi
}

startup_gate() {
  CURRENT_STAGE="Packaged startup benchmark"
  CURRENT_LOG="$LOG_ROOT/06-packaged-startup.log"
  section "$CURRENT_STAGE"
  local archive
  archive=$(find "$REPO_ROOT/dist" -maxdepth 1 -type f -name 'morpheus-*-linux-x64.tar.gz' -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)
  [[ -n "$archive" ]] || { echo "No Linux portable archive found"; return 1; }
  rm -rf "$EXTRACT_ROOT"
  mkdir -p "$EXTRACT_ROOT"
  tar -xzf "$archive" -C "$EXTRACT_ROOT"
  local launcher
  launcher=$(find "$EXTRACT_ROOT" -type f -path '*/bin/morpheus' -print -quit)
  [[ -n "$launcher" ]] || { echo "Packaged morpheus launcher not found"; return 1; }
  chmod +x "$launcher"
  "$launcher" --json version >"$CURRENT_LOG" 2>&1

  python3 - "$launcher" "$CURRENT_LOG" <<'PY'
import math
import pathlib
import subprocess
import sys
import time

launcher = sys.argv[1]
log = pathlib.Path(sys.argv[2])
samples = []
outputs = []
for index in range(1, 6):
    started = time.perf_counter_ns()
    proc = subprocess.run([launcher, "--json", "version"], capture_output=True, text=True)
    elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000
    outputs.extend([proc.stdout, proc.stderr])
    if proc.returncode != 0:
        log.write_text("\n".join(outputs), encoding="utf-8")
        raise SystemExit(f"packaged launcher iteration {index} failed: {proc.returncode}")
    samples.append(elapsed_ms)
    print(f"M19_METRIC packaged_startup_run_{index}_ms={elapsed_ms:.1f}")
log.write_text("\n".join(outputs), encoding="utf-8")
ordered = sorted(samples)
rank = math.ceil(len(ordered) * 0.95)
p95 = ordered[max(0, rank - 1)]
print(f"M19_METRIC packaged_startup_p95_ms={p95:.1f}")
if p95 > 5000.0:
    raise SystemExit(f"packaged startup p95 {p95:.1f} ms exceeds frozen 5000 ms budget")
PY
  local archive_bytes
  archive_bytes=$(stat -c%s "$archive")
  echo "M19_METRIC linux_archive_bytes=$archive_bytes"
  record "$CURRENT_STAGE" "PASS"
}

trap 'code=$?; if [[ $code -ne 0 ]]; then failure_summary "stage failed with exit code $code"; final_summary FAIL; fi' EXIT

section "Workspace / SHA"
command -v git >/dev/null
[[ -d .git ]]
VALIDATION_SHA=$(git rev-parse HEAD)
echo "Workspace: $REPO_ROOT"
echo "Branch:    $(git branch --show-current)"
echo "SHA:       $VALIDATION_SHA"
WORKSPACE_STATUS="$(git status --porcelain)"
echo "Dirty:     $([[ -n "$WORKSPACE_STATUS" ]] && echo true || echo false)"
if [[ -n "$WORKSPACE_STATUS" ]]; then
  printf '%s\n' "$WORKSPACE_STATUS"
  echo "Exact-head validation requires a clean Git workspace" >&2
  exit 1
fi
record "Workspace / SHA" "PASS"

section "Reference environment"
LOGICAL_PROCESSORS="$(getconf _NPROCESSORS_ONLN)"
VISIBLE_RAM_KIB="$(awk '/^MemTotal:/ {print $2}' /proc/meminfo)"
VISIBLE_RAM_GIB="$(python3 - "$VISIBLE_RAM_KIB" <<'PY'
import sys
print(f"{int(sys.argv[1]) / 1024 / 1024:.1f}")
PY
)"
FILESYSTEM_TYPE="$(findmnt -n -o FSTYPE --target "$REPO_ROOT")"
FILESYSTEM_SOURCE="$(findmnt -n -o SOURCE --target "$REPO_ROOT")"
{
  echo "OS:                 $(uname -sr)"
  echo "Architecture:       $(uname -m)"
  echo "Logical processors: $LOGICAL_PROCESSORS"
  echo "Visible RAM GiB:    $VISIBLE_RAM_GIB"
  echo "Workspace fs:       $FILESYSTEM_TYPE"
  echo "Workspace source:   $FILESYSTEM_SOURCE"
  echo "DB fixture fs:      $FILESYSTEM_TYPE (under workspace target/)"
} | tee "$LOG_ROOT/01-reference-environment.log"
(( LOGICAL_PROCESSORS >= 4 )) || { echo "Reference environment requires at least 4 logical processors" >&2; exit 1; }
python3 - "$VISIBLE_RAM_GIB" <<'PY'
import sys
if float(sys.argv[1]) < 8.0:
    raise SystemExit("Reference environment requires at least 8 GiB visible RAM")
PY
case "$FILESYSTEM_TYPE" in
  nfs*|cifs|smb*|fuse.sshfs) echo "Workspace filesystem is not local: $FILESYSTEM_TYPE" >&2; exit 1 ;;
esac
record "Reference environment" "PASS"

section "Toolchain"
command -v java >/dev/null
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")"
  export JAVA_HOME
fi
echo "JAVA_HOME: $JAVA_HOME"
java -version 2>&1 | tee "$LOG_ROOT/01-java-version.log"
chmod +x ./mvnw distribution/build-portable.sh
./mvnw --version 2>&1 | tee "$LOG_ROOT/01-maven-version.log"
record "Toolchain" "PASS"

run_stage "Full Maven reactor" "02-full-reactor.log" ./mvnw clean test
FULL_TEST_SUMMARY="$(surefire_totals "$REPO_ROOT")"
ARCHITECTURE_TEST_SUMMARY="$(surefire_totals "$REPO_ROOT/morpheus-architecture-tests")"
echo "M19_TESTS full=$FULL_TEST_SUMMARY"
echo "M19_TESTS architecture=$ARCHITECTURE_TEST_SUMMARY"
run_stage "M19 robustness contracts" "03-robustness.log" ./mvnw \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=LocalSourceInventorySecurityTest,PartialSourceInventoryContractTest,OperationalObservabilityContractTest,OperationalExecutionTest,SensitiveValueRedactorCrossPlatformTest,LocalWritePermissionHardenerTest,ExternalLinkPolicyTest,SqliteLocalSecurityContractTest,SqliteConcurrencyHardeningTest,SqliteConcurrentReaderContractTest,SqliteMigrationCompatibilityM19Test,SnapshotRecoveryContractTest,RuntimeSnapshotRecoveryContractTest,FailedPublishRecoveryContractTest,LocalOperabilityContractTest,MorpheusApiRuntimeRecoveryContractTest \
  test
run_stage "M19 performance gates" "04-performance-gates.log" ./mvnw \
  -pl morpheus-architecture-tests -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=M19PerformanceGate,M19QueryPerformanceGate,M19TraceabilityPerformanceGate,M19CompositionPerformanceGate,M19FullPublishPerformanceGate \
  -DargLine=-Xmx768m \
  test
run_stage "Linux portable packaging + smokes" "05-packaging.log" bash distribution/build-portable.sh
startup_gate

section "Exact-head stability"
ENDING_SHA="$(git rev-parse HEAD)"
ENDING_STATUS="$(git status --porcelain)"
[[ "$ENDING_SHA" == "$VALIDATION_SHA" ]] || { echo "HEAD changed during validation: $VALIDATION_SHA -> $ENDING_SHA" >&2; exit 1; }
[[ -z "$ENDING_STATUS" ]] || { printf '%s\n' "$ENDING_STATUS"; echo "Workspace changed during validation" >&2; exit 1; }
echo "Stable SHA: $ENDING_SHA"
record "Exact-head stability" "PASS"

trap - EXIT
final_summary PASS
exit 0
