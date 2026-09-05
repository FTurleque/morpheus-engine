#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "::error::SONAR_TOKEN is required to classify the SonarQube Cloud analysis"
  exit 1
fi

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
report_task="$(find "$workspace" -type f -path '*/target/sonar/report-task.txt' -print -quit)"
if [[ -z "$report_task" || ! -f "$report_task" ]]; then
  echo "::error::Sonar scanner failed before producing report-task.txt; refusing to hide a technical scanner failure"
  exit 1
fi

property() {
  local key="$1"
  sed -n "s/^${key}=//p" "$report_task" | head -n 1
}

ce_task_url="$(property ceTaskUrl)"
server_url="$(property serverUrl)"
if [[ -z "$ce_task_url" || -z "$server_url" ]]; then
  echo "::error::Sonar report-task.txt is missing ceTaskUrl or serverUrl"
  exit 1
fi

sonar_get() {
  curl --fail-with-body --silent --show-error \
    --connect-timeout 10 --max-time 30 \
    -H "Authorization: Bearer ${SONAR_TOKEN}" \
    "$1"
}

ce_json=""
ce_status="UNKNOWN"
for attempt in $(seq 1 30); do
  ce_json="$(sonar_get "$ce_task_url")" || {
    echo "::error::Unable to read the current Sonar compute-engine task"
    exit 1
  }
  ce_status="$(jq -r '.task.status // "UNKNOWN"' <<<"$ce_json")"
  case "$ce_status" in
    SUCCESS)
      break
      ;;
    FAILED|CANCELED)
      echo "::error::Sonar compute-engine task ended with status ${ce_status}"
      jq -r '.task.errorMessage // empty' <<<"$ce_json" >&2
      exit 1
      ;;
    PENDING|IN_PROGRESS)
      sleep 2
      ;;
    *)
      echo "::error::Unexpected Sonar compute-engine status: ${ce_status}"
      exit 1
      ;;
  esac
done

if [[ "$ce_status" != "SUCCESS" ]]; then
  echo "::error::Timed out waiting for the current Sonar compute-engine task"
  exit 1
fi

analysis_id="$(jq -r '.task.analysisId // empty' <<<"$ce_json")"
if [[ -z "$analysis_id" ]]; then
  echo "::error::Completed Sonar compute-engine task has no analysisId"
  exit 1
fi

gate_url="${server_url%/}/api/qualitygates/project_status?analysisId=${analysis_id}"
gate_json=""
gate_status="UNKNOWN"
for attempt in $(seq 1 12); do
  gate_json="$(sonar_get "$gate_url")" || {
    echo "::error::Unable to read the quality gate for analysis ${analysis_id}"
    exit 1
  }
  gate_status="$(jq -r '.projectStatus.status // "UNKNOWN"' <<<"$gate_json")"
  if [[ "$gate_status" != "NONE" ]]; then
    break
  fi
  sleep 5
done

case "$gate_status" in
  OK)
    echo "SonarQube Cloud quality gate: OK (analysis ${analysis_id})"
    ;;
  ERROR)
    echo "::error::SonarQube Cloud quality gate genuinely failed for analysis ${analysis_id}"
    jq -r '.projectStatus.conditions[]? | select(.status == "ERROR") |
      "::error::Sonar condition " + .metricKey + " actual=" + (.actualValue // "n/a") +
      " comparator=" + (.comparator // "n/a") + " threshold=" + (.errorThreshold // "n/a")' \
      <<<"$gate_json"
    exit 1
    ;;
  NONE)
    echo "::warning::SonarQube Cloud did not compute a quality gate for the successfully uploaded analysis ${analysis_id}. This is an indeterminate external-service state, not a MORPHEUS product failure."
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
      {
        echo "### SonarQube Cloud"
        echo
        echo "Analysis \`${analysis_id}\` was uploaded and processed, but SonarQube Cloud returned quality-gate status \`NONE\` after retries. The trusted main analysis is retained as advisory evidence; product gates are not marked failed for an absent Sonar verdict."
      } >> "$GITHUB_STEP_SUMMARY"
    fi
    ;;
  *)
    echo "::error::Unexpected Sonar quality-gate status: ${gate_status}"
    exit 1
    ;;
esac
