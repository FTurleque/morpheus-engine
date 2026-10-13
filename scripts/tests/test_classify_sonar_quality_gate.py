"""Behavioural tests of scripts/classify-sonar-quality-gate.sh, with SonarQube Cloud replaced by a stub `curl`.

The script decides what a failed scanner run means. One answer of the service is neither a pass nor a failure: the
analysis of a branch other than main is processed, and the read of its quality gate is refused (HTTP 403) because the
organization has no access to branch data. These tests hold that this refusal is reported as an unknown verdict for
such a branch only, and stays an error on main, when no branch is named, and for every other status.
"""

from __future__ import annotations

import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

from validator_harness import require_bash, require_tool

SCRIPT = Path(__file__).resolve().parents[2] / "scripts" / "classify-sonar-quality-gate.sh"
SCRIPTS_UNDER_TEST = ("classify-sonar-quality-gate.sh",)

STUB_CURL = """#!/usr/bin/env bash
# Stands for SonarQube Cloud: the compute-engine task always succeeded; the quality gate answers what the test set.
output=""
url=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -o) output="$2"; shift 2 ;;
    -w|-H|--connect-timeout|--max-time) shift 2 ;;
    -*) shift ;;
    *) url="$1"; shift ;;
  esac
done
case "$url" in
  *api/ce/task*) printf '%s' '{"task":{"status":"SUCCESS","analysisId":"analysis-1"}}' ;;
  *qualitygates*) printf '%s' "$STUB_GATE_BODY" > "$output"; printf '%s' "$STUB_GATE_HTTP" ;;
  *) exit 7 ;;
esac
"""


class ClassifySonarQualityGateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        # In CI a missing tool fails the run; a leg that cannot run the classifier must not report success.
        cls.bash = require_bash()
        require_tool("jq", shutil.which("jq"), "the classifier reads the answers of the service with it")

    def classify(self, http: str, body: str = "{}", branch: str | None = None) -> subprocess.CompletedProcess:
        with tempfile.TemporaryDirectory() as temp:
            workspace = Path(temp)
            (workspace / "target" / "sonar").mkdir(parents=True)
            (workspace / "target" / "sonar" / "report-task.txt").write_text(
                "serverUrl=https://sonar.example\nceTaskUrl=https://sonar.example/api/ce/task?id=1\n",
                encoding="utf-8", newline="\n")
            stubs = workspace / "stubs"
            stubs.mkdir()
            curl = stubs / "curl"
            curl.write_text(STUB_CURL, encoding="utf-8", newline="\n")
            curl.chmod(curl.stat().st_mode | stat.S_IXUSR)

            environment = dict(os.environ)
            environment.pop("SONAR_BRANCH_NAME", None)
            environment.pop("GITHUB_STEP_SUMMARY", None)
            environment.update({
                "PATH": str(stubs) + os.pathsep + environment["PATH"],
                "SONAR_TOKEN": "stub-token",
                "GITHUB_WORKSPACE": str(workspace),
                "STUB_GATE_HTTP": http,
                "STUB_GATE_BODY": body,
            })
            if branch is not None:
                environment["SONAR_BRANCH_NAME"] = branch
            return subprocess.run([self.bash, SCRIPT.as_posix()], env=environment, capture_output=True, text=True, check=False)

    def test_a_gate_the_service_refuses_to_serve_for_another_branch_is_unknown_not_failed(self):
        result = self.classify("403", branch="develop")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("SONAR_QUALITY_GATE_UNREADABLE_OFF_MAIN", result.stdout)
        self.assertIn("The verdict is unknown, not failed", result.stdout)
        self.assertNotIn("quality gate: OK", result.stdout)

    def test_the_same_refusal_on_main_stays_an_error(self):
        result = self.classify("403", branch="main")

        self.assertEqual(1, result.returncode)
        self.assertIn("Unable to read the quality gate", result.stdout + result.stderr)
        self.assertIn("HTTP 403", result.stdout + result.stderr)

    def test_the_same_refusal_without_a_named_branch_stays_an_error(self):
        result = self.classify("403")

        self.assertEqual(1, result.returncode)
        self.assertIn("HTTP 403", result.stdout + result.stderr)

    def test_another_refusal_on_another_branch_stays_an_error(self):
        result = self.classify("500", branch="develop")

        self.assertEqual(1, result.returncode)
        self.assertIn("HTTP 500", result.stdout + result.stderr)

    def test_a_gate_that_passed_is_reported_as_passed(self):
        result = self.classify("200", '{"projectStatus":{"status":"OK"}}', branch="develop")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("quality gate: OK", result.stdout)

    def test_a_gate_that_failed_is_still_a_failure_on_another_branch(self):
        body = ('{"projectStatus":{"status":"ERROR","conditions":[{"status":"ERROR","metricKey":"new_coverage",'
                '"actualValue":"10","comparator":"LT","errorThreshold":"80"}]}}')
        result = self.classify("200", body, branch="develop")

        self.assertEqual(1, result.returncode)
        self.assertIn("genuinely failed", result.stdout + result.stderr)
        self.assertIn("new_coverage", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
