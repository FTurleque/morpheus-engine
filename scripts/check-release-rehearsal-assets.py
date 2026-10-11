#!/usr/bin/env python3
"""Compare what a release rehearsal built with what the release workflow requires before publishing.

The release workflow refuses to publish unless every name of its `expected=(...)` list is present and non-empty. That
check only ever ran on a pushed tag, so a build script and the list could drift apart unseen until the day of the
release. The rehearsal runs the same build scripts every night; this script reads the list from the workflow itself --
it holds no asset name of its own -- and applies the same test to the files the rehearsal produced.

One family of names is produced only by a real release and is declared here: the attestation bundles, which exist once
the attestation step of the release has run. A rehearsal never attests.

Usage: check-release-rehearsal-assets.py <release.yml> <version> <directory holding the built files>
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

# Suffix -> why a rehearsal cannot produce it.
ONLY_A_REAL_RELEASE_PRODUCES = {
    ".attestation.jsonl": "written from the bundle of the attestation step, which a rehearsal never runs",
}

EXPECTED_BLOCK = re.compile(r"expected=\(\n(.*?)\n\s*\)", re.DOTALL)
EXPECTED_ENTRY = re.compile(r'"release-assets/([^"]+)"')


def expected_names(workflow_text: str, version: str) -> list[str]:
    block = EXPECTED_BLOCK.search(workflow_text.replace("\r\n", "\n"))
    if block is None:
        raise SystemExit("release workflow has no expected=( ... ) list: nothing to compare against")
    names = [name.replace("${version}", version) for name in EXPECTED_ENTRY.findall(block.group(1))]
    if not names:
        raise SystemExit("the expected=( ... ) list of the release workflow is empty: nothing to compare against")
    return names


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    workflow, version, directory = Path(argv[1]), argv[2], Path(argv[3])
    names = expected_names(workflow.read_text(encoding="utf-8"), version)

    rehearsed = [name for name in names if not name.endswith(tuple(ONLY_A_REAL_RELEASE_PRODUCES))]
    for name in names:
        if name not in rehearsed:
            print(f"not rehearsed: {name}")
    if report_unbuilt(rehearsed, directory):
        return 1
    print(f"RELEASE REHEARSAL asset names: {len(rehearsed)} of {len(names)} expected names built, "
          f"{len(names) - len(rehearsed)} produced only by a real release")
    return 0


def report_unbuilt(rehearsed: list[str], directory: Path) -> bool:
    """Annotates every expected name the rehearsal did not build, or built empty; true when there is one."""
    missing = [name for name in rehearsed if not (directory / name).is_file()]
    empty = [name for name in rehearsed if name not in missing and (directory / name).stat().st_size == 0]
    for name in missing:
        print(f"::error::the release workflow expects {name} and the rehearsal did not build it", file=sys.stderr)
    for name in empty:
        print(f"::error::the release workflow expects {name} and the rehearsal built it empty", file=sys.stderr)
    return bool(missing or empty)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
