"""Behavioural tests of scripts/check-spotbugs-reports.py, the completeness check of the report-only SpotBugs lane.

A report-only run is green whatever it finds, so the lane is only worth anything if it fails when the analysis did not
happen. Each case builds a throwaway repository of modules with and without classes and hand-written reports.
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check-spotbugs-reports.py"


def report(alerts: int = 0, errors: str = "0", missing: str = "0", cpu: str = "12.5", jar: bool = True) -> str:
    bugs = "".join("<BugInstance type='EI_EXPOSE_REP'/>" for _ in range(alerts))
    project = "<Project><Jar>/work/target/classes</Jar></Project>" if jar else "<Project/>"
    return (
        "<?xml version='1.0' encoding='utf-8'?><BugCollection version='4.10.4'>"
        f"{project}{bugs}<Errors missingClasses='{missing}' errors='{errors}'></Errors>"
        f"<FindBugsSummary total_classes='0' cpu_seconds='{cpu}'/></BugCollection>"
    )


class CheckSpotbugsReportsTest(unittest.TestCase):
    def setUp(self) -> None:
        self._directory = tempfile.TemporaryDirectory()
        self.root = Path(self._directory.name)

    def tearDown(self) -> None:
        self._directory.cleanup()

    def module(self, name: str, xml: str | None, classes: bool = True) -> None:
        target = self.root / name / "target"
        target.mkdir(parents=True, exist_ok=True)
        if classes:
            (target / "classes").mkdir(parents=True)
            (target / "classes" / "Demo.class").write_bytes(b"\xca\xfe\xba\xbe")
        if xml is not None:
            (target / "spotbugsXml.xml").write_text(xml, encoding="utf-8")

    def run_script(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(self.root)], capture_output=True, text=True, check=False
        )

    def test_alerts_alone_never_fail_the_run_and_are_counted_per_module(self) -> None:
        self.module("alpha", report(alerts=3))
        self.module("beta", report(alerts=0))
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("| `alpha` | 3 |", result.stdout)
        self.assertIn("| **Total** | **3** |", result.stdout)

    def test_a_module_with_classes_and_no_report_fails_naming_the_module(self) -> None:
        self.module("alpha", report())
        self.module("beta", None)
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("beta: no SpotBugs report", result.stderr)

    def test_missing_classes_make_the_analysis_partial(self) -> None:
        self.module("alpha", report(missing="4"))
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("alpha: missingClasses='4'", result.stderr)

    def test_analysis_errors_make_the_analysis_partial(self) -> None:
        self.module("alpha", report(errors="1"))
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("alpha: errors='1'", result.stderr)

    def test_zero_cpu_time_means_nothing_was_analysed(self) -> None:
        self.module("alpha", report(cpu="0.00"))
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("alpha: cpu_seconds is zero", result.stderr)

    def test_a_report_without_an_analysed_directory_or_errors_element_fails(self) -> None:
        self.module("alpha", "<BugCollection><Project/></BugCollection>")
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("alpha: the report names no analysed directory", result.stderr)
        self.assertIn("alpha: the report has no Errors element", result.stderr)

    def test_a_malformed_report_fails_instead_of_crashing(self) -> None:
        self.module("alpha", "<BugCollection")
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("alpha: the SpotBugs report is not well-formed XML", result.stderr)

    def test_a_module_without_classes_owes_no_report(self) -> None:
        self.module("alpha", report())
        self.module("tests-only", None, classes=False)
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("tests-only", result.stdout)

    def test_a_tree_where_nothing_was_built_fails(self) -> None:
        self.module("tests-only", None, classes=False)
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("No module with classes", result.stderr)


if __name__ == "__main__":
    unittest.main()
