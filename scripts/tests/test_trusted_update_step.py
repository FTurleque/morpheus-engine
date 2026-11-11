"""Behavioural tests of the trusted update step of .github/workflows/security.yml.

The step renews the Dependency-Check database from two sources: the NVD API with NVD_API_KEY, then, whenever that did
not refresh, the CVE JSON 2.0 data feeds the NVD publishes, which need no key. Only when both fail does it fall back to
the last trusted cache under its freshness budget. These tests extract the step's script from the workflow and run it
against a stand-in for the Maven Wrapper that answers each source as a scenario says, the way the refresh-alert tests
run the alert job. The sentinel and report scripts the step calls are the real ones.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import time
import unittest
from pathlib import Path

from validator_harness import SCRIPTS, require_bash

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "security.yml"
# A step of a workflow is run here, not a file of scripts/.
SCRIPTS_UNDER_TEST = ()
STEP = "      - name: Update Dependency-Check vulnerability database (trusted events)"

# Answers the key path with KEY_OUTPUT and KEY_EXIT, the data-feed path with DATAFEED_EXIT, and records every call.
FAKE_WRAPPER = """#!/usr/bin/env bash
echo "$*" >> "${CALLS_LOG}"
case "$*" in
  *-DnvdDatafeedUrl=*) exit "${DATAFEED_EXIT}" ;;
  *) [[ -n "${KEY_OUTPUT:-}" ]] && echo "${KEY_OUTPUT}"; exit "${KEY_EXIT}" ;;
