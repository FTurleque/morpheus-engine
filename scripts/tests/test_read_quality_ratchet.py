"""Behavioural tests of scripts/lib/read-quality-ratchet.sh, the one reader of config/m21-quality-ratchets.properties.

validate-m21.sh and validate-d2.sh read their presence minimums through it, so a refusal that names no key, a default
that hides a missing key, or a carriage return that survives a Windows checkout would reach both validators. The
PowerShell twin (Read-QualityRatchets.ps1) has no CI lane and is verified by hand on Windows.
"""

from __future__ import annotations

import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

LIBRARY = Path(__file__).resolve().parents[1] / "lib" / "read-quality-ratchet.sh"
BASH = shutil.which("bash")


@unittest.skipIf(BASH is None, "bash is not available on this platform")
class ReadQualityRatchetTest(unittest.TestCase):
    def setUp(self) -> None:
        self._directory = tempfile.TemporaryDirectory()
        self.properties = Path(self._directory.name) / "ratchets.properties"

    def tearDown(self) -> None:
        self._directory.cleanup()

    def read(self, key: str, kind: str, content: str | None = None) -> subprocess.CompletedProcess[str]:
        if content is not None:
            # newline="" keeps the line endings the case asks for instead of translating them.
            with open(self.properties, "w", encoding="utf-8", newline="") as handle:
                handle.write(content)
        script = f'. "{LIBRARY.as_posix()}"; V="$(read_quality_ratchet "{self.properties.as_posix()}" {key} {kind})" || exit 1; printf "[%s]" "$V"'
        return subprocess.run([BASH, "-c", script], capture_output=True, text=True, check=False)

    def test_an_integer_is_read(self) -> None:
        result = self.read("testsMinimum", "integer", "# comment\ntestsMinimum=3820\narchitectureTestsMinimum=585\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("[3820]", result.stdout)

    def test_a_ratio_is_read(self) -> None:
        result = self.read("aggregateLineCoverageMinimum", "ratio", "aggregateLineCoverageMinimum=0.900\n")
        self.assertEqual("[0.900]", result.stdout)

    def test_a_missing_key_is_refused_by_name_and_never_defaulted(self) -> None:
        result = self.read("architectureTestsMinimum", "integer", "testsMinimum=3820\n")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Missing M21 quality ratchet: architectureTestsMinimum", result.stderr)
        self.assertEqual("", result.stdout)

    def test_a_missing_file_is_refused_naming_the_file(self) -> None:
        result = self.read("testsMinimum", "integer")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Missing M21 quality ratchet configuration", result.stderr)

    def test_a_non_integer_count_is_refused_by_name(self) -> None:
        result = self.read("testsMinimum", "integer", "testsMinimum=many\n")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("M21 quality ratchet testsMinimum must be a positive integer: 'many'", result.stderr)

    def test_a_zero_count_is_refused_by_name(self) -> None:
        result = self.read("testsMinimum", "integer", "testsMinimum=0\n")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("testsMinimum must be a positive integer", result.stderr)

    def test_a_ratio_outside_zero_one_is_refused_by_name(self) -> None:
        for value in ("0", "0.0", "1.5", "abc"):
            with self.subTest(value=value):
                result = self.read("aggregateLineCoverageMinimum", "ratio", f"aggregateLineCoverageMinimum={value}\n")
                self.assertNotEqual(0, result.returncode)
                self.assertIn("aggregateLineCoverageMinimum must be a ratio in (0, 1]", result.stderr)

    def test_windows_line_endings_leave_no_carriage_return_in_the_value(self) -> None:
        result = self.read("testsMinimum", "integer", "testsMinimum=3820\r\narchitectureTestsMinimum=585\r\n")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("[3820]", result.stdout)

    def test_a_key_that_is_only_a_prefix_of_another_is_not_read(self) -> None:
        result = self.read("testsMinimum", "integer", "architectureTestsMinimum=585\n")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Missing M21 quality ratchet: testsMinimum", result.stderr)


if __name__ == "__main__":
    unittest.main()
