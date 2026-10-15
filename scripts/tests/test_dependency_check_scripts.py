"""Behavioural tests of the four scripts of the Dependency-Check lane of security.yml.

``write-`` and ``read-dependency-check-sentinel.sh`` record and classify the age of the vulnerability database,
``report-dependency-check-cache.sh`` announces how much of its freshness budget is left, and
``classify-dependency-check-failure.sh`` names why a scan failed. The workflow keeps every refusal inline and trusts
these four for the facts: a reader that reports a freshness it did not establish, or a classifier that ends on zero,
would turn an unknown into a pass. DependencyCheckWorkflowContractTest reads their text; these tests run them.
"""

from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

from validator_harness import SCRIPTS, clean_environment, require_bash

SCRIPTS_UNDER_TEST = ("write-dependency-check-sentinel.sh", "read-dependency-check-sentinel.sh",
                      "report-dependency-check-cache.sh", "classify-dependency-check-failure.sh")

THRESHOLD_LINE = ("[ERROR] One or more dependencies were identified with vulnerabilities that have a CVSS score "
                  "greater than or equal to '7.0':")


class DependencyCheckScriptCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.bash = require_bash()

    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)
        self.step_summary = self.directory / "step-summary.md"

    def run_script(self, script: str, *arguments: str, **environment: str) -> subprocess.CompletedProcess:
        env = {key: value for key, value in clean_environment().items()
               if key.upper() not in ("GITHUB_STEP_SUMMARY", "GITHUB_RUN_ID", "DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS")}
        env.update(environment)
        return subprocess.run([self.bash, (SCRIPTS / script).as_posix(), *arguments], env=env, cwd=self.directory,
                              capture_output=True, text=True, check=False)

    @staticmethod
    def facts(result: subprocess.CompletedProcess) -> dict[str, str]:
        return dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)


class SentinelTest(DependencyCheckScriptCase):
    def data(self) -> str:
        return (self.directory / "data").as_posix()

    def read(self, expected_schema: str = "5.6") -> subprocess.CompletedProcess:
        return self.run_script("read-dependency-check-sentinel.sh", self.data(), expected_schema)

    def sentinel(self, content: str) -> None:
        (self.directory / "data").mkdir(exist_ok=True)
        (self.directory / "data" / "dependency-check-refresh.sentinel").write_text(content, encoding="utf-8",
                                                                                   newline="\n")

    def test_a_refresh_that_was_recorded_is_read_back_with_its_age_and_its_origin(self) -> None:
        written = self.run_script("write-dependency-check-sentinel.sh", self.data(), "13.0.0", "5.6", "nvd-api",
                                  GITHUB_REF_NAME="develop")
        self.assertEqual(0, written.returncode, written.stderr)

        result = self.read()

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("status=OK", result.stdout.splitlines()[0], "status is the first fact a caller reads")
        facts = self.facts(result)
        self.assertTrue(0 <= int(facts["ageSeconds"]) < 120, facts)
        self.assertEqual("5.6", facts["recordedSchemaVersion"])
        self.assertEqual("13.0.0", facts["pluginVersion"])
        self.assertEqual("nvd-api", facts["refreshedBy"])
        self.assertEqual("develop", facts["refreshedOnRef"])

    def test_no_sentinel_is_an_unknown_age_never_a_fresh_one(self) -> None:
        result = self.read()

        self.assertEqual("status=MISSING", result.stdout.splitlines()[0])
        self.assertNotIn("ageSeconds", self.facts(result))
        self.assertIn("cannot be established", result.stderr)

    def test_a_sentinel_without_a_usable_epoch_is_malformed(self) -> None:
        for content in ("schemaVersion=5.6\n", "refreshedAtEpoch=\nschemaVersion=5.6\n",
                        "refreshedAtEpoch=yesterday\nschemaVersion=5.6\n", "refreshedAtEpoch=-5\nschemaVersion=5.6\n"):
            with self.subTest(content=content):
                self.sentinel(content)

                result = self.read()

                self.assertEqual("status=MALFORMED", result.stdout.splitlines()[0])
                self.assertNotIn("ageSeconds", self.facts(result))

    def test_a_database_of_another_schema_is_named_and_given_no_age(self) -> None:
        self.run_script("write-dependency-check-sentinel.sh", self.data(), "13.0.0", "5.6", "nvd-api")

        result = self.read(expected_schema="5.7")

        facts = self.facts(result)
        self.assertEqual("status=SCHEMA_MISMATCH", result.stdout.splitlines()[0])
        self.assertEqual(("5.6", "5.7"), (facts["recordedSchemaVersion"], facts["expectedSchemaVersion"]))
        self.assertNotIn("ageSeconds", facts)

    def test_a_plugin_of_another_version_is_reported_and_not_fatal(self) -> None:
        self.run_script("write-dependency-check-sentinel.sh", self.data(), "12.2.2", "5.6", "cache")

        facts = self.facts(self.read())

        self.assertEqual(("OK", "12.2.2"), (facts["status"], facts["pluginVersion"]))

    def test_the_writer_requires_its_four_arguments_and_writes_nothing_without_them(self) -> None:
        result = self.run_script("write-dependency-check-sentinel.sh", self.data(), "13.0.0", "5.6")

        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.directory / "data" / "dependency-check-refresh.sentinel").exists())

    def test_the_reader_requires_the_schema_it_must_expect(self) -> None:
        result = self.run_script("read-dependency-check-sentinel.sh", self.data())

        self.assertNotEqual(0, result.returncode)
        self.assertNotIn("status=", result.stdout)


