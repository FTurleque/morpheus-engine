"""The inventory of scripts/: every script is launched by something, and run by a test or excused by name.

The scripts of scripts/ are the enforcement device of the repository: its gates are held by them. Two things made a
script escape notice without anything failing -- nothing launched it any more, or nothing ever ran it in a test.

Both rules read text, the only hold on ``.yml``, ``.sh``, ``.ps1``, ``.cmd`` and ``.md`` (ADR-0103), and both read
*executable* text: a mention in a comment launches nothing.

* **Launched** means named in the executable text of a workflow, or of a script that is itself launched
  (``validate.cmd`` hands its target to ``validate.ps1``, which launches every ``validate-<target>.ps1`` by
  construction); the entry points a person runs by hand are named in LAUNCHED_BY_HAND with the reason, and launch
  what they name in turn. A documentation page is not a launch: a validator that only a guide names is run by
  nobody. That ``validate.ps1`` launches every target is a construction, not a proof that a workflow hands it each
  one: GateReachabilityArchitectureTest holds that, validator by validator.
* **Run by a test** means declared in the ``SCRIPTS_UNDER_TEST`` of a test file of this directory, which is where a
  test that launches a script says so. A Java test that reads a script as text does not count: reading is not running.
  The declaration is checked against the test code, not taken on trust: a validator a harness module declares must be
  the target of one of its generated classes, and nothing else; a library it declares must be named by a validator it
  runs; a script any other module declares must be named in a string of its code, outside the declaration and the
  docstrings. What this still cannot prove is that the string reaches a process in every case -- that is what the
  cases themselves assert.

There is no silent exception. A script nothing launches is named in NOT_LAUNCHED with the reason it is kept; a script
no test runs is named in NOT_RUN_BY_A_TEST with the reason; an entry for a script that is launched, tested or gone
fails, and names the entry to delete.

Run this file directly to print the table: ``python scripts/tests/test_script_inventory.py``.
"""

from __future__ import annotations

import ast
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

# The pages that state what to run now, as GateReachabilityArchitectureTest lists them, plus docs/developer/*.md. They
# launch nothing; an entry point launched by hand must be named by one of them, which is how a person learns to run it.
CURRENT_PAGES = ("README.md", "CONTRIBUTING.md", "scripts/README.md", "docs/README.md", "distribution/README.md")
# Scripts outside scripts/ that launch scripts of scripts/; they count only once something launches them too.
OTHER_LAUNCHER_DIRECTORIES = ("distribution", "distribution/windows", "integration")

AFTER_A_RELEASE = ("reads a release GitHub has published, through gh and the attestation API, and is run by hand "
                   "after the tag (docs/project/validation/RELEASE_QUALIFICATION.md): before a release exists there "
                   "is nothing for a workflow to verify")

# The entry points a person runs, named with the reason no workflow does. Each must be named by a current page.
LAUNCHED_BY_HAND = {
    "verify-release-provenance.sh": AFTER_A_RELEASE,
    "verify-release-provenance.ps1": AFTER_A_RELEASE,
}

# A script that no workflow, no launched script and no entry point launched by hand names. Each entry is a finding, not
# a licence. Empty since 2026-10-15: the .sh validators no workflow launched have a lane in nightly.yml.
NOT_LAUNCHED: dict[str, str] = {}

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
           "Windows leg of ci.yml runs the .ps1 for real, and nightly.yml runs both variants through r3",
    "r3": "everything after its scope policy and its reactor count: the inherited m28 gate, the release "
          "documentation. nightly.yml runs it for real on both platforms",
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


def current_pages() -> dict[str, str]:
    pages = [REPOSITORY / page for page in CURRENT_PAGES] + sorted((REPOSITORY / "docs" / "developer").glob("*.md"))
    return {page.relative_to(REPOSITORY).as_posix(): executable_text(page) for page in pages if page.is_file()}


def launchers(scripts: dict[str, Path], by_hand: bool = True) -> dict[str, list[str]]:
    """For every script that something launches, what launches it; ``by_hand=False`` counts the workflows alone."""
    roots: dict[str, str] = {}
    for workflow in sorted((REPOSITORY / ".github" / "workflows").glob("*.yml")):
        roots[f"workflow {workflow.name}"] = executable_text(workflow)

    candidates = {name: path for name, path in scripts.items() if path.suffix in SCRIPT_SUFFIXES}
    others: dict[str, Path] = {}
    for directory in OTHER_LAUNCHER_DIRECTORIES:
        for path in sorted((REPOSITORY / directory).glob("*")):
            if path.is_file() and path.suffix in SCRIPT_SUFFIXES:
                others[path.relative_to(REPOSITORY).as_posix()] = path
    everything = {**candidates, **others}
    texts = {name: executable_text(path) for name, path in everything.items()}

    launched: dict[str, list[str]] = {}
    if by_hand:
        for name in LAUNCHED_BY_HAND:
            if name in candidates:
                launched[name] = ["by hand (LAUNCHED_BY_HAND)"]
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


