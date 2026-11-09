#!/usr/bin/env bash
# Checks that the packaged MORPHEUS starts, initialises and closes a real MINOS MCP server.
#
#   distribution/test-minos-compatibility.sh <minos-jar> [minos-java] [morpheus-launcher] [minos-home]
#
# An empty minos-java runs MINOS on the runtime of the MORPHEUS under test, which is what an installation does by
# default; otherwise it is the absolute path of a Java launcher, since MORPHEUS refuses a bare command or a relative path.
set -euo pipefail

if [[ $# -lt 1 || -z "$1" ]]; then
  echo "usage: $0 <minos-jar> [minos-java] [morpheus-launcher] [minos-home]" >&2
  exit 2
fi
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
MINOS_JAR="$(realpath -e "$1")"
MINOS_JAVA="${2:-}"
LAUNCHER="${3:-dist/.m20-linux/image/morpheus/bin/morpheus}"
MINOS_HOME="${4:-}"
if [[ "$LAUNCHER" != /* ]]; then
  LAUNCHER="$REPO/$LAUNCHER"
fi
LAUNCHER="$(realpath -e "$LAUNCHER")"

export MORPHEUS_MINOS_JAR="$MINOS_JAR"
# MORPHEUS executes a peer JAR only when it is pinned; the pin of a smoke is the digest of the JAR it was given.
MORPHEUS_MINOS_JAR_SHA256="$(sha256sum "$MINOS_JAR" | cut -d' ' -f1)"
export MORPHEUS_MINOS_JAR_SHA256
if [[ -z "$MINOS_JAVA" ]]; then
  unset MORPHEUS_MINOS_JAVA
  LAUNCHER_DESCRIPTION="(the runtime of the MORPHEUS under test)"
else
  MORPHEUS_MINOS_JAVA="$(realpath -e "$MINOS_JAVA")"
  export MORPHEUS_MINOS_JAVA
  LAUNCHER_DESCRIPTION="$MORPHEUS_MINOS_JAVA"
fi
if [[ -z "$MINOS_HOME" ]]; then
  unset MORPHEUS_MINOS_HOME
else
  MORPHEUS_MINOS_HOME="$(realpath -e "$MINOS_HOME")"
  export MORPHEUS_MINOS_HOME
fi

echo "Checking MORPHEUS <-> real MINOS MCP compatibility..."
echo "MORPHEUS launcher: $LAUNCHER"
echo "MINOS JAR:        $MINOS_JAR"
echo "MINOS Java:       $LAUNCHER_DESCRIPTION"

if ! STATUS="$("$LAUNCHER" --json minos-status)"; then
  echo "MORPHEUS minos-status failed: $STATUS" >&2
  exit 1
fi
echo "$STATUS"
if [[ "$STATUS" != *'"system":"MINOS"'* ]]; then
  echo "MINOS compatibility smoke did not identify system MINOS: $STATUS" >&2
  exit 1
fi
if [[ "$STATUS" != *'"state":"AVAILABLE"'* ]]; then
  echo "MINOS compatibility smoke expected AVAILABLE: $STATUS" >&2
  exit 1
fi

echo "Real MINOS MCP compatibility smoke: PASS"
