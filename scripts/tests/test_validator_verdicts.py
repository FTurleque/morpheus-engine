"""The refusals of the validators that conclude from a build, provoked on a fabricated checkout.

A validator that stops refusing answers PASS, and nothing fails: that is how validate-d2 compared the test count with a
floor one fifth of the living one. Each scenario here alters one file of a checkout that passes and runs the real
validator; its test asserts the refusal: a non-zero exit, a message that names the cause, no PASS line for the refused
check, and no validation-summary.txt.

The contract is stated once and run against the ``.sh`` and the ``.ps1`` of every target that concludes from one
``clean verify``: m21, d2 and m22 to m27. A scenario added to the contract runs against the sixteen files. Packaging is
skipped by the switch each validator offers (``MORPHEUS_<TARGET>_SKIP_PORTABLE`` / ``-SkipPortable``): the fabricated
checkout holds no product, so what a validator does to a packaged launcher is not exercised here.
"""

from __future__ import annotations

import hashlib
import unittest
from dataclasses import dataclass

from validator_harness import (COVERAGE_EVIDENCE, FABRICATED_ARCHITECTURE_TESTS, FABRICATED_BRANCH_RATIO,
                               FABRICATED_LINE_RATIO, FABRICATED_RATCHETS, FABRICATED_TESTS, RATCHETS_FILE, SBOM_JSON,
                               SBOM_XML, FabricatedCheckout, PlayedScenarios, Run, require_bash, require_powershell)

DOMAIN_TESTS = FABRICATED_TESTS - FABRICATED_ARCHITECTURE_TESTS
REFERENCE_JAR = "morpheus-provider-reference/target/morpheus-provider-reference-{version}.jar"
SECURITY_REPORT = "target/d2-security/dependency-check-report.json"
MIGRATIONS = "morpheus-store-sqlite/src/main/resources/db/migration"
MANIFEST = "contracts/public-surfaces.tsv"


@dataclass(frozen=True)
class Target:
    name: str
    tests_pass: str = "Tests: PASS"
    coverage_pass: str = "JaCoCo: PASS"
    living_minimums: tuple[str, ...] = ()
    fallback_base: str = "main"

    @property
    def label(self) -> str:
        return self.name.upper()


TARGETS = (
    Target("m21", living_minimums=tuple(FABRICATED_RATCHETS)),
    Target("d2", tests_pass="D2 tests: PASS", coverage_pass="D2 coverage: PASS",
           living_minimums=("testsMinimum", "architectureTestsMinimum"), fallback_base="develop"),
    Target("m22"),
    Target("m23"),
    Target("m24"),
    Target("m25", fallback_base="develop"),
    Target("m26", fallback_base="develop"),
    Target("m27", fallback_base="develop"),
)


class Shell:
    extension = "sh"
    require = staticmethod(require_bash)

    @staticmethod
    def run(checkout: FabricatedCheckout, target: Target, version: str, base_ref: str | None = None,
            build_exit: int = 0) -> Run:
        prefix = f"MORPHEUS_{target.label}_"
        environment = {prefix + "SKIP_PORTABLE": "true", "MORPHEUS_STUB_BUILD_EXIT": str(build_exit)}
        if base_ref is not None:
            environment[prefix + "BASE_REF"] = base_ref
        return checkout.bash(f"validate-{target.name}.sh", version, environment=environment)


class PowerShell:
    extension = "ps1"
    require = staticmethod(require_powershell)

    @staticmethod
    def run(checkout: FabricatedCheckout, target: Target, version: str, base_ref: str | None = None,
            build_exit: int = 0) -> Run:
        arguments = ["-SkipPortable", "-Version", version]
        if base_ref is not None:
            arguments += ["-BaseRef", base_ref]
        return checkout.powershell(f"validate-{target.name}.ps1", *arguments,
                                   environment={"MORPHEUS_STUB_BUILD_EXIT": str(build_exit)})


def _failing_domain_suite(failures: int, errors: int):
    def given(checkout, validate):
        checkout.surefire("morpheus-domain", DOMAIN_TESTS, failures=failures, errors=errors)
    return staticmethod(given)


