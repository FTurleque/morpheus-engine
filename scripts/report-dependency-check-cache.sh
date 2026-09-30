#!/usr/bin/env bash
#
# Publishes how old the trusted Dependency-Check database is, how much of its freshness budget is left, and which
# path produced it.
#
# Usage: report-dependency-check-cache.sh <age-seconds> <source-label>
#
# This script reports; it never decides. The caller measures the age and keeps the fail-closed decision inline in
# .github/workflows/security.yml, where the refusal is visible next to the step that performs the scan -- a
# security gate whose refusal lives in a helper is one indirection away from being softened by accident, and a
# caller that had to parse this script's output to learn whether to stop would be exactly that. What is factored
# here is only the arithmetic and the rendering, which both call sites need identically.
#
# The early warning exists because the failure mode it precedes is abrupt. Without NVD_API_KEY the workflow
# cannot refresh the database at all; it can only reuse a cache a trusted event refreshed earlier. Nothing about
# that is visible while the cache is fresh, and the first symptom is every pull request failing at once when the
# budget runs out. Announcing the remaining margin on every run turns that cliff into a slope.

set -euo pipefail

age_seconds="${1:?age of the Dependency-Check database in seconds is required}"
source_label="${2:?a source label describing how the database was obtained is required}"

max_age_hours="${DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS:?DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS must be set}"
max_age_seconds="$(( max_age_hours * 60 * 60 ))"
remaining_seconds="$(( max_age_seconds - age_seconds ))"

human() {
  local total="$1"
  if (( total < 0 )); then
    echo "none"
    return
  fi
  printf '%dh %02dm' "$(( total / 3600 ))" "$(( (total % 3600) / 60 ))"
}

if (( max_age_seconds > 0 && age_seconds >= 0 )); then
  consumed_percent="$(( age_seconds * 100 / max_age_seconds ))"
else
  consumed_percent=100
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Dependency-Check database freshness"
    echo
    echo "| Fact | Value |"
    echo "|---|---|"
    echo "| Obtained via | ${source_label} |"
    echo "| Age | $(human "${age_seconds}") |"
    echo "| Freshness budget (\`DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS\`) | ${max_age_hours}h |"
    echo "| Remaining before expiry | $(human "${remaining_seconds}") |"
    echo "| Budget consumed | ${consumed_percent}% |"
    echo
  } >> "${GITHUB_STEP_SUMMARY}"
fi

echo "Dependency-Check database obtained via ${source_label}: $(human "${age_seconds}") old, $(human "${remaining_seconds}") of the ${max_age_hours}h budget remaining (${consumed_percent}% consumed)."

# Two thirds of the budget is the point where the remaining margin is shorter than the gap between two daily
# refresh attempts, so a single missed refresh from here is enough to expire the database.
if (( age_seconds * 3 >= max_age_seconds * 2 )); then
  echo "::warning::Dependency-Check database has consumed ${consumed_percent}% of its ${max_age_hours}h freshness budget (obtained via ${source_label}); $(human "${remaining_seconds}") remain before every scan starts failing with STALE_DATABASE. Refresh it by configuring NVD_API_KEY, or by upgrading Dependency-Check once upstream fix #8716 is released."
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
      echo "> **Approaching expiry.** ${consumed_percent}% of the freshness budget is consumed."
      echo "> When it reaches 100% every scan, including every pull request, fails with \`STALE_DATABASE\`."
      echo "> That is an infrastructure failure, not a vulnerability finding."
      echo
    } >> "${GITHUB_STEP_SUMMARY}"
  fi
fi
