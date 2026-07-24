#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-0.1.0}"
OUTPUT_DIRECTORY="${2:-dist}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST="$REPO/$OUTPUT_DIRECTORY"
WORK="$DIST/.m11-linux"
INPUT="$WORK/input"
IMAGE_ROOT="$WORK/image"

JPACKAGE="${JAVA_HOME:-}/bin/jpackage"
JAR_TOOL="${JAVA_HOME:-}/bin/jar"
if [[ -z "${JAVA_HOME:-}" || ! -x "$JPACKAGE" ]]; then
  echo "jpackage not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  exit 1
fi
if [[ ! -x "$JAR_TOOL" ]]; then
  echo "jar not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  exit 1
fi

printf '%s\n' "Building MORPHEUS CLI + MCP + API uber-JAR..."
"$REPO/mvnw" -pl morpheus-cli -am -DskipTests package

JAR="$(find "$REPO/morpheus-cli/target" -maxdepth 1 -type f -name 'morpheus-cli-*-all.jar' -print | sort | tail -n 1)"
if [[ -z "$JAR" ]]; then
  echo "Shaded MORPHEUS CLI JAR not found" >&2
  exit 1
fi

printf '%s\n' "Verifying MCP/API classes are embedded in the shaded JAR..."
JAR_ENTRIES="$($JAR_TOOL tf "$JAR")"
for entry in \
  'com/morpheus/mcp/MorpheusMcpServer.class' \
  'io/modelcontextprotocol/server/McpServer.class' \
  'io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class' \
  'com/morpheus/api/MorpheusHttpServer.class' \
  'com/morpheus/api/MorpheusApiService.class' \
  'tools/jackson/databind/json/JsonMapper.class'; do
  if ! grep -Fxq "$entry" <<<"$JAR_ENTRIES"; then
    echo "M11 packaging proof failed; shaded JAR is missing $entry" >&2
    exit 1
  fi
done
printf '%s\n' "MCP/API packaging proof: PASS"

rm -rf "$WORK"
mkdir -p "$INPUT" "$IMAGE_ROOT" "$DIST"
cp "$JAR" "$INPUT/morpheus.jar"

printf '%s\n' "Creating self-contained Linux app-image with embedded runtime + jdk.httpserver..."
"$JPACKAGE" \
  --type app-image \
  --name morpheus \
  --app-version "$VERSION" \
  --description "MORPHEUS Specification & Intent Intelligence Engine" \
  --input "$INPUT" \
  --main-jar morpheus.jar \
  --main-class com.morpheus.cli.MorpheusMain \
  --add-modules jdk.httpserver \
  --dest "$IMAGE_ROOT"

LAUNCHER="$IMAGE_ROOT/morpheus/bin/morpheus"
if [[ ! -x "$LAUNCHER" ]]; then
  echo "Packaged launcher not found: $LAUNCHER" >&2
  exit 1
fi

printf '%s\n' "Smoke testing packaged launcher..."
"$LAUNCHER" --version
JSON_VERSION="$("$LAUNCHER" --json version)"
if [[ "$JSON_VERSION" != *'"version"'* ]]; then
  echo "Packaged launcher --json version did not emit the expected JSON version field: $JSON_VERSION" >&2
  exit 1
fi
printf '%s\n' "$JSON_VERSION"

# Verify that the embedded runtime contains the module required by the M11 HTTP adapter.
if ! "$IMAGE_ROOT/morpheus/lib/runtime/bin/java" --list-modules | grep -Fxq 'jdk.httpserver@21'; then
  if ! "$IMAGE_ROOT/morpheus/lib/runtime/bin/java" --list-modules | grep -Eq '^jdk\.httpserver@'; then
    echo "Packaged runtime does not contain jdk.httpserver" >&2
    exit 1
  fi
fi
printf '%s\n' "Packaged jdk.httpserver module proof: PASS"

ARCHIVE="$DIST/morpheus-$VERSION-linux-x64.tar.gz"
rm -f "$ARCHIVE"
tar -C "$IMAGE_ROOT" -czf "$ARCHIVE" morpheus

printf '%s\n' "Portable Linux distribution: $ARCHIVE"
printf '%s\n' "The archive contains its Java runtime, MCP STDIO server and HTTP API; end users do not need a separately installed JDK."
