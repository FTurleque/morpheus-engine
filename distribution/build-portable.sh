#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-0.1.0}"
OUTPUT_DIRECTORY="${2:-dist}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST="$REPO/$OUTPUT_DIRECTORY"
WORK="$DIST/.m9-linux"
INPUT="$WORK/input"
IMAGE_ROOT="$WORK/image"

JPACKAGE="${JAVA_HOME:-}/bin/jpackage"
if [[ -z "${JAVA_HOME:-}" || ! -x "$JPACKAGE" ]]; then
  echo "jpackage not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  exit 1
fi

printf '%s\n' "Building MORPHEUS CLI uber-JAR..."
"$REPO/mvnw" -pl morpheus-cli -am -DskipTests package

JAR="$(find "$REPO/morpheus-cli/target" -maxdepth 1 -type f -name 'morpheus-cli-*-all.jar' -print | sort | tail -n 1)"
if [[ -z "$JAR" ]]; then
  echo "Shaded MORPHEUS CLI JAR not found" >&2
  exit 1
fi

rm -rf "$WORK"
mkdir -p "$INPUT" "$IMAGE_ROOT" "$DIST"
cp "$JAR" "$INPUT/morpheus.jar"

printf '%s\n' "Creating self-contained Linux app-image with embedded runtime..."
"$JPACKAGE" \
  --type app-image \
  --name morpheus \
  --app-version "$VERSION" \
  --description "MORPHEUS Specification & Intent Intelligence Engine" \
  --input "$INPUT" \
  --main-jar morpheus.jar \
  --main-class com.morpheus.cli.MorpheusMain \
  --dest "$IMAGE_ROOT"

LAUNCHER="$IMAGE_ROOT/morpheus/bin/morpheus"
if [[ ! -x "$LAUNCHER" ]]; then
  echo "Packaged launcher not found: $LAUNCHER" >&2
  exit 1
fi

printf '%s\n' "Smoke testing packaged launcher..."
"$LAUNCHER" --version

ARCHIVE="$DIST/morpheus-$VERSION-linux-x64.tar.gz"
rm -f "$ARCHIVE"
tar -C "$IMAGE_ROOT" -czf "$ARCHIVE" morpheus

printf '%s\n' "Portable Linux distribution: $ARCHIVE"
printf '%s\n' "The archive contains its Java runtime; end users do not need a separately installed JDK."