#!/usr/bin/env bash
# Checks that the packaged MORPHEUS starts, initialises and closes a real NEXUS MCP server.
#
#   distribution/test-nexus-compatibility.sh <nexus-runner-jar> [nexus-java] [morpheus-launcher] [nexus-home]
#
# An empty nexus-java runs NEXUS on the runtime of the MORPHEUS under test, which is what an installation does by
# default; otherwise it is the absolute path of a Java launcher, since MORPHEUS refuses a bare command or a relative path.
set -euo pipefail

if [[ $# -lt 1 || -z "$1" ]]; then
  echo "usage: $0 <nexus-runner-jar> [nexus-java] [morpheus-launcher] [nexus-home]" >&2
  exit 2
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
NEXUS_JAR="$(realpath -e "$1")"
NEXUS_JAVA="${2:-}"
LAUNCHER="${3:-dist/.m20-linux/image/morpheus/bin/morpheus}"
NEXUS_HOME="${4:-}"
if [[ "$LAUNCHER" != /* ]]; then
  LAUNCHER="$REPO/$LAUNCHER"
fi
LAUNCHER="$(realpath -e "$LAUNCHER")"

export MORPHEUS_NEXUS_JAR="$NEXUS_JAR"
# MORPHEUS executes a peer JAR only when it is pinned; the pin of a smoke is the digest of the JAR it was given.
MORPHEUS_NEXUS_JAR_SHA256="$(sha256sum "$NEXUS_JAR" | cut -d' ' -f1)"
export MORPHEUS_NEXUS_JAR_SHA256
if [[ -z "$NEXUS_JAVA" ]]; then
  unset MORPHEUS_NEXUS_JAVA
  LAUNCHER_DESCRIPTION="(the runtime of the MORPHEUS under test)"
else
  MORPHEUS_NEXUS_JAVA="$(realpath -e "$NEXUS_JAVA")"
  export MORPHEUS_NEXUS_JAVA
  LAUNCHER_DESCRIPTION="$MORPHEUS_NEXUS_JAVA"
fi
if [[ -z "$NEXUS_HOME" ]]; then
  unset MORPHEUS_NEXUS_HOME
else
  MORPHEUS_NEXUS_HOME="$(realpath -e "$NEXUS_HOME")"
  export MORPHEUS_NEXUS_HOME
fi

echo "Checking MORPHEUS <-> real NEXUS MCP compatibility..."
echo "MORPHEUS launcher: $LAUNCHER"
echo "NEXUS JAR:        $NEXUS_JAR"
echo "NEXUS Java:       $LAUNCHER_DESCRIPTION"

if ! STATUS="$("$LAUNCHER" --json nexus-status)"; then
  echo "MORPHEUS nexus-status failed: $STATUS" >&2
  exit 1
fi
echo "$STATUS"
if [[ "$STATUS" != *'"system":"NEXUS"'* ]]; then
  echo "NEXUS compatibility smoke did not identify system NEXUS: $STATUS" >&2
  exit 1
fi
if [[ "$STATUS" != *'"state":"AVAILABLE"'* ]]; then
  echo "NEXUS compatibility smoke expected AVAILABLE: $STATUS" >&2
  exit 1
fi

echo "Real NEXUS MCP compatibility smoke: PASS"
