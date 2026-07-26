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
    echo
    for entry in "${RESULTS[@]:-}"; do
      IFS='|' read -r name result <<< "$entry"
      printf '%-34s %s\n' "$name" "$result"
    done
  } | tee "$summary"
  echo "Summary file: $summary"
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
echo "Dirty:     $([[ -n $(git status --porcelain) ]] && echo true || echo false)"
record "Workspace / SHA" "PASS"

section "Toolchain"
command -v java >/dev/null
java -version 2>&1 | tee "$LOG_ROOT/01-java-version.log"
chmod +x ./mvnw distribution/build-portable.sh
./mvnw --version 2>&1 | tee "$LOG_ROOT/01-maven-version.log"
record "Toolchain" "PASS"

run_stage "Full Maven reactor" "02-full-reactor.log" ./mvnw clean test
run_stage "M19 robustness contracts" "03-robustness.log" ./mvnw \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=LocalSourceInventorySecurityTest,OperationalObservabilityContractTest,LocalWritePermissionHardenerTest,SqliteConcurrencyHardeningTest,SnapshotRecoveryContractTest \
  test
run_stage "M19 performance gates" "04-performance-gates.log" ./mvnw \
  -pl morpheus-architecture-tests -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=M19PerformanceGate,M19QueryPerformanceGate,M19TraceabilityPerformanceGate,M19CompositionPerformanceGate,M19FullPublishPerformanceGate \
  -DargLine=-Xmx768m \
  test
run_stage "Linux portable packaging + smokes" "05-packaging.log" bash distribution/build-portable.sh
startup_gate

trap - EXIT
final_summary PASS
exit 0