class CacheReportTest(DependencyCheckScriptCase):
    def report(self, age_seconds: int, **environment: str) -> subprocess.CompletedProcess:
        environment.setdefault("DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS", "72")
        return self.run_script("report-dependency-check-cache.sh", str(age_seconds), "trusted cache", **environment)

    def test_a_fresh_database_is_reported_without_a_warning(self) -> None:
        result = self.report(3600)

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("1h 00m old, 71h 00m of the 72h budget remaining (1% consumed)", result.stdout)
        self.assertNotIn("::warning::", result.stdout)

    def test_the_warning_starts_at_two_thirds_of_the_budget_inclusive(self) -> None:
        two_thirds = 48 * 3600
        self.assertNotIn("::warning::", self.report(two_thirds - 1).stdout)

        result = self.report(two_thirds)

        self.assertEqual(0, result.returncode, "the script reports; the refusal stays in the workflow")
        self.assertIn("::warning::", result.stdout)
        self.assertIn("24h 00m remain", result.stdout)

    def test_an_expired_database_has_no_margin_left(self) -> None:
        result = self.report(80 * 3600)

        self.assertIn("none of the 72h budget remaining", result.stdout)
        self.assertIn("::warning::", result.stdout)

    def test_the_budget_is_the_one_the_environment_declares(self) -> None:
        self.assertIn("::warning::", self.report(3600, DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS="1").stdout)

    def test_without_a_declared_budget_nothing_is_reported(self) -> None:
        result = self.run_script("report-dependency-check-cache.sh", "3600", "trusted cache")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS must be set", result.stderr)

    def test_the_step_summary_carries_the_same_facts(self) -> None:
        self.report(48 * 3600, GITHUB_STEP_SUMMARY=self.step_summary.as_posix())

        summary = self.step_summary.read_text(encoding="utf-8")
        self.assertIn("| Obtained via | trusted cache |", summary)
        self.assertIn("| Budget consumed | 66% |", summary)
        self.assertIn("Approaching expiry", summary)


class FailureClassifierTest(DependencyCheckScriptCase):
    def classify(self, log: str | None, **environment: str) -> subprocess.CompletedProcess:
        path = self.directory / "scan.log"
        if log is not None:
            path.write_text(log, encoding="utf-8", newline="\n")
        return self.run_script("classify-dependency-check-failure.sh", path.as_posix(), "aggregate", **environment)

    def test_a_scan_stopped_by_the_threshold_is_a_vulnerability(self) -> None:
        result = self.classify(f"[INFO] scanning\n{THRESHOLD_LINE}\n[ERROR] lib.jar: CVE-0000-0000\n")

        self.assertEqual(1, result.returncode)
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_FAILURE=VULNERABILITY_THRESHOLD_EXCEEDED in the aggregate scan",
                      result.stdout)
        self.assertIn("CVE-0000-0000", result.stdout, "the finding is shown with the label")

    def test_a_scan_that_did_not_complete_is_not_a_vulnerability_and_not_a_pass(self) -> None:
        result = self.classify("[ERROR] Unable to connect to the database\n")

        self.assertEqual(1, result.returncode)
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_FAILURE=SCAN_EXECUTION_FAILED", result.stdout)
        self.assertNotIn("VULNERABILITY_THRESHOLD_EXCEEDED", result.stdout)

    def test_a_missing_log_is_a_scan_that_did_not_complete(self) -> None:
        result = self.classify(None)

        self.assertEqual(1, result.returncode)
        self.assertIn("SCAN_EXECUTION_FAILED", result.stdout)

    def test_it_never_ends_on_zero_even_without_its_arguments(self) -> None:
        result = self.run_script("classify-dependency-check-failure.sh")

        self.assertNotEqual(0, result.returncode)

    def test_the_step_summary_names_the_reason_and_says_it_is_not_a_stale_database(self) -> None:
        self.classify(THRESHOLD_LINE + "\n", GITHUB_STEP_SUMMARY=self.step_summary.as_posix())

        summary = self.step_summary.read_text(encoding="utf-8")
        self.assertIn("| Reason | `VULNERABILITY_THRESHOLD_EXCEEDED` |", summary)
        self.assertIn("is not STALE_DATABASE", summary)


del DependencyCheckScriptCase  # the shared base holds no case of its own

if __name__ == "__main__":
    unittest.main()
