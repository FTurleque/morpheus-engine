"""The fabricated checkout the validator tests run against, and the two interpreters that run a validator in it.

A validator finds the repository as the parent of its own directory, so a copy of ``scripts/`` placed in another Git
repository validates that repository. The fabricated checkout is such a repository: the real ``scripts/``, the real
POMs, a stub ``mvnw`` that records what it was asked and builds nothing, and the files a real ``./mvnw clean verify``
leaves behind (Surefire reports, the aggregate coverage evidence, the SBOM). A case alters one of them and runs the
real validator: what is under test is the file a workflow runs, never a copy of its logic.

What a stub can prove is bounded. The stub build records its arguments and the tests assert them; nothing here says
that Maven, the packaging or the installer work -- that is what running a validator for real does.

Tools. In a CI run a missing interpreter is a failure, never a skip: a leg that cannot run the cases must not report
success. Outside CI the cases are skipped with the reason. Windows PowerShell 5.1 -- the interpreter the workflows use
for the ``.ps1`` validators -- exists on Windows only, so on another platform its cases are skipped by name and the
Windows leg is the one that runs them.
"""

from __future__ import annotations

import atexit
import concurrent.futures
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[2]
SCRIPTS = REPOSITORY / "scripts"
WINDOWS = os.name == "nt"

# Larger than every literal floor a milestone validator still carries, so one fabricated build passes all of them.
FABRICATED_TESTS = 5000
FABRICATED_ARCHITECTURE_TESTS = 1000
FABRICATED_LINE_RATIO = "0.950000"
FABRICATED_BRANCH_RATIO = "0.850000"
FABRICATED_RATCHETS = {
    "testsMinimum": str(FABRICATED_TESTS),
    "architectureTestsMinimum": str(FABRICATED_ARCHITECTURE_TESTS),
    "aggregateLineCoverageMinimum": "0.900",
    "aggregateBranchCoverageMinimum": "0.800",
    "perModuleLineCoverageMinimum": "0.700",
    "perModuleBranchCoverageMinimum": "0.600",
}
# The one suite a validator reads by name: validate-r2 requires the report of the SQLite upgrade test.
UPGRADE_SUITE = "com.morpheus.store.sqlite.R2UpgradeCompatibilityTest"
UPGRADE_TESTS = 1
DOMAIN_TESTS = FABRICATED_TESTS - FABRICATED_ARCHITECTURE_TESTS - UPGRADE_TESTS
RATCHETS_FILE = "config/m21-quality-ratchets.properties"
COVERAGE_EVIDENCE = "morpheus-architecture-tests/target/m21-aggregate-coverage-summary.txt"
SBOM_JSON = "target/m21-supply-chain/morpheus-sbom.json"
SBOM_XML = "target/m21-supply-chain/morpheus-sbom.xml"

STUB_MVNW = """#!/usr/bin/env bash
# Stands for the Maven Wrapper: records what it was asked, builds nothing.
printf '%s\\n' "$*" >> "${MORPHEUS_STUB_RECORD:?}"
case "${1:-}" in
  -v|--version) echo 'Apache Maven 0.0.0 (stub)'; exit 0 ;;
esac
exit "${MORPHEUS_STUB_BUILD_EXIT:-0}"
"""

STUB_MVNW_CMD = """@echo off
rem Stands for the Maven Wrapper: records what it was asked, builds nothing.
>>"%MORPHEUS_STUB_RECORD%" echo %*
if "%~1"=="-v" goto version
if "%~1"=="--version" goto version
if defined MORPHEUS_STUB_BUILD_EXIT exit /b %MORPHEUS_STUB_BUILD_EXIT%
exit /b 0
:version
echo Apache Maven 0.0.0 (stub)
exit /b 0
"""

STUB_JAVA = """#!/usr/bin/env bash
echo 'openjdk version "0" (stub)' >&2
"""

STUB_JAVA_CMD = """@echo off
echo openjdk version "0" (stub) 1>&2
exit /b 0
"""

