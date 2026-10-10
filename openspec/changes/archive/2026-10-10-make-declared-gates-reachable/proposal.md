# Proposal

## Why

The 2026-10-09 audit (sprint 1, "put the verification apparatus back into service") found that several gates the
repository declares cannot be reached by any real invocation: they exist, they are pinned, they are documented, and
nothing runs them or the documented command does not resolve. Re-read on `origin/develop` at `fe88c856` on
2026-10-10, every finding below still holds.

- **AUD-TST-01 — the five M19 performance budgets run in no pipeline.** `M19PerformanceGate` and its four siblings
  carry a class name that deliberately matches none of Surefire's default patterns
  (`morpheus-architecture-tests/.../m19/M19PerformanceGate.java:24`: "run it only through the M19 validator"), the
  Surefire configuration (root `pom.xml:146-154`, with no override in the module POM) adds no `<includes>`, and
  `grep -rniE "m19|spotbugs|pitest|audit-mutation" .github/workflows/` returns nothing. `ci.yml:43` runs
  `validate-m21.sh` and `ci.yml:86` runs `validate-m28.ps1`; neither chain reaches `validate-m19`. A scan of every
  `src/test/java` finds exactly five test classes outside the default patterns, all five being these gates. A
  performance regression breaks no build, contrary to what `.claude/rules/testing.md:181` says. It is the critical
  path of the whole audit: it blocks AUD-PRF-01/02/04/05/06, AUD-TRV-02 and AUD-DEP-12.
- **AUD-QUA-14 — SpotBugs and PIT are pinned and configured, and run by no workflow.** `pom.xml:87-90` pins
  `spotbugs.maven.plugin.version`, `spotbugs.version`, `pitest.maven.plugin.version` and
  `pitest.junit5.plugin.version`; the profiles `audit-spotbugs` and `audit-mutation` exist; none of the five
  workflows names either. Of the four profiles the root POM declares, the two `d2-security*` profiles are named by
  `security.yml` and these two are named by nothing.
- **AUD-TRV-01 (merging AUD-QUA-03, AUD-TST-05, AUD-DEP-11) — `validate-m15..m18` cannot start, have no `.sh`, and the
  commands documented to replay them do not exist.** `scripts/validate-m15.ps1:9` pins
  `$Branch = 'm15/acceptance-verification-evidence'` and `:113` runs `git switch $Branch` (m16 `:9/:104`, m17
  `:9/:136`, m18 `:9/:136`); `git branch -a` knows `develop` and `main` only. Every other validator
  (`m19..m28`, `d2`, `r2`, `r3`) has its `.sh` twin; these four do not. `docs/validation/VALIDATION_M15.md:199`,
  `M16:199`, `M17:187`, `M18:130,209` and `docs/roadmap/M18_EXECUTION.md:122,161` cite `.\validate-m1x.cmd`.
  `scripts/validate.ps1` still lists the four as targets. The only parity assertion is
  `McpClientIntegrationArchitectureTest.java:219-231`, which names the artefacts of M28 by hand, so nothing stops the
  next milestone from shipping a lone `.ps1`; `.claude/rules/governance.md:61` says the parity "est assertée".
- **AUD-TST-12 — the M19 documentation cites an entry point that does not exist.**
  `docs/validation/VALIDATION_M19.md:89` names it as the command run and `docs/roadmap/M19_EXECUTION.md:210` lists it
  among the files the milestone was to deliver; the root wrapper was removed in `c413a5018` and `scripts/` holds one `.cmd`, the generic `validate.cmd`. The same dead form
  is cited for M20 to M27 (about twenty documents, four of them current developer guides:
  `docs/developer/REMOTE_SERVER_PLATFORM.md:402`, `PROVIDER_SDK.md:347`, `POLICY_PLATFORM.md:332`,
  `ASSISTED_REASONING.md:259`).

