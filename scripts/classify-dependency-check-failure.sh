#!/usr/bin/env bash
#
# Names why a Dependency-Check scan failed, so the checks list distinguishes a vulnerability from an outage.
#
# Usage: classify-dependency-check-failure.sh <scan-log> <scope-label>
#
# Both failures used to present identically: "MORPHEUS Security failed". They are not the same event and they do
# not have the same response. VULNERABILITY_THRESHOLD_EXCEEDED means a dependency crossed the CVSS >= 7.0 line the
# build declares and someone must triage or suppress it. SCAN_EXECUTION_FAILED means the analyzer itself did not
# complete, so nothing was proven either way -- which is why it still fails the build rather than passing quietly.
# STALE_DATABASE is emitted by security.yml before a scan is even attempted and is an infrastructure failure.
#
# This script only labels a failure the caller has already decided to fail on; it never converts a failure into a
# success, and it exits non-zero on every path it handles so it cannot be the reason a scan appears to pass.

set -euo pipefail

log="${1:?path to the Dependency-Check scan log is required}"
scope="${2:?a scope label describing which scan failed is required}"

# Dependency-Check prints this exact sentence when, and only when, failBuildOnCVSS is what stopped the build.
threshold_marker='One or more dependencies were identified with vulnerabilities that have a CVSS score greater than or equal to'

if [[ -f "${log}" ]] && grep -qF "${threshold_marker}" "${log}"; then
  reason='VULNERABILITY_THRESHOLD_EXCEEDED'
  explanation='A dependency crossed the CVSS >= 7.0 threshold this build declares. This is a security finding: triage it, upgrade the dependency, or add a justified suppression to config/dependency-check-suppressions.xml.'
else
  reason='SCAN_EXECUTION_FAILED'
  explanation='The analyzer did not complete, so no vulnerability verdict was produced. This is not a clean scan: the build fails closed because nothing was proven either way.'
fi

echo "::error::MORPHEUS_DEPENDENCY_CHECK_FAILURE=${reason} in the ${scope} scan. ${explanation}"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Dependency-Check failure"
    echo
    echo "| Fact | Value |"
    echo "|---|---|"
    echo "| Reason | \`${reason}\` |"
    echo "| Scope | ${scope} |"
    echo
    echo "${explanation}"
    echo
    echo "${reason} is not STALE_DATABASE: the database was fresh enough to scan with, so this failure is about what the scan found or about the analyzer itself, not about the freshness of the vulnerability feed."
    echo
  } >> "${GITHUB_STEP_SUMMARY}"
fi

if [[ "${reason}" == 'VULNERABILITY_THRESHOLD_EXCEEDED' ]]; then
  grep -F -A 40 "${threshold_marker}" "${log}" | head -n 60 || true
fi

exit 1
