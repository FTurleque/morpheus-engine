#!/usr/bin/env python3
"""Fail when a Dependency-Check report no longer identifies org.xerial:sqlite-jdbc as the SQLite engine it embeds.

sqlite-jdbc ships the native SQLite engine, so a CVE published against cpe:2.3:a:sqlite:sqlite must reach the CVSS
gate through the driver. Dependency-Check makes that association or not depending on the history of its database:
the long-lived CI database identifies the driver JAR as sqlite:sqlite, a database built from scratch files the CPE
under the native ecosystem and identifies the JAR as sqlite_jdbc only. config/dependency-check-hints.xml restores
the association on the native libraries extracted from the JAR. Neither path says anything when it stops working:
the scan stays green and simply sees fewer vulnerabilities. This script is the signal.

It passes when the report names the driver (by its package URL) and when the driver JAR or one of the native
libraries extracted from it carries an identifier cpe:2.3:a:sqlite:sqlite:<driver version>:. It fails otherwise,
naming what it found, and on a report it cannot read. It never reads vulnerabilities: whether the engine is
vulnerable is the scan's verdict, whether it is looked at is this one's.

Usage: check-dependency-check-sqlite-identification.py <dependency-check-report.json>
Writes a markdown table to $GITHUB_STEP_SUMMARY when set, and to stdout.
"""

from __future__ import annotations

import json
import os
import re
import sys
from pathlib import Path

DRIVER_PACKAGE = re.compile(r"^pkg:maven/org\.xerial/sqlite-jdbc@([^?#]+)$")
ENGINE_CPE_PREFIX = "cpe:2.3:a:sqlite:sqlite:"
FAILURE = "MORPHEUS_DEPENDENCY_CHECK_FAILURE=SQLITE_ENGINE_NOT_IDENTIFIED"
EXPLANATION = ("The scan can no longer report a vulnerability of the SQLite engine sqlite-jdbc embeds: a CVE "
               "published against cpe:2.3:a:sqlite:sqlite would not cross the CVSS gate. This is a detection gap, "
               "not a vulnerability finding. Check config/dependency-check-hints.xml against the driver version and "
               "the file names of its native libraries. See RT-13 in docs/architecture/risks/register.md.")


def driver_versions(dependencies: list[dict]) -> list[str]:
    versions = set()
    for dependency in dependencies:
        for package in dependency.get("packages") or []:
            match = DRIVER_PACKAGE.match(str(package.get("id", "")))
            if match:
                versions.add(match.group(1))
    return sorted(versions)


def engine_identifiers(dependencies: list[dict], version: str) -> list[tuple[str, str]]:
    """(file name, identifier) for every engine CPE carried by the driver JAR or a library extracted from it."""
    jar = f"sqlite-jdbc-{version}.jar"
    wanted = f"{ENGINE_CPE_PREFIX}{version}:"
    found = []
    for dependency in dependencies:
        name = str(dependency.get("fileName", ""))
        if name != jar and not name.startswith(jar + ": "):
            continue
        for identifier in dependency.get("vulnerabilityIds") or []:
            value = str(identifier.get("id", ""))
            if value.startswith(wanted):
                found.append((name, value))
    return sorted(set(found))


def inspect(report: Path) -> tuple[list[str], list[str]]:
    """Return (summary rows, problems)."""
    try:
        document = json.loads(report.read_text(encoding="utf-8"))
    except (OSError, ValueError) as failure:
        return [], [f"the report {report.as_posix()} cannot be read as JSON ({failure})"]
    dependencies = document.get("dependencies") if isinstance(document, dict) else None
    if not isinstance(dependencies, list):
        return [], [f"the report {report.as_posix()} has no dependencies list"]

    versions = driver_versions(dependencies)
    if not versions:
        return [], ["the report names no pkg:maven/org.xerial/sqlite-jdbc package: the driver was not scanned"]

    rows: list[str] = []
    problems: list[str] = []
    for version in versions:
        identified = engine_identifiers(dependencies, version)
        if identified:
            rows.extend(f"| `{version}` | `{name}` | `{value}` |" for name, value in identified)
        else:
            rows.append(f"| `{version}` | none | none |")
            problems.append(f"sqlite-jdbc {version}: neither the JAR nor a native library extracted from it carries "
                            f"{ENGINE_CPE_PREFIX}{version}:")
    return rows, problems


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print("usage: check-dependency-check-sqlite-identification.py <dependency-check-report.json>", file=sys.stderr)
        return 2
    rows, problems = inspect(Path(argv[1]))

    lines = ["### SQLite engine identification of sqlite-jdbc", ""]
    if rows:
        lines.extend(["| Driver | Dependency | Identifier |", "|---|---|---|", *rows])
    if problems:
        lines.extend(["", f"**{FAILURE}**", ""])
        lines.extend(f"- {problem}" for problem in problems)
        lines.extend(["", EXPLANATION])
    text = "\n".join(lines) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(text)
    if problems:
        print(f"::error::{FAILURE}. {'; '.join(problems)}. {EXPLANATION}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
