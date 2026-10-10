# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull
request that introduces this change.

## 1. Reproduce before fixing — one failing test per rule

- [x] 1.1 Add a suite `GateReachabilityArchitectureTest` in `morpheus-architecture-tests` (filesystem and text, per
  - 2026-10-10: `GateReachabilityArchitectureTest`, five methods; committed red first (`ac0cc01b`), all five failing on `develop`.
      `enforcement-choice`: the targets are `.sh`, `.ps1`, `.yml`, `.xml`, `.md`) with one method per intention, not one
      per class (`rules/architecture.md`: splitting by intention keeps `architectureTestsMinimum` from falling):
      `everyValidatorExistsOnBothPlatforms`, `noValidatorSwitchesToANamedBranch`,
      `everyTestClassOutsideSurefireDefaultsIsNamedByAValidatorAWorkflowRuns`,
      `everyRootPomProfileIsNamedByAWorkflowOrExcusedWithAReason`, `currentDocumentationNamesOnlyResolvingCommands`.
      Proof: on `HEAD` the first two fail naming `m15`..`m18`, the third fails because no workflow names
      `validate-m19`, the fourth names `audit-spotbugs` and `audit-mutation`, the fifth names the four
      `docs/developer/*` guides. Save each failure message in the pull request.
- [x] 1.2 For each of the five methods, break the rule once and observe the failure (add a lone `validate-m99.ps1`; a
  - 2026-10-10: ten breakages (lone `.ps1` and `.sh`, `git switch`, `M19ExampleGate`, comment-only mention, stray `*Test` in `morpheus-coverage-report`, unrun profile, `-Pd2-security-tests` only, removed wrapper, unknown dispatcher target), each failed the intended method; tree restored.
      `M19ExampleGate` with a `@Test`; the same name present only in a YAML comment; a `*Test` class in
      `morpheus-coverage-report`; a profile `audit-example`; only `-Pd2-security-tests` passed so that `d2-security` must
      be reported; `.\validate-m25.cmd` in a guide), then revert (`enforcement-choice`, obligation 3). Proof: the commands
      and outputs listed in `design.md` section "Verification", replayed against the real test.
- [x] 1.3 These are filesystem rules, so `archRule.failOnEmptyShould` does not protect them: assert explicitly that each
      listing is non-empty (the validators, the test classes found, the profiles, the documents scanned) so no rule can pass
      on an empty population. The test-class selection reads the module POMs (`<includes>` of `morpheus-coverage-report`)
      as well as Surefire's defaults, and the workflow/profile rules read executable text only: YAML comments stripped, `-P`
      arguments tokenised.

## 2. Retire the validators that cannot start

- [x] 2.1 `git rm scripts/validate-m15.ps1 scripts/validate-m16.ps1 scripts/validate-m17.ps1 scripts/validate-m18.ps1`.
      Proof: `scripts\validate.cmd list` shows none of them; `everyValidatorExistsOnBothPlatforms` and
      `noValidatorSwitchesToANamedBranch` pass.
- [x] 2.2 `docs/validation/README.md`: one paragraph naming, for each of the four, the last commit that held the script
  - 2026-10-10: last commits `d1aebd974`, `3861ccf07`, `1a6fb8b38`, `3d58bb9d4` recorded in `docs/validation/README.md`; all resolve with `git cat-file -t`.
      (read with `git log -1 --format=%h -- scripts/validate-mNN.ps1` before removal — do not guess), stating that
      `validate-<target>.cmd` in older records means `scripts\validate.cmd <target>` since `c413a5018`, and that
      `VALIDATION_M15..M18` are unchanged. Proof: the sentence is present and the four hashes resolve with `git cat-file -t`.
- [x] 2.3 Search `scripts/README.md`, `docs/validation/README.md` and `docs/developer/*` for `validate-m15..m18` and
      repair any current mention (dated records stay as they are). Proof:
      `grep -rn "validate-m1[5-8]" scripts docs/developer README.md` is empty.

## 3. Documentation that is current

- [x] 3.1 In `docs/developer/REMOTE_SERVER_PLATFORM.md:402`, `PROVIDER_SDK.md:347`, `POLICY_PLATFORM.md:332`,
  - 2026-10-10: the four guides now give `scriptsalidate.cmd <target> -Version <version>` and the `.sh` form; each target has a `Version` parameter in its `.ps1` (argument binding not run: those validators would start a full run).
      `ASSISTED_REASONING.md:259`: read the enclosing block first, then always replace the wrapper by
      `scripts\validate.cmd <target> -Version <current version, read from pom.xml>` and drop the stale `1.0.0`. A dated
      result sitting beside it keeps its result line; only the wrapper name goes. No exemption marker. Proof: `currentDocumentationNamesOnlyResolvingCommands`
      passes; each replaced command is run up to its argument binding (`powershell -NoProfile -File scripts/validate-<t>.ps1
      -Version <v>` → no `NamedParameterNotFound`).
