"""Tests of scripts/check-release-rehearsal-assets.py against the real release workflow and the real POM.

The list of expected names is read from the publish job of .github/workflows/release.yml, never restated here: the
fabricated listings are filled from what the script itself reads, so a name added to the workflow is exercised without
editing this file.
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
SCRIPTS_UNDER_TEST = ("check-release-rehearsal-assets.py",)
WORKFLOW = ROOT / ".github" / "workflows" / "release.yml"
POM = ROOT / "pom.xml"

_spec = importlib.util.spec_from_file_location("check_release_rehearsal_assets", SCRIPT)
checker = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(checker)

VERSION = checker.product_version(POM)


def names() -> list[str]:
    return checker.expected_names(WORKFLOW.read_text(encoding="utf-8"), VERSION)


def rehearsable() -> list[str]:
    return [name for name in names() if not name.endswith(tuple(checker.ONLY_A_REAL_RELEASE_PRODUCES))]


def run(*arguments: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(SCRIPT), *arguments], capture_output=True, text=True, check=False)


def compare(sizes: dict[str, int], workflow: Path = WORKFLOW) -> subprocess.CompletedProcess:
    """Splits the listing in two files, as the two platform legs do."""
    with tempfile.TemporaryDirectory() as temp:
        entries = sorted(sizes.items())
        listings = []
        for index, half in enumerate((entries[::2], entries[1::2])):
            listing = Path(temp) / f"leg-{index}.tsv"
            listing.write_text("".join(f"{name}\t{size}\n" for name, size in half), encoding="utf-8")
            listings.append(str(listing))
        return run("compare", str(workflow), str(POM), *listings)


class ReleaseRehearsalAssetsTest(unittest.TestCase):
    def test_the_workflow_list_is_read_and_carries_the_version(self):
        self.assertGreaterEqual(len(names()), 4, "the release workflow must expect assets on both platforms")
        self.assertTrue(all(VERSION in name for name in names()), names())
        self.assertLess(len(rehearsable()), len(names()), "the attestation bundles must be set aside")

    def test_the_version_is_the_one_the_pom_declares(self):
        result = run("version", str(POM))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(VERSION, result.stdout.strip())
        self.assertRegex(VERSION, r"^\d+\.\d+\.\d+$")

    def test_a_complete_rehearsal_passes_without_the_attestation_bundles(self):
        result = compare({name: 7 for name in rehearsable()})
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("RELEASE REHEARSAL asset names", result.stdout)
        self.assertIn("not rehearsed:", result.stdout)
        self.assertNotIn("RELEASE QUALIFICATION PASS", result.stdout)

    def test_an_expected_name_nobody_built_fails_and_is_named(self):
        absent = rehearsable()[0]
        result = compare({name: 7 for name in rehearsable()[1:]})
        self.assertEqual(1, result.returncode)
        self.assertIn(absent, result.stderr)
        self.assertIn("did not build it", result.stderr)

    def test_an_empty_file_fails_and_is_named(self):
        hollow = rehearsable()[-1]
        result = compare({name: (0 if name == hollow else 7) for name in rehearsable()})
        self.assertEqual(1, result.returncode)
        self.assertIn(hollow, result.stderr)
        self.assertIn("built it empty", result.stderr)

    def test_a_directory_is_listed_with_names_and_sizes_and_without_its_subdirectories(self):
        with tempfile.TemporaryDirectory() as temp:
            (Path(temp) / "built.zip").write_text("12345", encoding="utf-8")
            (Path(temp) / "empty.sha256").write_text("", encoding="utf-8")
            (Path(temp) / "work").mkdir()
            result = run("list", temp)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["built.zip\t5", "empty.sha256\t0"], result.stdout.splitlines())

    def test_the_list_of_another_job_is_not_the_one_compared(self):
        """A build job with a shorter list of its own, placed first, must not stand in for the publish job's."""
        real = WORKFLOW.read_text(encoding="utf-8").replace("\r\n", "\n")
        decoy = ('  decoy-build:\n    steps:\n      - run: |\n          expected=(\n'
                 '            "release-assets/only-this-${version}.txt"\n          )\n\n')
        with tempfile.TemporaryDirectory() as temp:
            workflow = Path(temp) / "release.yml"
            workflow.write_text(real.replace("jobs:\n", "jobs:\n" + decoy, 1), encoding="utf-8")
            result = compare({f"only-this-{VERSION}.txt": 7}, workflow)
        self.assertEqual(1, result.returncode, "the decoy list was compared instead of the publish job's")
        self.assertIn("did not build it", result.stderr)

    def test_a_workflow_without_the_publish_job_is_refused_rather_than_passed(self):
        with tempfile.TemporaryDirectory() as temp:
            workflow = Path(temp) / "release.yml"
            workflow.write_text("jobs: {}\n", encoding="utf-8")
            result = compare({}, workflow)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("nothing to compare against", result.stderr)

    def test_the_script_holds_no_asset_name_of_its_own(self):
        source = SCRIPT.read_text(encoding="utf-8")
        for fragment in ("linux-x64", "windows-x64", "setup.exe", ".tar.gz", ".zip"):
            self.assertNotIn(fragment, source, f"{fragment} must come from the release workflow, not from the script")


if __name__ == "__main__":
    unittest.main()
