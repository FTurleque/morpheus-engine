"""Behavioural tests of scripts/check-diff-coverage.py, the gate every pull request goes through (CI-AUD-5).

Each case runs the script as CI does -- a git diff on stdin, JaCoCo reports found from the working directory -- in a
throwaway repository holding one module, one Java source and a hand-written JaCoCo report.
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check-diff-coverage.py"
SOURCE = "demo-module/src/main/java/com/example/Demo.java"


def diff_adding(path: str, first_line: int, count: int) -> str:
    added = "".join(f"+line {number}\n" for number in range(first_line, first_line + count))
    return (
        f"diff --git a/{path} b/{path}\n"
        f"--- a/{path}\n"
        f"+++ b/{path}\n"
        f"@@ -0,0 +{first_line},{count} @@\n"
        f"{added}"
    )


def jacoco_report(lines: list[tuple[int, int, int, int, int]]) -> str:
    """lines: (number, missed instructions, covered instructions, missed branches, covered branches)."""
    rendered = "".join(
        f'<line nr="{nr}" mi="{mi}" ci="{ci}" mb="{mb}" cb="{cb}"/>' for nr, mi, ci, mb, cb in lines
    )
    return (
        '<?xml version="1.0" encoding="UTF-8"?><report name="demo">'
        '<package name="com/example"><sourcefile name="Demo.java">'
        f"{rendered}"
        "</sourcefile></package></report>"
    )


class CheckDiffCoverageTest(unittest.TestCase):
    def setUp(self) -> None:
        self._directory = tempfile.TemporaryDirectory()
        self.root = Path(self._directory.name)
        source = self.root / SOURCE
        source.parent.mkdir(parents=True)
        source.write_text("class Demo {}\n", encoding="utf-8")

    def tearDown(self) -> None:
        self._directory.cleanup()

    def write_module_report(self, lines: list[tuple[int, int, int, int, int]]) -> None:
        report = self.root / "demo-module/target/site/jacoco/jacoco.xml"
        report.parent.mkdir(parents=True)
        report.write_text(jacoco_report(lines), encoding="utf-8")

    def run_gate(self, diff: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--minimum", "0.80", "--minimum-branch", "0.70"],
            cwd=self.root,
            input=diff,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_covered_lines_and_branches_pass(self) -> None:
        self.write_module_report([(10, 0, 3, 0, 2), (11, 0, 2, 0, 0)])

        result = self.run_gate(diff_adding(SOURCE, 10, 2))

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("coverage_source=module-local", result.stdout)
        self.assertIn(f"{SOURCE}: lines=2/2 branches=2/2", result.stdout)
        evidence = (self.root / "validation-output/m21/diff-coverage.txt").read_text(encoding="utf-8")
        self.assertIn("line_coverage=1.0000", evidence)

    def test_uncovered_changed_lines_fail_on_the_line_minimum(self) -> None:
        self.write_module_report([(10, 3, 0, 0, 0), (11, 0, 2, 0, 0)])

        result = self.run_gate(diff_adding(SOURCE, 10, 2))

        self.assertEqual(1, result.returncode)
        self.assertIn("Changed-line coverage 50.00% is below required 80.00%", result.stderr)
        self.assertNotIn("Changed-branch coverage", result.stderr)

    def test_missed_changed_branches_fail_on_the_branch_minimum(self) -> None:
        self.write_module_report([(10, 0, 3, 3, 1)])

        result = self.run_gate(diff_adding(SOURCE, 10, 1))

        self.assertEqual(1, result.returncode)
        self.assertIn("Changed-branch coverage 25.00% is below required 70.00%", result.stderr)
        self.assertNotIn("Changed-line coverage", result.stderr)

    def test_a_changed_source_absent_from_every_report_fails(self) -> None:
        self.write_module_report([(10, 0, 3, 0, 0)])
        other = "demo-module/src/main/java/com/example/Other.java"
        (self.root / other).write_text("class Other {}\n", encoding="utf-8")

        result = self.run_gate(diff_adding(other, 1, 1))

        self.assertEqual(1, result.returncode)
        self.assertIn(f"MISSING_REPORT {other}", result.stdout)
        self.assertIn("JaCoCo report is missing for changed production Java source", result.stderr)

    def test_the_aggregate_report_is_preferred_and_mapped_back_to_its_module(self) -> None:
        self.write_module_report([(10, 3, 0, 0, 0)])
        aggregate = self.root / "morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml"
        aggregate.parent.mkdir(parents=True)
        aggregate.write_text(jacoco_report([(10, 0, 3, 0, 0)]), encoding="utf-8")

        result = self.run_gate(diff_adding(SOURCE, 10, 1))

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("coverage_source=aggregate", result.stdout)
        self.assertIn(f"{SOURCE}: lines=1/1", result.stdout)

    def test_test_sources_and_non_java_files_are_not_measured(self) -> None:
        self.write_module_report([(10, 3, 0, 0, 0)])
        diff = diff_adding("demo-module/src/test/java/com/example/DemoTest.java", 10, 1) + diff_adding(
            "docs/README.md", 1, 1
        )

        result = self.run_gate(diff)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("changed_java_files=0", result.stdout)

    def test_an_unsafe_path_in_the_diff_is_refused(self) -> None:
        self.write_module_report([(10, 0, 3, 0, 0)])

        result = self.run_gate(diff_adding("../outside/src/main/java/Evil.java", 1, 1))

        self.assertEqual(2, result.returncode)
        self.assertIn("unsafe path in git diff", result.stderr)


if __name__ == "__main__":
    unittest.main()
