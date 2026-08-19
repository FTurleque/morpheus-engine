#!/usr/bin/env python3
"""Fail a pull request when executable changed Java lines are insufficiently covered by JaCoCo."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True, help="Base commit SHA/ref for base...HEAD")
    parser.add_argument("--minimum", type=float, default=0.80, help="Minimum changed-line coverage ratio")
    parser.add_argument("--output", type=Path, required=True, help="Evidence output file")
    args = parser.parse_args()
    if not 0.0 <= args.minimum <= 1.0:
        parser.error("--minimum must be between 0 and 1")
    return args


def git_diff(base: str) -> str:
    command = ["git", "diff", "--unified=0", "--no-color", f"{base}...HEAD", "--"]
    return subprocess.run(command, check=True, text=True, capture_output=True).stdout


def is_main_java(path: str) -> bool:
    return "/src/main/java/" in path and not path.endswith(("module-info.java", "package-info.java"))


def changed_lines(diff: str) -> dict[str, set[int]]:
    changed: dict[str, set[int]] = defaultdict(set)
    current_path: str | None = None
    new_line: int | None = None

    for line in diff.splitlines():
        if line.startswith("diff --git "):
            current_path = None
            new_line = None
            continue
        if line.startswith("+++ "):
            raw = line[4:]
            if raw == "/dev/null":
                current_path = None
            else:
                current_path = raw[2:] if raw.startswith("b/") else raw
                if not is_main_java(current_path):
                    current_path = None
            new_line = None
            continue
        match = HUNK.match(line)
        if match:
            new_line = int(match.group(1))
            continue
        if current_path is None or new_line is None:
            continue
        if line.startswith("+") and not line.startswith("+++"):
            changed[current_path].add(new_line)
            new_line += 1
        elif line.startswith("-") and not line.startswith("---"):
            continue
        elif not line.startswith("\\"):
            new_line += 1

    return {path: lines for path, lines in changed.items() if lines}


def source_key(source_path: str) -> tuple[str, str]:
    module, relative = source_path.split("/src/main/java/", 1)
    return module, relative


def report_module(report: Path) -> str:
    marker = Path("target/site/jacoco/jacoco.xml")
    parts = report.as_posix()
    suffix = marker.as_posix()
    if not parts.endswith(suffix):
        raise ValueError(f"unexpected JaCoCo report path: {report}")
    prefix = parts[: -len(suffix)].rstrip("/")
    return prefix or "."


def load_jacoco() -> dict[tuple[str, str], dict[int, bool]]:
    coverage: dict[tuple[str, str], dict[int, bool]] = {}
    for report in sorted(Path(".").rglob("target/site/jacoco/jacoco.xml")):
        module = report_module(report)
        root = ET.parse(report).getroot()
        for package in root.findall("package"):
            package_name = package.get("name", "")
            for source in package.findall("sourcefile"):
                relative = f"{package_name}/{source.get('name')}" if package_name else str(source.get("name"))
                lines: dict[int, bool] = {}
                for item in source.findall("line"):
                    missed = int(item.get("mi", "0"))
                    covered = int(item.get("ci", "0"))
                    if missed + covered > 0:
                        lines[int(item.get("nr", "0"))] = covered > 0
                coverage[(module, relative)] = lines
    return coverage


def evaluate(changed: dict[str, set[int]], jacoco: dict[tuple[str, str], dict[int, bool]]) -> tuple[int, int, list[str]]:
    executable = 0
    covered = 0
    details: list[str] = []

    for path in sorted(changed):
        key = source_key(path)
        report_lines = jacoco.get(key)
        if report_lines is None:
            details.append(f"MISSING_REPORT {path}")
            continue
        file_total = 0
        file_covered = 0
        for line in sorted(changed[path]):
            state = report_lines.get(line)
            if state is None:
                continue
            executable += 1
            file_total += 1
            if state:
                covered += 1
                file_covered += 1
        if file_total:
            details.append(f"{path}: {file_covered}/{file_total} executable changed lines covered")

    return covered, executable, details


def main() -> int:
    args = parse_args()
    changed = changed_lines(git_diff(args.base))
    jacoco = load_jacoco()
    covered, executable, details = evaluate(changed, jacoco)
    ratio = 1.0 if executable == 0 else covered / executable

    args.output.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        f"base={args.base}",
        f"changed_java_files={len(changed)}",
        f"covered_executable_changed_lines={covered}",
        f"executable_changed_lines={executable}",
        f"coverage={ratio:.4f}",
        f"minimum={args.minimum:.4f}",
        *details,
    ]
    args.output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))

    missing = [line for line in details if line.startswith("MISSING_REPORT ")]
    if missing:
        print("::error::JaCoCo report is missing for changed production Java source", file=sys.stderr)
        return 1
    if ratio + 1e-12 < args.minimum:
        print(
            f"::error::Changed-line coverage {ratio:.2%} is below required {args.minimum:.2%}",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
