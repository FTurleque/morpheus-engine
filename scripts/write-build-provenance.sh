#!/usr/bin/env bash
set -euo pipefail

OUTPUT_PATH="${1:-target/m21-supply-chain/build-provenance.properties}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

VERSION="$(sed -n 's:.*<version>\([^<]*\)</version>.*:\1:p' pom.xml | head -n 1 | xargs)"
if [[ -z "$VERSION" ]]; then
  echo "Cannot resolve product version from pom.xml" >&2
  exit 1
fi

GIT_SHA="$(git rev-parse HEAD)"
GIT_REF="$(git describe --tags --exact-match HEAD 2>/dev/null || true)"
if [[ -z "$GIT_REF" ]]; then
  GIT_REF="${GITHUB_REF_NAME:-$(git branch --show-current)}"
fi
GIT_REF="${GIT_REF:-detached}"
if [[ -z "$(git status --porcelain)" ]]; then WORKSPACE_CLEAN=true; else WORKSPACE_CLEAN=false; fi
JAVA_VERSION="$(java -version 2>&1 | head -n 1 | tr '\r\n=' '   ' | xargs)"
MAVEN_VERSION="$(./mvnw -v | head -n 1 | tr '\r\n=' '   ' | xargs)"
SBOM="target/m21-supply-chain/morpheus-sbom.json"
if [[ -f "$SBOM" ]]; then
  SBOM_SHA256="$(sha256sum "$SBOM" | awk '{print $1}')"
else
  SBOM_SHA256=missing
fi
GENERATED_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
OS_VALUE="$(uname -a | tr '\r\n=' '   ' | xargs)"

mkdir -p "$(dirname "$OUTPUT_PATH")"
cat > "$OUTPUT_PATH" <<EOF
schema=morpheus-build-provenance-v1
product=MORPHEUS
version=$VERSION
gitSha=$GIT_SHA
gitRef=$GIT_REF
workspaceClean=$WORKSPACE_CLEAN
os=$OS_VALUE
java=$JAVA_VERSION
maven=$MAVEN_VERSION
sbomSha256=$SBOM_SHA256
generatedAt=$GENERATED_AT
EOF

echo "M21 provenance written to $OUTPUT_PATH"