STUB_PACKAGING = """#!/usr/bin/env bash
# Stands for the packaging script. The cases skip packaging; a validator that reaches this did not honour the switch.
echo 'the fabricated checkout does not package' >&2
exit 97
"""

STUB_PACKAGING_PS1 = """Write-Error 'the fabricated checkout does not package'
exit 97
"""


def in_ci() -> bool:
    return bool(os.environ.get("CI") or os.environ.get("GITHUB_ACTIONS"))


def require_tool(name: str, path: str | None, reason: str | None = None) -> str:
    """The tool's path, or a failure in CI and a named skip outside it."""
    if path is not None:
        return path
    message = f"{name} was not found" + (f": {reason}" if reason else "")
    if in_ci():
        raise AssertionError(f"{message}; in CI a case that cannot run is a failure, not a skip")
    raise unittest.SkipTest(message)


def find_bash() -> str | None:
    """Git's bash on Windows: ``System32\\bash.exe`` is the WSL launcher and runs in another filesystem."""
    found = shutil.which("bash")
    if not WINDOWS:
        return found
    if found and "system32" not in found.lower():
        return found
    git = shutil.which("git")
    if git:
        for parent in Path(git).resolve().parents:
            candidate = parent / "bin" / "bash.exe"
            if candidate.is_file():
                return str(candidate)
    return None


def find_windows_powershell() -> str | None:
    """Windows PowerShell 5.1, the interpreter ``ci.yml`` runs the validators with (``shell: powershell``), not pwsh."""
    if not WINDOWS:
        return None
    found = shutil.which("powershell")
    if found:
        return found
    candidate = Path(os.environ.get("SystemRoot", r"C:\Windows")) / "System32" / "WindowsPowerShell" / "v1.0" / "powershell.exe"
    return str(candidate) if candidate.is_file() else None


BASH = find_bash()
POWERSHELL = find_windows_powershell()


def require_bash() -> str:
    return require_tool("bash", BASH)


def require_powershell() -> str:
    if not WINDOWS:
        raise unittest.SkipTest("Windows PowerShell 5.1 exists on Windows only; the Windows leg of ci.yml runs these cases")
    return require_tool("powershell", POWERSHELL)


def squeeze(text: str) -> str:
    """The text without any whitespace: Windows PowerShell wraps an error message at the width of its host."""
    return "".join(text.split())


def product_version() -> str:
    project = ET.parse(REPOSITORY / "pom.xml").getroot()
    return project.find("{http://maven.apache.org/POM/4.0.0}version").text.strip()


def clean_environment() -> dict[str, str]:
    # NoDefaultCurrentDirectoryInExePath: Git Bash exports it, and cmd.exe then no longer resolves a command from the
    # current directory, so the provenance writer's `cmd /c "mvnw.cmd -v"` fails under it. A workflow runs the .ps1
    # validators from PowerShell, without it. JAVA_HOME: the validators that discover it must find the stub.
    dropped = ("NODEFAULTCURRENTDIRECTORYINEXEPATH", "JAVA_HOME")
    return {key: value for key, value in os.environ.items()
            if not key.upper().startswith(("MORPHEUS_", "GITHUB_REF")) and key.upper() not in dropped}


def _write(path: Path, text: str, newline: str = "\n", executable: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline=newline) as handle:
        handle.write(text)
    if executable:
        path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def _git(root: Path, *arguments: str) -> str:
    completed = subprocess.run(
        ["git", "-c", "user.name=fabricated", "-c", "user.email=fabricated@example.invalid",
         "-c", "commit.gpgsign=false", "-c", "core.autocrlf=false", "-c", "core.safecrlf=false", *arguments],
        cwd=root, capture_output=True, text=True, check=False)
    if completed.returncode != 0:
        raise AssertionError(f"git {' '.join(arguments)} failed in the fabricated checkout: {completed.stderr}")
    return completed.stdout.strip()


