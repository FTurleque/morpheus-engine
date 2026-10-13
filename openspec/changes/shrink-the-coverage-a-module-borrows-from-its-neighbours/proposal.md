# Proposal

## Why

AUD-TRV-02 (sprint 13, Medium, merging AUD-TST-07 and AUD-QUA-10) reports that about a fifth of the repository's
coverage is credited to modules by tests that live elsewhere, and that `morpheus-application` - the module of the
ports and the use cases - is the one that borrows most. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding
**holds**, and its proposed remedy needs two corrections.

What was re-read:

- The two qualified ceilings the finding subtracts are still the ones in the gates:
  `CoverageQualityGateTest.java:95-96` (per-module scale) and `AggregateCoverageGateTest.java:77-78` (aggregate scale).
  The finding cites lines 92 and 74; the constants moved, their values did not. The four ratchet keys of
  `config/m21-quality-ratchets.properties` are unchanged since the audit.
- The seven behaviour classes the finding names are still named by no test file of the repository:
  `CompactChangeAnalysisView`, `PolicyPublicViews`, `CompactQualityReportView`, `CompactQualityReportService`,
  `QuerySchemaRegistry`, `ObservedProjectSnapshotPublisher`, `WorkspacePathBoundary` - zero occurrence under any
  `src/test`. Re-counted the same way as the audit, 31 classes of `morpheus-application` are named by no test, as the
  audit says; the module now holds 343 source files and 24 861 lines for 76 test files and 11 732 lines (the audit
  read 341, 24 819 and 11 460).
- 49 test files of `morpheus-architecture-tests` import an application service, against 4 in `morpheus-application`
  itself. The cross-module credit is not incidental: the behavioural suites of the application live in the transverse
  test module.

**First correction - the remedy as written cannot be built.** The finding asks for unit tests in
`morpheus-application/src/test` "with the memory store and the synthetic provider". `morpheus-application/pom.xml`
declares two dependencies, `morpheus-domain` and `junit-jupiter-api`; `morpheus-store-memory` and
`morpheus-provider-synthetic` both depend on `morpheus-application`. A test-scoped dependency the other way is a
reactor cycle, which Maven refuses. The application's own tests already work without them: they implement the ports
by hand (`EmptyContentStore`, `RecordingVersionStore`, `StubPortfolioStore` and others). That is the meaning of the
finding's own title - the module is not testable *with its neighbours* - and it is what the tests of this change use.

**Second correction - the gap cannot be followed through the `perModule*` keys without requalifying a ceiling.**
The finding proposes to "follow the reduction of the gap by raising `perModule*`". The per-module line ratchet is
already the highest thousandth that keeps three times the reference spread under its ceiling (comment of
`CoverageQualityGateTest`, requalification of 09/10/2026). Any further rise is a requalification, on two platforms,
of a ceiling - which the sprint's exit criterion excludes, and which ADR-0104 section 4 makes a lot of its own. And a
gap computed as *one ratio minus the other* is exactly the comparison ADR-0104 section 5 forbids reading as a
threshold. Today the gap is recorded nowhere: each gate writes the totals of its own scale, no evidence names a
module, and nothing fails when a module's own tests stop covering what its consumers still cover.

The figure in the finding's title is perishable and is not restated as a requirement here. Nothing was measured by
this specification: it could not build. The per-module measurement is the first task, and it needs no build - every
run of `ci.yml` uploads the per-module and the aggregate `jacoco.xml` of both platforms in `m21-integrity-<OS>`.

## What Changes

- The gap gets a definition that is not a difference of ratios: for each module of the common population, its
  **borrowed credit** - the lines and the branches the aggregate scale counts as covered and the module's own report
  does not. It is recorded per module, in a third evidence file that names its own scope.
- "The gap decreases" becomes two enforced properties. A class whose every covered line is covered by other modules'
  tests is **named**, in a list that is exactly the measured set and only shrinks. And the total borrowed credit has a
  recorded maximum that is only ever lowered.
- The borrowed credit of `morpheus-application` is reduced by tests written in that module against hand-written port
  doubles, starting with the classes the measurement ranks first.
- No coverage key and no qualified ceiling moves. The two scales keep their keys, their ceilings, their evidence and
  their messages; the third quantity reads neither's keys and neither reads its own.

## Capabilities

### New Capabilities

- `coverage-attribution-gap`: what each module's coverage owes to tests that live in another module - how it is
  measured, where it is recorded, and what keeps it from growing.

### Modified Capabilities

(none)

## Impact

- `morpheus-coverage-report` (a third gate beside `AggregateCoverageGateTest`: the only module where both reports
  exist during a build), `CoverageScaleSeparationTest` (the separation extended to the third quantity),
  `config/m21-quality-ratchets.properties` (one new pair of keys, of the opposite direction: a maximum),
  `.github/workflows/ci.yml` (the new evidence joins the `m21-integrity-<OS>` artifact),
  `morpheus-application/src/test` (the tests).
- **ADR-0104 is amended**: a third quantity, its definition, its qualification rule and what it must never be used
  for. No new ADR.
- Ratchets: the four coverage keys and the four `*_QUALIFIED_*_RATIO` constants are **not** touched. The presence
  ratchets (`testsMinimum`) may be raised by a later lot with the two-platform evidence their header demands; this
  change does not.
- No public contract: `contracts/public-surfaces.tsv` and the OpenAPI files are untouched.
- Documentation: `docs/developer/BUILD_AND_TEST.md` (the third quantity, without a figure),
  `.claude/rules/testing.md` and `.claude/skills/coverage-ratchet/SKILL.md` (the maximum and its direction). The
  destinations of the new keys are enumerated by `RepositoryDocumentationCoherenceTest` and
  `ProductionIntegrityContractTest`, not by this list.
- Depends on sprint 1 (`make-declared-gates-reachable`, archived: the gates run) and meets sprint 11 on two classes
  (see the design).
