"""The inventory of scripts/: every script is launched by something, and run by a test or excused by name.

The scripts of scripts/ are the enforcement device of the repository: its gates are held by them. Two things made a
script escape notice without anything failing -- nothing launched it any more, or nothing ever ran it in a test.

Both rules read text, the only hold on ``.yml``, ``.sh``, ``.ps1``, ``.cmd`` and ``.md`` (ADR-0103), and both read
*executable* text: a mention in a comment launches nothing.

* **Launched** means named in the executable text of a workflow, in a current documentation page, or in a script that
  is itself launched; ``validate.ps1`` launches every ``validate-<target>.ps1`` by construction. The pages are the ones
  GateReachabilityArchitectureTest holds to commands that resolve.
* **Run by a test** means declared in the ``SCRIPTS_UNDER_TEST`` of a test file of this directory, which is where a
  test that launches a script says so. A Java test that reads a script as text does not count: reading is not running.

There is no silent exception. A script nothing launches is named in NOT_LAUNCHED with the reason it is kept; a script
no test runs is named in NOT_RUN_BY_A_TEST with the reason; an entry for a script that is launched, tested or gone
fails, and names the entry to delete.

Run this file directly to print the table: ``python scripts/tests/test_script_inventory.py``.
"""

from __future__ import annotations

import importlib
import re
import sys
import unittest
from pathlib import Path

TESTS = Path(__file__).resolve().parent
SCRIPTS = TESTS.parent
REPOSITORY = SCRIPTS.parent
if str(TESTS) not in sys.path:
    sys.path.insert(0, str(TESTS))

SCRIPT_SUFFIXES = (".sh", ".ps1", ".py", ".cmd")
NOT_A_SCRIPT = ("README.md",)

# The pages that state what to run now, as GateReachabilityArchitectureTest lists them, plus docs/developer/*.md.
CURRENT_PAGES = ("README.md", "CONTRIBUTING.md", "scripts/README.md", "docs/README.md", "distribution/README.md")
# Scripts outside scripts/ that launch scripts of scripts/; they count only once something launches them too.
OTHER_LAUNCHER_DIRECTORIES = ("distribution", "distribution/windows", "integration")

OPEN_QUESTION = ("keeping it, giving it a lane or retiring it is the maintainer's decision (open question 1 of the "
                 "change test-the-validators-that-give-a-verdict); the milestone suites assert that both variants "
                 "of a validator exist, so it cannot simply be removed")

# A script that no workflow, no launched script and no current page names. Each entry is a finding, not a licence.
NOT_LAUNCHED = {
    "validate-m23.sh": "frozen milestone validator whose .ps1 is reachable through the dispatcher and whose .sh is "
                       "named nowhere; " + OPEN_QUESTION,
    "validate-m24.sh": "frozen milestone validator whose .ps1 is reachable through the dispatcher and whose .sh is "
                       "named nowhere; " + OPEN_QUESTION,
    "validate-r3.sh": "frozen release validator whose .ps1 is reachable through the dispatcher and whose .sh is named "
                      "nowhere; " + OPEN_QUESTION,
}

RUN_FOR_REAL_ON_WINDOWS = ("is itself a verification, run for real on every pull request by the Windows leg of ci.yml "
                           "through validate-m28.ps1")

# A script no test of scripts/tests runs.
NOT_RUN_BY_A_TEST = {
    "verify-m28-mcp-client-integration.ps1": RUN_FOR_REAL_ON_WINDOWS + ", against the real MCP client manager",
    "verify-windows-transactional-upgrade.ps1": RUN_FOR_REAL_ON_WINDOWS + ", against the real upgrade engine",
    "verify-windows-setup-lifecycle.ps1": RUN_FOR_REAL_ON_WINDOWS + ", against the Setup.exe that leg has just built",
    "verify-windows-setup-mcp-smoke.ps1": RUN_FOR_REAL_ON_WINDOWS + ", against the smoke Setup.exe that leg has just "
                                          "built",
    "verify-release-provenance.sh": "reads a release GitHub has published, through gh and the attestation API; run by "
                                    "hand after a release (docs/project/validation/RELEASE_QUALIFICATION.md). A test "
                                    "needs a stub of gh that answers as GitHub does, and is not written",
    "verify-release-provenance.ps1": "reads a release GitHub has published, through gh and the attestation API; run by "
                                     "hand after a release (docs/project/validation/RELEASE_QUALIFICATION.md). A test "
                                     "needs a stub of gh that answers as GitHub does, and is not written",
}

PACKAGING = ("everything after the packaging switch -- the packaged launcher, its version, the API it serves -- which "
             "the fabricated checkout cannot provide")

