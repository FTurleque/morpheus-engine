#!/usr/bin/env bash
# Refuses M21 coverage evidence that was not produced on the aggregate scale.
#
# Two gates measure coverage and they measure different grandeurs: CoverageQualityGateTest sums each module's
# own JaCoCo report, AggregateCoverageGateTest reads the canonical jacoco-aggregate report. Until 09/09/2026
# both wrote one file under one name, so whichever ran last in a given reactor invocation left its own ratio
# behind and every validator compared it to the same threshold under the same label -- with no way to tell
# which scale it had just read. Each gate now writes its own file, declares its scale on the first line, and
# a consumer that wants one scale must refuse the other rather than conclude from whatever it found.
set -euo pipefail

EVIDENCE="${1:?usage: require-aggregate-coverage-evidence.sh <evidence-file>}"

if [[ ! -f "$EVIDENCE" ]]; then
  echo "Missing M21 aggregate coverage evidence: $EVIDENCE" >&2
  exit 1
fi

SCOPE="$(sed -n 's/^coverageScope=//p' "$EVIDENCE" | head -n 1 | tr -d '\r')"
if [[ "$SCOPE" != aggregate ]]; then
  echo "M21 coverage evidence is not the aggregate scope: $EVIDENCE declares '${SCOPE:-<none>}'" >&2
  exit 1
fi
