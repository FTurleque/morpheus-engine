#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.0.0}"
OUTPUT_DIRECTORY="${2:-dist}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST="$REPO/$OUTPUT_DIRECTORY"
WORK="$DIST/.m20-linux"
INPUT="$WORK/input"
IMAGE_ROOT="$WORK/image"

JPACKAGE="${JAVA_HOME:-}/bin/jpackage"
JAR_TOOL="${JAVA_HOME:-}/bin/jar"
JIMAGE_TOOL="${JAVA_HOME:-}/bin/jimage"
if [[ -z "${JAVA_HOME:-}" || ! -x "$JPACKAGE" ]]; then
  echo "jpackage not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  exit 1
fi
if [[ ! -x "$JAR_TOOL" ]]; then
  echo "jar not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  exit 1
fi
if [[ ! -x "$JIMAGE_TOOL" ]]; then
  echo "jimage not found under JAVA_HOME=${JAVA_HOME:-<unset>}" >&2
  exit 1
fi

printf '%s\n' "Building MORPHEUS 1.0 CLI + MCP + API + provider SDK + optional MINOS/NEXUS adapters + M14-M22 contracts uber-JAR..."
"$REPO/mvnw" -pl morpheus-cli -am -DskipTests package

JAR="$(find "$REPO/morpheus-cli/target" -maxdepth 1 -type f -name 'morpheus-cli-*-all.jar' -print | sort | tail -n 1)"
if [[ -z "$JAR" ]]; then
  echo "Shaded MORPHEUS CLI JAR not found" >&2
  exit 1
fi

printf '%s\n' "Verifying MCP/API/provider-SDK/MINOS/NEXUS/M14-M22 classes are embedded in the shaded JAR..."
JAR_ENTRIES="$($JAR_TOOL tf "$JAR")"
for entry in \
  'com/morpheus/mcp/MorpheusMcpServer.class' \
  'com/morpheus/mcp/MorpheusJarvisOrchestrationMcpTools.class' \
  'com/morpheus/mcp/MorpheusProductMcpTools.class' \
  'com/morpheus/mcp/MorpheusProviderPluginMcpTools.class' \
  'io/modelcontextprotocol/server/McpServer.class' \
  'io/modelcontextprotocol/client/McpClient.class' \
  'io/modelcontextprotocol/client/transport/StdioClientTransport.class' \
  'com/morpheus/api/MorpheusHttpServer.class' \
  'com/morpheus/api/MorpheusOperabilityApiService.class' \
  'com/morpheus/api/MorpheusJarvisOrchestrationApiService.class' \
  'com/morpheus/api/MorpheusProviderPluginApiService.class' \
  'com/morpheus/cli/MorpheusJarvisOrchestrationCli.class' \
  'com/morpheus/cli/MorpheusProductCli.class' \
  'com/morpheus/cli/MorpheusProviderPluginCli.class' \
  'com/morpheus/sdk/provider/MorpheusProviderPlugin.class' \
  'com/morpheus/sdk/provider/ProviderPluginService.class' \
  'com/morpheus/application/product/ProductMetadata.class' \
  'com/morpheus/application/product/UpdateDiscoveryService.class' \
  'com/morpheus/application/orchestration/ChangeOrchestrationStateService.class' \
  'com/morpheus/application/orchestration/ChangeTransitionEvaluationService.class' \
  'com/morpheus/integration/minos/MinosMcpExternalReferenceResolver.class' \
  'com/morpheus/integration/minos/MinosMcpCodeGateway.class' \
  'com/morpheus/integration/minos/MinosIntegrationRuntime.class' \
  'com/morpheus/integration/nexus/NexusMcpContextGateway.class' \
  'com/morpheus/integration/nexus/NexusMcpTechnicalContextProvider.class' \
  'com/morpheus/integration/nexus/NexusIntegrationRuntime.class' \
  'tools/jackson/databind/json/JsonMapper.class'; do
  if ! grep -Fxq "$entry" <<<"$JAR_ENTRIES"; then
    echo "M22 packaging proof failed; shaded JAR is missing $entry" >&2
    exit 1
  fi
done
if grep -Eq '^com/minos/' <<<"$JAR_ENTRIES"; then
  echo "M22 packaging proof failed; MINOS implementation classes must not be embedded" >&2
  exit 1
fi
if grep -Eq '^com/nexus/' <<<"$JAR_ENTRIES"; then
  echo "M22 packaging proof failed; NEXUS implementation classes must not be embedded" >&2
  exit 1
fi
if grep -Eq '^com/jarvis/' <<<"$JAR_ENTRIES"; then
  echo "M22 packaging proof failed; JARVIS implementation classes must not be embedded" >&2
  exit 1
fi
if grep -Eq '^com/morpheus/provider/reference/' <<<"$JAR_ENTRIES"; then
  echo "M22 packaging proof failed; the reference provider plugin must remain external to the MORPHEUS launcher" >&2
  exit 1
fi
printf '%s\n' "MCP/API/provider-SDK/MINOS/NEXUS/M14-M22 packaging proof: PASS"

rm -rf "$WORK"
mkdir -p "$INPUT" "$IMAGE_ROOT" "$DIST"
cp "$JAR" "$INPUT/morpheus.jar"

printf '%s\n' "Creating self-contained Linux app-image with embedded runtime + jdk.httpserver + java.sql + java.net.http..."
"$JPACKAGE" \
  --type app-image \
  --name morpheus \
  --app-version "$VERSION" \
  --description "MORPHEUS Specification & Intent Intelligence Engine" \
  --input "$INPUT" \
  --main-jar morpheus.jar \
  --main-class com.morpheus.cli.MorpheusMain \
  --add-modules jdk.httpserver,java.sql,java.net.http \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --dest "$IMAGE_ROOT"