def named_by(scripts: dict[str, Path]) -> dict[str, set[str]]:
    """For every script of scripts/, the scripts of scripts/ its executable text names."""
    texts = {name: executable_text(path) for name, path in scripts.items() if path.suffix in SCRIPT_SUFFIXES}
    return {launcher: {name for name in texts if name != launcher and names(name, text)}
            for launcher, text in texts.items()}


def reached_from(starts: set[str], graph: dict[str, set[str]]) -> set[str]:
    reached, pending = set(), list(starts)
    while pending:
        for name in graph.get(pending.pop(), ()):
            if name not in reached:
                reached.add(name)
                pending.append(name)
    return reached


def harness_validators(module) -> set[str]:
    """The validators the generated classes of a module hand to the harness: such a class carries a target and the
    interpreter whose ``run`` launches ``validate-<target>.<extension>``."""
    found = set()
    for value in vars(module).values():
        if (isinstance(value, type) and issubclass(value, unittest.TestCase) and hasattr(value, "interpreter")
                and hasattr(value, "target")):
            target = getattr(value.target, "name", value.target)
            found.add(f"validate-{target}.{value.interpreter.extension}")
    return found


def strings_of_the_code(source: str) -> list[str]:
    """The string constants of a test file, without its docstrings and without its SCRIPTS_UNDER_TEST declaration."""
    tree = ast.parse(source)
    skipped: set[int] = set()
    for node in ast.walk(tree):
        if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)) and node.body:
            first = node.body[0]
            if isinstance(first, ast.Expr) and isinstance(first.value, ast.Constant):
                skipped.add(id(first.value))
        if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name) and target.id == "SCRIPTS_UNDER_TEST"
                                                for target in node.targets):
            skipped.update(id(inner) for inner in ast.walk(node))
    return [node.value for node in ast.walk(tree)
            if isinstance(node, ast.Constant) and isinstance(node.value, str) and id(node) not in skipped]


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
                         "no workflow, launched script or entry point launched by hand names these scripts in "
                         "executable text (a documentation page is not a launch); launch them, remove them, or name "
                         "them in NOT_LAUNCHED with the reason they are kept")
        for script, reason in NOT_LAUNCHED.items():
            self.assertTrue(script in self.scripts,
                            f"NOT_LAUNCHED names a script that no longer exists: delete the entry {script}")
            self.assertFalse(script in launched,
                             f"NOT_LAUNCHED names a script that {launched.get(script)} launches: delete the entry "
                             f"{script}")
            self.assertGreater(len(reason.strip()), 40, f"{script} needs the reason it is kept")

    def test_every_script_launched_by_hand_is_named_with_its_reason_and_by_a_current_page(self) -> None:
        automatic = launchers(self.scripts, by_hand=False)
        pages = current_pages()
        self.assertGreater(len(pages), 5, "too few current pages found: the rule would pass on nothing")
        for script, reason in LAUNCHED_BY_HAND.items():
            self.assertTrue(script in self.scripts,
                            f"LAUNCHED_BY_HAND names a script that no longer exists: delete the entry {script}")
            self.assertFalse(script in automatic,
                             f"LAUNCHED_BY_HAND names a script that {automatic.get(script)} launches: delete the "
                             f"entry {script}")
            self.assertGreater(len(reason.strip()), 40, f"{script} needs the reason no workflow launches it")
            self.assertTrue(any(names(script, text) for text in pages.values()),
                            f"{script} is launched by hand, and no current page tells a person to run it")

    def test_what_a_test_file_declares_is_what_its_code_runs(self) -> None:
        graph = named_by(self.scripts)
        checked = 0
        for name, module in test_modules().items():
            declared = set(getattr(module, "SCRIPTS_UNDER_TEST", ()))
            validators = {script for script in declared if validator_target(script)}
            generated = harness_validators(module)
            if generated:
                # The generated classes are what hands a validator to the harness: the declaration must equal them.
                self.assertEqual(sorted(generated), sorted(validators),
                                 f"{name}.py declares validators its generated classes do not run, or runs some it "
                                 "does not declare")
                reachable = reached_from(generated, graph)
                for script in sorted(declared - validators):
                    self.assertTrue(script in reachable,
                                    f"{name}.py declares {script}, which no validator it runs names: nothing in that "
                                    "module launches it")
                checked += len(declared)
                continue
            strings = strings_of_the_code((TESTS / f"{name}.py").read_text(encoding="utf-8"))
            for script in sorted(declared):
                basename = script.rsplit("/", 1)[-1]
                self.assertTrue(any(names(basename, text) for text in strings),
                                f"{name}.py declares {script} and names it in no string of its code: the declaration "
                                "is the only place it appears")
                checked += 1
        self.assertGreater(checked, 20, "too few declarations checked: the rule would pass on nothing")

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
