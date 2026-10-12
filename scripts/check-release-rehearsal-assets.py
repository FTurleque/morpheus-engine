#!/usr/bin/env python3
"""Compare what a release rehearsal built with what the release workflow requires before publishing.

The release workflow refuses to publish unless every name of the `expected=(...)` list of its publish job is present
and non-empty. That check only ever ran on a pushed tag, so a build script and the list could drift apart unseen until
the day of the release. The rehearsal runs the same build scripts; this script reads the list from the publish job of
the workflow itself -- it holds no asset name of its own -- and applies the same test to what the rehearsal produced.

A rehearsal leg does not ship its binaries to the comparison: it lists them, one `name<TAB>size` line per file, and the
comparison reads the listings. A name and a size are all the publish job looks at.

One family of names is produced only by a real release and is declared here: the attestation bundles, which exist once
the attestation step of the release has run. A rehearsal never attests.

Usage:
  check-release-rehearsal-assets.py version <pom.xml>
  check-release-rehearsal-assets.py list <directory>
  check-release-rehearsal-assets.py compare <release.yml> <pom.xml> <listing> [<listing> ...]
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

# Suffix -> why a rehearsal cannot produce it.
ONLY_A_REAL_RELEASE_PRODUCES = {
    ".attestation.jsonl": "written from the bundle of the attestation step, which a rehearsal never runs",
}

PUBLISH_JOB = "publish"
JOB_KEY = re.compile(r"^  [A-Za-z0-9_-]+:\s*$", re.MULTILINE)
EXPECTED_BLOCK = re.compile(r"expected=\(\n(.*?)\n\s*\)", re.DOTALL)
EXPECTED_ENTRY = re.compile(r'"release-assets/([^"]+)"')
POM_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"


def product_version(pom: Path) -> str:
    version = ElementTree.parse(pom).getroot().find(POM_NAMESPACE + "version")
    if version is None or not (version.text or "").strip():
        raise SystemExit(f"{pom} declares no project version")
    return version.text.strip()


def publish_job(workflow_text: str) -> str:
    """The publish job, from its key to the next job key: the list of another job is not the one that gates a release."""
    start = re.search(rf"^  {re.escape(PUBLISH_JOB)}:\s*$", workflow_text, re.MULTILINE)
    if start is None:
        raise SystemExit(f"release workflow has no {PUBLISH_JOB} job: nothing to compare against")
    following = JOB_KEY.search(workflow_text, start.end())
    return workflow_text[start.start():following.start() if following else len(workflow_text)]


def expected_names(workflow_text: str, version: str) -> list[str]:
    block = EXPECTED_BLOCK.search(publish_job(workflow_text.replace("\r\n", "\n")))
    if block is None:
        raise SystemExit("the publish job of the release workflow has no expected=( ... ) list: nothing to compare against")
    names = [name.replace("${version}", version) for name in EXPECTED_ENTRY.findall(block.group(1))]
    if not names:
        raise SystemExit("the expected=( ... ) list of the release workflow is empty: nothing to compare against")
    return names


def built_sizes(listings: list[Path]) -> dict[str, int]:
    sizes: dict[str, int] = {}
    for listing in listings:
        for line in listing.read_text(encoding="utf-8").splitlines():
            if line.strip():
                name, size = line.rstrip("\r").split("\t")
                sizes[name] = int(size)
    return sizes


def report_unbuilt(rehearsed: list[str], sizes: dict[str, int]) -> bool:
    """Annotates every expected name the rehearsal did not build, or built empty; true when there is one."""
    missing = [name for name in rehearsed if name not in sizes]
    empty = [name for name in rehearsed if sizes.get(name) == 0]
    for name in missing:
        print(f"::error::the release workflow expects {name} and the rehearsal did not build it", file=sys.stderr)
    for name in empty:
        print(f"::error::the release workflow expects {name} and the rehearsal built it empty", file=sys.stderr)
    return bool(missing or empty)


def compare(workflow: Path, pom: Path, listings: list[Path]) -> int:
    names = expected_names(workflow.read_text(encoding="utf-8"), product_version(pom))
    rehearsed = [name for name in names if not name.endswith(tuple(ONLY_A_REAL_RELEASE_PRODUCES))]
    for name in names:
        if name not in rehearsed:
            print(f"not rehearsed: {name}")
    if report_unbuilt(rehearsed, built_sizes(listings)):
        return 1
    print(f"RELEASE REHEARSAL asset names: {len(rehearsed)} of {len(names)} expected names built, "
          f"{len(names) - len(rehearsed)} produced only by a real release")
    return 0


def main(argv: list[str]) -> int:
    command, arguments = (argv[1], argv[2:]) if len(argv) > 1 else ("", [])
    if command == "version" and len(arguments) == 1:
        print(product_version(Path(arguments[0])))
        return 0
    if command == "list" and len(arguments) == 1:
        for entry in sorted(Path(arguments[0]).iterdir()):
            if entry.is_file():
                print(f"{entry.name}\t{entry.stat().st_size}")
        return 0
    if command == "compare" and len(arguments) >= 3:
        return compare(Path(arguments[0]), Path(arguments[1]), [Path(listing) for listing in arguments[2:]])
    print("\n".join(__doc__.strip().splitlines()[-4:]), file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