class Run:
    """What one run of a validator left: its exit code, its output, and the files of its output directory."""

    def __init__(self, completed: subprocess.CompletedProcess, checkout: "FabricatedCheckout") -> None:
        self.returncode = completed.returncode
        self.stdout = completed.stdout
        self.stderr = completed.stderr
        self.output = completed.stdout + completed.stderr
        self.checkout = checkout

    def says(self, fragment: str) -> bool:
        return squeeze(fragment) in squeeze(self.output)


class FabricatedCheckout:
    _template: Path | None = None
    _template_lock = threading.Lock()

    def __init__(self, register_cleanup) -> None:
        """``register_cleanup`` is ``self.addCleanup`` of a test, or ``cls.addClassCleanup`` of a test class."""
        self._directory = tempfile.TemporaryDirectory(prefix="morpheus-fabricated-", ignore_cleanup_errors=True)
        register_cleanup(self._directory.cleanup)
        self.home = Path(self._directory.name)
        shutil.copytree(self._template_directory(), self.home, dirs_exist_ok=True)
        self.root = self.home / "checkout"
        self.record = self.home / "build-record.txt"
        self.version = product_version()

    # -- construction ------------------------------------------------------------------------------------------------

    @classmethod
    def _template_directory(cls) -> Path:
        with cls._template_lock:
            return cls._locked_template_directory()

    @classmethod
    def _locked_template_directory(cls) -> Path:
        if cls._template is None:
            holder = tempfile.TemporaryDirectory(prefix="morpheus-fabricated-template-", ignore_cleanup_errors=True)
            atexit.register(holder.cleanup)
            cls._build_template(Path(holder.name))
            cls._template = Path(holder.name)
        return cls._template

    @classmethod
    def _build_template(cls, home: Path) -> None:
        root = home / "checkout"
        root.mkdir()
        # The real scripts, byte for byte: these are the files under test.
        shutil.copytree(SCRIPTS, root / "scripts", ignore=shutil.ignore_patterns("tests", "__pycache__"))
        # The real POMs: validate-d2 and validate-r2 compare the reactor with what is on disk and read the version.
        namespace = "{http://maven.apache.org/POM/4.0.0}"
        declared = ET.parse(REPOSITORY / "pom.xml").getroot().findall(f"{namespace}modules/{namespace}module")
        modules = [module.text.strip() for module in declared]
        for pom in ["pom.xml", *(f"{module}/pom.xml" for module in modules)]:
            (root / pom).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPOSITORY / pom, root / pom)
        # The real contract files validate-m27 compares.
        for contract in ("contracts/public-surfaces.tsv",
                         "docs/developer/reference/openapi/morpheus-v1-reasoning-m27.yaml"):
            (root / contract).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPOSITORY / contract, root / contract)
        # The real release scripts: validate-r2 compares the default version they carry with the reactor's.
        for release in ("distribution/build-release.sh", "distribution/build-release.ps1"):
            (root / release).parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPOSITORY / release, root / release)
        _write(root / ".gitignore", "**/target/\nvalidation-output/\n")
        _write(root / RATCHETS_FILE, "# fabricated\n" + "".join(f"{k}={v}\n" for k, v in FABRICATED_RATCHETS.items()))
        _write(root / "mvnw", STUB_MVNW, executable=True)
        _write(root / "mvnw.cmd", STUB_MVNW_CMD, newline="\r\n")
        _write(root / "distribution/build-portable.sh", STUB_PACKAGING, executable=True)
        _write(root / "distribution/build-portable.ps1", STUB_PACKAGING_PS1, newline="\r\n")
        # validate-m26 derives the expected schema from the migrations it finds.
        _write(root / "morpheus-store-sqlite/src/main/resources/db/migration/V001__fabricated.sql", "-- fabricated\n")
        _write(home / "tools/bin/java", STUB_JAVA, executable=True)
        _write(home / "tools/bin/java.cmd", STUB_JAVA_CMD, newline="\r\n")

        _git(root, "init", "--quiet", "--initial-branch=develop")
        _git(root, "add", "--all")
        if not WINDOWS:
            _git(root, "update-index", "--chmod=+x", "mvnw", "distribution/build-portable.sh")
        _git(root, "commit", "--quiet", "--message", "fabricated checkout")
        _git(root, "branch", "main")

        version = product_version()
        cls._surefire(root, "morpheus-architecture-tests", "fabricated.ArchitectureTest", FABRICATED_ARCHITECTURE_TESTS)
        cls._surefire(root, "morpheus-store-sqlite", UPGRADE_SUITE, UPGRADE_TESTS)
        cls._surefire(root, "morpheus-domain", "fabricated.DomainTest", DOMAIN_TESTS)
        _write(root / COVERAGE_EVIDENCE, cls.coverage_evidence("aggregate"))
        _write(root / SBOM_JSON, '{"bomFormat":"CycloneDX"}\n')
        _write(root / SBOM_XML, "<bom/>\n")
        _write(root / "target/d2-security/dependency-check-report.json", "{}\n")
        _write(root / f"morpheus-provider-reference/target/morpheus-provider-reference-{version}.jar", "fabricated\n")

    @staticmethod
    def _surefire(root: Path, module: str, suite: str, tests: int, failures: int = 0, errors: int = 0) -> None:
        _write(root / module / "target" / "surefire-reports" / f"TEST-{suite}.xml",
               '<?xml version="1.0" encoding="UTF-8"?>\n'
               f'<testsuite name="{suite}" time="0.001" tests="{tests}" errors="{errors}" skipped="0" '
               f'failures="{failures}" flakes="0"/>\n')

    @staticmethod
    def coverage_evidence(scope: str, line: str = FABRICATED_LINE_RATIO, branch: str = FABRICATED_BRANCH_RATIO) -> str:
        """The lines a validator reads of the file the coverage gate writes; the scale it declares comes first."""
        return (f"coverageScope={scope}\n"
                "coverageSource=fabricated\n"
                f"lineCovered=95\nlineMissed=5\nlineRatio={line}\n"
                f"branchCovered=85\nbranchMissed=15\nbranchRatio={branch}\n")

    # -- what a case alters ------------------------------------------------------------------------------------------

    def write(self, relative: str, text: str) -> None:
        _write(self.root / relative, text)

    def remove(self, relative: str) -> None:
        (self.root / relative).unlink()

    def surefire(self, module: str, tests: int, failures: int = 0, errors: int = 0) -> None:
        suite = {"morpheus-architecture-tests": "fabricated.ArchitectureTest",
                 "morpheus-store-sqlite": UPGRADE_SUITE}.get(module, "fabricated.DomainTest")
        self._surefire(self.root, module, suite, tests, failures, errors)

    def failing_suite(self, failures: int = 0, errors: int = 0) -> None:
        """One suite of the build reports a failure or an error; the total is unchanged."""
        self.surefire("morpheus-domain", DOMAIN_TESTS, failures=failures, errors=errors)

    def total_tests(self, total: int, architecture: int = FABRICATED_ARCHITECTURE_TESTS) -> None:
        self.surefire("morpheus-architecture-tests", architecture)
        self.surefire("morpheus-domain", total - architecture - UPGRADE_TESTS)

    def ratchets(self, **changes: str | None) -> None:
        """Rewrites the living ratchet file and commits it: a validator refuses a tree with a tracked change."""
        values = dict(FABRICATED_RATCHETS)
        for key, value in changes.items():
            if value is None:
                values.pop(key)
            else:
                values[key] = value
        self.write(RATCHETS_FILE, "# fabricated\n" + "".join(f"{k}={v}\n" for k, v in values.items()))
        self.commit("ratchets")

    def commit(self, message: str) -> None:
        _git(self.root, "add", "--all")
        _git(self.root, "commit", "--quiet", "--message", message)

    def git(self, *arguments: str) -> str:
        return _git(self.root, *arguments)

    def head(self) -> str:
        return _git(self.root, "rev-parse", "HEAD")

    # -- what a run left ---------------------------------------------------------------------------------------------

    def builds(self) -> list[str]:
        """What the stub Maven Wrapper was asked, one invocation per entry, in order."""
        if not self.record.is_file():
            return []
        return [line.strip() for line in self.record.read_text(encoding="utf-8", errors="replace").splitlines()
                if line.strip()]

    def output_file(self, directory: str, name: str) -> Path:
        return self.root / "validation-output" / directory / name

    def summary(self, directory: str) -> str | None:
        path = self.output_file(directory, "validation-summary.txt")
        return path.read_text(encoding="utf-8-sig") if path.is_file() else None

    # -- running -----------------------------------------------------------------------------------------------------

    def _environment(self, extra: dict[str, str] | None) -> dict[str, str]:
        environment = clean_environment()
        environment["PATH"] = str(self.home / "tools" / "bin") + os.pathsep + environment["PATH"]
        environment["MORPHEUS_STUB_RECORD"] = str(self.record)
        environment.update(extra or {})
        return environment

    def run_script(self, command: list[str], environment: dict[str, str] | None = None) -> Run:
        completed = subprocess.run(command, cwd=self.home, env=self._environment(environment), capture_output=True,
                                   text=True, encoding="utf-8", errors="replace", check=False, timeout=300)
        return Run(completed, self)

    def bash(self, script: str, *arguments: str, environment: dict[str, str] | None = None) -> Run:
        return self.run_script([require_bash(), (self.root / "scripts" / script).as_posix(), *arguments], environment)

    def powershell(self, script: str, *arguments: str, environment: dict[str, str] | None = None) -> Run:
        return self.run_script([require_powershell(), "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy",
                                "Bypass", "-File", str(self.root / "scripts" / script), *arguments], environment)


