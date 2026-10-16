"""What the other validators refuse before they need a product: m19, m20, r2, m28 and r3, on both platforms.

test_validator_verdicts holds the eight targets that conclude from one build. The five here go further than a
fabricated checkout can follow: m19 measures a reference machine, m20 builds a tagged release, m28 runs the Windows
installer, r3 chains through m28. What they decide before that is tested here -- their arguments, the tree they accept,
the scope they allow, and for r2 the whole verdict, which is a chain through validate-m27 plus its own checks. What is
not reached is said in test_script_inventory, target by target.
"""

from __future__ import annotations

import unittest
import xml.etree.ElementTree as ET

from validator_harness import (FABRICATED_TESTS, REPOSITORY, FabricatedCheckout, PlayedScenarios, Run, require_bash,
                               require_powershell)

UPGRADE_MODULE = "morpheus-store-sqlite"
MIGRATION = "morpheus-store-sqlite/src/main/resources/db/migration/V002__fabricated.sql"


class Shell:
    extension = "sh"
    require = staticmethod(require_bash)
    output_suffix = "-linux"

    @staticmethod
    def run(checkout: FabricatedCheckout, target: str, version: str | None, base_ref: str | None, build_exit: int,
            extra: tuple[str, ...]) -> Run:
        prefix = f"MORPHEUS_{target.upper()}_"
        environment = {prefix + "SKIP_PORTABLE": "true", "MORPHEUS_STUB_BUILD_EXIT": str(build_exit)}
        if base_ref is not None:
            environment[prefix + "BASE_REF"] = base_ref
        arguments = [] if version is None or target == "m19" else [version]
        return checkout.bash(f"validate-{target}.sh", *arguments, *extra, environment=environment)


class PowerShell:
    extension = "ps1"
    require = staticmethod(require_powershell)
    output_suffix = ""

    @staticmethod
    def run(checkout: FabricatedCheckout, target: str, version: str | None, base_ref: str | None, build_exit: int,
            extra: tuple[str, ...]) -> Run:
        arguments: list[str] = []
        if version is not None and target != "m19":
            arguments += ["-Version", version]
        if target in ("r2", "m28", "r3"):
            arguments += ["-SkipPortable", "-SkipInstaller"]
            if base_ref is not None:
                arguments += ["-BaseRef", base_ref]
        return checkout.powershell(f"validate-{target}.ps1", *arguments, *extra,
                                   environment={"MORPHEUS_STUB_BUILD_EXIT": str(build_exit)})


class EntryContract(PlayedScenarios):
    target: str
    interpreter: type
    # m19 and m20 keep one output directory per platform; the others share one.
    per_platform_output = False
    clean_tree_refusal = "{label} exact-head gate requires no tracked workspace delta before validation"

    @classmethod
    def setUpClass(cls) -> None:
        cls.interpreter.require()

    @classmethod
    def launch(cls, checkout: FabricatedCheckout, version: str | None = "", base_ref: str | None = "develop",
               build_exit: int = 0, extra: tuple[str, ...] = ()) -> Run:
        return cls.interpreter.run(checkout, cls.target, checkout.version if version == "" else version, base_ref,
                                   build_exit, extra)

    @property
    def label(self) -> str:
        return self.target.upper()

    @property
    def output_directory(self) -> str:
        return self.target + (self.interpreter.output_suffix if self.per_platform_output else "")

    def refused(self, scenario: str, *causes: str, before_the_build: bool = False) -> Run:
        checkout, run = self.played(scenario)
        self.assertNotEqual(0, run.returncode, run.output)
        for cause in causes:
            self.assertTrue(run.says(cause), f"the refusal must say {cause!r}:\n{run.output}")
        summary = checkout.summary(self.output_directory)
        if summary is not None:
            # m19 and validate-m20.ps1 write a summary of a refused run; it must say so.
            self.assertIn("Result:FAIL", "".join(summary.split()), summary)
            self.assertNotIn("PASS", summary.splitlines()[0])
        if before_the_build:
            self.assertEqual([], checkout.builds(), "the refusal must come before the build is launched")
        return run

    @staticmethod
    def given_a_tracked_change(checkout, validate):
        checkout.write("pom.xml", "<project/>\n")

    def test_a_tracked_change_is_refused_before_the_build(self) -> None:
        self.refused("a_tracked_change", self.clean_tree_refusal.format(label=self.label), before_the_build=True)


