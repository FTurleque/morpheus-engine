# Design

## Context

- A validator is one file that does everything in order: resolve the repository from its own location, check the tree,
  run `./mvnw clean verify`, sum the Surefire reports, compare with minimums, read the coverage evidence, check the
  supply-chain files, package, smoke the launcher, write `validation-output/<target>/validation-summary.txt`.
- It finds the repository as the parent of its own directory (`SCRIPT_DIR/..`, `$PSScriptRoot`). A copy of `scripts/`
  placed in another directory therefore validates that directory.
- The existing script tests already replace a tool by a stub on the `PATH` (`test_classify_sonar_quality_gate.py` stubs
  `curl`) and already extract and run a workflow step (`test_refresh_alert_step.py`).
- CI runs the validators with `bash` on Linux and with `powershell` — Windows PowerShell 5.1 — on Windows
  (`ci.yml:86`). `CoverageScaleSeparationTest` already executes the two coverage-evidence guards under both shells from
  Java.
- Every milestone test asserts that its two validators exist (`rules/testing.md`, quadruplet); the parity of the pair
  is asserted for every target by `GateReachabilityArchitectureTest`.

## Goals / Non-Goals

**Goals**: a validator that stops refusing is noticed by a test in minutes, not by a workflow that stays green; the
PowerShell half of the device is tested by a machine; no script is in the tree without something that launches it.

**Non-Goals**: testing Maven, the packaging or the installer through the validators — that is what running them does;
a lane for every validator (rejected on measurement by `rehearse-the-release-chain-before-the-tag`, decision 6);
rewriting the validators into a shared library; changing what a validator checks.

## Decisions

1. **A fabricated checkout, and a stub build.** The tests copy `scripts/` into a temporary Git repository that holds a
   stub `mvnw`, a stub packaging script and the files a validator reads: Surefire reports, the coverage evidence, the
   ratchet file, the supply-chain files. Each case alters one of them and runs the real validator. Where a validator
   offers a switch that skips packaging (`MORPHEUS_M24_SKIP_PORTABLE` and its siblings), the test uses it and says so.
   *Rejected*: extracting the verdict logic into functions and testing those — it tests a new file, not the one the
   workflow runs, and it is the rewrite this change excludes. *Rejected*: running the validators for real in a nightly
   lane as their test — a green run does not show that a refusal still refuses.

2. **The contract is stated once and applied to every target that can meet it.** Dirty tree, Surefire failure, count
   below the living minimum, missing or wrong-scale coverage evidence, missing supply-chain file: each must end
   non-zero, name its cause, print no PASS line for the refused check and leave no passing summary. A target that
   cannot be run on a fabricated checkout is excused by name with the reason, in the inventory.

3. **Both platforms in CI, and no silent skip there.** The Windows leg of `ci.yml` runs the same discovery. A case that
   needs `bash`, `jq` or `powershell` and does not find it fails when the run is a CI run, and is skipped only on a
   developer's machine. The PowerShell cases launch `powershell`, the interpreter CI uses, not `pwsh`.

4. **Twin libraries share their cases.** `read-quality-ratchet.sh` and `Read-QualityRatchets.ps1`, the two
   coverage-evidence guards: one table of cases, run against each implementation. A case added for one is added for
   the other by construction.

5. **The inventory is a textual rule, in the gate-reachability family.** Intent: "a script that nothing launches, or
   that nothing tests and nobody excused, must not be in `scripts/`". It reads file names, workflow steps, script
   bodies and test files: text is the only hold on `.yml`, `.sh`, `.ps1` and `.py` (ADR-0103). A launch is a mention in
   executable text — comments do not count, as in `gate-reachability`. "Tested" means a test file of `scripts/tests/`
   or a Java test that **runs** the script; a Java test that only reads it as text does not count. *Rejected*: an
   inventory in a Markdown table — nothing would compare it with the directory.

   *Amended on 2026-10-15.* The inventory is a test of `scripts/tests/` (`test_script_inventory.py`), not a rule of
   `morpheus-architecture-tests`: "tested" is read from each test file's `SCRIPTS_UNDER_TEST`, which Python imports
   and Java could only parse. The architecture suite holds the step that runs it on each leg
   (`GateReachabilityArchitectureTest`). A launch is a mention in the executable text of a workflow or of a launched
   script; a documentation page no longer counts, because six `.sh` validators that only a guide named looked
   launched while nothing ran them. The entry points a person runs (the dispatcher, the provenance verification of a
   published release) are named in `LAUNCHED_BY_HAND` with the reason and must be named by a current page. The
   declaration is checked against the test code: a harness module's declared validators equal the targets of its
   generated classes, the libraries it declares are named by a validator it runs, and any other declared script
   appears in a string of the test code outside the declaration and the docstrings. That a named string reaches a
   process in every case is left to the cases themselves; nothing cheaper proves it.

