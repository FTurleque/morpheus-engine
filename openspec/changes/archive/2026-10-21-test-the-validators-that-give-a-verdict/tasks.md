# Tasks

Notes dated 2026-10-13 were written on the branch `test/audit-trv07-validators-under-test`, from `develop` at
`d5be889b`, on a Windows machine with Git Bash, Windows PowerShell 5.1 and WSL (Ubuntu). No validator was run for real:
every result below comes from the fabricated checkout of `scripts/tests/validator_harness.py`, unless it says otherwise.
Notes dated 2026-10-15 were written on the branch `fix/audit-tst18-trv07-validators`, from `develop` at `984bb270`, on
the same machine, under Git Bash and Windows PowerShell 5.1 only; again no validator was run for real. On that
branch `python -m unittest discover -s scripts/tests` ran 521 cases, 1 class skipped (`jq` is not installed).

## 1. Measure before changing

- [x] 1.1 List every script file under `scripts/` outside `scripts/tests/` with what launches it (workflow step, other
      script, dispatcher, current page) and what tests it (a test that runs it, a test that reads it, nothing). Proof:
      the table. On 2026-10-13 at `8b26d3d7`: 50 files; the audit counted 44. Re-measure, trust neither.
  - 2026-10-13: 50 files, re-counted: 22 `.ps1` (20 at the root, 2 under `lib/`), 23 `.sh` (20 and 3), 4 `.py`,
    1 `.cmd`. The table is printed by `python scripts/tests/test_script_inventory.py` and is what the inventory rule
    asserts, so it cannot drift from the directory.
- [x] 1.2 List the validator targets and, per platform, the workflow step or chain that launches each. Proof: the table,
      compared with the one produced by task 2.3 of `rehearse-the-release-chain-before-the-tag`. On 2026-10-13: 13
      targets, of which `m19`, `m20`, `m21` (Linux), `m28` (Windows), and by chain `r2` and `m27` (Windows).
  - 2026-10-13: confirmed. `nightly.yml` names `validate-m19` and `validate-m20` in both variants, `ci.yml` names
    `validate-m21.sh` and `validate-m28.ps1`, `release.yml` names `validate-m28.ps1`; `validate-m28.ps1` runs
    `validate-r2.ps1`, which runs `validate-m27.ps1`. No workflow names `d2`, `m22` to `m26` or `r3`, nor
    `validate-m21.ps1`, `validate-m27.sh`, `validate-m28.sh`, `validate-r2.sh`. Not compared with the table of the
    other change.
- [x] 1.3 List the refusals each validator claims (its `exit 1` and `throw` sites) and the files it reads. Proof: the
      table from which the fabricated checkout and the cases are written.
  - 2026-10-13: not produced as a table. The refusals up to the packaging switch were read validator by validator and
    are the scenarios of `test_validator_verdicts.py` and `test_validator_entry_refusals.py`; the files read are the
    ones `validator_harness.py` fabricates. The refusals after the packaging switch are not listed anywhere.
  - 2026-10-18: produced, `refusals-table.md` beside this file: 545 sites in the 26 validator files of the 13 targets
    (184 before the build, 122 between the build and the packaging switch, 239 after it), each with file, line, kind,
    zone and message, read at `29c942ae`. The table says which zone a test reaches: the first two, on the fabricated
    checkout; the 239 sites after the packaging switch are reached by no test and only a real run observes them (1.4).
    The files read are listed at the head of the table; the fabricated checkout holds that set (2.1).
