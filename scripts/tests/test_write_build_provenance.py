"""Behavioural tests of the build provenance writer, in its two languages, on the same cases.

``validate-m21`` to ``validate-m27`` end a build by writing ``build-provenance.properties`` and refuse to go on without
it. A checkout that is on no branch -- a detached HEAD without a tag, which is what a person gets from ``git checkout
<sha>`` and what a clone pinned to a commit is -- has no branch name to record: the writer says ``detached``. It is run
here on the fabricated checkout of ``validator_harness``, with its stub Maven Wrapper and its stub ``java``.
"""

from __future__ import annotations

import unittest

from validator_harness import FabricatedCheckout, Run, require_bash, require_powershell

SCRIPTS_UNDER_TEST = ("write-build-provenance.sh", "write-build-provenance.ps1")

PROVENANCE = "target/m21-supply-chain/build-provenance.properties"


class ProvenanceCases:
    """The cases shared by both writers; a subclass says how to run the writer."""

    def write(self, checkout: FabricatedCheckout) -> Run:
        raise NotImplementedError

    def setUp(self) -> None:
        self.checkout = FabricatedCheckout(self.addCleanup)

    def properties(self) -> dict[str, str]:
        text = (self.checkout.root / PROVENANCE).read_text(encoding="utf-8-sig")
        return dict(line.split("=", 1) for line in text.splitlines() if "=" in line)

    def test_a_checkout_on_a_branch_records_the_branch(self) -> None:
        run = self.write(self.checkout)
        self.assertEqual(0, run.returncode, run.output)
        values = self.properties()
        self.assertEqual("develop", values["gitRef"])
        self.assertEqual(self.checkout.head(), values["gitSha"])

    def test_a_detached_head_without_a_tag_records_detached(self) -> None:
        self.checkout.git("checkout", "--quiet", "--detach")
        run = self.write(self.checkout)
        self.assertEqual(0, run.returncode, run.output)
        self.assertEqual("detached", self.properties()["gitRef"])

    def test_a_tag_on_the_commit_is_the_reference(self) -> None:
        self.checkout.git("tag", "v-fabricated")
        self.checkout.git("checkout", "--quiet", "--detach")
        run = self.write(self.checkout)
        self.assertEqual(0, run.returncode, run.output)
        self.assertEqual("v-fabricated", self.properties()["gitRef"])

    def test_a_missing_sbom_is_refused_by_name_and_writes_nothing(self) -> None:
        self.checkout.remove("target/m21-supply-chain/morpheus-sbom.json")
        run = self.write(self.checkout)
        self.assertNotEqual(0, run.returncode, run.output)
        self.assertTrue(run.says("BUILD_PROVENANCE_SBOM_MISSING"), run.output)
        self.assertFalse((self.checkout.root / PROVENANCE).exists())


class ShellProvenanceTest(ProvenanceCases, unittest.TestCase):
    def write(self, checkout: FabricatedCheckout) -> Run:
        require_bash()
        return checkout.bash("write-build-provenance.sh")


class PowerShellProvenanceTest(ProvenanceCases, unittest.TestCase):
    def write(self, checkout: FabricatedCheckout) -> Run:
        require_powershell()
        return checkout.powershell("write-build-provenance.ps1")


if __name__ == "__main__":
    unittest.main()
