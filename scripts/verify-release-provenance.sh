#!/usr/bin/env bash
# Verifies a PUBLISHED MORPHEUS GitHub release end to end.
#
# This script proves nothing about a release that does not exist: it reads what GitHub actually published
# and fails closed on anything it cannot confirm. It never creates a tag, a release or an asset, so running
# it can never be mistaken for having qualified one.
#
# Usage: scripts/verify-release-provenance.sh vX.Y.Z [work-directory]
set -euo pipefail

TAG="${1:-}"
WORKDIR="${2:-}"

if [[ -z "$TAG" ]]; then
  echo "usage: $0 vX.Y.Z [work-directory]" >&2
  exit 2
fi
if [[ ! "$TAG" =~ ^v([0-9]+\.[0-9]+\.[0-9]+)$ ]]; then
  echo "Release tag must use semantic version form vX.Y.Z: $TAG" >&2
  exit 1
fi
VERSION="${BASH_REMATCH[1]}"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=scripts/lib/python.sh
source "$REPO_ROOT/scripts/lib/python.sh"

for tool in gh git sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || { echo "required tool not found: $tool" >&2; exit 1; }
done

if [[ -z "$WORKDIR" ]]; then
  WORKDIR="$(mktemp -d)"
  trap 'rm -rf -- "$WORKDIR"' EXIT
fi
mkdir -p -- "$WORKDIR"

fail() { echo "RELEASE QUALIFICATION FAIL: $*" >&2; exit 1; }
pass() { printf '%s: PASS\n' "$1"; }

echo "MORPHEUS release qualification"
echo "Tag:     $TAG"
echo "Version: $VERSION"
echo "Workdir: $WORKDIR"
echo

# ---------------------------------------------------------------------------
# 1. The tag resolves to one commit, and that commit is reachable from main.
# ---------------------------------------------------------------------------
git -C "$REPO_ROOT" fetch --no-tags origin main >/dev/null 2>&1 || fail "cannot fetch origin/main"
git -C "$REPO_ROOT" fetch origin "refs/tags/$TAG:refs/tags/$TAG" >/dev/null 2>&1 \
  || fail "tag $TAG does not exist on origin"
TAG_SHA="$(git -C "$REPO_ROOT" rev-list -n 1 "$TAG")"
git -C "$REPO_ROOT" merge-base --is-ancestor "$TAG_SHA" origin/main \
  || fail "tag $TAG points at $TAG_SHA which is not reachable from main"
pass "tag reachable from main ($TAG_SHA)"

# ---------------------------------------------------------------------------
# 2. The published release carries exactly the expected asset set.
# ---------------------------------------------------------------------------
EXPECTED_ASSETS=(
  "morpheus-$VERSION-linux-x64.tar.gz"
  "morpheus-$VERSION-linux-x64.tar.gz.sha256"
  "morpheus-$VERSION-linux-x64-release-manifest.json"
  "morpheus-$VERSION-linux-x64.attestation.jsonl"
  "morpheus-$VERSION-sbom.cdx.json"
  "morpheus-$VERSION-sbom.cdx.json.sha256"
  "morpheus-$VERSION-windows-x64.zip"
  "morpheus-$VERSION-windows-x64.zip.sha256"
  "MORPHEUS-$VERSION-windows-x64-setup.exe"
  "MORPHEUS-$VERSION-windows-x64-setup.exe.sha256"
  "morpheus-$VERSION-windows-x64-release-manifest.json"
  "morpheus-$VERSION-windows-x64.attestation.jsonl"
)

gh release view "$TAG" --json tagName,targetCommitish,assets > "$WORKDIR/release.json" \
  || fail "GitHub release $TAG is not published"

morpheus_python - "$WORKDIR/release.json" "$TAG" "${EXPECTED_ASSETS[@]}" <<'PY'
import json
import sys
from pathlib import Path

payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
tag = sys.argv[2]
expected = set(sys.argv[3:])

if payload.get("tagName") != tag:
    raise SystemExit(f"release tagName {payload.get('tagName')!r} does not match {tag!r}")

published = {asset["name"] for asset in payload.get("assets", [])}
missing = sorted(expected - published)
unexpected = sorted(published - expected)
if missing:
    raise SystemExit(f"release is missing expected assets: {missing}")
if unexpected:
    raise SystemExit(f"release publishes unexpected assets: {unexpected}")
print(f"assets: {len(published)} published, all expected")
PY
pass "published asset set"