# A validator a test runs, and the part of it no test reaches: said by target, for both variants.
NOT_REACHED_BY_A_TEST = {
    "d2": PACKAGING,
    "m21": PACKAGING,
    "m22": PACKAGING,
    "m23": PACKAGING,
    "m24": PACKAGING,
    "m25": PACKAGING,
    "m26": PACKAGING,
    "m27": PACKAGING,
    "r2": PACKAGING + "; and the installer build of the .ps1",
    "m19": "everything after the clean-tree check: the reference machine, the build, the five performance gates, the "
           "packaging. nightly.yml runs it for real on both platforms",
    "m20": "everything after the build: the tagged release build, the archive, the install and upgrade smokes. "
           "nightly.yml runs it for real on both platforms",
    "m28": "everything after the inherited r2 gate: the client manager, the installer, the setup lifecycle. The "
           "Windows leg of ci.yml runs the .ps1 for real; nothing runs the .sh to its end",
    "r3": "everything after its scope policy and its reactor count: the inherited m28 gate, the release "
          "documentation. Nothing runs it to its end, on either platform",
}


def executable_text(path: Path) -> str:
    """The file without its comments: a mention in a comment launches nothing."""
    text = path.read_text(encoding="utf-8", errors="replace")
    if path.suffix == ".md":
        return text
    kept = []
    for line in text.splitlines():
        stripped = line.lstrip()
        if path.suffix == ".cmd":
            if stripped.lower().startswith(("rem ", "::")):
                continue
            kept.append(line)
        else:
            if stripped.startswith("#"):
                continue
            kept.append(re.sub(r"\s#.*$", "", line))
    return "\n".join(kept)


def names(script: str, text: str) -> bool:
    basename = script.rsplit("/", 1)[-1]
    return re.search(r"(?<![A-Za-z0-9_-])" + re.escape(basename) + r"(?![A-Za-z0-9_-])", text) is not None


def population() -> dict[str, Path]:
    """Every file of scripts/ outside scripts/tests/, by its path relative to scripts/."""
    found = {}
    for path in sorted(SCRIPTS.rglob("*")):
        relative = path.relative_to(SCRIPTS).as_posix()
        if not path.is_file() or relative.startswith("tests/") or "__pycache__" in relative or relative in NOT_A_SCRIPT:
            continue
        found[relative] = path
    return found


def launchers(scripts: dict[str, Path]) -> dict[str, list[str]]:
    """For every script that something launches, what launches it."""
    roots: dict[str, str] = {}
    for workflow in sorted((REPOSITORY / ".github" / "workflows").glob("*.yml")):
        roots[f"workflow {workflow.name}"] = executable_text(workflow)
    pages = [REPOSITORY / page for page in CURRENT_PAGES] + sorted((REPOSITORY / "docs" / "developer").glob("*.md"))
    for page in pages:
        if page.is_file():
            roots[f"page {page.relative_to(REPOSITORY).as_posix()}"] = executable_text(page)

    candidates = {name: path for name, path in scripts.items() if path.suffix in SCRIPT_SUFFIXES}
    others: dict[str, Path] = {}
    for directory in OTHER_LAUNCHER_DIRECTORIES:
        for path in sorted((REPOSITORY / directory).glob("*")):
            if path.is_file() and path.suffix in SCRIPT_SUFFIXES:
                others[path.relative_to(REPOSITORY).as_posix()] = path
    everything = {**candidates, **others}
    texts = {name: executable_text(path) for name, path in everything.items()}

    launched: dict[str, list[str]] = {}
    for root, text in roots.items():
        for name in everything:
            if names(name, text):
                launched.setdefault(name, []).append(root)
    changed = True
    while changed:
        changed = False
        for launcher in list(launched):
            if launcher == "validate.ps1" and "validate-$normalizedTarget.ps1" in texts[launcher]:
                targets = [name for name in candidates if re.fullmatch(r"validate-[a-z0-9]+\.ps1", name)]
            else:
                targets = [name for name in everything if name != launcher and names(name, texts[launcher])]
            for name in targets:
                by = launched.setdefault(name, [])
                label = f"script {launcher}"
                if label not in by:
                    changed = changed or not by
                    by.append(label)
    return {name: by for name, by in launched.items() if name in candidates}


def test_modules() -> dict[str, object]:
    return {path.stem: importlib.import_module(path.stem) for path in sorted(TESTS.glob("test_*.py"))}


def tested() -> dict[str, list[str]]:
    """For every script a test file declares it runs, the test files that declare it."""
    by_script: dict[str, list[str]] = {}
    for name, module in test_modules().items():
        for script in getattr(module, "SCRIPTS_UNDER_TEST", ()):
            by_script.setdefault(script, []).append(name)
    return by_script


def validator_target(script: str) -> str | None:
    match = re.fullmatch(r"validate-([a-z0-9]+)\.(?:sh|ps1)", script)
    return match.group(1) if match else None


class ScriptInventoryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.scripts = population()
        self.assertGreater(len(self.scripts), 20, "too few scripts found: the rules would pass on nothing")

    def test_scripts_holds_scripts_and_nothing_else(self) -> None:
        strangers = [name for name, path in self.scripts.items() if path.suffix not in SCRIPT_SUFFIXES]
        self.assertEqual([], strangers, "a file of scripts/ that is not a script escapes both rules")
        basenames = [name.rsplit("/", 1)[-1] for name in self.scripts]
        self.assertEqual(len(basenames), len(set(basenames)), "two scripts share a name: a mention could mean either")

    def test_every_script_is_launched_by_something_or_named_with_the_reason_it_is_kept(self) -> None:
        launched = launchers(self.scripts)
        self.assertGreater(len(launched), 20, "too few launches found: the rule would pass on nothing")

        orphans = sorted(set(self.scripts) - set(launched) - set(NOT_LAUNCHED))
        self.assertEqual([], orphans,
                         "no workflow, launched script or current page names these scripts in executable text; "
                         "launch them, remove them, or name them in NOT_LAUNCHED with the reason they are kept")
        for script, reason in NOT_LAUNCHED.items():
            self.assertTrue(script in self.scripts,
                            f"NOT_LAUNCHED names a script that no longer exists: delete the entry {script}")
            self.assertFalse(script in launched,
                             f"NOT_LAUNCHED names a script that {launched.get(script)} launches: delete the entry "
                             f"{script}")
            self.assertGreater(len(reason.strip()), 40, f"{script} needs the reason it is kept")

    def test_every_test_file_says_which_scripts_it_runs(self) -> None:
        modules = test_modules()
        self.assertGreater(len(modules), 5, "too few test files found: the rule would pass on nothing")
        for name, module in modules.items():
            declared = getattr(module, "SCRIPTS_UNDER_TEST", None)
            self.assertIsInstance(declared, tuple, f"{name}.py must declare SCRIPTS_UNDER_TEST, empty if it runs none")
            source = (TESTS / f"{name}.py").read_text(encoding="utf-8")
            for script in declared:
                self.assertTrue(script in self.scripts, f"{name}.py declares a script that does not exist: {script}")
            if declared:
                self.assertTrue("subprocess" in source or "validator_harness" in source,
                                f"{name}.py declares scripts and launches no process: reading a script is not "
                                "running it")

    def test_every_script_is_run_by_a_test_or_excused_with_its_reason(self) -> None:
        run = tested()
        self.assertGreater(len(run), 20, "too few tested scripts found: the rule would pass on nothing")

        untested = sorted(set(self.scripts) - set(run) - set(NOT_RUN_BY_A_TEST))
        self.assertEqual([], untested,
                         "no test file of scripts/tests declares that it runs these scripts; write the test, or name "
                         "them in NOT_RUN_BY_A_TEST with the reason")
        for script, reason in NOT_RUN_BY_A_TEST.items():
            self.assertTrue(script in self.scripts,
                            f"NOT_RUN_BY_A_TEST names a script that no longer exists: delete the entry {script}")
            self.assertFalse(script in run,
                             f"NOT_RUN_BY_A_TEST names a script that {run.get(script)} runs: delete the entry {script}")
            self.assertGreater(len(reason.strip()), 40, f"{script} needs the reason no test runs it")

    def test_every_validator_says_what_no_test_reaches_in_it(self) -> None:
        targets = sorted({validator_target(script) for script in self.scripts} - {None})
        self.assertGreater(len(targets), 5, "too few validators found: the rule would pass on nothing")
        self.assertEqual(targets, sorted(NOT_REACHED_BY_A_TEST),
                         "each validator target states the part of it no test reaches, and only existing targets do")
        run = tested()
        for target in targets:
            for extension in ("sh", "ps1"):
                self.assertTrue(f"validate-{target}.{extension}" in run,
                                f"validate-{target}.{extension} is run by no test at all")


SCRIPTS_UNDER_TEST = ()


def table() -> str:
    scripts = population()
    launched, run = launchers(scripts), tested()
    lines = [f"{len(scripts)} files under scripts/ outside scripts/tests/", ""]
    for name in scripts:
        by = "; ".join(launched.get(name, [])) or "NOTHING -- " + NOT_LAUNCHED.get(name, "UNEXPLAINED")
        tests = ", ".join(run.get(name, [])) or "NO TEST -- " + NOT_RUN_BY_A_TEST.get(name, "UNEXPLAINED")
        lines += [name, f"    launched by: {by}", f"    run by:      {tests}"]
        target = validator_target(name)
        if target in NOT_REACHED_BY_A_TEST:
            lines.append(f"    not reached: {NOT_REACHED_BY_A_TEST[target]}")
    return "\n".join(lines)


if __name__ == "__main__":
    print(table())