LAUNCHER="$IMAGE_ROOT/morpheus/bin/morpheus"
if [[ ! -x "$LAUNCHER" ]]; then
  echo "Packaged launcher not found: $LAUNCHER" >&2
  exit 1
fi

printf '%s\n' "Smoke testing packaged launcher without MINOS/NEXUS/JARVIS or external provider-plugin configuration..."
"$LAUNCHER" --version
JSON_VERSION="$("$LAUNCHER" --json version)"
python3 - "$JSON_VERSION" "$VERSION" <<'PY'
import json
import sys
payload = json.loads(sys.argv[1])
expected = sys.argv[2]
if payload.get("version") != expected:
    raise SystemExit(f"packaged version mismatch: {payload!r}, expected={expected}")
PY
printf '%s\n' "$JSON_VERSION"
PRODUCT_INFO="$("$LAUNCHER" --json product-info)"
if [[ "$PRODUCT_INFO" != *'"updateChannel":"stable"'* ]]; then
  echo "Packaged M21 product-info smoke failed: $PRODUCT_INFO" >&2
  exit 1
fi
printf '%s\n' "$PRODUCT_INFO"
MINOS_STATUS="$("$LAUNCHER" --json minos-status)"
if [[ "$MINOS_STATUS" != *'"state":"DISABLED"'* ]]; then
  echo "Packaged standalone MINOS status smoke failed: $MINOS_STATUS" >&2
  exit 1
fi
printf '%s\n' "$MINOS_STATUS"
NEXUS_STATUS="$("$LAUNCHER" --json nexus-status)"
if [[ "$NEXUS_STATUS" != *'"state":"DISABLED"'* ]]; then
  echo "Packaged standalone NEXUS status smoke failed: $NEXUS_STATUS" >&2
  exit 1
fi
printf '%s\n' "$NEXUS_STATUS"
HELP="$($LAUNCHER help)"
if [[ "$HELP" != *'change-orchestration'* || "$HELP" != *'update-check'* || "$HELP" != *'provider-plugins'* ]]; then
  echo "Packaged M14-M22 CLI help smoke failed" >&2
  exit 1
fi
printf '%s\n' "Packaged standalone optional-engines + provider SDK + M14-M22 CLI surface smoke: PASS"

test_packaged_api_operability() (
  local launcher="$1"
  local work_directory="$2"
  local port api_data stdout stderr api_pid ready
  port="$(python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
)"
  api_data="$work_directory/api-smoke-data"
  stdout="$work_directory/api-smoke.stdout.log"
  stderr="$work_directory/api-smoke.stderr.log"
  mkdir -p "$api_data"
  "$launcher" --data-dir "$api_data" api --host 127.0.0.1 --port "$port" >"$stdout" 2>"$stderr" &
  api_pid=$!
  trap 'kill "$api_pid" >/dev/null 2>&1 || true; wait "$api_pid" >/dev/null 2>&1 || true' EXIT
  ready=false
  for _ in $(seq 1 40); do
    if ! kill -0 "$api_pid" >/dev/null 2>&1; then
      echo "Packaged API exited before operability checks" >&2
      cat "$stderr" >&2
      return 1
    fi
    if python3 - "$port" "$VERSION" 2>/dev/null <<'PY'
import json
import sys
import urllib.request

port, expected_version = sys.argv[1:]
def get(path):
    with urllib.request.urlopen(f"http://127.0.0.1:{port}{path}", timeout=0.5) as response:
        if response.status != 200:
            raise RuntimeError(response.status)
        return json.load(response)["data"]

assert get("/api/v1/health")["status"] == "UP"
assert get("/api/v1/readiness")["status"] == "READY"
assert "counters" in get("/api/v1/metrics")
assert get("/api/v1/version")["version"] == expected_version
PY
    then
      ready=true
      break
    fi
    sleep 0.25
  done
  if [[ "$ready" != true ]]; then
    echo "Packaged API operability/version smoke timed out" >&2
    cat "$stderr" >&2
    return 1
  fi
  printf '%s\n' "Packaged API health/readiness/metrics/version smoke: PASS (http://127.0.0.1:$port/api/v1)"
)

test_packaged_api_operability "$LAUNCHER" "$WORK"

PACKAGED_MODULES="$("$JIMAGE_TOOL" list "$IMAGE_ROOT/morpheus/lib/runtime/lib/modules")"
for module in jdk.httpserver java.sql java.net.http; do
  if ! grep -Fxq "Module: $module" <<<"$PACKAGED_MODULES"; then
    echo "Packaged runtime does not contain $module" >&2
    exit 1
  fi
done
printf '%s\n' "Packaged jdk.httpserver + java.sql + java.net.http module proof: PASS"

ARCHIVE="$DIST/morpheus-$VERSION-linux-x64.tar.gz"
rm -f "$ARCHIVE"
tar -C "$IMAGE_ROOT" -czf "$ARCHIVE" morpheus

printf '%s\n' "Portable Linux distribution: $ARCHIVE"
printf '%s\n' "The archive contains MORPHEUS $VERSION, its Java runtime, provider SDK, MCP/API, optional MINOS/NEXUS client adapters and M14-M22 contracts; external provider plugins, MINOS, NEXUS and JARVIS are not embedded or required."