# ---------------------------------------------------------------------------
# 3. Every checksum published beside an artifact matches that artifact.
# ---------------------------------------------------------------------------
gh release download "$TAG" --dir "$WORKDIR/assets" --clobber >/dev/null \
  || fail "cannot download release assets"

CHECKED=0
for checksum in "$WORKDIR/assets"/*.sha256; do
  artifact="${checksum%.sha256}"
  [[ -s "$artifact" ]] || fail "checksum $checksum has no artifact beside it"
  expected_digest="$(tr -d '\r' < "$checksum" | awk '{print $1}')"
  actual_digest="$(sha256sum "$artifact" | awk '{print $1}')"
  [[ "$expected_digest" == "$actual_digest" ]] \
    || fail "checksum mismatch for $(basename "$artifact"): published=$expected_digest actual=$actual_digest"
  CHECKED=$((CHECKED + 1))
done
[[ "$CHECKED" -ge 4 ]] || fail "expected at least four published checksums, verified $CHECKED"
pass "published checksums ($CHECKED verified)"

# ---------------------------------------------------------------------------
# 4. Each release manifest agrees with the tag, the version and the tagged commit.
# ---------------------------------------------------------------------------
morpheus_python - "$WORKDIR/assets" "$VERSION" "$TAG" "$TAG_SHA" <<'PY'
import json
import sys
from pathlib import Path

assets, version, tag, sha = Path(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]
manifests = sorted(assets.glob("morpheus-*-release-manifest.json"))
if len(manifests) != 2:
    raise SystemExit(f"expected one release manifest per platform, found {len(manifests)}")

platforms = set()
for manifest in manifests:
    payload = json.loads(manifest.read_text(encoding="utf-8"))
    for field, expected in (("version", version), ("tag", tag), ("gitSha", sha), ("product", "MORPHEUS")):
        if payload.get(field) != expected:
            raise SystemExit(f"{manifest.name}: {field}={payload.get(field)!r} expected {expected!r}")
    platforms.add(payload.get("platform"))
    for asset in payload.get("assets", []):
        artifact = assets / asset["name"]
        if not artifact.is_file():
            raise SystemExit(f"{manifest.name} names an asset that was not published: {asset['name']}")
        if artifact.stat().st_size != asset["bytes"]:
            raise SystemExit(f"{manifest.name}: {asset['name']} size does not match the manifest")

if platforms != {"linux-x64", "windows-x64"}:
    raise SystemExit(f"release manifests must cover both platforms, found {sorted(platforms)}")
print(f"manifests: {sorted(platforms)} agree on version, tag and commit")
PY
pass "release manifests"

# ---------------------------------------------------------------------------
# 5. Provenance is publicly verifiable for every distributed artifact.
# ---------------------------------------------------------------------------
REPO_SLUG="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
ATTESTED=0
for artifact in \
  "$WORKDIR/assets/morpheus-$VERSION-linux-x64.tar.gz" \
  "$WORKDIR/assets/morpheus-$VERSION-sbom.cdx.json" \
  "$WORKDIR/assets/morpheus-$VERSION-windows-x64.zip" \
  "$WORKDIR/assets/MORPHEUS-$VERSION-windows-x64-setup.exe"; do
  gh attestation verify "$artifact" --repo "$REPO_SLUG" >/dev/null \
    || fail "provenance attestation did not verify for $(basename "$artifact")"
  ATTESTED=$((ATTESTED + 1))
done
[[ "$ATTESTED" -eq 4 ]] || fail "expected four attested artifacts, verified $ATTESTED"
pass "public provenance ($ATTESTED artifacts)"

# ---------------------------------------------------------------------------
# 6. The preserved attestation bundles are present and non-empty.
# ---------------------------------------------------------------------------
for bundle in \
  "$WORKDIR/assets/morpheus-$VERSION-linux-x64.attestation.jsonl" \
  "$WORKDIR/assets/morpheus-$VERSION-windows-x64.attestation.jsonl"; do
  [[ -s "$bundle" ]] || fail "attestation bundle is empty: $(basename "$bundle")"
done
pass "preserved attestation bundles"

# ---------------------------------------------------------------------------
# 7. The published inventory describes this release, and covers what each archive attributes.
# ---------------------------------------------------------------------------
morpheus_python - "$WORKDIR/assets" "$VERSION" <<'PY'
import json
import re
import sys
import tarfile
import zipfile
from pathlib import Path

assets, version = Path(sys.argv[1]), sys.argv[2]
sbom = json.loads((assets / f"morpheus-{version}-sbom.cdx.json").read_text(encoding="utf-8"))
described = sbom.get("metadata", {}).get("component", {}).get("version")
if described != version:
    raise SystemExit(f"published SBOM describes version {described!r}, expected {version!r}")
inventory = {f"{c.get('group')}:{c.get('name')} {c.get('version')}" for c in sbom.get("components", [])}

with tarfile.open(assets / f"morpheus-{version}-linux-x64.tar.gz") as archive:
    member = archive.extractfile("morpheus/THIRD-PARTY-NOTICES")
    if member is None:
        raise SystemExit("the Linux archive carries no THIRD-PARTY-NOTICES")
    notices = {"linux": member.read().decode("utf-8")}
with zipfile.ZipFile(assets / f"morpheus-{version}-windows-x64.zip") as archive:
    names = [name for name in archive.namelist() if re.fullmatch(r"morpheus[\\/]THIRD-PARTY-NOTICES", name)]
    if not names:
        raise SystemExit("the Windows archive carries no THIRD-PARTY-NOTICES")
    notices["windows"] = archive.read(names[0]).decode("utf-8")

for platform, text in notices.items():
    attributed = re.findall(r"^  (\S+:\S+ \S+) \(", text, re.MULTILINE)
    if not attributed:
        raise SystemExit(f"the {platform} archive attributes no third-party component")
    absent = sorted(set(attributed) - inventory)
    if absent:
        raise SystemExit(f"the {platform} archive attributes components the published SBOM does not list: {absent}")
print(f"inventory: {len(inventory)} components, both archives covered")
PY
pass "published inventory"

# ---------------------------------------------------------------------------
# 8. The Authenticode signature of the Windows binaries. Windows' own trust decision is the authority, and this
#    variant has no tool it can rely on to reproduce it, so it does not judge the signature: it reads the same
#    declaration as the Windows variant, requires the Windows manifest to state each binary's signature, and says
#    by name that the signature itself was not verified here. Its verdict is therefore not the full pass sentence.
# ---------------------------------------------------------------------------
morpheus_python - "$REPO_ROOT/distribution/windows/authenticode-signing.properties" \
  "$WORKDIR/assets/morpheus-$VERSION-windows-x64-release-manifest.json" "$VERSION" <<'PY'
import json
import sys
from pathlib import Path

declaration_path, manifest_path, version = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
if not declaration_path.is_file():
    raise SystemExit(f"AUTHENTICODE_PUBLISHER_NOT_DECLARED: the signing declaration is missing: {declaration_path}")
declared = {}
for line in declaration_path.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    key, separator, value = line.partition("=")
    if not separator or key.strip() not in ("publisherSubject", "signingBaseline", "timestampAuthority"):
        raise SystemExit(f"the signing declaration holds an unexpected line: {line}")
    if key.strip() in declared:
        raise SystemExit(f"the signing declaration states {key.strip()} twice")
    declared[key.strip()] = value.strip()
missing = sorted({"publisherSubject", "signingBaseline", "timestampAuthority"} - declared.keys())
if missing:
    raise SystemExit(f"the signing declaration does not state {missing}")

manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
for name in (f"MORPHEUS-{version}-windows-x64-setup.exe", f"morpheus-{version}-windows-x64.zip"):
    entries = [asset for asset in manifest.get("assets", []) if asset.get("name") == name]
    if len(entries) != 1:
        raise SystemExit(f"the Windows manifest does not describe {name} once")
    statement = entries[0].get("signature")
    if not isinstance(statement, dict) or statement.get("status") not in ("signed", "unsigned"):
        raise SystemExit(f"the Windows manifest states no signature status for {name}")
    print(f"  {name}: the manifest states {statement['status']} {statement.get('signer', '')}".rstrip())
baseline = declared["signingBaseline"] or "none declared"
print(f"  signing baseline: {baseline}")
PY
echo "AUTHENTICODE_NOT_VERIFIED_ON_THIS_PLATFORM: the Authenticode signature of the Windows binaries was not verified" \
  "by this variant; only scripts\\verify-release-provenance.ps1, run on Windows, verifies it"

echo
echo "RELEASE QUALIFICATION INCOMPLETE: $TAG @ $TAG_SHA -- every check of this variant passed, the Authenticode" \
  "signature was not verified on this platform"
echo "Record this output with the Windows variant's in docs/project/validation/VALIDATION_RELEASE_<version>.md before closing issue #185."
