"""Behavioural tests of the Authenticode verification and of the signing entry point, on real Windows binaries.

The verification (scripts/lib/MorpheusAuthenticode.ps1) is what the release qualification and the release rehearsal
call; the entry point (distribution/windows/sign-windows-binary.ps1) is the only caller of a signing tool. Both answer
by name, and each case below asserts the name, not only a failure.

The binaries are made by the test: an executable compiled for the run (signed by nobody and listed in no catalog), a
copy of it signed with an identity created for the run and removed with it, a copy of a binary validly signed by a third
party (Git for Windows), the same copy with one byte changed, and a copy of a Windows binary that verifies through a
catalog only. Authenticode and the certificate stores exist on Windows only: elsewhere these cases are skipped by the
platform condition of require_powershell, and the Windows leg of ci.yml runs them.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

from validator_harness import REPOSITORY, SCRIPTS, clean_environment, require_powershell

SCRIPTS_UNDER_TEST = ("lib/MorpheusAuthenticode.ps1",)

VERIFICATION = SCRIPTS / "lib" / "MorpheusAuthenticode.ps1"
ENTRY_POINT = REPOSITORY / "distribution" / "windows" / "sign-windows-binary.ps1"
MARKER = "marker-6f1d0c2e-never-printed"


def quoted(value: object) -> str:
    """A PowerShell single-quoted literal: an apostrophe in a path is doubled, never taken as the end of the string."""
    return "'" + str(value).replace("'", "''") + "'"


class WindowsAuthenticodeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.powershell = require_powershell()
        directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(directory.cleanup)
        cls.work = Path(directory.name)
        cls.unsigned = cls.work / "unsigned.exe"
        cls.test_signed = cls.work / "test-signed.exe"
        cls.third_party = cls.work / "third-party.exe"
        cls.altered = cls.work / "altered.exe"
        cls.catalog = cls.work / "catalog-only.exe"

        git = shutil.which("git")
        if git is None:
            raise AssertionError("git is required: its Windows build is the validly signed third-party binary")
        signed_git = Path(git).resolve().parent.parent / "cmd" / "git.exe"
        shutil.copyfile(signed_git if signed_git.is_file() else git, cls.third_party)
        altered_bytes = bytearray(cls.third_party.read_bytes())
        altered_bytes[len(altered_bytes) // 2] ^= 0xFF
        cls.altered.write_bytes(bytes(altered_bytes))
        shutil.copyfile(Path(os.environ["SystemRoot"]) / "System32" / "whoami.exe", cls.catalog)

        # The identity is created alone, and its removal registered before anything else can fail.
        created = cls.run_powershell("""
            $certificate = New-SelfSignedCertificate -Type CodeSigningCert -CertStoreLocation 'Cert:\\CurrentUser\\My' `
                -Subject ('CN=MORPHEUS script test identity ' + [Guid]::NewGuid().ToString('N')) `
                -KeyExportPolicy NonExportable -NotAfter (Get-Date).AddDays(1)
            [pscustomobject]@{ thumbprint = $certificate.Thumbprint; subject = $certificate.Subject } |
                ConvertTo-Json -Compress
        """)
        identity = json.loads(created.stdout.strip().splitlines()[-1])
        cls.thumbprint = identity["thumbprint"]
        cls.test_subject = identity["subject"]
        cls.addClassCleanup(cls.remove_identity)

        # Signing a disposable binary is the fixture of this test, not a build step: the rule that keeps the signing
        # entry point the only caller of a signing tool leaves scripts/tests out for that reason.
        prepared = cls.run_powershell(f"""
            Add-Type -OutputAssembly {quoted(cls.unsigned)} -OutputType ConsoleApplication `
                -TypeDefinition 'public static class P {{ public static void Main() {{ }} }}'
            Copy-Item -LiteralPath {quoted(cls.unsigned)} -Destination {quoted(cls.test_signed)}
            $certificate = Get-Item -LiteralPath 'Cert:\\CurrentUser\\My\\{cls.thumbprint}'
            Set-AuthenticodeSignature -LiteralPath {quoted(cls.test_signed)} -Certificate $certificate -HashAlgorithm SHA256 |
                Out-Null
            $third = Get-AuthenticodeSignature -LiteralPath {quoted(cls.third_party)}
            [pscustomobject]@{{ status = [string]$third.Status; subject = [string]$third.SignerCertificate.Subject }} |
                ConvertTo-Json -Compress
        """)
        third_party = json.loads(prepared.stdout.strip().splitlines()[-1])
        if third_party["status"] != "Valid":
            raise AssertionError(f"the third-party binary is not validly signed here: {third_party}")
        cls.third_party_subject = third_party["subject"]

    @classmethod
    def remove_identity(cls) -> None:
        left = cls.run_powershell(f"""
            Get-ChildItem -Path 'Cert:\\CurrentUser' -Recurse | Where-Object {{
                $_.PSObject.Properties['Thumbprint'] -and $_.Thumbprint -eq '{cls.thumbprint}' }} | ForEach-Object {{
                Remove-Item -LiteralPath $_.PSPath -DeleteKey -ErrorAction SilentlyContinue
                if (Test-Path -LiteralPath $_.PSPath) {{ Remove-Item -LiteralPath $_.PSPath }} }}
            @(Get-ChildItem -Path 'Cert:\\' -Recurse | Where-Object {{
                $_.PSObject.Properties['Thumbprint'] -and $_.Thumbprint -eq '{cls.thumbprint}' }}).Count
        """)
        if left.stdout.strip().splitlines()[-1] != "0":
            raise AssertionError(f"the test identity outlived the test: {left.stdout}")

    @classmethod
    def run_powershell(cls, script: str, environment: dict[str, str] | None = None,
                       check: bool = True) -> subprocess.CompletedProcess:
        result = subprocess.run([cls.powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                                 "Bypass", "-Command", "$ErrorActionPreference = 'Stop'\n" + script],
                                capture_output=True, text=True, encoding="utf-8", errors="replace", check=False,
                                env=environment or clean_environment())
        if check and result.returncode != 0:
            raise AssertionError(f"PowerShell failed ({result.returncode}): {result.stdout}{result.stderr}")
        return result

    def verify(self, path: Path, expected_subject: str, required: bool = True) -> dict[str, str]:
        switch = "-SignatureRequired" if required else ""
        result = self.run_powershell(f"""
            . {quoted(VERIFICATION)}
            Test-MorpheusAuthenticode -Path {quoted(path)} -ExpectedSubject {quoted(expected_subject)} {switch} |
                Select-Object Outcome, Signer, Detail | ConvertTo-Json -Compress
        """)
        return json.loads(result.stdout.strip().splitlines()[-1])

    # -- The verification ---------------------------------------------------------------------------------------------

    def test_an_unsigned_binary_is_refused_when_a_signature_is_required(self) -> None:
        self.assertEqual("AUTHENTICODE_SIGNATURE_MISSING", self.verify(self.unsigned, "CN=Stand-in")["Outcome"])

    def test_an_unsigned_binary_is_reported_unsigned_when_no_signature_is_required(self) -> None:
        self.assertEqual("UNSIGNED", self.verify(self.unsigned, "CN=Stand-in", required=False)["Outcome"])

    def test_a_catalog_signature_does_not_count_as_a_signature_the_binary_carries(self) -> None:
        self.assertEqual("AUTHENTICODE_SIGNATURE_MISSING", self.verify(self.catalog, "CN=Stand-in")["Outcome"])

    def test_another_publishers_valid_signature_is_refused_with_the_signer_it_found(self) -> None:
        answer = self.verify(self.third_party, "CN=Stand-in")
        self.assertEqual("AUTHENTICODE_SIGNER_UNEXPECTED", answer["Outcome"])
        self.assertIn(self.third_party_subject, answer["Detail"])

    def test_a_signed_binary_is_refused_while_no_publisher_is_declared(self) -> None:
        self.assertEqual("AUTHENTICODE_PUBLISHER_NOT_DECLARED", self.verify(self.third_party, "")["Outcome"])

    def test_a_binary_altered_after_signing_is_refused_whatever_is_required(self) -> None:
        for required in (True, False):
            with self.subTest(required=required):
                answer = self.verify(self.altered, self.third_party_subject, required=required)
                self.assertEqual("AUTHENTICODE_SIGNATURE_INVALID", answer["Outcome"])

    def test_a_signature_that_chains_to_no_trusted_root_is_refused(self) -> None:
        answer = self.verify(self.test_signed, self.test_subject)
        self.assertEqual("AUTHENTICODE_SIGNATURE_NOT_TRUSTED", answer["Outcome"])

    def test_the_positive_control_passes(self) -> None:
        answer = self.verify(self.third_party, self.third_party_subject)
        self.assertEqual("PASS", answer["Outcome"], answer["Detail"])

    def test_the_baseline_requires_a_signature_from_its_version_on(self) -> None:
        result = self.run_powershell(f"""
            . {quoted(VERIFICATION)}
            @((Test-MorpheusSigningRequired -Version '1.2.1' -Baseline ''),
              (Test-MorpheusSigningRequired -Version '1.2.1' -Baseline '1.3.0'),
              (Test-MorpheusSigningRequired -Version '1.3.0' -Baseline '1.3.0'),
              (Test-MorpheusSigningRequired -Version '1.10.0' -Baseline '1.9.0')) -join ','
        """)
        self.assertEqual("False,False,True,True", result.stdout.strip().splitlines()[-1])

    def test_a_missing_declaration_is_refused_by_name(self) -> None:
        result = self.run_powershell(f"""
            . {quoted(VERIFICATION)}
            Read-MorpheusSigningDeclaration -Repository {quoted(self.work)}
        """, check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("AUTHENTICODE_PUBLISHER_NOT_DECLARED", result.stdout + result.stderr)

    def test_the_committed_declaration_is_read(self) -> None:
        result = self.run_powershell(f"""
            . {quoted(VERIFICATION)}
            $declaration = Read-MorpheusSigningDeclaration -Repository {quoted(REPOSITORY)}
            'read:' + ($null -ne $declaration.PublisherSubject)
        """)
        self.assertEqual("read:True", result.stdout.strip().splitlines()[-1])

    # -- The signing entry point --------------------------------------------------------------------------------------

    def sign(self, state: str | None, target: Path, thumbprint: str | None = None) -> subprocess.CompletedProcess:
        environment = clean_environment()
        environment["MORPHEUS_WINDOWS_SIGNING_CREDENTIAL"] = MARKER
        environment["MORPHEUS_WINDOWS_SIGNING_PASSWORD"] = MARKER
        if state is not None:
            environment["MORPHEUS_WINDOWS_SIGNING_STATE"] = state
        if thumbprint is not None:
            environment["MORPHEUS_WINDOWS_SIGNING_TEST_THUMBPRINT"] = thumbprint
        return subprocess.run([self.powershell, "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                               "Bypass", "-File", str(ENTRY_POINT), "-Path", str(target)],
                              capture_output=True, text=True, encoding="utf-8", errors="replace", check=False,
                              env=environment)

    def fresh_copy(self, name: str) -> Path:
        target = self.work / name
        shutil.copyfile(self.unsigned, target)
        return target

    def test_an_unknown_state_is_refused(self) -> None:
        for state in (None, "", "Test", "none", "rehearsal"):
            with self.subTest(state=state):
                result = self.sign(state, self.fresh_copy("unknown-state.exe"))
                self.assertNotEqual(0, result.returncode)
                self.assertIn("WINDOWS_SIGNING_FAILED", result.stderr)
                self.assertIn("unknown signing state", result.stderr)

    def test_the_release_state_without_a_configured_identity_fails_and_leaves_the_file_unsigned(self) -> None:
        target = self.fresh_copy("release-state.exe")
        result = self.sign("release", target)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("WINDOWS_SIGNING_FAILED", result.stderr)
        self.assertEqual("AUTHENTICODE_SIGNATURE_MISSING", self.verify(target, "CN=Stand-in")["Outcome"])

    def test_the_test_state_signs_with_the_selected_identity(self) -> None:
        target = self.fresh_copy("test-state.exe")
        result = self.sign("test", target, self.thumbprint)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        answer = self.verify(target, "CN=Stand-in")
        self.assertEqual("AUTHENTICODE_SIGNATURE_NOT_TRUSTED", answer["Outcome"])
        self.assertEqual(self.test_subject, answer["Signer"])

    def test_the_test_state_without_an_identity_in_the_store_fails(self) -> None:
        result = self.sign("test", self.fresh_copy("absent-identity.exe"), "0" * 40)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("WINDOWS_SIGNING_FAILED", result.stderr)
        self.assertIn("not in the personal certificate store", result.stderr)

    def test_the_entry_point_prints_nothing_it_was_given_through_the_environment(self) -> None:
        runs = (self.sign("release", self.fresh_copy("echo-release.exe")),
                self.sign("test", self.fresh_copy("echo-test.exe"), self.thumbprint),
                self.sign("bogus", self.fresh_copy("echo-bogus.exe")))
        for result in runs:
            with self.subTest(arguments=result.args[-1]):
                output = result.stdout + result.stderr
                self.assertNotIn(MARKER, output)
                self.assertNotIn(self.thumbprint, output)


if __name__ == "__main__":
    unittest.main()