class WholeTreeContract:
    """m19 and m20 qualify an exact head: an untracked file is a change too, and a refusal leaves its record."""

    per_platform_output = True
    clean_tree_refusal = "validation requires a clean Git workspace"

    @staticmethod
    def given_an_untracked_file(checkout, validate):
        checkout.write("stray.txt", "not part of the commit\n")

    def test_an_untracked_file_is_refused_before_the_build(self) -> None:
        self.refused("an_untracked_file", self.clean_tree_refusal, before_the_build=True)

    def test_a_refused_run_leaves_a_failure_summary_that_names_its_cause(self) -> None:
        checkout, run = self.played("a_tracked_change")

        path = checkout.output_file(self.output_directory, "failure-summary.txt")
        self.assertTrue(path.is_file(), f"no failure-summary.txt in validation-output/{self.output_directory}:\n{run.output}")
        failure = path.read_text(encoding="utf-8-sig")
        self.assertIn("VALIDATION FAILURE", failure.splitlines()[0])
        self.assertIn(f"SHA:       {checkout.head()}", failure)
        self.assertIn("Error:", failure)


class M19PowerShellCases:
    """validate-m19.sh reads no argument at all; only the .ps1 declares parameters, so only it can refuse one."""

    @staticmethod
    def given_a_version_argument(checkout, validate):
        return {"extra": ("-Version", checkout.version)}

    def test_a_version_argument_is_refused(self) -> None:
        checkout, run = self.played("a_version_argument")

        self.assertNotEqual(0, run.returncode, run.output)
        self.assertTrue(run.says("Version"), run.output)
        self.assertEqual([], checkout.builds())
        self.assertIsNone(checkout.summary(self.output_directory), "a run refused on its arguments validated nothing")


class M20Cases:
    @staticmethod
    def given_another_version(checkout, validate):
        return {"version": "9.9.9"}

    def test_a_version_the_pom_does_not_carry_is_refused_before_the_build(self) -> None:
        checkout, _ = self.played("another_version")
        self.refused("another_version", f"pom.xml version is {checkout.version}; expected 9.9.9", before_the_build=True)

    @staticmethod
    def given_no_version(checkout, validate):
        return {"version": None}

    def test_without_a_version_the_dated_default_is_refused_before_the_build(self) -> None:
        checkout, _ = self.played("no_version")
        self.refused("no_version", f"pom.xml version is {checkout.version}; expected", before_the_build=True)

    @staticmethod
    def given_a_failed_build(checkout, validate):
        return {"build_exit": 3}

    def test_a_build_that_fails_is_refused_and_no_rehearsal_tag_is_left(self) -> None:
        checkout, _ = self.played("a_failed_build")
        self.refused("a_failed_build")
        self.assertEqual(["clean verify"], checkout.builds())
        self.assertEqual("", checkout.git("tag", "--list"))