class PlayedScenarios:
    """Plays the scenarios of a test class once, concurrently, and hands each test the run it asserts on.

    A validator run is a process, and a class holds a score of them: played one after the other they cost minutes on
    Windows, where a process is slow to start. A scenario is a static method ``given_<name>(checkout, validate)`` that
    alters its own fabricated checkout and returns the options of the run to assert on (or nothing); ``validate`` runs
    the validator in that checkout, for a scenario that needs an earlier run. Each scenario has its own checkout, so
    playing them together changes nothing they can observe. A scenario that raises fails the test that asks for it.
    """

    WORKERS = min(8, os.cpu_count() or 2)

    @classmethod
    def launch(cls, checkout: FabricatedCheckout, **options) -> Run:
        raise NotImplementedError

    @classmethod
    def scenarios(cls) -> dict:
        found = {}
        for klass in reversed(cls.__mro__):
            for name, value in vars(klass).items():
                if name.startswith("given_") and isinstance(value, staticmethod):
                    found[name[len("given_"):]] = value.__func__
        return found

    @classmethod
    def _play(cls, prepare) -> tuple[FabricatedCheckout, Run]:
        checkout = FabricatedCheckout(cls.addClassCleanup)
        options = prepare(checkout, lambda **earlier: cls.launch(checkout, **earlier)) or {}
        return checkout, cls.launch(checkout, **options)

    def played(self, name: str) -> tuple[FabricatedCheckout, Run]:
        cls = type(self)
        if "_outcomes" not in vars(cls):
            FabricatedCheckout._template_directory()
            pool = concurrent.futures.ThreadPoolExecutor(max_workers=cls.WORKERS)
            cls._outcomes = {scenario: pool.submit(cls._play, prepare) for scenario, prepare in cls.scenarios().items()}
            pool.shutdown(wait=True)
        return cls._outcomes[name].result()


if __name__ == "__main__":
    sys.exit("validator_harness is the support of the validator tests; run: python -m unittest discover -s scripts/tests")
