#!/usr/bin/env python3
"""Validate the canonical post-reactor JaCoCo aggregate report and write M21 coverage evidence."""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

D2_MIN_LINE_RATIO = 0.40
D2_MIN_BRANCH_RATIO = 0.35


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml"),
    )
    parser.add_argument(
        "--ratchets",
        type=Path,
        default=Path("config/m21-quality-ratchets.properties"),
    )
    parser.add_argument(
        "--summary",
        type=Path,
        default=Path("morpheus-architecture-tests/target/m21-coverage-summary.txt"),
    )
    return parser.parse_args()


def properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator or not key.strip() or not value.strip():
            raise ValueError(f"invalid M21 quality ratchet entry: {raw}")
        values[key.strip()] = value.strip()
    return values


def required_ratio(values: dict[str, str], key: str) -> float:
    if key not in values:
        raise ValueError(f"missing M21 quality ratchet: {key}")
    ratio = float(values[key])
    if not 0.0 < ratio <= 1.0:
        raise ValueError(f"M21 quality ratchet must be in (0, 1]: {key}")
    return ratio


def counter(root: ET.Element, kind: str) -> tuple[int, int]:
    for element in root.findall("counter"):
        if element.get("type") == kind:
            return int(element.get("covered", "0")), int(element.get("missed", "0"))
    raise ValueError(f"aggregate JaCoCo report has no top-level {kind} counter")


def ratio(covered: int, missed: int) -> float:
    total = covered + missed
    return 1.0 if total == 0 else covered / total


def main() -> int:
    args = parse_args()
    try:
        if not args.report.is_file():
            raise ValueError(f"aggregate JaCoCo report is missing: {args.report}")
        if not args.ratchets.is_file():
            raise ValueError(f"M21 quality ratchet configuration is missing: {args.ratchets}")

        values = properties(args.ratchets)
        line_minimum = max(D2_MIN_LINE_RATIO, required_ratio(values, "lineCoverageMinimum"))
        branch_minimum = max(D2_MIN_BRANCH_RATIO, required_ratio(values, "branchCoverageMinimum"))

        root = ET.parse(args.report).getroot()
        line_covered, line_missed = counter(root, "LINE")
        branch_covered, branch_missed = counter(root, "BRANCH")
        line_ratio = ratio(line_covered, line_missed)
        branch_ratio = ratio(branch_covered, branch_missed)

        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(
            "\n".join(
                [
                    "coverageSource=jacoco-report-aggregate",
                    f"aggregateReport={args.report.as_posix()}",
                    f"lineCovered={line_covered}",
                    f"lineMissed={line_missed}",
                    f"lineRatio={line_ratio:.6f}",
                    f"branchCovered={branch_covered}",
                    f"branchMissed={branch_missed}",
                    f"branchRatio={branch_ratio:.6f}",
                    f"lineRatchet={line_minimum:.3f}",
                    f"branchRatchet={branch_minimum:.3f}",
                    f"d2LineFloor={D2_MIN_LINE_RATIO:.2f}",
                    f"d2BranchFloor={D2_MIN_BRANCH_RATIO:.2f}",
                ]
            )
            + "\n",
            encoding="utf-8",
        )

        print(args.summary.read_text(encoding="utf-8"), end="")
        failed = False
        if line_ratio + 1e-12 < line_minimum:
            print(
                f"aggregate JaCoCo line coverage {line_ratio:.6f} is below ratchet {line_minimum:.3f}",
                file=sys.stderr,
            )
            failed = True
        if branch_ratio + 1e-12 < branch_minimum:
            print(
                f"aggregate JaCoCo branch coverage {branch_ratio:.6f} is below ratchet {branch_minimum:.3f}",
                file=sys.stderr,
            )
            failed = True
        return 1 if failed else 0
    except (OSError, ValueError, ET.ParseError) as failure:
        print(f"aggregate coverage validation failed: {failure}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