class R2Cases:
    """validate-r2 is a link of the chain the Windows leg of ci.yml runs: m28 -> r2 -> m27."""

    @staticmethod
    def given_a_complete_checkout(checkout, validate):
        pass

    def test_a_complete_checkout_passes_and_the_summary_names_the_commit_and_the_inherited_gate(self) -> None:
        checkout, run = self.played("a_complete_checkout")

        self.assertEqual(0, run.returncode, run.output)
        lines = checkout.summary("r2").splitlines()
        self.assertEqual("R2 VALIDATION PASS", lines[0])
        self.assertIn(f"sha={checkout.head()}", lines)
        self.assertIn(f"version={checkout.version}", lines)
        self.assertIn(f"tests={FABRICATED_TESTS}", lines)
        self.assertEqual("M27 VALIDATION PASS", checkout.summary("m27").splitlines()[0])
        self.assertEqual("clean verify", checkout.builds()[0])
        self.assertEqual(1, checkout.builds().count("clean verify"), "the chain builds once")

    def no_summary_of(self, scenario: str, *directories: str) -> None:
        checkout, _ = self.played(scenario)
        for directory in directories:
            self.assertIsNone(checkout.summary(directory), f"validation-output/{directory}/validation-summary.txt")

    @staticmethod
    def given_another_version(checkout, validate):
        return {"version": "9.9.9"}

    def test_a_version_the_reactor_does_not_carry_is_refused_before_the_build(self) -> None:
        self.refused("another_version", "MORPHEUS 9.9.9 version missing from", before_the_build=True)
        self.no_summary_of("another_version", "r2", "m27")

    @staticmethod
    def given_too_few_tests(checkout, validate):
        checkout.total_tests(2, architecture=1)

    def test_a_refusal_of_the_inherited_gate_is_a_refusal_of_this_one(self) -> None:
        self.refused("too_few_tests", "M27", "regression: 2 <")
        self.no_summary_of("too_few_tests", "r2", "m27")

    @staticmethod
    def given_no_upgrade_report(checkout, validate):
        checkout.remove(f"{UPGRADE_MODULE}/target/surefire-reports/TEST-com.morpheus.store.sqlite.R2UpgradeCompatibilityTest.xml")
        checkout.total_tests(FABRICATED_TESTS + 1)

    def test_a_missing_upgrade_report_is_refused_by_name(self) -> None:
        self.refused("no_upgrade_report", "R2 upgrade report is missing", "R2UpgradeCompatibilityTest")
        self.no_summary_of("no_upgrade_report", "r2")

    @staticmethod
    def given_an_upgrade_report_that_ran_nothing(checkout, validate):
        checkout.surefire(UPGRADE_MODULE, 0)
        checkout.total_tests(FABRICATED_TESTS + 1)

    def test_an_upgrade_suite_that_ran_no_test_is_refused(self) -> None:
        self.refused("an_upgrade_report_that_ran_nothing", "R2 upgrade compatibility test failed: tests=0")
        self.no_summary_of("an_upgrade_report_that_ran_nothing", "r2")

    @staticmethod
    def given_a_release_script_with_another_default(checkout, validate):
        script = checkout.root / "distribution/build-release.sh"
        text = script.read_text(encoding="utf-8")
        needle = 'VERSION="${1:-' + checkout.version + '}"'
        if needle not in text:
            raise AssertionError(f"distribution/build-release.sh no longer carries {needle}")
        checkout.write("distribution/build-release.sh", text.replace(needle, 'VERSION="${1:-0.0.1}"'))
        checkout.commit("another default")

    def test_a_release_script_whose_default_version_is_not_the_reactor_s_is_refused(self) -> None:
        self.refused("a_release_script_with_another_default", "Linux release default version is incoherent")
        self.no_summary_of("a_release_script_with_another_default", "r2")

    @staticmethod
    def given_a_refusal_after_a_pass(checkout, validate):
        first = validate()
        if first.returncode != 0 or checkout.summary("r2") is None:
            raise AssertionError(f"the earlier run must pass and leave its summary:\n{first.output}")
        checkout.failing_suite(failures=1)

    def test_a_refused_run_does_not_leave_the_passing_summary_of_an_earlier_run(self) -> None:
        self.refused("a_refusal_after_a_pass", "Surefire failures=1")
        self.no_summary_of("a_refusal_after_a_pass", "r2", "m27")