- [x] 1.4 Run once, on Windows and on Linux, each validator that no workflow launches on that platform. Proof: per
      target and platform, the exit code and the `validation-summary.txt` or `failure-summary.txt`. A validator that
      does not finish is a finding, reported before anything is repaired.
  - 2026-10-13: **not done.** A real run is a full `clean verify` plus packaging, and was excluded from this session.
    What the fabricated checkout shows instead, with packaging skipped, under Git Bash and Windows PowerShell 5.1 on
    Windows and under bash on Linux (WSL): `d2`, `m22` to `m27` and `r2` run to `VALIDATION PASS` in both variants;
    `validate-r3`, both variants, stops on `Unexpected Maven reactor POM count: 19, expected 17` and cannot pass on
    today's reactor. Whether the packaging half of `d2`, `m22` to `m26` still runs is still not known.
  - 2026-10-15: **still not done.** The `r3` reactor count is fixed (3.9). The lane `validators-without-a-lane` of
    `nightly.yml` (3.7) will give the Linux run of `d2`, `m22` to `m26` and `r3` (and through it `m28`, `r2`, `m27`) once
    it runs: on a schedule only after `nightly.yml` reaches `main`, before that by `workflow_dispatch`. No Windows run
    of the `.ps1` variants is planned by it.
  - 2026-10-18: **done**, on `develop` at `29c942ae`, version 1.2.1, from two fresh clones, one per platform, each
    validator run alone with its full `clean verify`. The table, the findings and every summary the validators wrote
    are in `real-validator-runs.md`. Result: on Linux (WSL) `d2`, `m22` to `m26` and `r3` (with `m28`, `r2`, `m27`
    through it) end on PASS; on Windows (PowerShell 5.1) `d2`, `m21`, `m23` to `m26` and `r3` (with `m28`, `r2`,
    `m27`) end on PASS, and `m22` **did not finish** the first time. Single finding: `write-build-provenance.ps1:35`
    calls `.Trim()` on nothing when HEAD is detached and untagged (`git branch --show-current` is empty), after the
    whole build and `JaCoCo: PASS`; seven `.ps1` validators call it. Corrected in 3.6 (below), `m22` passes on a branch
    in 573 s. Durations on a loaded development machine: 503 s to 2149 s per target, none hangs.
- [x] 1.5 Find whether any test runs the four scripts of the Dependency-Check lane, or only reads them. Proof: the test
      and the line that launches the process, or the statement that none does.
  - 2026-10-13: none did (`DependencyCheckWorkflowContractTest` reads them). `test_dependency_check_scripts.py` now
    runs the four.

## 2. Reproduce before fixing

- [x] 2.1 Build the fabricated checkout and compare the files it holds with those a real `./mvnw clean verify` leaves
      (names, first lines, attributes read). Proof: the comparison.
  - 2026-10-13: the checkout is built. Compared by reading, not against a fresh build: the Surefire root element and
    the attributes the validators read (`tests`, `failures`, `errors`, `skipped`) with reports left by an earlier
    build, the evidence lines with the format string of `AggregateCoverageGateTest`, the SBOM names with
    `target/m21-supply-chain/`. The comparison against a `clean verify` of the same commit is not done.
  - 2026-10-18: done against the files a real `./mvnw clean verify` left at `29c942ae`, from the Linux (WSL) run of
    `validate-d2.sh` (the Windows run of `validate-d2.ps1` passed on the same reactor and wrote the same totals). The
    fabricated checkout was built by `validator_harness.FabricatedCheckout` and compared file class by file class:
    - Surefire reports: real 560 files, root `testsuite` in every one; the attributes the validators read (`tests`,
      `failures`, `errors`, `skipped`) are on every real root. The fabricated root carries the same attributes
      (`name`, `time`, `tests`, `errors`, `skipped`, `failures`, `flakes`); a real root adds `version` and an
      `xsi:noNamespaceSchemaLocation`, read by no validator. The report `validate-r2` requires by name
      (`com.morpheus.store.sqlite.R2UpgradeCompatibilityTest`) exists in `morpheus-store-sqlite` at that name.
    - Aggregate coverage evidence: the first line of the real file is `coverageScope=aggregate`; every key of the
      fabricated file exists in the real one with the same name; the real file adds ten keys (`aggregateReport`,
      `populationRule`, `populationModules=16`, `population`, the two qualified baselines, the two ratchets, the two D2
      floors) that the guards do not read, and `coverageSource` carries `jacoco-report-aggregate`, not `fabricated`. Real ratios: `lineRatio=0.910060`,
      `branchRatio=0.771111` on Linux, `0.910525` / `0.771586` on Windows, tests 4194, architecture tests 693.
    - Supply chain: `target/m21-supply-chain/` holds `morpheus-sbom.json` and `morpheus-sbom.xml`, the two names of
      the fabricated checkout. `build-provenance.properties` is written by the validator, not by the build, and is
      not fabricated: the real provenance writer runs against the stub.
    - `target/d2-security/dependency-check-report.json` is **not** left by `clean verify`: only the Dependency-Check
      scan writes it. The fabricated checkout holds it for the cases of the scanning variant of `validate-d2`; the real
      runs of 1.4 used `MORPHEUS_D2_SKIP_SECURITY_SCAN=true` / `-SkipSecurityScan`, so that file is the one thing of the
      fabricated checkout no real run here confirmed.
    - Artifacts looked up by name: `morpheus-provider-reference/target/morpheus-provider-reference-1.2.1.jar` and
      `morpheus-cli/target/morpheus-cli-1.2.1-all.jar` exist under those names.
    No attribute or name the validators read differs; the fabricated checkout is a subset of the real files by design.
