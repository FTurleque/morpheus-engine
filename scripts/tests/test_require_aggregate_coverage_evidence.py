"""Behavioural tests of the two guards that refuse coverage evidence of the wrong scale, on one list of cases.

Two gates measure coverage on two scales and each writes its own evidence file, whose first line declares the scale.
Every validator that concludes on the aggregate scale calls scripts/lib/require-aggregate-coverage-evidence.sh or
scripts/lib/Require-AggregateCoverageEvidence.ps1 before it reads a ratio. Every case below is run against both.
"""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from validator_harness import SCRIPTS, clean_environment, require_bash, require_powershell, squeeze

SCRIPTS_UNDER_TEST = ("lib/require-aggregate-coverage-evidence.sh", "lib/Require-AggregateCoverageEvidence.ps1")

MISSING = "Missing M21 aggregate coverage evidence"
NOT_AGGREGATE = "M21 coverage evidence is not the aggregate scope"

# (case, file content or None for no file, what a refusal must say or None when the evidence is accepted)
CASES = (
    ("aggregate evidence is accepted", "coverageScope=aggregate\nlineRatio=0.9\n", None),
    ("aggregate evidence with windows line endings is accepted", "coverageScope=aggregate\r\nlineRatio=0.9\r\n", None),
    ("a missing file is refused", None, (MISSING,)),
    ("per-module evidence is refused with the scale it declares", "coverageScope=per-module\nlineRatio=0.9\n",
     (NOT_AGGREGATE, "declares 'per-module'")),
    ("evidence that declares no scale is refused", "lineRatio=0.9\nbranchRatio=0.8\n",
     (NOT_AGGREGATE, "declares '<none>'")),
    ("an empty file is refused", "", (NOT_AGGREGATE, "declares '<none>'")),
    ("an empty scale is refused", "coverageScope=\nlineRatio=0.9\n", (NOT_AGGREGATE, "declares '<none>'")),
    ("a scale that only starts like the aggregate one is refused", "coverageScope=aggregate-per-module\n",
     (NOT_AGGREGATE, "declares 'aggregate-per-module'")),
    ("a scale in another case is refused", "coverageScope=Aggregate\n", (NOT_AGGREGATE, "declares 'Aggregate'")),
    ("the first declaration decides, not a later one", "coverageScope=per-module\ncoverageScope=aggregate\n",
     (NOT_AGGREGATE, "declares 'per-module'")),
    ("a key that only ends like the scale key is not the declaration", "otherCoverageScope=aggregate\n",
     (NOT_AGGREGATE, "declares '<none>'")),
)


class SharedGuardCases:
    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.evidence = Path(directory.name) / "m21-aggregate-coverage-summary.txt"

    def guard(self) -> subprocess.CompletedProcess:
        raise NotImplementedError

    def test_every_shared_case(self) -> None:
        for case, content, refusal in CASES:
            with self.subTest(case=case):
                self.evidence.unlink(missing_ok=True)
                if content is not None:
                    with open(self.evidence, "w", encoding="utf-8", newline="") as handle:
                        handle.write(content)

                result = self.guard()

                if refusal is None:
                    self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                else:
                    self.assertNotEqual(0, result.returncode, case)
                    for fragment in refusal:
                        self.assertIn(squeeze(fragment), squeeze(result.stdout + result.stderr))


class ShellGuardTest(SharedGuardCases, unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.bash = require_bash()

    def guard(self) -> subprocess.CompletedProcess:
        script = (SCRIPTS / "lib" / "require-aggregate-coverage-evidence.sh").as_posix()
        return subprocess.run([self.bash, script, self.evidence.as_posix()], capture_output=True, text=True,
                              check=False, env=clean_environment())


class PowerShellGuardTest(SharedGuardCases, unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.powershell = require_powershell()

    def guard(self) -> subprocess.CompletedProcess:
        script = SCRIPTS / "lib" / "Require-AggregateCoverageEvidence.ps1"
        return subprocess.run([self.powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                               "Bypass", "-File", str(script), "-EvidencePath", str(self.evidence)],
                              capture_output=True, text=True, encoding="utf-8", errors="replace", check=False,
                              env=clean_environment())


if __name__ == "__main__":
    unittest.main()