def _without(relative: str):
    def given(checkout, validate):
        checkout.remove(relative.format(version=checkout.version))
    return staticmethod(given)


def _evidence(text: str):
    def given(checkout, validate):
        checkout.write(COVERAGE_EVIDENCE, text)
    return staticmethod(given)


def _ratchets(**changes):
    def given(checkout, validate):
        checkout.ratchets(**changes)
    return staticmethod(given)


class VerdictContract(PlayedScenarios):
    """What every validator that concludes from one build must refuse. Mixed into one class per target and language."""

    target: Target
    interpreter: type

    @classmethod
    def setUpClass(cls) -> None:
        cls.interpreter.require()

    @classmethod
    def launch(cls, checkout: FabricatedCheckout, version: str | None = None, **options) -> Run:
        return cls.interpreter.run(checkout, cls.target, version or checkout.version, **options)

    def refused(self, scenario: str, *causes: str, before_the_build: bool = False) -> Run:
        checkout, run = self.played(scenario)
        self.assertNotEqual(0, run.returncode, run.output)
        for cause in causes:
            self.assertTrue(run.says(cause), f"the refusal must say {cause!r}:\n{run.output}")
        self.assertFalse(run.says(f"{self.target.label} VALIDATION PASS"), run.output)
        summary = checkout.summary(self.target.name)
        self.assertIsNone(summary, f"a refused run must leave no validation-summary.txt, found:\n{summary}")
        if before_the_build:
            self.assertEqual([], checkout.builds(), "the refusal must come before the build is launched")
        return run

    def assertNoPassLine(self, run: Run, *lines: str) -> None:
        for line in lines:
            self.assertFalse(run.says(line), f"a refused check must print no {line!r} line:\n{run.output}")

    # -- the checkout that passes ------------------------------------------------------------------------------------

    @staticmethod
    def given_a_complete_checkout(checkout, validate):
        pass

    def test_a_complete_checkout_passes_and_the_summary_names_the_commit_it_validated(self) -> None:
        checkout, run = self.played("a_complete_checkout")

        self.assertEqual(0, run.returncode, run.output)
        summary = checkout.summary(self.target.name)
        self.assertIsNotNone(summary, run.output)
        lines = summary.splitlines()
        self.assertEqual(f"{self.target.label} VALIDATION PASS", lines[0])
        self.assertIn(f"sha={checkout.head()}", lines)
        self.assertIn(f"version={checkout.version}", lines)
        self.assertIn(f"baseRef={self.target.fallback_base}", lines,
                      "without the remote ref the validator falls back to the local branch and says which")
        self.assertIn(f"tests={FABRICATED_TESTS}", lines)
        self.assertIn(f"architectureTests={FABRICATED_ARCHITECTURE_TESTS}", lines)
        self.assertIn(f"lineCoverage={FABRICATED_LINE_RATIO}", lines)
        self.assertIn(f"branchCoverage={FABRICATED_BRANCH_RATIO}", lines)
        self.assertTrue(run.says(self.target.tests_pass) and run.says(self.target.coverage_pass), run.output)

    def test_the_build_the_validator_asks_for_is_one_clean_verify_and_it_comes_first(self) -> None:
        checkout, run = self.played("a_complete_checkout")

        self.assertEqual(0, run.returncode, run.output)
        builds = checkout.builds()
        self.assertEqual("clean verify", builds[0], builds)
        self.assertEqual(1, builds.count("clean verify"), builds)

    # -- the refusals ------------------------------------------------------------------------------------------------

    @staticmethod
    def given_a_tracked_change(checkout, validate):
        checkout.write("pom.xml", "<project/>\n")

    def test_a_tracked_change_is_refused_before_the_build(self) -> None:
        run = self.refused(
            "a_tracked_change",
            f"{self.target.label} exact-head gate requires no tracked workspace delta before validation",
            before_the_build=True)
        self.assertTrue(run.says("pom.xml"), f"the refusal must name the changed file:\n{run.output}")

    @staticmethod
    def given_no_base_ref(checkout, validate):
        checkout.git("checkout", "--quiet", "--detach")
        checkout.git("branch", "--quiet", "-D", "main", "develop")

    def test_a_base_ref_that_resolves_nowhere_is_refused_before_the_build(self) -> None:
        self.refused("no_base_ref", f"{self.target.label} base ref not found", before_the_build=True)

    @staticmethod
    def given_a_failed_build(checkout, validate):
        return {"build_exit": 3}

    def test_a_build_that_fails_is_refused(self) -> None:
        run = self.refused("a_failed_build")
        self.assertEqual(["clean verify"], self.played("a_failed_build")[0].builds())
        self.assertNoPassLine(run, self.target.tests_pass, self.target.coverage_pass)

    given_a_surefire_failure = _failing_domain_suite(failures=1, errors=0)
    given_a_surefire_error = _failing_domain_suite(failures=0, errors=1)

    def test_a_surefire_report_with_a_failure_is_refused(self) -> None:
        run = self.refused("a_surefire_failure", "Surefire failures=1 errors=0")
        self.assertNoPassLine(run, self.target.tests_pass)

    def test_a_surefire_report_with_an_error_is_refused(self) -> None:
        run = self.refused("a_surefire_error", "Surefire failures=0 errors=1")
        self.assertNoPassLine(run, self.target.tests_pass)

    @staticmethod
    def given_too_few_tests(checkout, validate):
        checkout.total_tests(FABRICATED_TESTS - 1)

    @staticmethod
    def given_two_tests(checkout, validate):
        checkout.total_tests(2, architecture=1)

    def test_a_test_count_below_the_minimum_is_refused_with_both_counts(self) -> None:
        if self.target.living_minimums:
            run = self.refused("too_few_tests", f"{self.target.label} test baseline regression: "
                                                f"{FABRICATED_TESTS - 1} < {FABRICATED_TESTS}")
        else:
            # A milestone validator still compares with the literal floor of its date; any reactor is far above it.
            run = self.refused("two_tests", "regression: 2 <")
        self.assertNoPassLine(run, self.target.tests_pass)

    @staticmethod
    def given_too_few_architecture_tests(checkout, validate):
        checkout.total_tests(FABRICATED_TESTS, architecture=FABRICATED_ARCHITECTURE_TESTS - 1)

    @staticmethod
    def given_one_architecture_test(checkout, validate):
        checkout.total_tests(FABRICATED_TESTS, architecture=1)

    def test_an_architecture_count_below_the_minimum_is_refused_with_both_counts(self) -> None:
        prefix = f"{self.target.label} architecture baseline regression: "
        if self.target.living_minimums:
            run = self.refused("too_few_architecture_tests",
                               f"{prefix}{FABRICATED_ARCHITECTURE_TESTS - 1} < {FABRICATED_ARCHITECTURE_TESTS}")
        else:
            run = self.refused("one_architecture_test", f"{prefix}1 <")
        self.assertNoPassLine(run, self.target.tests_pass, "Architecture: PASS")

    given_no_coverage_evidence = _without(COVERAGE_EVIDENCE)

    def test_missing_coverage_evidence_is_refused_by_name(self) -> None:
        run = self.refused("no_coverage_evidence", "Missing M21 aggregate coverage evidence",
                           "m21-aggregate-coverage-summary.txt")
        self.assertNoPassLine(run, self.target.coverage_pass)

    # Ratios that would pass: the refusal has to come from the scale the file declares, not from its numbers.
    given_evidence_of_the_other_scale = _evidence(FabricatedCheckout.coverage_evidence("per-module"))

    def test_coverage_evidence_of_the_other_scale_is_refused_before_a_ratio_is_read(self) -> None:
        run = self.refused("evidence_of_the_other_scale", "M21 coverage evidence is not the aggregate scope",
                           "declares 'per-module'")
        self.assertNoPassLine(run, self.target.coverage_pass)

    given_evidence_without_a_scale = _evidence(
        f"lineRatio={FABRICATED_LINE_RATIO}\nbranchRatio={FABRICATED_BRANCH_RATIO}\n")

    def test_coverage_evidence_that_declares_no_scale_is_refused(self) -> None:
        run = self.refused("evidence_without_a_scale", "M21 coverage evidence is not the aggregate scope",
                           "declares '<none>'")
        self.assertNoPassLine(run, self.target.coverage_pass)

    given_low_line_coverage = _evidence(FabricatedCheckout.coverage_evidence("aggregate", line="0.010000"))
    given_low_branch_coverage = _evidence(FabricatedCheckout.coverage_evidence("aggregate", branch="0.010000"))

    def test_line_coverage_below_the_minimum_is_refused_and_named(self) -> None:
        run = self.refused("low_line_coverage", f"{self.target.label} line coverage below")
        self.assertNoPassLine(run, self.target.coverage_pass)

    def test_branch_coverage_below_the_minimum_is_refused_and_named(self) -> None:
        run = self.refused("low_branch_coverage", f"{self.target.label} branch coverage below")
        self.assertNoPassLine(run, self.target.coverage_pass)

    given_no_sbom_json = _without(SBOM_JSON)
    given_no_sbom_xml = _without(SBOM_XML)

    def test_a_missing_sbom_is_refused_by_name(self) -> None:
        for scenario in ("no_sbom_json", "no_sbom_xml"):
            with self.subTest(scenario=scenario):
                run = self.refused(scenario, "SBOM")
                self.assertTrue(run.says("is missing") or run.says("missing after clean verify"), run.output)
                self.assertNoPassLine(run, "Supply chain: PASS", "D2 SBOM: PASS")

    @staticmethod
    def given_a_refusal_after_a_pass(checkout, validate):
        first = validate()
        if first.returncode != 0 or checkout.summary(first_summary_directory(first)) is None:
            raise AssertionError(f"the earlier run must pass and leave its summary:\n{first.output}")
        checkout.surefire("morpheus-domain", DOMAIN_TESTS, failures=1)

    def test_a_refused_run_does_not_leave_the_passing_summary_of_an_earlier_run(self) -> None:
        # validation-summary.txt is the record a reader is told to read instead of the output (rules/tooling.md).
        self.refused("a_refusal_after_a_pass", "Surefire failures=1")


