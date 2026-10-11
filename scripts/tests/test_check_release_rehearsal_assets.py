"""Tests of scripts/check-release-rehearsal-assets.py against the real release workflow.

The list of expected names is read from .github/workflows/release.yml, never restated here: the fabricated directories
are filled from what the script itself reads, so a name added to the workflow is exercised without editing this file.
"""

from __future__ import annotations

import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "check-release-rehearsal-assets.py"
WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"
VERSION = "9.9.9"

_spec = importlib.util.spec_from_file_location("check_release_rehearsal_assets", SCRIPT)
checker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(checker)


def names() -> list[str]:
    return checker.expected_names(WORKFLOW.read_text(encoding="utf-8"), VERSION)


def rehearsable() -> list[str]:
    return [name for name in names() if not name.endswith(tuple(checker.ONLY_A_REAL_RELEASE_PRODUCES))]


def run(directory: Path) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(SCRIPT), str(WORKFLOW), VERSION, str(directory)],
        capture_output=True, text=True, check=False)


class ReleaseRehearsalAssetsTest(unittest.TestCase):
    def test_the_workflow_list_is_read_and_carries_the_version(self):
        self.assertGreaterEqual(len(names()), 4, "the release workflow must expect assets on both platforms")
        self.assertTrue(all(VERSION in name for name in names()), names())
        self.assertLess(len(rehearsable()), len(names()), "the attestation bundles must be set aside")

    def test_a_complete_rehearsal_passes_without_the_attestation_bundles(self):
        with tempfile.TemporaryDirectory() as temp:
            for name in rehearsable():
                (Path(temp) / name).write_text("built", encoding="utf-8")
            result = run(Path(temp))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("RELEASE REHEARSAL asset names", result.stdout)
        self.assertIn("not rehearsed:", result.stdout)
        self.assertNotIn("RELEASE QUALIFICATION PASS", result.stdout)

    def test_an_expected_name_nobody_built_fails_and_is_named(self):
        absent = rehearsable()[0]
        with tempfile.TemporaryDirectory() as temp:
            for name in rehearsable()[1:]:
                (Path(temp) / name).write_text("built", encoding="utf-8")
            result = run(Path(temp))
        self.assertEqual(1, result.returncode)
        self.assertIn(absent, result.stderr)
        self.assertIn("did not build it", result.stderr)

    def test_an_empty_file_fails_and_is_named(self):
        hollow = rehearsable()[-1]
        with tempfile.TemporaryDirectory() as temp:
            for name in rehearsable():
                (Path(temp) / name).write_text("" if name == hollow else "built", encoding="utf-8")
            result = run(Path(temp))
        self.assertEqual(1, result.returncode)
        self.assertIn(hollow, result.stderr)
        self.assertIn("built it empty", result.stderr)

    def test_a_workflow_without_the_list_is_refused_rather_than_passed(self):
        with tempfile.TemporaryDirectory() as temp:
            workflow = Path(temp) / "release.yml"
            workflow.write_text("jobs: {}\n", encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(workflow), VERSION, temp],
                capture_output=True, text=True, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("nothing to compare against", result.stderr)

    def test_the_script_holds_no_asset_name_of_its_own(self):
        source = SCRIPT.read_text(encoding="utf-8")
        for fragment in ("linux-x64", "windows-x64", "setup.exe", ".tar.gz", ".zip"):
            self.assertNotIn(fragment, source, f"{fragment} must come from the release workflow, not from the script")


if __name__ == "__main__":
    unittest.main()