class M28Cases:
    @staticmethod
    def given_another_version(checkout, validate):
        return {"version": "9.9.9"}

    def test_a_refusal_of_the_inherited_chain_is_a_refusal_of_this_gate(self) -> None:
        checkout, _ = self.played("another_version")
        self.refused("another_version", "MORPHEUS 9.9.9 version missing from", before_the_build=True)
        for directory in ("m28", "r2", "m27"):
            self.assertIsNone(checkout.summary(directory), directory)


def declared_reactor_poms() -> int:
    """The root POM plus one per module it declares, read here independently of the validators."""
    namespace = "{http://maven.apache.org/POM/4.0.0}"
    modules = ET.parse(REPOSITORY / "pom.xml").getroot().findall(f"{namespace}modules/{namespace}module")
    return 1 + len(modules)


class ReactorCountCases:
    """validate-r2 and validate-r3 compare the POMs on disk with the reactor the root POM declares.

    validate-r3 compared them with the 17 POMs of its date and could not pass once the reactor grew (AUD-TST-18): the
    expected count is the root POM's, never a literal, and these cases hold both validators to it.
    """

    @staticmethod
    def given_the_declared_reactor(checkout, validate):
        pass

    def test_the_reactor_the_root_pom_declares_is_accepted_with_its_count(self) -> None:
        checkout, run = self.played("the_declared_reactor")

        self.assertTrue(run.says(f"Maven reactor version: PASS ({checkout.version} across {declared_reactor_poms()} "
                                 "POMs)"), run.output)
        self.assertFalse(run.says("Unexpected Maven reactor POM count"), run.output)

    @staticmethod
    def given_a_pom_the_root_does_not_declare(checkout, validate):
        checkout.write("morpheus-undeclared/pom.xml", f"<project><version>{checkout.version}</version></project>\n")
        checkout.commit("undeclared module")

    def test_a_pom_the_root_does_not_declare_is_refused_with_both_counts_before_the_build(self) -> None:
        expected = declared_reactor_poms()
        run = self.refused("a_pom_the_root_does_not_declare",
                           f"Unexpected Maven reactor POM count: {expected + 1}, expected {expected}",
                           before_the_build=True)
        self.assertFalse(run.says("Maven reactor version: PASS"), run.output)


class R3Cases:
    @staticmethod
    def given_a_workflow_change(checkout, validate):
        checkout.write(".github/workflows/ci.yml", "name: changed\n")
        checkout.commit("workflow")
        return {"base_ref": "main"}

    def test_a_change_to_a_workflow_since_the_base_is_refused_before_the_build(self) -> None:
        self.refused("a_workflow_change", "R3 must not modify GitHub Actions workflows", before_the_build=True)

    @staticmethod
    def given_a_new_migration(checkout, validate):
        checkout.write(MIGRATION, "-- fabricated\n")
        checkout.commit("migration")
        return {"base_ref": "main"}

    def test_a_migration_added_since_the_base_is_refused_before_the_build(self) -> None:
        self.refused("a_new_migration", "R3 must not introduce a SQLite migration", before_the_build=True)


CASES = {"m19": (WholeTreeContract,), "m20": (M20Cases, WholeTreeContract), "r2": (R2Cases, ReactorCountCases),
         "m28": (M28Cases,), "r3": (R3Cases, ReactorCountCases)}

SCRIPTS_UNDER_TEST = tuple(f"validate-{target}.{interpreter.extension}"
                           for target in CASES for interpreter in (Shell, PowerShell))


def _declare() -> None:
    for target, cases in CASES.items():
        for interpreter in (Shell, PowerShell):
            name = f"Validate{target.upper()}{interpreter.extension.capitalize()}EntryTest"
            if target == "m19" and interpreter is PowerShell:
                cases = (M19PowerShellCases, *cases)
            globals()[name] = type(name, (*cases, EntryContract, unittest.TestCase),
                                   {"target": target, "interpreter": interpreter, "__module__": __name__})


_declare()

if __name__ == "__main__":
    unittest.main()