def first_summary_directory(run: Run) -> str:
    """The output directory of the only validator that ran in that checkout."""
    directories = [path.name for path in (run.checkout.root / "validation-output").iterdir() if path.is_dir()]
    if len(directories) != 1:
        raise AssertionError(f"one validator ran, found the output of {directories}")
    return directories[0]


class LivingMinimumsContract:
    """validate-m21 and validate-d2 claim to read their minimums in config/m21-quality-ratchets.properties."""

    given_a_raised_tests_minimum = _ratchets(testsMinimum=str(FABRICATED_TESTS + 1))

    def test_raising_the_tests_minimum_in_the_file_turns_the_passing_build_into_a_refusal(self) -> None:
        run = self.refused("a_raised_tests_minimum", f"{self.target.label} test baseline regression: "
                                                     f"{FABRICATED_TESTS} < {FABRICATED_TESTS + 1}")
        self.assertNoPassLine(run, self.target.tests_pass)

    given_a_raised_architecture_minimum = _ratchets(architectureTestsMinimum=str(FABRICATED_ARCHITECTURE_TESTS + 1))

    def test_raising_the_architecture_minimum_in_the_file_turns_the_passing_build_into_a_refusal(self) -> None:
        self.refused("a_raised_architecture_minimum", f"{self.target.label} architecture baseline regression: "
                     f"{FABRICATED_ARCHITECTURE_TESTS} < {FABRICATED_ARCHITECTURE_TESTS + 1}")

    @staticmethod
    def given_a_smaller_build_and_lowered_minimums(checkout, validate):
        checkout.total_tests(FABRICATED_TESTS - 10, architecture=FABRICATED_ARCHITECTURE_TESTS - 5)
        if validate().returncode == 0:
            raise AssertionError("the smaller build must be refused while the file holds the fabricated minimums")
        checkout.ratchets(testsMinimum=str(FABRICATED_TESTS - 10),
                          architectureTestsMinimum=str(FABRICATED_ARCHITECTURE_TESTS - 5))

    def test_lowering_the_minimums_in_the_file_lets_a_build_it_refused_pass(self) -> None:
        checkout, run = self.played("a_smaller_build_and_lowered_minimums")

        self.assertEqual(0, run.returncode, run.output)
        self.assertTrue(run.says(f">= {FABRICATED_TESTS - 10}"), run.output)
        self.assertIn(f"tests={FABRICATED_TESTS - 10}", checkout.summary(self.target.name).splitlines())

    @classmethod
    def scenarios(cls) -> dict:
        found = super().scenarios()
        for key in cls.target.living_minimums:
            found[f"no_{key}"] = _ratchets(**{key: None}).__func__
        return found

    def test_a_missing_minimum_is_refused_by_its_key_before_the_build(self) -> None:
        self.assertTrue(self.target.living_minimums)
        for key in self.target.living_minimums:
            with self.subTest(key=key):
                self.refused(f"no_{key}", f"Missing M21 quality ratchet: {key}", before_the_build=True)

    @staticmethod
    def given_no_ratchet_file(checkout, validate):
        checkout.git("rm", "--quiet", RATCHETS_FILE)
        checkout.commit("no ratchets")

    def test_a_missing_ratchet_file_is_refused_before_the_build(self) -> None:
        self.refused("no_ratchet_file", "Missing M21 quality ratchet configuration", before_the_build=True)

    given_a_minimum_that_is_not_a_count = _ratchets(testsMinimum="many")

    def test_a_minimum_that_is_not_a_count_is_refused_by_its_key_before_the_build(self) -> None:
        self.refused("a_minimum_that_is_not_a_count", "M21 quality ratchet testsMinimum must be a positive integer",
                     before_the_build=True)


