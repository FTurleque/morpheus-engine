"""Behavioural tests of the two readers of config/m21-quality-ratchets.properties, on one list of cases.

validate-m21 and validate-d2 read their minimums through scripts/lib/read-quality-ratchet.sh on Linux and through
scripts/lib/Read-QualityRatchets.ps1 on Windows. A refusal that names no key, a default that hides a missing key, a
carriage return that survives a Windows checkout, or a value one reader accepts and the other refuses would give the
same commit two verdicts. Every case below is run against both readers: a case added for one is added for the other.
"""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from validator_harness import SCRIPTS, clean_environment, require_bash, require_powershell, squeeze

SCRIPTS_UNDER_TEST = ("lib/read-quality-ratchet.sh", "lib/Read-QualityRatchets.ps1")

# (case, file content or None for no file, key, kind, the value read or None, what a refusal must say)
CASES = (
    ("an integer is read", "# comment\ntestsMinimum=3820\narchitectureTestsMinimum=585\n",
     "testsMinimum", "integer", "3820", None),
    ("a ratio is read", "aggregateLineCoverageMinimum=0.900\n",
     "aggregateLineCoverageMinimum", "ratio", "0.900", None),
    ("a ratio of one is read", "aggregateLineCoverageMinimum=1\n",
     "aggregateLineCoverageMinimum", "ratio", "1", None),
    ("a missing key is refused by name and never defaulted", "testsMinimum=3820\n",
     "architectureTestsMinimum", "integer", None, "Missing M21 quality ratchet: architectureTestsMinimum"),
    ("a missing file is refused", None,
     "testsMinimum", "integer", None, "Missing M21 quality ratchet configuration"),
    ("a count that is not a number is refused by name", "testsMinimum=many\n",
     "testsMinimum", "integer", None, "M21 quality ratchet testsMinimum must be a positive integer: 'many'"),
    ("a count of zero is refused by name", "testsMinimum=0\n",
     "testsMinimum", "integer", None, "M21 quality ratchet testsMinimum must be a positive integer"),
    ("a negative count is refused by name", "testsMinimum=-5\n",
     "testsMinimum", "integer", None, "M21 quality ratchet testsMinimum must be a positive integer"),
    ("a count with a fraction is refused by name", "testsMinimum=3820.5\n",
     "testsMinimum", "integer", None, "M21 quality ratchet testsMinimum must be a positive integer"),
    ("a ratio of zero is refused by name", "aggregateLineCoverageMinimum=0\n",
     "aggregateLineCoverageMinimum", "ratio", None, "aggregateLineCoverageMinimum must be a ratio in (0, 1]"),
    ("a ratio written 0.0 is refused by name", "aggregateLineCoverageMinimum=0.0\n",
     "aggregateLineCoverageMinimum", "ratio", None, "aggregateLineCoverageMinimum must be a ratio in (0, 1]"),
    ("a ratio above one is refused by name", "aggregateLineCoverageMinimum=1.5\n",
     "aggregateLineCoverageMinimum", "ratio", None, "aggregateLineCoverageMinimum must be a ratio in (0, 1]"),
    ("a ratio that is not a number is refused by name", "aggregateLineCoverageMinimum=abc\n",
     "aggregateLineCoverageMinimum", "ratio", None, "aggregateLineCoverageMinimum must be a ratio in (0, 1]"),
    ("a percentage is refused by name", "aggregateLineCoverageMinimum=90\n",
     "aggregateLineCoverageMinimum", "ratio", None, "aggregateLineCoverageMinimum must be a ratio in (0, 1]"),
    ("a ratio in exponent notation is refused by name", "aggregateLineCoverageMinimum=9e-1\n",
     "aggregateLineCoverageMinimum", "ratio", None, "aggregateLineCoverageMinimum must be a ratio in (0, 1]"),
    ("a signed ratio is refused by name", "aggregateLineCoverageMinimum=+0.9\n",
     "aggregateLineCoverageMinimum", "ratio", None, "aggregateLineCoverageMinimum must be a ratio in (0, 1]"),
    ("windows line endings leave no carriage return in the value",
     "testsMinimum=3820\r\narchitectureTestsMinimum=585\r\n", "testsMinimum", "integer", "3820", None),
    ("windows line endings leave no carriage return in the last value",
     "testsMinimum=3820\r\narchitectureTestsMinimum=585\r\n", "architectureTestsMinimum", "integer", "585", None),
    ("a key that is only the end of another is not read", "architectureTestsMinimum=585\n",
     "testsMinimum", "integer", None, "Missing M21 quality ratchet: testsMinimum"),
    ("a key that is only the start of another is not read", "testsMinimumExtra=7\n",
     "testsMinimum", "integer", None, "Missing M21 quality ratchet: testsMinimum"),
    ("a commented key is not read", "# testsMinimum=1\n",
     "testsMinimum", "integer", None, "Missing M21 quality ratchet: testsMinimum"),
    ("the first of two declarations is the one read", "testsMinimum=3820\ntestsMinimum=1\n",
     "testsMinimum", "integer", "3820", None),
    ("the generated minimum is read beside the written one", "testsMinimum=2600\ngeneratedTestsMinimum=1570\n",
     "generatedTestsMinimum", "integer", "1570", None),
    ("the written minimum is not the generated one", "generatedTestsMinimum=1570\ntestsMinimum=2600\n",
     "testsMinimum", "integer", "2600", None),
    ("a missing generated minimum is refused by name and not replaced by the written one", "testsMinimum=2600\n",
     "generatedTestsMinimum", "integer", None, "Missing M21 quality ratchet: generatedTestsMinimum"),
    ("a generated minimum of zero is refused by name", "generatedTestsMinimum=0\n",
     "generatedTestsMinimum", "integer", None, "M21 quality ratchet generatedTestsMinimum must be a positive integer"),
    ("a generated minimum that is not a count is refused by name", "generatedTestsMinimum=many\n",
     "generatedTestsMinimum", "integer", None,
     "M21 quality ratchet generatedTestsMinimum must be a positive integer: 'many'"),
)