- [x] 3.2 `docs/validation/VALIDATION_M19.md:89`: keep the recorded command, add a dated note under it naming
      `scripts\validate.cmd m19` (no `-Version`). `docs/roadmap/M19_EXECUTION.md:210` lists files to deliver, not a command:
      leave it, the index paragraph of 2.2 covers it. Proof: the original lines are byte-identical in `git diff`; the note is
      the only addition.
- [x] 3.3 `scripts/README.md`: reword line 7 (`validate-<target>.sh  Linux / WSL lorsque disponible`) so that it no longer
      licenses a missing `.sh`, and add a short M19 section (Windows `scripts\validate.cmd m19`, Linux
      `bash ./scripts/validate-m19.sh`, switches `-SkipPackaging`/`-SkipBenchmarks` are diagnostic and never a
      qualification, as the D2 section already says of its own). Proof: the section exists; the page's own commands run
      up to argument binding.

## 4. The scheduled lanes

- [ ] 4.1 `nightly.yml`: add a matrix job (`ubuntu-latest` → `bash ./scripts/validate-m19.sh`, `windows-latest` →
      `.\scripts\validate-m19.ps1`), checkout of `develop` on `schedule` and of the dispatched ref on `workflow_dispatch`
      (`ref: ${{ github.event_name == 'schedule' && 'develop' || github.ref }}`), pinned action SHAs already present in the
      file, no `continue-on-error`, and no mention of the packaging tool's name in comments (a contract test refuses it
      next to `macos`),
      `timeout-minutes` sized from a first run (the reference figures are 188–339 s for the gate stage alone, plus a
      reactor and packaging), `actions/upload-artifact` of `validation-output/m19*/**` with `if: always()`. No secret is
      used by these jobs. Update the file header, which today says the workflow has "two" lanes.
      Start with a reconnaissance dispatch of the Windows job: `validate-m19.ps1:149-152` refuses a disk that is neither `SSD`
      nor `NVMe`, and a hosted runner may report neither. If it refuses, stop and take the decision recorded as an open
      question in `design.md` (recognise a hosted runner, or leave the Windows lane out and keep that proof explicitly
      missing); never relax a budget. Proof: a `workflow_dispatch` run from the pull-request branch (allowed because the jobs
      hold no secret, and now testing the branch because of the `ref` expression) passes on both platforms; the artefact holds `validation-summary.txt` with five groups of `M19_METRIC`; the five timed budgets, the heap
      ceiling and the database-size budgets are listed with their margin; the run id and the measured values are recorded in `docs/validation/README.md` as a dated measurement (not a ratchet).
  - 2026-10-10, `workflow_dispatch` run 38072058177 from the branch (`4142c00f`): Linux **PASS** in about 8 minutes (reactor 357 s, performance gates 77 s, packaged startup PASS; every `M19_METRIC` inside its budget, `max_heap_mib=768`); evidence artefact `m19-budget-evidence-Linux`. Windows **refused by the validator**, as predicted: `Logical processors: 4`, `Visible RAM GiB: 16`, `Disk model: Msft Virtual Disk`, `Disk bus: SAS`, `Disk media: Unspecified` -> `Workspace must be on a local SSD; media=Unspecified bus=SAS`. Per this task the work stops here for the decision recorded as an open question in `design.md`; left unchecked.
- [ ] 4.2 Prove the lane goes red: in a throw-away commit lower one budget constant below the measured value, dispatch,
      observe `failure-summary.txt` naming "M19 performance gates" and the job failing on that platform, then discard
      the commit. Proof: run id and the failure summary in the pull request. Never keep a changed budget
      (`M19_PERFORMANCE_BUDGETS.md` §8).
- [ ] 4.3 `nightly.yml`: add the SpotBugs job (`./mvnw -Paudit-spotbugs -Dspotbugs.failOnError=false -DskipTests
      verify`, JDK 21 — Enforcer refuses another) with a completeness step that reads each module's `spotbugsXml.xml`
      (`Project/Jar`, `errors='0'`, `missingClasses='0'`, non-zero `cpu_seconds`, one XML per module that has
      `target/classes`), writes the alert total and per-module counts to `$GITHUB_STEP_SUMMARY`, and uploads
      `**/target/spotbugsXml.xml` and `**/target/reports/spotbugs.html`. Proof: a dispatch run passes and its summary
      matches a local `-Dspotbugs.failOnError=false` run on the same SHA; deleting one module's XML in a throw-away
      commit fails the step naming the module.
  - 2026-10-10, same run: the SpotBugs job passed (analysis proof accepted by `check-spotbugs-reports.py`). The "delete one XML in a throw-away commit" proof was replaced by the unit tests of the checker (`scripts/tests/test_check_spotbugs_reports.py`); left unchecked until it is run for real.