- [x] 2.2 Write the cases of the contract for `validate-m21` on both platforms. Proof: the result on `HEAD`; a refusal
      that does not happen is a defect of the validator, listed.
  - 2026-10-13: on `d5be889b` the contract was red on two points, both corrected (3.6): the earlier passing summary
    survived a refused run, and no `.ps1` reached its base-ref fallback.
- [x] 2.3 Run the shared cases of the ratchet reader against `Read-QualityRatchets.ps1`. Proof: the result on `HEAD`.
  - 2026-10-13: two cases red on `HEAD` (`9e-1`, `+0.9` accepted by the PowerShell reader only), corrected (3.6).
- [x] 2.4 Write the inventory rule with no excuse. Proof: red, naming the scripts of 1.1 that nothing runs in a test.
  - 2026-10-13: `test_script_inventory.py`. With no entry it names three scripts nothing launches
    (`validate-m23.sh`, `validate-m24.sh`, `validate-r3.sh`) and six no test runs.

## 3. The change

- [x] 3.1 Extend the cases to every target that can be run on the fabricated checkout; excuse the others by name with
      the reason. Proof: per target, the cases executed on each platform.
  - 2026-10-13: the full contract on `m21`, `d2`, `m22` to `m27` (`test_validator_verdicts.py`) and `r2`
    (`test_validator_entry_refusals.py`); `m19`, `m20`, `m28`, `r3` up to the point they need a product. What no test
    reaches is said per target in `NOT_REACHED_BY_A_TEST`, which must name exactly the existing targets.
- [x] 3.2 The dispatcher's tests.
  - 2026-10-13: `test_validate_dispatcher.py`, `validate.ps1` and `validate.cmd`.
- [x] 3.3 The shared lists of cases for the libraries that exist in two languages.
  - 2026-10-13: `test_read_quality_ratchet.py` and `test_require_aggregate_coverage_evidence.py`.
- [x] 3.4 `ci.yml`: run the script tests on the Windows leg; rename the existing step for what it runs. Make a missing
      tool a failure in CI. Check what `AuditHardeningWorkflowContractTest`, `D2RepositoryHardeningArchitectureTest`
      and `GateReachabilityArchitectureTest` assert about `ci.yml` before editing it.
  - 2026-10-13: done; no `uses:` touched. The three classes and eleven others that read scripts, workflows or pages
    were run after the edit and pass. The Windows step has not run on a GitHub runner yet (4.1).
- [x] 3.5 The inventory rule and its excuses. Mechanism: textual assertion, intent "a script that nothing launches, or
      that nothing tests and nobody excused, must not be in `scripts/`" (ADR-0103; skill `enforcement-choice`). Add the
      rule that each leg runs the script tests.
  - 2026-10-13: **the inventory is a Python test of `scripts/tests/`, not a rule of `morpheus-architecture-tests`** as
    the design and the spec say. "Run by a test" is read from what each test file declares (`SCRIPTS_UNDER_TEST`),
    which Python can import and Java could only parse. The rule that each leg runs the script tests is in
    `GateReachabilityArchitectureTest`, so the architecture suite holds the step that runs the inventory. To be
    confirmed by the maintainer, and the spec amended if it stands.
  - 2026-10-15: spec delta amended (`specs/validator-self-test/spec.md`, the inventory requirement and its scenarios
    now speak of the script tests, not the architecture suite), and decision 5 of the design with it. Two changes of
    substance, both in `test_script_inventory.py`: a documentation page is no longer a launch (the entry points a
    person runs are named in `LAUNCHED_BY_HAND`, with a reason and a current page that names them); and
    `SCRIPTS_UNDER_TEST` is checked against the test code (generated harness classes, validators that name the
    declared libraries, strings of the code). `openspec validate test-the-validators-that-give-a-verdict`: valid.