class M21Cases:
    given_a_raised_line_minimum = _ratchets(aggregateLineCoverageMinimum="0.990")
    given_a_raised_branch_minimum = _ratchets(aggregateBranchCoverageMinimum="0.990")

    def test_raising_an_aggregate_coverage_minimum_in_the_file_turns_the_passing_build_into_a_refusal(self) -> None:
        for scenario, measure in (("a_raised_line_minimum", "line"), ("a_raised_branch_minimum", "branch")):
            with self.subTest(measure=measure):
                run = self.refused(scenario, f"M21 {measure} coverage below 0.990 ratchet")
                self.assertNoPassLine(run, self.target.coverage_pass)

    given_other_minimums = _ratchets(testsMinimum="4000", architectureTestsMinimum="900",
                                     aggregateLineCoverageMinimum="0.500", aggregateBranchCoverageMinimum="0.400")

    def test_the_summary_states_the_minimums_the_file_held(self) -> None:
        checkout, run = self.played("other_minimums")

        self.assertEqual(0, run.returncode, run.output)
        self.assertIn("qualityRatchets=4000/900/0.500/0.400", checkout.summary("m21").splitlines())

    def test_the_provenance_it_writes_vouches_for_the_commit_and_the_sbom(self) -> None:
        checkout, run = self.played("a_complete_checkout")

        self.assertEqual(0, run.returncode, run.output)
        provenance = (checkout.root / "target/m21-supply-chain/build-provenance.properties").read_text(
            encoding="utf-8-sig").splitlines()
        sbom = hashlib.sha256((checkout.root / SBOM_JSON).read_bytes()).hexdigest()
        self.assertIn(f"gitSha={checkout.head()}", provenance)
        self.assertIn(f"version={checkout.version}", provenance)
        self.assertIn(f"sbomSha256={sbom}", provenance)
        self.assertIn("workspaceClean=true", provenance)
        self.assertIn("-v", checkout.builds(), "the provenance states the Maven the wrapper answers for")