class SharedReaderCases:
    """The cases of CASES, against one implementation. ``read`` returns (exit code, value printed, messages)."""

    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.properties = Path(directory.name) / "ratchets.properties"

    def read(self, key: str, kind: str) -> tuple[int, str, str]:
        raise NotImplementedError

    def test_every_shared_case(self) -> None:
        self.assertGreater(len(CASES), 10, "too few cases: the two readers would agree on nothing")
        for case, content, key, kind, value, refusal in CASES:
            with self.subTest(case=case):
                self.properties.unlink(missing_ok=True)
                if content is not None:
                    # newline="" keeps the line endings the case asks for instead of translating them.
                    with open(self.properties, "w", encoding="utf-8", newline="") as handle:
                        handle.write(content)

                code, printed, messages = self.read(key, kind)

                if refusal is None:
                    self.assertEqual(0, code, messages)
                    self.assertEqual(f"[{value}]", printed)
                else:
                    self.assertNotEqual(0, code, f"{case}: read {printed!r}")
                    self.assertIn(squeeze(refusal), squeeze(messages))
                    self.assertEqual("", printed, "a refused read must print no value a caller could use")


class ShellReaderTest(SharedReaderCases, unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.bash = require_bash()

    def read(self, key: str, kind: str) -> tuple[int, str, str]:
        library = (SCRIPTS / "lib" / "read-quality-ratchet.sh").as_posix()
        script = (f'. "{library}"; V="$(read_quality_ratchet "{self.properties.as_posix()}" {key} {kind})" || exit 1; '
                  'printf "[%s]" "$V"')
        result = subprocess.run([self.bash, "-c", script], capture_output=True, text=True, check=False,
                                env=clean_environment())
        return result.returncode, result.stdout, result.stderr


class PowerShellReaderTest(SharedReaderCases, unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.powershell = require_powershell()

    def read(self, key: str, kind: str) -> tuple[int, str, str]:
        library = SCRIPTS / "lib" / "Read-QualityRatchets.ps1"
        script = (f"$ErrorActionPreference = 'Stop'; . '{library}'; "
                  f"$v = Get-QualityRatchet -Path '{self.properties}' -Key {key} -Kind {kind}; "
                  "[Console]::Out.Write('[' + $v + ']')")
        result = subprocess.run([self.powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                                 "Bypass", "-Command", script], capture_output=True, text=True, encoding="utf-8",
                                errors="replace", check=False, env=clean_environment())
        return result.returncode, result.stdout, result.stderr


if __name__ == "__main__":
    unittest.main()
