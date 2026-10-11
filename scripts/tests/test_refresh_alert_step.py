"""Behavioural tests of the refresh-alert job of .github/workflows/security.yml.

The job decides in a shell step whether a Dependency-Check refresh that did not happen has gone on long enough to
warrant a red run. Its predicate lives in the workflow, where a refusal is visible next to the step it guards, so these
tests extract the step's script from the workflow and run it with the values the scan job would publish, the way the
contract tests of the update step run theirs. The `if:` that keeps it off pull requests is checked on the text.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import unittest
from pathlib import Path

WORKFLOW = Path(__file__).resolve().parents[2] / ".github" / "workflows" / "security.yml"
BASH = shutil.which("bash")
HOUR = 3600


def alert_job() -> str:
    text = WORKFLOW.read_text(encoding="utf-8").replace("\r\n", "\n")
    start = text.index("\n  refresh-alert:")
    return text[start:]


def alert_script() -> str:
    lines = alert_job().split("\n")
    index = lines.index("        run: |")
    body = []
    for line in lines[index + 1:]:
        if line.strip() and not line.startswith("          "):
            break
        body.append(line[10:] if line.startswith("          ") else line)
    return "\n".join(body) + "\n"


@unittest.skipIf(BASH is None, "bash is not available on this platform")
class RefreshAlertStepTest(unittest.TestCase):
    def run_step(self, age_hours: float | str | None, refreshed: str = "false", cause: str = "") -> subprocess.CompletedProcess[str]:
        published = "" if age_hours is None else (age_hours if isinstance(age_hours, str) else str(int(age_hours * HOUR)))
        environment = dict(os.environ)
        environment.update({
            "DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS": "72",
            "AGE_SECONDS_PUBLISHED": published,
            "REFRESHED": refreshed,
            "REFRESH_CAUSE": cause,
        })
        return subprocess.run([BASH, "-eo", "pipefail", "-c", alert_script()], capture_output=True, text=True,
                              env=environment, check=False)

    def test_a_single_missed_daily_refresh_stays_green(self) -> None:
        result = self.run_step(24, cause="NVD_API_KEY was refused by the NVD")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("REFRESH_OVERDUE", result.stdout)

    def test_a_second_missed_refresh_raises_the_alert_with_the_cause_and_the_time_left(self) -> None:
        result = self.run_step(50, cause="NVD_API_KEY was refused by the NVD")
        self.assertEqual(1, result.returncode)
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_ALERT=REFRESH_OVERDUE", result.stdout)
        self.assertIn("NVD_API_KEY was refused by the NVD", result.stdout)
        self.assertIn("50h old", result.stdout)
        self.assertIn("about 22h before", result.stdout)

    def test_a_missing_key_is_alerted_like_a_refused_one(self) -> None:
        result = self.run_step(50, cause="NVD_API_KEY is not configured")
        self.assertEqual(1, result.returncode)
        self.assertIn("NVD_API_KEY is not configured", result.stdout)

    def test_the_threshold_is_two_thirds_of_the_budget_and_inclusive(self) -> None:
        self.assertEqual(1, self.run_step(48).returncode, "48h of 72h is exactly two thirds and alerts")
        self.assertEqual(0, self.run_step(48 - 1 / 3600).returncode, "one second under two thirds stays green")

    def test_a_run_that_refreshed_raises_nothing_whatever_the_age_says(self) -> None:
        result = self.run_step(50, refreshed="true")
        self.assertEqual(0, result.returncode)
        self.assertNotIn("REFRESH_OVERDUE", result.stdout)

    def test_an_unknown_age_raises_no_alert_and_no_arithmetic_error(self) -> None:
        for published in (None, "", "abc", "-5", "12.5"):
            with self.subTest(published=published):
                result = self.run_step(published)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertNotIn("syntax error", result.stderr)
                self.assertNotIn("REFRESH_OVERDUE", result.stdout)

    def test_the_job_never_runs_on_a_pull_request_and_runs_after_a_failed_scan(self) -> None:
        job = alert_job()
        self.assertRegex(job, re.compile(r"\n    if: always\(\) && github\.event_name != 'pull_request'\n"))
        self.assertIn("    needs: dependency-check\n", job)


if __name__ == "__main__":
    unittest.main()