- [ ] 4.4 `nightly.yml`: add the PIT job on the `audit-mutation` defaults (`-pl morpheus-domain test-compile
      org.pitest:pitest-maven:mutationCoverage`, `-Dmorpheus.project.version` is already in the profile's `jvmArgs`),
      uploading `**/target/pit-reports/**`. Proof: a dispatch run passes in about the 13 s the profile's default scope
      takes locally; narrowing `pit.targetClasses` to a package without tests in a throw-away commit fails the step.
      Do not widen the scope here: `CODE_AUDIT.md` lots are serial and leave orphan JVMs on `provider-sdk` and
      `mcp-transport`; widening is its own measured decision.
  - 2026-10-10, same run: the PIT job passed on the profile default scope. The "narrow `pit.targetClasses`" failure proof is not run; left unchecked.
- [x] 4.5 Run `D2RepositoryHardeningArchitectureTest` and `AuditHardeningWorkflowContractTest` — both pin
  - 2026-10-10: `D2RepositoryHardeningArchitectureTest`, `AuditHardeningWorkflowContractTest`, `RepositoryDocumentationCoherenceTest` green.
      `nightly.yml`. Proof: green. If `scripts/validate-d2.*` is run on the branch, remember it refuses a diff that
      touches `.github/workflows` ("D2 is local-only"); that refusal is by design and not a defect of this change.

## 5. Rules and records that contradicted the code

- [x] 5.1 `.claude/rules/testing.md:181`, the paragraph "Budgets de performance" of
      `.claude/skills/milestone-quadruplet/SKILL.md` (line 77) and `.claude/CLAUDE.md` "Gates actifs : M19 (perf)": the
      sentence "une régression de perf casse le build" is false of `clean verify`; say it breaks the nightly lane (once
      promoted to `main`, or when dispatched) and that `clean verify` does not run the budgets.
- [x] 5.2 `.claude/rules/governance.md:61`: the parity is asserted by `GateReachabilityArchitectureTest` for every
      validator, not for the last milestone only.
- [x] 5.3 `.claude/rules/tooling.md` ("Lancer les validateurs"), `.claude/skills/milestone-quadruplet/SKILL.md`
      ("Passer la version explicitement") and `.claude/hooks/pre-bash.ps1:108`: except `m19`, which takes no version.
      Proof: the hook is tested after editing, per `rules/tooling.md`
      (`echo '{"command":"..."}' | powershell -NoProfile -File .claude/hooks/pre-bash.ps1`), with the file kept ASCII.
- [x] 5.4 ADR-0085: dated amendment (`## Amendement du <date>`, the form ADR-0021, 0028 and 0103 use; verdict unchanged,
      index unchanged) - the budgets run nightly on both platforms through the M19 validators; the nightly is a regression
      signal, the M19 qualification proof stays the local validators of `VALIDATION_M19.md`. Read ADR-0089 section 1 ("CI
      durable", rejected alternative "un workflow par milestone") and `VALIDATION_M19.md:33` first and say why neither is
      contradicted (a job of a generic workflow, not a workflow per milestone; a regression signal, not a qualification), and
      why the general rule "every declared gate is reachable" has no ADR of its own (it is carried by a test; ADR-0103).
      No new ADR number.
- [x] 5.5 `docs/developer/CODE_AUDIT.md` and, once PR #424 is merged (merge it first; this change cites its measurements),
      `docs/developer/SPOTBUGS.md`: state that
      `audit-spotbugs` runs nightly report-only and that the blocking check stays out of CI until its alerts are
      qualified; they now say the profile runs in no CI.

## 6. Integration

- [ ] 6.1 `./mvnw clean verify`, then `./mvnw test -pl morpheus-architecture-tests`. Proof: green, with the new suite's
      methods counted.
  - 2026-10-10: the whole `morpheus-architecture-tests` module ran locally, 603 of 604 green; the one failure is `CoverageQualityGateTest`, which needs a preceding full `clean verify` (partial local reports, 0.56 against 0.691). The full reactor is left to CI.
- [ ] 6.2 Decide, with `coverage-ratchet`, whether `architectureTestsMinimum` rises by the new methods (it never
      falls); if so change it and every destination `rules/meta.md` lists in the same commit. This is a decision, not a
      requirement of this change.
- [ ] 6.3 `scripts\validate.cmd m21 -Version <current>` on Windows and `bash ./scripts/validate-m21.sh <current>` on
      Linux. Proof: both PASS on the same SHA.