- [x] 3.6 Correct each validator in which 2.2 or 3.1 showed a refusal that does not happen; one commit per correction,
      with its case.
  - 2026-10-13: six corrections, one commit each: the stale passing summary (22 validator files and
    `validate-m20.sh`); the `.ps1` base-ref fallback under Windows PowerShell 5.1 (8 files); `validate-m20.sh` without
    a failure summary; the dispatcher handing a null argument to a validator launched without one, which made
    `scripts\validate.cmd m19` fail; the PowerShell ratchet reader accepting ratios the shell one refuses; the
    PowerShell evidence guard comparing the scale without its case.
  - 2026-10-18: a seventh, found by the real runs of 1.4: `write-build-provenance.ps1` called `.Trim()` on the empty
    output of `git branch --show-current` on a detached HEAD without a tag, so the `detached` fallback two lines below
    could never apply. The read is now `"$(git branch --show-current)".Trim()`. Case first, red on the old line:
    `scripts/tests/test_write_build_provenance.py` (a branch, a detached head, a tag, a missing SBOM, on the `.sh` and the
    `.ps1`; only the detached case of the `.ps1` failed). The file stays ASCII and CRLF.

- [x] 3.7 Put the decision of open question 1 to the maintainer with the results of 1.4, target by target. A retirement
      follows `milestone-quadruplet` and the precedent of `docs/project/validation/README.md`.
  - 2026-10-13: put, not decided, and without the real runs of 1.4. The three `.sh` nothing launches are named in
    `NOT_LAUNCHED`, each entry a finding; `validate-r3` cannot pass in either variant.
  - 2026-10-15: still not decided by the maintainer. **Default taken**, recorded under open question 1 of the
    design: the recommendation sets none for `m22` to `m26` and `r3`, so every `.sh` validator no workflow launched
    gets a nightly lane (`validators-without-a-lane`: `d2`, `m22` to `m26`, `r3`, Linux, D2 without its
    Dependency-Check scan). Once a page stopped counting as a launch (3.5) the orphans were ten, not three:
    `validate-d2.sh`, `m22` to `m28.sh`, `r2.sh`, `r3.sh`; the lane launches all ten, `NOT_LAUNCHED` is empty. Left
    unchecked: the decision itself, with the results of 1.4, is still the maintainer's.
  - 2026-10-18: the results of 1.4 are in, target by target (`real-validator-runs.md`); **stopped here, waiting for the
    maintainer**. Nothing was retired, nothing was decided. What the runs say: all ten `.sh` validators the lane launches
    finish on PASS on Linux; the `.ps1` of `d2`, `m21`, `m22` to `m26` and `r3` finish on PASS on Windows (`m22` after the
    correction of 3.6). So no candidate is dead weight in the sense "no longer runs". The decision left is whether a
    validator that runs and that no workflow reached is still worth its lane, its minutes and the milestone assertions
    that name it.
  - 2026-10-18, decided by the maintainer on the results above: `d2`, `m22` to `m26` and `r3` are **kept**, with the
    lane `validators-without-a-lane` of `nightly.yml` as it is; no validator is retired, so no milestone assertion
    and no `docs/project/validation/README.md` entry changes. The default taken on 2026-10-15 becomes the decision.
- [x] 3.8 Break each rule once, then revert: remove a comparison from a validator; remove the step from one leg; add an
      untested script; move a launch into a comment; leave an excuse for a tested script. Proof: the named failures.
  - 2026-10-13: each done and reverted. The comparison of the test count removed from `validate-m21.sh` and from
    `validate-d2.ps1`: two named cases fail on each. The evidence guard removed from `validate-m24.sh`: three. The
    clean-tree check removed from `validate-m27.ps1`: one. The Windows command removed from `ci.yml`: the Java rule
    fails and names the Windows leg. A script added: both inventory rules name it. The launches of
    `check-release-rehearsal-assets.py` commented out in `nightly.yml`: named. An entry left for `validate-m21.sh`
    in each list: named as the entry to delete.
  - 2026-10-15, each done and reverted: the reactor fix of `validate-r3` reverted, the `ReactorCount` cases of `r3`
    fail on "expected 17"; the test count of `validate-m24.sh` back to `473` and the line minimum of `validate-m25.ps1`
    back to `0.25`, three named cases fail; `validate-m23.sh` left only in a comment of the nightly matrix, and the `r3`
    leg removed, the inventory names `validate-m23.sh`, then `m27`, `m28`, `r2`, `r3`; a validator and a library
    declared by `test_validator_entry_refusals.py` that it does not run, and a script declared by
    `test_check_diff_coverage.py` only in its declaration, each named; `validate-m21.sh` added to
    `LAUNCHED_BY_HAND`, named as launched by `ci.yml`.