**Two things the audit's own recommended actions get wrong, found while re-reading on `HEAD`** (details in
`design.md`): `scripts\validate.cmd m19 -Version 1.2.1` — the form AUD-TST-12 proposes — fails, because
`validate-m19.ps1` has no `-Version` parameter (`powershell -File scripts/validate-m19.ps1 -Version 1.2.1` →
`NamedParameterNotFound`); and the audit's AUD-QUA-14 target, a non-blocking SpotBugs run, must not be confused with
the blocking check that `docs/developer/SPOTBUGS.md` (PR `FTurleque/morpheus-engine#424`) says must stay out of CI
until its alerts are qualified.

Sprint exit criterion (first half): **every gate the repository declares is reachable by a real invocation of the
repository.** Two honest limits are part of the proposal, not footnotes: a `schedule` trigger runs the workflow file of
the default branch, so the new nightly lanes run on their own only once 1.2.1 is promoted to `main` (until then they are
exercised by `workflow_dispatch`, as the existing lanes of that workflow would be); and the last scheduled run of
`nightly.yml` (id `38022097608`, `main`) is red because of its Sonar lane, so an M19 regression must be read at job level
until that verdict is restored.

## What Changes

- The five M19 budget gates run **every night on both platforms** through the M19 validators
  (`validate-m19.sh`, `validate-m19.ps1`), with their evidence uploaded; a budget miss turns the lane red. The budgets
  and the fixture are not touched.
- A test class that Surefire's default patterns do not select **must be named by a validator that a workflow runs**;
  the architecture suite refuses a new one that is not.
- `audit-spotbugs` (report-only, with an analysis-completeness check) and `audit-mutation` (on its declared bounded
  scope) run on a schedule, reports archived; the architecture suite refuses a Maven profile no workflow names unless
  it is listed as manual-only with a reason.
- `validate-m15..m18` are **removed** (their proofs stay in `docs/validation/`; Git history keeps the scripts), and the
  validator parity becomes one generic rule: every `scripts/validate-<target>.ps1` has its `.sh` and vice versa. No
  validator moves the working tree to a named branch.
- Documentation that is *current* names only commands that resolve (`scripts\validate.cmd <target>`); dated records
  keep what they recorded and are pointed to the supported form from their index, `VALIDATION_M19.md` being annotated
  inline because it is where an operator lands to replay the budgets.
- The statements that contradict this (`testing.md:181`, `governance.md:61`, `tooling.md` version-argument paragraph,
  the `milestone-quadruplet` skill, `.claude/CLAUDE.md` "Gates actifs: M19") are corrected in the delivery commit.

## Capabilities

### New Capabilities

- `gate-reachability`: when a gate, validator or documented verification command counts as reachable — run by a real
  pipeline, present on both platforms, and named by documentation only in a form that resolves.

### Modified Capabilities

(none — `openspec/specs/` holds no capability about gates; `public-surface-convergence` covers routes, not
validators)

## Impact

- `.github/workflows/nightly.yml` (new jobs; header comment), `scripts/validate-m15..m18.ps1` (removed),
  `scripts/validate.ps1` (no change expected; its enumeration follows the files).
- `morpheus-architecture-tests`: one new filesystem/text suite (parity, gate classes, profiles, current-doc commands);
  `McpClientIntegrationArchitectureTest` keeps its M28 completeness assertion; `D2RepositoryHardeningArchitectureTest`
  and `AuditHardeningWorkflowContractTest` pin `nightly.yml` and must keep passing.
- Documentation: `docs/validation/README.md`, `docs/validation/VALIDATION_M19.md`, `docs/roadmap/M19_EXECUTION.md`,
  four `docs/developer/*` guides, `docs/developer/CODE_AUDIT.md`, `scripts/README.md`; amendment to
  `ADR-0085` (execution venue of the budgets; no new ADR number).
- `.claude/rules/testing.md`, `governance.md`, `tooling.md`, `.claude/CLAUDE.md`, `.claude/skills/milestone-quadruplet`,
  `.claude/hooks/pre-bash.ps1` (version-argument advice for `m19`).
- No CLI command, MCP tool or HTTP route moves: `contracts/public-surfaces.tsv` and `docs/openapi/` are unaffected.
