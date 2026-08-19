#!/usr/bin/env python3
"""Fail a pull request when executable changed Java lines are insufficiently covered by JaCoCo."""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path, PurePosixPath

HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
JACOCO_SUFFIX = PurePosixPath("target/site/jacoco/jacoco.xml")
EVIDENCE_OUTPUT = Path("validation-output/m21/diff-coverage.txt")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--minimum", type=float, default=0.80, help="Minimum changed-line coverage ratio")
    args = parser.parse_args()
    if not 0.0 <= args.minimum <= 1.0:
        parser.error("--minimum must be between 0 and 1")
    return args


def is_main_java(path: str) -> bool:
    return "/src/main/java/" in path and not path.endswith(("module-info.java", "package-info.java"))


def normalize_diff_path(raw: str) -> str | None:
    if raw == "/dev/null":
        return None
    candidate = raw[2:] if raw.startswith("b/") else raw
    path = PurePosixPath(candidate)
    if path.is_absolute() or ".." in path.parts:
        raise ValueError(f"unsafe path in git diff: {candidate}")
    normalized = path.as_posix()
    return normalized if is_main_java(normalized) else None


class DiffCursor:
    def __init__(self, changed: dict[str, set[int]]) -> None:
        self.changed = changed
        self.current_path: str | None = None
        self.new_line: int | None = None

    def consume(self, line: str) -> None:
        if line.startswith("diff --git "):
            self.current_path = None
            self.new_line = None
            return
        if line.startswith("+++ "):
            self.current_path = normalize_diff_path(line[4:])
            self.new_line = None
            return
        match = HUNK.match(line)
        if match:
            self.new_line = int(match.group(1))
            return
        if self.current_path is None or self.new_line is None:
            return
        self.new_line = apply_content_line(line, self.current_path, self.new_line, self.changed)


def apply_content_line(
    line: str,
    current_path: str,
    new_line: int,
    changed: dict[str, set[int]],
) -> int:
    if line.startswith("+") and not line.startswith("+++"):
        changed[current_path].add(new_line)
        return new_line + 1
    if line.startswith("-") and not line.startswith("---"):
        return new_line
    if not line.startswith("\\"):
        return new_line + 1
    return new_line


def changed_lines(diff: str) -> dict[str, set[int]]:
    changed: dict[str, set[int]] = defaultdict(set)
    cursor = DiffCursor(changed)
    for line in diff.splitlines():
        cursor.consume(line)
    return {path: lines for path, lines in changed.items() if lines}


def source_key(source_path: str) -> tuple[str, str]:
    module, relative = source_path.split("/src/main/java/", 1)
    return module, relative


def report_module(report: Path) -> str:
    parts = PurePosixPath(report.as_posix())
    suffix_parts = JACOCO_SUFFIX.parts
    if parts.parts[-len(suffix_parts):] != suffix_parts:
        raise ValueError(f"unexpected JaCoCo report path: {report}")
    module_parts = parts.parts[:-len(suffix_parts)]
    return PurePosixPath(*module_parts).as_posix() if module_parts else "."


def source_lines(source: ET.Element) -> dict[int, bool]:
    lines: dict[int, bool] = {}
    for item in source.findall("line"):
        missed = int(item.get("mi", "0"))
        covered = int(item.get("ci", "0"))
        if missed + covered > 0:
            lines[int(item.get("nr", "0"))] = covered > 0
    return lines


def package_coverage(module: str, package: ET.Element) -> dict[tuple[str, str], dict[int, bool]]:
    package_name = package.get("name", "")
    coverage: dict[tuple[str, str], dict[int, bool]] = {}
    for source in package.findall("sourcefile"):
        source_name = source.get("name")
        if not source_name:
            continue
        relative = f"{package_name}/{source_name}" if package_name else source_name
        coverage[(module, relative)] = source_lines(source)
    return coverage


def load_jacoco_report(report: Path) -> dict[tuple[str, str], dict[int, bool]]:
    module = report_module(report)
    root = ET.parse(report).getroot()
    coverage: dict[tuple[str, str], dict[int, bool]] = {}
    for package in root.findall("package"):
        coverage.update(package_coverage(module, package))
    return coverage


def load_jacoco() -> dict[tuple[str, str], dict[int, bool]]:
    coverage: dict[tuple[str, str], dict[int, bool]] = {}
    for report in sorted(Path(".").rglob(JACOCO_SUFFIX.as_posix())):
        coverage.update(load_jacoco_report(report))
    return coverage


def file_coverage(
    changed_line_numbers: set[int],
    report_lines: dict[int, bool],
) -> tuple[int, int]:
    covered = 0
    executable = 0
    for line in sorted(changed_line_numbers):
        state = report_lines.get(line)
        if state is None:
            continue
        executable += 1
        if state:
            covered += 1
    return covered, executable


def evaluate(
    changed: dict[str, set[int]],
    jacoco: dict[tuple[str, str], dict[int, bool]],
) -> tuple[int, int, list[str]]:
    executable = 0
    covered = 0
    details: list[str] = []

    for path in sorted(changed):
        report_lines = jacoco.get(source_key(path))
        if report_lines is None:
            details.append(f"MISSING_REPORT {path}")
            continue
        file_covered, file_total = file_coverage(changed[path], report_lines)
        covered += file_covered
        executable += file_total
        if file_total:
            details.append(f"{path}: {file_covered}/{file_total} executable changed lines covered")

    return covered, executable, details


def evidence_lines(
    minimum: float,
    changed: dict[str, set[int]],
    covered: int,
    executable: int,
    ratio: float,
    details: list[str],
) -> list[str]:
    return [
        "diff_source=stdin",
        f"changed_java_files={len(changed)}",
        f"covered_executable_changed_lines={covered}",
        f"executable_changed_lines={executable}",
        f"coverage={ratio:.4f}",
        f"minimum={minimum:.4f}",
        *details,
    ]


def write_evidence(lines: list[str]) -> None:
    EVIDENCE_OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    EVIDENCE_OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8")


def gate_result(ratio: float, minimum: float, details: list[str]) -> int:
    if any(line.startswith("MISSING_REPORT ") for line in details):
        print("::error::JaCoCo report is missing for changed production Java source", file=sys.stderr)
        return 1
    if ratio + 1e-12 < minimum:
        print(f"::error::Changed-line coverage {ratio:.2%} is below required {minimum:.2%}", file=sys.stderr)
        return 1
    return 0


def main() -> int:
    args = parse_args()
    try:
        changed = changed_lines(sys.stdin.read())
        jacoco = load_jacoco()
    except (ValueError, ET.ParseError) as failure:
        print(f"::error::{failure}", file=sys.stderr)
        return 2

    covered, executable, details = evaluate(changed, jacoco)
    ratio = 1.0 if executable == 0 else covered / executable
    lines = evidence_lines(args.minimum, changed, covered, executable, ratio, details)
    write_evidence(lines)
    print("\n".join(lines))
    return gate_result(ratio, args.minimum, details)


if __name__ == "__main__":
    raise SystemExit(main())