class D2Cases:
    @staticmethod
    def given_another_version(checkout, validate):
        return {"version": "9.9.9"}

    def test_a_version_the_reactor_does_not_carry_is_refused_before_the_build(self) -> None:
        self.refused("another_version", "MORPHEUS 9.9.9 version missing from", before_the_build=True)

    @staticmethod
    def given_a_workflow_change(checkout, validate):
        checkout.write(".github/workflows/ci.yml", "name: changed\n")
        checkout.commit("workflow")
        return {"base_ref": "main"}

    def test_a_change_to_a_workflow_since_the_base_is_refused_before_the_build(self) -> None:
        run = self.refused("a_workflow_change", "D2 is local-only and must not modify GitHub Actions workflows",
                           before_the_build=True)
        self.assertNoPassLine(run, "D2 no-CI scope: PASS")

    @staticmethod
    def given_an_undeclared_module(checkout, validate):
        checkout.write("morpheus-undeclared/pom.xml", f"<project><version>{checkout.version}</version></project>\n")
        checkout.commit("undeclared module")

    def test_a_pom_outside_the_declared_reactor_is_refused_before_the_build(self) -> None:
        run = self.refused("an_undeclared_module", before_the_build=True)
        self.assertNoPassLine(run, "D2 reactor: PASS")

    given_no_security_report = _without(SECURITY_REPORT)

    def test_the_dependency_scan_is_asked_of_the_security_profile_and_its_missing_report_is_refused(self) -> None:
        run = self.refused("no_security_report", "D2 dependency-check JSON report missing")
        scans = [build for build in self.played("no_security_report")[0].builds() if "d2-security" in build]
        self.assertEqual(1, len(scans), scans)
        self.assertIn("-Pd2-security", scans[0])
        self.assertTrue(scans[0].rstrip("'\"").endswith(":aggregate"), scans[0])
        self.assertNoPassLine(run, "D2 SCA: PASS")


