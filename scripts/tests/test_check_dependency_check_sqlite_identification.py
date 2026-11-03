"""Behavioural tests of scripts/check-dependency-check-sqlite-identification.py.

The check exists because a lost association is silent: the scan stays green and sees fewer vulnerabilities. Each case
writes a small report shaped like the JSON report of Dependency-Check 13.0.0 (reportSchema 1.1): the driver JAR with
its package URL, and the native libraries Dependency-Check extracts from it, which carry no package URL.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check-dependency-check-sqlite-identification.py"
SCRIPTS_UNDER_TEST = ("check-dependency-check-sqlite-identification.py",)

VERSION = "3.53.4.0"
ENGINE = f"cpe:2.3:a:sqlite:sqlite:{VERSION}:*:*:*:*:*:*:*"
DRIVER_CPE = f"cpe:2.3:a:sqlite_jdbc_project:sqlite_jdbc:{VERSION}:*:*:*:*:*:*:*"


def jar(*identifiers: str, version: str = VERSION) -> dict:
    return {
        "isVirtual": False,
        "fileName": f"sqlite-jdbc-{version}.jar",
        "packages": [{"id": f"pkg:maven/org.xerial/sqlite-jdbc@{version}", "confidence": "HIGH"}],
        "vulnerabilityIds": [{"id": value, "confidence": "HIGHEST"} for value in identifiers],
    }


def native(*identifiers: str, version: str = VERSION) -> dict:
    entry = {"isVirtual": False, "fileName": f"sqlite-jdbc-{version}.jar: sqlitejdbc.dll"}
    if identifiers:
        entry["vulnerabilityIds"] = [{"id": value, "confidence": "LOW"} for value in identifiers]
    return entry


def store(*identifiers: str) -> dict:
    return {
        "isVirtual": False,
        "fileName": "morpheus-store-sqlite-1.2.1.jar",
        "packages": [{"id": "pkg:maven/io.github.fturleque/morpheus-store-sqlite@1.2.1", "confidence": "HIGHEST"}],
        "vulnerabilityIds": [{"id": value, "confidence": "LOW"} for value in identifiers],
    }


class CheckDependencyCheckSqliteIdentificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self._directory = tempfile.TemporaryDirectory()
        self.root = Path(self._directory.name)

    def tearDown(self) -> None:
        self._directory.cleanup()

    def run_script(self, *dependencies: dict, raw: str | None = None,
                   summary: Path | None = None) -> subprocess.CompletedProcess[str]:
        report = self.root / "dependency-check-report.json"
        if raw is not None:
            report.write_text(raw, encoding="utf-8")
        else:
            report.write_text(json.dumps({"reportSchema": "1.1", "dependencies": list(dependencies)}), encoding="utf-8")
        environment = {key: value for key, value in os.environ.items() if key != "GITHUB_STEP_SUMMARY"}
        if summary is not None:
            environment["GITHUB_STEP_SUMMARY"] = str(summary)
        return subprocess.run([sys.executable, str(SCRIPT), str(report)], capture_output=True, text=True,
                              check=False, env=environment)

    def test_the_long_lived_database_shape_passes_on_the_jar_itself(self) -> None:
        result = self.run_script(jar(ENGINE, DRIVER_CPE), native())
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(f"| `{VERSION}` | `sqlite-jdbc-{VERSION}.jar` | `{ENGINE}` |", result.stdout)

    def test_the_hinted_fresh_database_shape_passes_on_an_extracted_native_library(self) -> None:
        result = self.run_script(jar(DRIVER_CPE), native(ENGINE, DRIVER_CPE))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn(f"`sqlite-jdbc-{VERSION}.jar: sqlitejdbc.dll` | `{ENGINE}`", result.stdout)

    def test_a_driver_identified_as_sqlite_jdbc_only_fails_naming_the_gap(self) -> None:
        result = self.run_script(jar(DRIVER_CPE), native())
        self.assertEqual(1, result.returncode)
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_FAILURE=SQLITE_ENGINE_NOT_IDENTIFIED", result.stderr)
        self.assertIn(f"sqlite-jdbc {VERSION}: neither the JAR nor a native library", result.stderr)

    def test_an_engine_identifier_on_another_dependency_does_not_count(self) -> None:
        result = self.run_script(jar(DRIVER_CPE), store("cpe:2.3:a:sqlite:sqlite:1.2.1:*:*:*:*:*:*:*"))
        self.assertEqual(1, result.returncode)
        self.assertIn("SQLITE_ENGINE_NOT_IDENTIFIED", result.stderr)

    def test_an_engine_identifier_at_another_version_than_the_driver_fails(self) -> None:
        stale = "cpe:2.3:a:sqlite:sqlite:3.50.1.0:*:*:*:*:*:*:*"
        result = self.run_script(jar(DRIVER_CPE), native(stale))
        self.assertEqual(1, result.returncode)
        self.assertIn(f"carries cpe:2.3:a:sqlite:sqlite:{VERSION}:", result.stderr)

    def test_a_report_without_the_driver_fails_because_the_driver_was_not_scanned(self) -> None:
        result = self.run_script(store())
        self.assertEqual(1, result.returncode)
        self.assertIn("names no pkg:maven/org.xerial/sqlite-jdbc package", result.stderr)

    def test_an_unreadable_report_fails(self) -> None:
        result = self.run_script(raw="{ not json")
        self.assertEqual(1, result.returncode)
        self.assertIn("cannot be read as JSON", result.stderr)

    def test_a_report_without_a_dependencies_list_fails(self) -> None:
        result = self.run_script(raw=json.dumps({"reportSchema": "1.1"}))
        self.assertEqual(1, result.returncode)
        self.assertIn("has no dependencies list", result.stderr)

    def test_the_verdict_reaches_the_job_summary(self) -> None:
        summary = self.root / "summary.md"
        result = self.run_script(jar(DRIVER_CPE), summary=summary)
        self.assertEqual(1, result.returncode)
        written = summary.read_text(encoding="utf-8")
        self.assertIn("### SQLite engine identification of sqlite-jdbc", written)
        self.assertIn("**MORPHEUS_DEPENDENCY_CHECK_FAILURE=SQLITE_ENGINE_NOT_IDENTIFIED**", written)


if __name__ == "__main__":
    unittest.main()