6. **Unlaunched validators are run before anything is decided about them.** Seven targets have no workflow. Each is run
   once on each platform; the result decides between a lane, a test under decision 1, and retirement. Retirement is the
   maintainer's decision, target by target: the milestone suites assert that their validators exist, so retiring one
   means amending that assertion with a justification, and recording the last commit that held the script.

7. **No ADR.**

## Risks / Trade-offs

- A fabricated checkout can drift from what a real build leaves. The files it holds are produced by the same fixture
  for every target, and task 2.1 compares that fixture with the files of a real `clean verify`.
- A stubbed `mvnw` proves nothing about the Maven command a validator runs. The stub records its arguments; the test
  asserts them, which is the only thing a stub can honestly prove.
- Windows PowerShell 5.1 decodes a file without BOM in the ANSI code page: every `.ps1` added stays ASCII
  (`rules/tooling.md`).
- The Windows leg already lasts longest. The script tests are counted in seconds; task 4.2 measures the step.

## Open Questions

1. **What becomes of `d2`, `m22` to `m26` and `r3`, which no workflow launches?** Recommendation: keep `d2` and give it a
   lane or a test first, since it carries the hardening contract and reads the living minimums; decide `m22` to `m26`
   and `r3` after task 1.4 shows whether they still run. A validator that no longer runs and that nobody needs is
   retired, not repaired for its own sake.

   *Decision taken by default on 2026-10-15, pending the maintainer.* The recommendation sets no default for `m22` to
   `m26` and `r3`, and task 1.4 has not run. Every `.sh` validator that no workflow launched gets a lane:
   `validators-without-a-lane` in `nightly.yml` runs `validate-d2.sh`, `validate-m22.sh` to `validate-m26.sh` and
   `validate-r3.sh` on Linux with the POM's version (r3 chains `m28`, `r2`, `m27`), D2 without its Dependency-Check
   scan, which `security.yml` runs with the NVD cache. The `.ps1` variants stay reachable through the dispatcher only.
   A red leg is the first real run task 1.4 asked for, not a merge gate. Keeping, testing or retiring each validator
   remains the maintainer's decision; the lane is what lets that decision rest on a run rather than a guess.
2. **Should a validator refuse to start without the version argument?** Each has a default dated from its milestone and
   fails on the launcher's version after the build. Recommendation: yes, as a separate small change once the tests of
   this one exist to hold it; it is a change of behaviour, not a test.
3. **Should the literal milestone floors of `m22` to `m28` be removed?** `validate-m24.sh` compares the test count with
   507 and the coverage with 25 %: assertions the living ratchets dominate many times over. `gate-verdict-currency`
   forbids such literals in `m21` and `d2` only. Recommendation: extend that requirement to every validator in a
   follow-up, after question 1 says which validators remain.

   *Done for `m22` to `m27` on 2026-10-15.* Both variants read `testsMinimum`, `architectureTestsMinimum` and the two
   aggregate coverage keys of `config/m21-quality-ratchets.properties` before the build, through the shared readers;
   the aggregate keys are the scale their evidence declares. The verdict contract holds them to the file (the
   living-minimum cases now run on the twelve files). What keeps a literal, and why: `validate-d2` keeps its two
   absolute coverage floors, which `D2RepositoryHardeningArchitectureTest` asserts by name next to the ratchets the Java
   gates apply; `validate-m19` keeps its frozen performance budgets (ADR-0085), which are not counts; `m20`, `m28`,
   `r2` and `r3` carry no count floor (r2's "at least one upgrade test" is a presence check, not a ratchet). Extending
   the `gate-verdict-currency` requirement itself to every validator is still a follow-up.
