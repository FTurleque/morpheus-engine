#!/usr/bin/env bash
#
# Reads the trusted-refresh sentinel and reports what it says about the restored database.
#
# Usage: read-dependency-check-sentinel.sh <data-directory> <expected-schema-version>
#
# Emits machine-readable `key=value` facts on stdout and human commentary on stderr, so a caller can capture
# the facts without parsing prose. It classifies; it never decides. Every refusal stays inline in
# .github/workflows/security.yml, next to the step that would otherwise scan -- a gate whose refusal lives in
# a helper is one indirection away from being softened by accident.
#
# Because it never decides, it must never be able to report freshness it did not establish. Every path that
# fails to prove an age reports a non-OK status, and `status` is the first thing written, so a caller that
# reads only that line still fails closed:
#
#   MISSING          no sentinel. A cache restored without one predates this mechanism or was produced by a
#                    path that refreshed nothing. Its age is unknown -- which is not the same as fresh, and
#                    under the tri-state rule this project applies elsewhere it must not be read as fresh.
#   MALFORMED        a sentinel that does not carry a usable epoch. Same consequence as MISSING.
#   SCHEMA_MISMATCH  the database was written against an H2 schema this analyzer does not expect. The scans
#                    run with -DautoUpdate=false, under which Dependency-Check refuses to migrate and throws;
#                    catching it here names it instead of leaving it to a stack trace mid-scan.
#   OK               an epoch was recorded by a real refresh and the schema matches. `ageSeconds` is then the
#                    age of the refresh itself, not of a file's mtime.
#
# `pluginVersion` is reported and never fatal. The plugin version is not the schema version and does not
# track it: 12.2.2 and 13.0.0 both write schema 5.6 with identical DDL, so a database refreshed by one and
# scanned by the other is correct. Reporting it is what makes that cross-version reuse visible instead of
# silent, which is the property the plugin-versioned cache key claimed to provide and did not.

set -euo pipefail

data_dir="${1:?the Dependency-Check data directory is required}"
expected_schema="${2:?the expected Dependency-Check H2 schema version is required}"

sentinel="${data_dir}/dependency-check-refresh.sentinel"

emit_and_exit() {
  echo "status=$1"
  echo "sentinelPath=${sentinel}"
  shift
  local line
  for line in "$@"; do
    echo "${line}"
  done
  exit 0
}

value_of() {
  sed -n "s/^$1=//p" "${sentinel}" | head -n 1
}

if [[ ! -f "${sentinel}" ]]; then
  echo "No trusted-refresh sentinel at ${sentinel}: the age of this database cannot be established." >&2
  emit_and_exit MISSING
fi

refreshed_at="$(value_of refreshedAtEpoch)"
recorded_schema="$(value_of schemaVersion)"
recorded_plugin="$(value_of pluginVersion)"
recorded_source="$(value_of refreshedBy)"
recorded_ref="$(value_of refreshedOnRef)"

if [[ ! "${refreshed_at}" =~ ^[0-9]+$ ]]; then
  echo "Sentinel at ${sentinel} carries no usable refreshedAtEpoch: the age of this database cannot be established." >&2
  emit_and_exit MALFORMED
fi

if [[ "${recorded_schema}" != "${expected_schema}" ]]; then
  echo "Sentinel at ${sentinel} records H2 schema ${recorded_schema:-<none>}, but this analyzer expects ${expected_schema}." >&2
  emit_and_exit SCHEMA_MISMATCH \
    "recordedSchemaVersion=${recorded_schema}" \
    "expectedSchemaVersion=${expected_schema}" \
    "pluginVersion=${recorded_plugin}"
fi

age_seconds="$(( $(date +%s) - refreshed_at ))"

echo "Trusted refresh recorded ${age_seconds}s ago by Dependency-Check ${recorded_plugin:-unknown} on ${recorded_ref:-unknown} (schema ${recorded_schema}, via ${recorded_source:-unknown})." >&2

emit_and_exit OK \
  "ageSeconds=${age_seconds}" \
  "refreshedAtEpoch=${refreshed_at}" \
  "recordedSchemaVersion=${recorded_schema}" \
  "expectedSchemaVersion=${expected_schema}" \
  "pluginVersion=${recorded_plugin}" \
  "refreshedBy=${recorded_source}" \
  "refreshedOnRef=${recorded_ref}"
