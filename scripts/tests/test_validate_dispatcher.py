"""Behavioural tests of scripts/validate.ps1 and scripts/validate.cmd, the Windows dispatcher of the validators.

Every guide tells a Windows user to run ``scripts\\validate.cmd <target> [arguments]``. The dispatcher is run here
beside stub validators that record what they received: the targets it lists, the targets it refuses, the arguments it
forwards, and what it answers when the validator it launched fails.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from validator_harness import (SCRIPTS, WINDOWS, FabricatedCheckout, clean_environment, require_powershell,
                               squeeze)

SCRIPTS_UNDER_TEST = ("validate.ps1", "validate.cmd")

STUB_VALIDATOR = """param(
    [string]$Version = 'default',
    [string]$BaseRef = 'none',
    [switch]$SkipPortable
)
"Version=$Version;BaseRef=$BaseRef;SkipPortable=$SkipPortable;Rest=$($args -join ',')" |
    Set-Content -Encoding ASCII -LiteralPath $env:STUB_RECORD
if ($env:STUB_THROW) { throw 'the stub validator refused' }
if ($env:STUB_EXIT) { exit [int]$env:STUB_EXIT }
"""

# The shape of validate-m19.ps1: an advanced script with switches only, which cannot bind a positional argument.
STUB_SWITCHES_ONLY = """[CmdletBinding()]
param(
    [switch]$SkipPackaging
)
"SkipPackaging=$SkipPackaging" | Set-Content -Encoding ASCII -LiteralPath $env:STUB_RECORD
if ($env:STUB_THROW) { throw 'the stub validator refused' }
"""


class ValidateDispatcherTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.powershell = require_powershell()

    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory(prefix="morpheus-dispatcher-", ignore_cleanup_errors=True)
        self.addCleanup(directory.cleanup)
        self.scripts = Path(directory.name) / "scripts"
        self.scripts.mkdir()
        for name in SCRIPTS_UNDER_TEST:
            shutil.copyfile(SCRIPTS / name, self.scripts / name)
        for target, stub in (("m1", STUB_VALIDATOR), ("r9", STUB_SWITCHES_ONLY)):
            with open(self.scripts / f"validate-{target}.ps1", "w", encoding="ascii", newline="\r\n") as handle:
                handle.write(stub)
        self.record = Path(directory.name) / "record.txt"

    def run_command(self, command: list[str], **stub: str) -> subprocess.CompletedProcess:
        environment = clean_environment()
        environment["STUB_RECORD"] = str(self.record)
        environment.update(stub)
        return subprocess.run(command, cwd=self.scripts.parent, env=environment, capture_output=True, text=True,
                              encoding="utf-8", errors="replace", check=False, timeout=120)

    def dispatch(self, *arguments: str, **stub: str) -> subprocess.CompletedProcess:
        return self.run_command([self.powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                                 "Bypass", "-File", str(self.scripts / "validate.ps1"), *arguments], **stub)

    def received(self) -> str | None:
        return self.record.read_text(encoding="ascii").strip() if self.record.is_file() else None

    def assertSays(self, result: subprocess.CompletedProcess, fragment: str) -> None:
        output = result.stdout + result.stderr
        self.assertIn(squeeze(fragment), squeeze(output), output)

    def test_list_prints_exactly_the_targets_that_have_a_validator(self) -> None:
        for spelling in ("list", "--list", "-list", "LIST"):
            with self.subTest(spelling=spelling):
                result = self.dispatch(spelling)

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(["m1", "r9"], result.stdout.split())
                self.assertIsNone(self.received(), "listing the targets runs none")

    def test_the_real_dispatcher_lists_every_validator_of_the_repository_and_nothing_else(self) -> None:
        expected = sorted(path.name[len("validate-"):-len(".ps1")] for path in SCRIPTS.glob("validate-*.ps1"))
        self.assertTrue(expected, "no validator found: the comparison would pass on nothing")

        result = self.run_command([self.powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                                   "Bypass", "-File", str(SCRIPTS / "validate.ps1"), "list"])

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(expected, result.stdout.split())

    def test_no_target_is_refused_with_the_available_ones(self) -> None:
        result = self.dispatch()

        self.assertNotEqual(0, result.returncode)
        self.assertSays(result, "Missing validation target")
        self.assertSays(result, "Available targets: m1, r9")

    def test_a_target_without_a_validator_is_refused_with_the_available_ones(self) -> None:
        result = self.dispatch("m99", "-Version", "1.2.3")

        self.assertNotEqual(0, result.returncode)
        self.assertSays(result, "Validation target 'm99' does not exist. Available targets: m1, r9")
        self.assertIsNone(self.received())

    def test_a_malformed_target_is_refused_before_any_path_is_built_from_it(self) -> None:
        for target in ("m1;calc", "..\\validate-m1", "m1.ps1", "validate-m1", "m"):
            with self.subTest(target=target):
                result = self.dispatch(target)

                self.assertNotEqual(0, result.returncode)
                self.assertSays(result, "Invalid validation target")
                self.assertSays(result, "Available targets: m1, r9")
                self.assertIsNone(self.received())

    def test_the_target_is_matched_without_its_case(self) -> None:
        result = self.dispatch("M1")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("Version=default;BaseRef=none;SkipPortable=False;Rest=", self.received())

    def test_without_arguments_the_validator_receives_none_and_keeps_its_defaults(self) -> None:
        result = self.dispatch("m1")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("Version=default;BaseRef=none;SkipPortable=False;Rest=", self.received())

    def test_a_validator_that_takes_no_positional_argument_is_launched_without_arguments(self) -> None:
        # `scripts\validate.cmd m19` is the documented command, and validate-m19.ps1 declares two switches only.
        result = self.dispatch("r9")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("SkipPackaging=False", self.received())

    def test_the_remaining_arguments_reach_the_validator_as_named_parameters(self) -> None:
        result = self.dispatch("m1", "-Version", "1.2.3", "-BaseRef", "origin/develop", "-SkipPortable")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("Version=1.2.3;BaseRef=origin/develop;SkipPortable=True;Rest=", self.received())

    def test_a_validator_that_exits_non_zero_fails_the_dispatcher(self) -> None:
        result = self.dispatch("m1", STUB_EXIT="3")

        self.assertNotEqual(0, result.returncode)
        self.assertIsNotNone(self.received(), "the validator must have run")

    def test_a_validator_that_throws_fails_the_dispatcher(self) -> None:
        result = self.dispatch("r9", STUB_THROW="1")

        self.assertNotEqual(0, result.returncode)
        self.assertSays(result, "the stub validator refused")

    def test_the_cmd_wrapper_forwards_the_arguments_and_the_failure(self) -> None:
        self.assertTrue(WINDOWS)
        wrapper = [os.environ.get("ComSpec", "cmd.exe"), "/d", "/c", str(self.scripts / "validate.cmd")]

        passed = self.run_command([*wrapper, "m1", "-Version", "1.2.3", "-SkipPortable"])
        self.assertEqual(0, passed.returncode, passed.stdout + passed.stderr)
        self.assertEqual("Version=1.2.3;BaseRef=none;SkipPortable=True;Rest=", self.received())

        failed = self.run_command([*wrapper, "m1"], STUB_EXIT="3")
        self.assertNotEqual(0, failed.returncode)

        unknown = self.run_command([*wrapper, "m99"])
        self.assertNotEqual(0, unknown.returncode)
        self.assertSays(unknown, "Available targets: m1, r9")


class DispatchedValidatorTest(unittest.TestCase):
    """The dispatcher and a real validator together, in the fabricated checkout."""

    @classmethod
    def setUpClass(cls) -> None:
        require_powershell()

    def test_the_documented_m19_command_reaches_the_validator(self) -> None:
        checkout = FabricatedCheckout(self.addCleanup)
        checkout.write("pom.xml", "<project/>")

        run = checkout.powershell("validate.ps1", "m19")

        # Refused by validate-m19 on the tree it was given: the dispatcher launched it.
        self.assertNotEqual(0, run.returncode)
        self.assertTrue(run.says("Exact-head validation requires a clean Git workspace"), run.output)

    def test_a_version_given_to_the_dispatcher_is_the_one_the_validator_checks(self) -> None:
        checkout = FabricatedCheckout(self.addCleanup)

        run = checkout.powershell("validate.ps1", "m20", "-Version", "9.9.9")

        self.assertNotEqual(0, run.returncode)
        self.assertTrue(run.says(f"pom.xml version is {checkout.version}; expected 9.9.9"), run.output)
        self.assertEqual([], checkout.builds())


if __name__ == "__main__":
    unittest.main()