class M22Cases:
    given_no_reference_jar = _without(REFERENCE_JAR)

    def test_a_missing_reference_provider_jar_is_refused_by_name(self) -> None:
        self.refused("no_reference_jar", "M22 reference provider JAR missing after reactor verify")


class M26Cases:
    @staticmethod
    def given_no_migration(checkout, validate):
        checkout.git("rm", "--quiet", "-r", MIGRATIONS)
        checkout.commit("no migration")

    def test_a_checkout_without_a_migration_is_refused_before_the_build(self) -> None:
        self.refused("no_migration", before_the_build=True)


class M27Cases:
    @staticmethod
    def given_a_manifest_without_reasoning(checkout, validate):
        kept = [line for line in (checkout.root / MANIFEST).read_text(encoding="utf-8").splitlines()
                if not line.startswith("reasoning.")]
        checkout.write(MANIFEST, "\n".join(kept) + "\n")
        checkout.commit("manifest without reasoning")

    def test_a_manifest_that_lost_the_reasoning_surfaces_is_refused(self) -> None:
        run = self.refused("a_manifest_without_reasoning")
        self.assertNoPassLine(run, "CLI/MCP/HTTP convergence + OpenAPI mutation boundary: PASS")


EXTRA_CASES = {"m21": (LivingMinimumsContract, M21Cases), "d2": (LivingMinimumsContract, D2Cases),
               "m22": (M22Cases,), "m26": (M26Cases,), "m27": (M27Cases,)}

SCRIPTS_UNDER_TEST = tuple(f"validate-{target.name}.{interpreter.extension}"
                           for target in TARGETS for interpreter in (Shell, PowerShell)) + (
    # Run for real by every scenario that reaches them: the provenance writer, the ratchet readers, the evidence guards.
    "write-build-provenance.sh", "write-build-provenance.ps1", "lib/python.sh",
    "lib/read-quality-ratchet.sh", "lib/Read-QualityRatchets.ps1",
    "lib/require-aggregate-coverage-evidence.sh", "lib/Require-AggregateCoverageEvidence.ps1")


def _declare() -> None:
    for target in TARGETS:
        for interpreter in (Shell, PowerShell):
            name = f"Validate{target.label}{interpreter.extension.capitalize()}Test"
            bases = (*EXTRA_CASES.get(target.name, ()), VerdictContract, unittest.TestCase)
            globals()[name] = type(name, bases, {"target": target, "interpreter": interpreter, "__module__": __name__})


_declare()

if __name__ == "__main__":
    unittest.main()
