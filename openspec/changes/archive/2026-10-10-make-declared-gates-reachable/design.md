# Design

## Context

Reading the repository at `fe88c856` (`origin/develop`, 2026-10-10):

- `scripts/validate-m19.{sh,ps1}` are the only callers of the five budget gates. Each runs, in order: a clean
  workspace check, a reference-environment check (at least 4 logical processors and 8 GiB — below that the verdict is
  "informative, not a proof", `docs/roadmap/M19_PERFORMANCE_BUDGETS.md` §1), a full `clean verify`, the robustness
  contracts, the five gates with `-DargLine=-Xmx768m`, portable packaging, and a packaged-startup benchmark. They list
  the five classes **by hand** in a `-Dtest=` argument.
- The same document records what that costs, measured at M19 on a 16-logical-processor machine: performance-gate stage
  187.8 s (Windows) and 339 s (Linux, ext4), against a full reactor of 69.2 s / 66 s at the time.
- `nightly.yml` already hosts two non-gating daily lanes (`sonar-develop`, `macos-smoke`), checks out `develop`
  explicitly, and is pinned by `D2RepositoryHardeningArchitectureTest` (pinned action SHAs) and
  `AuditHardeningWorkflowContractTest`.
- `VALIDATION_M19.md` §1 records that a `m19-validation.yml` workflow was tried and removed ("GitHub Actions n'est pas
  une source de vérité M19"). The sentence is about the **qualification** of M19 — the proof that the milestone's
  candidate SHA met its budgets — and stays true: this change adds a regression signal, it does not replace that proof.
- The repository is public (`gh repo view`: `PUBLIC`), so the standard hosted runners are the 4-vCPU / 16-GiB
  machines; the validator's own environment stage is the arbiter, not this document, and refuses a smaller machine
  instead of reporting a budget verdict.

## Goals / Non-Goals

**Goals**
- Every gate the repository declares is reached by an invocation a workflow performs, on each platform it claims.
- The set of gates that need such an invocation cannot shrink or grow silently.
- A documented replay command resolves.

**Non-Goals**
- Changing a budget, a fixture or a timeout of M19 (ADR-0085 decision 7 and `M19_PERFORMANCE_BUDGETS.md` §8 forbid it).
- Making SpotBugs blocking. Its alerts are not qualified (`docs/developer/SPOTBUGS.md` in PR #424); a blocking run
  would be red on day one.
- Repairing the performance regressions the audit lists (AUD-PRF-*): they are unblocked by this change, not part of it.

## Decisions

1. **A dedicated nightly job running the M19 validators, on both platforms — not a rename of the gates.**
   The audit offers either. Cost of the rename, from the figures above: the five gates become 188–339 s of every
   `clean verify` — that is the Linux and Windows lanes of every pull request (and of the push to `develop`, which
   `ci.yml` runs twice), `sonar-develop` and `macos-smoke`; five executions per integration event against one per
   night. Three more reasons it loses:
   (a) the heap gate asserts `Runtime.getRuntime().maxMemory() <= 768 MiB` (`M19PerformanceGate.java:92-94`), which only
   holds in a JVM started with `-Xmx768m` — what the validators pass for this stage alone. In the shared Surefire JVM
   (a quarter of the machine's memory by default) the assertion would **fail**, so keeping the rename honest needs a
   second Surefire execution with its own `argLine`, i.e. a second configuration to keep in step;
   (b) the packaged-startup budget (p95 ≤ 5 s) is not a Surefire test at all and would stay unreachable;
   (c) the gates would start counting in the presence ratchets (`testsMinimum`, `architectureTestsMinimum`), which
   would then have to be raised in step (`rules/meta.md`).
   Also rejected: running only the Maven gate stage from YAML — it would become a third copy of the hand-written class
   list (after `.sh` and `.ps1`), lose the startup budget, and lose the evidence format. The job therefore runs
   `bash ./scripts/validate-m19.sh` on `ubuntu-latest` and `.\scripts\validate-m19.ps1` on `windows-latest`
   (M19 §7: "Windows PASS != Linux PASS"), uploads `validation-output/m19*/` (summary, failure summary, logs with the
   `M19_METRIC` lines), and is red when the validator is. The validator repeats a `clean verify`; accepting that
   duplicate (the lane is advisory and the repository is public) is cheaper than adding a "skip reactor" switch to a
   frozen validator. If the minutes ever matter, the switch is the follow-up, not a reason to wait.
   Cadence: `nightly.yml` is scheduled daily at 03:41 UTC. The recorded margins are large for the timed budgets
   (inventory scan 107-699 ms against 20 s; full publish 2.4-3.1 s against 60 s; packaged startup 160-290 ms against
   5 s), so runner noise is not the expected risk; the heap and database-size budgets have no such margin cited and the
   first run records all of them (task 4.1). The new jobs check out `develop` on `schedule` and the dispatched ref on
   `workflow_dispatch` (`ref: ${{ github.event_name == 'schedule' && 'develop' || github.ref }}`): the existing
   `ref: develop` of the Sonar lane exists to keep a secret away from unmerged code, and these jobs hold no secret, so
   pinning `develop` would only make every pre-merge proof test the wrong tree.
   Two constraints already pinned in the file: `D2RepositoryHardeningArchitectureTest` refuses the word `jpackage` next to
   `macos` in `nightly.yml` (do not name the packaging tool in a comment there) and requires `continue-on-error: true` to
   remain for the macOS lane, which the new jobs must not carry.
   An ADR-0085 amendment records the venue; no new ADR number is needed (see "ADR" below).

2. **The invariant "a gate outside the default patterns is run" is a filesystem/text test, not an ArchUnit rule** — per
   `enforcement-choice`: its intent ("this name must appear in that script and that script in a workflow") targets
   `.sh`, `.ps1` and `.yml`, which ArchUnit cannot see. It lists test classes whose simple name matches none of
   Surefire 3.6's defaults (`Test*`, `*Test`, `*Tests`, `*TestCase`) and that carry `@Test`, and requires each to be
   named in `validate-m19.sh` and `validate-m19.ps1` and the validator to be named in a workflow. Two precisions found in
   review: a module that restricts Surefire with `<includes>` selects only those classes (`morpheus-coverage-report`
   runs `**/AggregateCoverageGateTest.java` only, and only in `verify`), so the selection is computed from the module's
   POM as well as from the default patterns; and "named" must mean named in executable text - YAML comments are
   stripped and `run:` content only is read, otherwise a comment such as the header of `nightly.yml` would satisfy the
   rule. Profile names are tokenised from `-P` arguments (`-Pa`, `-P a,b`), because `d2-security` is a prefix of
   `d2-security-tests` and a `contains` would let one hide the other. Why not derive the
   `-Dtest=` list in the validators from the filesystem instead? It would make the omission impossible rather than
   detected, but it turns a frozen validator into a generated one and widens this change; the test is the smaller step
   and the derivation can follow.

3. **SpotBugs: report-only nightly with an analysis-completeness check; PIT: the profile's declared bounded scope.**
   Measured (`CODE_AUDIT.md` §"Durées" and `SPOTBUGS.md` §9.1): the full-reactor report-only run takes 231 s / 157 s,
   16 modules, 192 alerts at the last run; PIT's default scope takes 13 s. A report-only run exits 0 on alerts, so the
   lane must also fail when the analysis did not happen: a module that carries classes without a report,
   `missingClasses` or `errors` greater than zero, or an empty PIT scope (`pit.coverageThreshold=1` already makes PIT
   refuse a scope that covers nothing). That reproduces the repository's own rule that "`BUILD SUCCESS` ne dit pas que
   le code est propre" and that `total_classes='0'` proves nothing. PIT is invoked as `-pl morpheus-domain ...` (the form `CODE_AUDIT.md` documents and the 13 s were measured with): the
   profile's default targets live in that module and, without `-pl`, every other module would find no mutation to run.
Rejected: PIT over every module from hosted runners —
   `CODE_AUDIT.md` requires one batch at a time (two concurrent batches turn mutations into `TIMED_OUT`, counted as
   killed), and `provider-sdk` / `mcp-transport` batches leave orphan JVMs; widening the scope is a decision with its
   own cost measurement. Rejected: also failing on alerts — it pre-empts the qualification decision `SPOTBUGS.md` §9.3
   says is not taken. The job summary publishes the alert total and per-module counts so a revision-to-revision
   comparison needs no manual re-run.

4. **Profiles: every Maven profile the root POM declares is named by a workflow, or is listed as manual-only with a
   reason.** This is the generic form of AUD-QUA-14 (it flags exactly `audit-spotbugs` and `audit-mutation` today). The
   exclusion list follows the house pattern of `public-surface-convergence` ("an explicit exclusion with a reason").

5. **M15–M18: remove, do not repair, do not archive in place.**
   *Repair* would pin a SHA argument and write four `.sh` — but these validators check the state of a milestone branch
   that no longer exists, against assertions of that milestone; running them on today's `HEAD` would produce a verdict
   that is neither the recorded proof nor a current check, and nothing could prove the four ports faithful.
   *Archive in `scripts/archive/`* keeps startable-looking dead code that the dispatcher's glob would not list but a
   reader would; `rules/code-style.md` forbids dead code. Their proofs live in `docs/validation/VALIDATION_M15..M18.md`
   and the scripts in Git history (`git show <sha>:scripts/validate-m15.ps1`). The milestone quadruplet is not
   weakened: no test suite directory exists for M15–M18 under `morpheus-architecture-tests/.../architecture/`
   (the per-milestone suites start at `m19`), so the "four artefacts" contract never applied to them; the new parity
   rule is stated on `scripts/` and is independent of which milestones exist.

6. **Parity is one rule over the directory, plus "no validator moves the checkout to a named branch".**
   `validate-<t>.ps1` ⇔ `validate-<t>.sh`, symmetric (a lone `.sh` is as wrong as a lone `.ps1`). The branch-pinning
   prohibition targets the cause of the four failures (`git switch <literal branch>`); no remaining validator uses it
   (`grep` on `scripts/` after the removal returns nothing).

7. **Documentation: fix current surfaces, annotate dated ones.** `docs/validation/VALIDATION_*.md`,
   `docs/roadmap/*_EXECUTION.md` and the ADRs are dated records of what was run; rewriting `.\validate-m27.cmd 1.0.0`
   there would falsify them (the lesson recorded in `RepositoryDocumentationCoherenceTest`'s own javadoc: "a page is not
   the unit of currency; a block is"). So: the four `docs/developer/*` guides and `scripts/README.md` are fixed by
   **rewriting the command**, always - a guide gives instructions, and a command that does not resolve is not an
   instruction even when a block calls it "historical" (`REMOTE_SERVER_PLATFORM.md:400-404` does); any dated result that
   sits beside it keeps its result line and loses only the wrapper name. No marker-based exemption is introduced: it
   would be a mechanism to excuse exactly the defect. `VALIDATION_M19.md:89` gets an inline dated note (it is where an
   operator lands to replay the budgets); `M19_EXECUTION.md:210` is a list of files the milestone was to deliver, not a
   replay command, so it is covered by the index paragraph only; `scripts/README.md:7` ("validate-<target>.sh - Linux /
   WSL lorsque disponible") is reworded because it licenses a missing `.sh`, which the parity rule now forbids; and
   `docs/validation/README.md` gains one paragraph stating that `validate-<t>.cmd` in older records means
   `scripts\validate.cmd <t>` since `c413a5018`. The test scans an explicit list of current surfaces — extending a list
   rather than writing a sweep, as that same javadoc asks.

8. **The supported M19 replay command is `scripts\validate.cmd m19`, without `-Version`.** The audit proposes
   `... m19 -Version 1.2.1`; it fails (`NamedParameterNotFound`), and `validate-m19.sh` silently ignores a positional
   version. Giving M19 a `-Version` parameter would be a change to a frozen validator for a budget that is version
   independent, and ignoring it is the silent degradation the repository forbids. The honest fix is to document the
   form that works and to correct the three places that say every validator takes a version
   (`tooling.md` "Lancer les validateurs", the `milestone-quadruplet` skill, `pre-bash.ps1` line 108) so they except
   `m19`. Whether `m19` should instead gain a version-checking parameter is left as an open question.

## Verification of the proposed rules (broken once, as `enforcement-choice` requires)

Run against the working tree on 2026-10-10, without modifying it:

```text
parity            HEAD                         -> ['m15', 'm16', 'm17', 'm18']   (rule fails: not vacuous)
                  after removing m15..m18      -> []                             (rule passes)
                  + a lone validate-m99.ps1    -> ['m99']                        (rule fails again)
                  + a lone validate-m99.sh     -> ['m99']                        (symmetric)
gate classes      outside Surefire defaults    -> 5 (all M19*Gate), each named once in .sh and in .ps1
                  a sixth class absent from both validators -> 0 occurrences      (rule would fail)
profiles          audit-spotbugs, audit-mutation -> named by 0 workflows         (rule fails today)
                  d2-security, d2-security-tests -> named by 1 workflow          (rule passes)
```

## Non-duplication check

Done by hand against the eight capabilities of `openspec/specs/` on 2026-10-10. None of them speaks about gates,
validators, pipelines or documented commands: `public-surface-convergence` is about HTTP routes, the manifest and OpenAPI;
`sqlite-schema-history`, `store-closed-refusal`, `policy-*`, `portfolio-provider-identity`, `openspec-provider-reading`
and `mcp-tool-failure-contract` are product behaviour. Each of the seven new requirements (unselected test classes are run;
budget gates scheduled on both platforms and fail the run; analysis tooling exercised without gating; profiles named or
excused; validator on both platforms or neither; validator checks its own checkout; current documentation names only
resolving commands) is absent from all eight. The closest — "an explicit exclusion with a reason" in
`public-surface-convergence` — is a pattern reused, not a requirement repeated.

## Supervision objections and their treatment

Two read-only reviewers read the four changes on 2026-10-10: the `architect` agent (layer rules, existing tests, feasibility) and the `contract-guardian` agent (public surface, ADR need). Neither found a public surface touched, a layer rule violated, or a duplicate of the eight existing capabilities. Their objections and what became of each:


| # | Objection (reviewer) | Treatment |
|---|---|---|
| 1 | `ref: develop` in the new jobs makes every dispatch proof test `develop`, not the branch (both) | **Accepted.** `ref` expression of decision 1; tasks 4.1-4.4 |
| 2 | Docs rule contradicts "annotate a dated block" for `REMOTE_SERVER_PLATFORM.md:400-404` (architect) | **Accepted.** Rewrite the command in the four guides, no exemption marker (decision 7, task 3.1) |
| 3 | Windows validator demands an SSD/NVMe disk, unmentioned (architect) | **Accepted.** Risk, open question, reconnaissance dispatch first (task 4.1) |
| 4 | Nothing runs by `schedule` before promotion to `main`; nightly already red (both) | **Accepted.** Stated in the proposal, Risks, Open questions |
| 5 | PIT command lacks `-pl morpheus-domain` (architect) | **Accepted.** Decision 3, task 4.4 |
| 6 | "Named by a workflow/validator" and "profile named" satisfied by a comment or a name prefix (architect) | **Accepted.** Executable text only, `-P` tokenised; spec scenarios added; task 1.2/1.3 |
| 7 | `morpheus-coverage-report` restricts its own Surefire `<includes>` (architect) | **Accepted.** Spec scenario; decision 2 |
| 8 | `scripts/README.md:7` licenses a missing `.sh` (architect) | **Accepted.** Reworded (decision 7, task 3.3) |
| 9 | Heap-budget direction inverted in decision 1(a) (architect) | **Accepted and corrected**: the assertion would fail, not pass, in a default JVM |
| 10 | `M19_EXECUTION.md:210` is a deliverables list, not a replay command (architect) | **Accepted.** Only `VALIDATION_M19.md:89` is annotated |
| 11 | Skill `milestone-quadruplet` line 77 omitted from the corrections (both) | **Accepted.** Task 5.1 |
| 12 | `nightly.yml` is pinned by contract tests (`jpackage`+`macos`, `continue-on-error`) (architect) | **Accepted.** Recorded in decision 1 and task 4.1 |
| 13 | ADR-0085 amendment too thin; ADR-0089 section 1 and `VALIDATION_M19.md:33` must be addressed (convergence) | **Accepted.** Task 5.4 now requires reading and citing both and saying why the general rule has no ADR of its own (it is carried by a test; ADR-0103) |
| 14 | Margins cited for one budget only (convergence) | **Accepted.** Task 4.1 records all of them at the first run |
| 15 | Dependence on PR #424 / `SPOTBUGS.md` (both) | **Accepted.** Risks; merge #424 first |


## Risks / Trade-offs

- [The nightly lanes consume runner minutes and add two daily failures if the budgets regress] → advisory by design
  (header of `nightly.yml`): a red nightly is the signal wanted; the public repository bears no minute cost.
- [Nothing runs by itself before 1.2.1 reaches `main`] -> a `schedule` runs the default branch's workflow, and `git diff
  origin/main origin/develop -- .github/workflows/nightly.yml` is empty today. Until promotion the lanes are exercised by
  `workflow_dispatch`; backporting `nightly.yml` to `main` is an open question, and the sprint criterion is stated with
  this limit.
- [The nightly workflow is already red] -> its last scheduled run failed in the Sonar lane (`38022097608`); M19 results are
  separate jobs and are read per job. Restoring the Sonar verdict is out of scope.
- [The Windows validator requires an SSD or NVMe disk] -> `validate-m19.ps1:149-152` throws unless `MediaType` is `SSD` or
  `BusType` is `NVMe`; a hosted Windows runner may report `Unspecified`. `M19_PERFORMANCE_BUDGETS.md` section 1 accepts
  "local SSD / runner local filesystem", so a check that recognises a GitHub-hosted runner is consistent with the budget
  document, but changing it is a decision: task 4.1 starts with a reconnaissance dispatch, and if the check refuses, the
  Windows lane waits for that decision and is reported as not executed, never as a pass or a relaxed budget.
- [This change cites `SPOTBUGS.md`, which exists only on PR #424] -> the references are to dated measurements in a document
  that is not on `develop` yet; merge #424 first, or read the same figures in `CODE_AUDIT.md`.
- [A hosted runner slower than the M19 reference machine produces a false budget miss] → margins above are 10x or more;
  the validator refuses a machine below the reference environment rather than reporting a verdict; if a miss appears
  that is not a regression, the response is an environment diagnosis, never a higher budget.
- [Removing the validators loses a way to re-run the M15–M18 proofs] → the proofs are dated records of runs that
  needed the milestone branches; Git history preserves the code.
- [`nightly.yml` is pinned by two contract tests] → the new jobs reuse the pinned action SHAs already in the file.
- [A dated record now says one thing and the current guide another] → the index paragraph and the inline M19 notes
  state the equivalence in both directions.

## ADR

No new ADR. The execution venue of the budgets is an amendment to ADR-0085 (decision 5 already says when the budgets are
blocking; the amendment says where they are run). The numbering was read from `docs/adr/` on 2026-10-10: 109 numbered
records, highest `0109`, no duplicate — nothing is attributed here.

## Open Questions

- Does the maintainer want `validate-m19` to accept `-Version` for uniformity (decision 8), or to stay version-free?
- Backport `nightly.yml` to `main` ahead of the promotion of 1.2.1, so that the lanes run on their own sooner?
- RESOLVED 2026-10-10 (maintainer: option 1): `validate-m19.ps1` recognises a runner GitHub declares hosted (`GITHUB_ACTIONS` and `RUNNER_ENVIRONMENT=github-hosted`); a self-hosted runner or a developer machine must still show an SSD. Was: if the Windows reference-environment check refuses a hosted runner: recognise `GITHUB_ACTIONS` in the check, or leave the
  Windows lane out and keep the Windows proof explicitly missing?
