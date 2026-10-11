#!/usr/bin/env python3
"""Prove that a report-only SpotBugs run analysed every module that has classes, then summarise the alerts.

A run with -Dspotbugs.failOnError=false ends in BUILD SUCCESS whatever it finds, and the plugin's own summary publishes
total_classes='0'. Neither says the analysis happened. This script reads what does: for every module that carries
classes under target/classes it requires target/spotbugsXml.xml with errors='0', missingClasses='0' and a non-zero
cpu_seconds, and it fails naming the module otherwise. Alerts never fail it; an analysis that did not take place does.

Usage: check-spotbugs-reports.py [repository-root]
Writes a markdown table to $GITHUB_STEP_SUMMARY when set, and to stdout.
"""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPORT = Path("target/spotbugsXml.xml")
CLASSES = Path("target/classes")


def has_classes(module: Path) -> bool:
    classes = module / CLASSES
    return classes.is_dir() and any(classes.rglob("*.class"))


def inspect(module: Path) -> tuple[int, list[str]]:
    """Return (alert count, problems) for one module that has classes."""
    report = module / REPORT
    if not report.is_file():
        return 0, [f"{module.name}: no SpotBugs report at {REPORT.as_posix()} although it has classes"]
    try:
        root = ET.parse(report).getroot()
    except ET.ParseError as failure:
        return 0, [f"{module.name}: the SpotBugs report is not well-formed XML ({failure})"]
    problems: list[str] = []
    jar = root.find("Project/Jar")
    if jar is None or not (jar.text or "").strip():
        problems.append(f"{module.name}: the report names no analysed directory (Project/Jar)")
    errors = root.find("Errors")
    if errors is None:
        problems.append(f"{module.name}: the report has no Errors element, so a failed analysis cannot be told apart")
    else:
        for attribute in ("errors", "missingClasses"):
            if errors.get(attribute) != "0":
                problems.append(f"{module.name}: {attribute}={errors.get(attribute)!r}, the analysis is partial")
    summary = root.find("FindBugsSummary")
    try:
        cpu_seconds = float(summary.get("cpu_seconds")) if summary is not None else 0.0
    except (TypeError, ValueError):
        cpu_seconds = 0.0
    if cpu_seconds <= 0.0:
        problems.append(f"{module.name}: cpu_seconds is zero or missing, nothing was analysed")
    return len(root.findall("BugInstance")), problems


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else Path.cwd()
    modules = sorted(path for path in root.iterdir() if path.is_dir() and has_classes(path))
    if not modules:
        print("No module with classes under target/classes: the build did not run, or ran elsewhere.", file=sys.stderr)
        return 1

    rows: list[tuple[str, int]] = []
    problems: list[str] = []
    for module in modules:
        alerts, module_problems = inspect(module)
        rows.append((module.name, alerts))
        problems.extend(module_problems)

    lines = ["### SpotBugs (report-only)", "", "| Module | Alerts |", "|---|---:|"]
    lines.extend(f"| `{name}` | {alerts} |" for name, alerts in rows)
    lines.append(f"| **Total** | **{sum(alerts for _, alerts in rows)}** |")
    if problems:
        lines.extend(["", "**The analysis is incomplete:**", ""])
        lines.extend(f"- {problem}" for problem in problems)
    text = "\n".join(lines) + "\n"
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(text)
    for problem in problems:
        print(f"::error::{problem}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