esac
"""


def update_script() -> str:
    lines = WORKFLOW.read_text(encoding="utf-8").replace("\r\n", "\n").split("\n")
    index = lines.index("        run: |", lines.index(STEP))
    body = []
    for line in lines[index + 1:]:
        if line.strip() and not line.startswith("          "):
            break
        body.append(line[10:] if line.startswith("          ") else line)
    return "\n".join(body) + "\n"


class TrustedUpdateStepTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.bash = require_bash()

    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.workspace = Path(directory.name)
        shutil.copytree(SCRIPTS, self.workspace / "scripts")
        wrapper = self.workspace / "mvnw"
        wrapper.write_text(FAKE_WRAPPER, encoding="utf-8", newline="\n")
        wrapper.chmod(0o755)
        self.output = self.workspace / "github-output"
        self.calls = self.workspace / "calls"
        self.data = self.workspace / "target" / "dependency-check-data"

    def cached_sentinel(self, age_hours: int) -> None:
        self.data.mkdir(parents=True)
        (self.data / "dependency-check-refresh.sentinel").write_text(
            f"refreshedAtEpoch={int(time.time()) - age_hours * 3600}\nschemaVersion=5.6\npluginVersion=13.0.0\n"
            "refreshedBy=NVD API key refresh\n", encoding="utf-8", newline="\n")

    def run_step(self, key: str = "", key_exit: int = 0, key_output: str = "",
                 datafeed_exit: int = 0) -> subprocess.CompletedProcess[str]:
        environment = {name: value for name, value in os.environ.items() if not name.startswith("GITHUB_")}
        environment.update({
            "GITHUB_WORKSPACE": self.workspace.as_posix(),
            "GITHUB_OUTPUT": self.output.as_posix(),
            "RUNNER_TEMP": self.workspace.as_posix(),
            "DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS": "72",
            "DEPENDENCY_CHECK_SCHEMA_VERSION": "5.6",
            "DEPENDENCY_CHECK_PLUGIN_VERSION": "13.0.0",
            "NVD_DATAFEED_URL": "https://nvd.example/nvdcve-2.0-{0}.json.gz",
            "NVD_API_KEY": key,
            "KEY_EXIT": str(key_exit),
            "KEY_OUTPUT": key_output,
            "DATAFEED_EXIT": str(datafeed_exit),
            "CALLS_LOG": self.calls.as_posix(),
        })
        return subprocess.run([self.bash, "-eo", "pipefail", "-c", update_script()], cwd=self.workspace,
                              capture_output=True, text=True, env=environment, check=False)

    def outputs(self) -> dict[str, str]:
        if not self.output.exists():
            return {}
        return dict(line.split("=", 1) for line in self.output.read_text(encoding="utf-8").splitlines() if "=" in line)

    def sentinel_source(self) -> str | None:
        sentinel = self.data / "dependency-check-refresh.sentinel"
        if not sentinel.exists():
            return None
        lines = sentinel.read_text(encoding="utf-8").splitlines()
        return next(line.split("=", 1)[1] for line in lines if line.startswith("refreshedBy="))

    def calls_made(self) -> list[str]:
        return self.calls.read_text(encoding="utf-8").splitlines() if self.calls.exists() else []

    def test_a_working_key_refreshes_and_never_reaches_the_data_feed(self) -> None:
        result = self.run_step(key="key")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("true", self.outputs().get("updated"))
        self.assertEqual("NVD API key refresh", self.sentinel_source())
        self.assertEqual(1, len(self.calls_made()))
        self.assertNotIn("nvdDatafeedUrl", self.calls_made()[0])

    def test_without_a_key_the_data_feed_refreshes_and_says_the_gate_has_one_source(self) -> None:
        result = self.run_step()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("true", self.outputs().get("updated"))
        self.assertEqual("NVD data feed refresh", self.sentinel_source())
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_REFRESH=REFRESHED_FROM_DATA_FEED", result.stdout)
        self.assertIn("NVD_API_KEY is not configured", result.stdout)
        self.assertEqual(["-Pd2-security"], [call.split()[0] for call in self.calls_made()])
        self.assertIn("-DnvdDatafeedUrl=https://nvd.example/nvdcve-2.0-{0}.json.gz", self.calls_made()[0])
        self.assertNotIn("nvdApiKey", self.calls_made()[0])

    def test_a_refused_key_is_named_and_the_data_feed_refreshes(self) -> None:
        result = self.run_step(key="key", key_exit=1, key_output="NvdApiException: Invalid API Key")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("Replace NVD_API_KEY", result.stdout)
        self.assertEqual("NVD data feed refresh", self.sentinel_source())
        self.assertEqual("true", self.outputs().get("updated"))
        self.assertEqual(2, len(self.calls_made()))

    def test_an_api_outage_with_the_key_configured_is_covered_by_the_data_feed(self) -> None:
        result = self.run_step(key="key", key_exit=1, key_output="HTTP 503")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("NVD data feed refresh", self.sentinel_source())
        self.assertNotIn("Replace NVD_API_KEY", result.stdout)

    def test_both_sources_failing_fall_back_to_a_fresh_cache_and_publish_both_causes(self) -> None:
        self.cached_sentinel(age_hours=10)
        result = self.run_step(datafeed_exit=1)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        outputs = self.outputs()
        self.assertEqual("false", outputs.get("updated"))
        self.assertEqual("NVD_API_KEY is not configured; the NVD data feed refresh failed", outputs.get("refresh_cause"))
        self.assertEqual("NVD API key refresh", self.sentinel_source(), "a failed refresh must not re-date the cache")
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_REFRESH=REFRESH_FAILED", result.stdout)

    def test_both_sources_failing_over_a_stale_cache_refuse_the_scan(self) -> None:
        self.cached_sentinel(age_hours=80)
        result = self.run_step(key="key", key_exit=1, key_output="HTTP 503", datafeed_exit=1)
        self.assertEqual(1, result.returncode)
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_FAILURE=STALE_DATABASE", result.stdout)
        self.assertIn("Both refresh sources failed", result.stdout)

    def test_both_sources_failing_without_any_cache_refuse_the_scan(self) -> None:
        result = self.run_step(datafeed_exit=1)
        self.assertEqual(1, result.returncode)
        self.assertIn("MORPHEUS_DEPENDENCY_CHECK_FAILURE=STALE_DATABASE", result.stdout)
        self.assertIsNone(self.sentinel_source())


if __name__ == "__main__":
    unittest.main()
