#!/usr/bin/env bash
#
# Records that a trusted Dependency-Check refresh actually happened, as a fact the refresh produces.
#
# Usage: write-dependency-check-sentinel.sh <data-directory> <plugin-version> <schema-version> <source-label>
#
# Freshness used to be inferred from the mtime of a database file, and that inference was wrong twice over.
# The file was picked with `find ... -print -quit`, which returns whatever the directory walk reaches first
# rather than the database or the newest file; and actions/cache restores through tar, which preserves
# mtimes, so even the right file reports when Dependency-Check last rewrote it rather than when the feed was
# last refreshed. An incremental update-only that touches nothing leaves an old mtime behind a genuinely
# current database. The 54h age that the first freshness report published was that artefact, not an age.
#
# A sentinel replaces an inference with a record. It is written only on the path that really refreshed the
# feed, it travels inside the cached directory, and it carries what a later reader needs to judge it:
#
#   refreshedAtEpoch  when the refresh completed, from the machine that performed it
#   schemaVersion     the H2 schema the writing analyzer used -- fatal to a reader that expects another
#   pluginVersion     which analyzer wrote it -- reported, never fatal, see read-dependency-check-sentinel.sh
#   refreshedBy       how it was obtained, so a summary can name the path without re-deriving it
#
# This script writes; it never decides. The refusal lives in .github/workflows/security.yml.

set -euo pipefail

data_dir="${1:?the Dependency-Check data directory is required}"
plugin_version="${2:?the Dependency-Check plugin version that performed the refresh is required}"
schema_version="${3:?the Dependency-Check H2 schema version is required}"
source_label="${4:?a source label describing how the refresh was obtained is required}"

mkdir -p -- "${data_dir}"

sentinel="${data_dir}/dependency-check-refresh.sentinel"
tmp="${sentinel}.tmp"

{
  echo "# MORPHEUS trusted Dependency-Check refresh sentinel."
  echo "# Written only when a refresh succeeded; its absence means the age of this database is unknown."
  echo "refreshedAtEpoch=$(date +%s)"
  echo "refreshedAtIso=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "schemaVersion=${schema_version}"
  echo "pluginVersion=${plugin_version}"
  echo "refreshedBy=${source_label}"
  echo "refreshedOnRef=${GITHUB_REF_NAME:-unknown}"
  echo "refreshedByRun=${GITHUB_RUN_ID:-unknown}"
} > "${tmp}"

mv -f -- "${tmp}" "${sentinel}"

echo "Wrote trusted Dependency-Check refresh sentinel (schema ${schema_version}, plugin ${plugin_version}, via ${source_label}) to ${sentinel}."