- [x] 3.9 AUD-TST-18: `validate-r3` derives the reactor count from the `<module>` entries of the root POM, as
      `validate-r2` does, and the entry contract holds `r2` and `r3` to it. Proof: commit `c360a79e`; the
      `ReactorCountCases` of `test_validator_entry_refusals.py` (the declared reactor accepted with its count, an
      undeclared POM refused with both counts before the build), 30 cases green on `r2` and `r3`, both interpreters.
- [x] 3.10 The literal floors of `m22` to `m27` (open question 3): both variants read the presence ratchets and the
      aggregate coverage keys before the build. Proof: commit `0099bd4c`; `LivingMinimumsContract` and
      `AggregateCoverageMinimumsContract` now run on the twelve files, `test_validator_verdicts.py` 384 cases green.
      Kept and justified in the design: D2's absolute coverage floors, M19's frozen budgets.

## 4. Verification and destinations

- [x] 4.1 The pull request's required checks on both platforms. Proof: the script tests executed on each leg, with the
      number of cases run and none skipped.
  - 2026-10-20: pull request 520 merged (`c165eb80`); every required check green on both platforms. Run `39409286981`
    (`ci.yml`, head `72366974`): the Linux leg ran the Linux step, 322 cases, `OK (skipped=21)` -- the skipped are the
    Windows PowerShell 5.1 classes, which cannot exist on Linux; the Windows leg ran the Windows step, 579 cases, `OK`
    with none skipped.
  - 2026-10-18: **pending, needs a push and a pull request** (not done here by instruction). The text of the description
    is in `pull-request-description.md`. Locally, Windows, after this session's additions: 529 cases run, 1 class skipped (`jq` is not
    installed here; with `CI` set it fails instead); Linux (WSL), the new file alone: 4 cases run, 4 skipped by name (the
    Windows PowerShell 5.1 cases).
  - 2026-10-13: no pull request opened. Locally: Windows, 427 cases run, 1 class skipped (`jq` is not installed on
    this machine; with `CI` set the same class fails instead); Linux (WSL), 238 cases run, 18 skipped, 17 of them the
    PowerShell classes. "None skipped" cannot hold on the Linux leg: Windows PowerShell 5.1 does not exist there, and
    those classes are skipped by name on any platform but Windows.
- [x] 4.2 Measure the duration of the new step on the Windows leg. Proof: the figure, in the pull request.
  - 2026-10-20: the Windows step of run `39409286981` lasted 477 s (579 cases, 10:38:12 to 10:46:12 UTC, per the
    log), the Linux step 25 s (322 cases). That is a little over the 3 to 4 minutes measured on the development
    machine, and well over "seconds": the Windows leg is the longest of the workflow and the step is what a pull
    request waits for there.
  - 2026-10-18: **pending, needs a runner**: the figure comes from the Windows leg of the pull request.
  - 2026-10-13: not measured on a runner. On the development machine the whole discovery takes three to four minutes
    on Windows (measured twice: 179 s for 404 cases, 234 s for 427) and 36 s on Linux. The step is not "counted in
    seconds" as the design expected: a validator run is a process, and there are several hundred.
- [x] 4.3 Every `.ps1` added or edited is ASCII (`rules/tooling.md`); the hook's check is run.
  - 2026-10-13: 14 `.ps1` edited, none holds a non-ASCII byte; no `.ps1` added. `pre-bash.ps1` still parses.
  - 2026-10-15: 7 `.ps1` edited (`validate-r3`, `validate-m22` to `validate-m27`), none holds a non-ASCII byte, CRLF
    kept; no `.ps1` added, no hook edited.
- [x] 4.4 `scripts/README.md`: how the validators are tested, how to add a case, how to excuse a script. `.claude/rules`
      is touched only if a sentence there describes the old state.
  - 2026-10-13: done. One sentence of `rules/tooling.md` corrected: it said every validator writes a
    `failure-summary.txt`.
  - 2026-10-15: `scripts/README.md` says what a launch is now, what `LAUNCHED_BY_HAND` holds, how a declaration is
    checked, and which validators the nightly lane runs. No sentence of `.claude/rules` described the old state.
- [x] 4.5 Pull request description: the finding, the tables of 1.1 and 1.2, the validators corrected, the excuses and
      their reasons, the justification of the test added under `morpheus-architecture-tests/` and of the workflow edit.
  - 2026-10-18: drafted in `pull-request-description.md`.
  - 2026-10-21: opened as pull request 520 with that description, merged.
  - 2026-10-13: no pull request opened.
