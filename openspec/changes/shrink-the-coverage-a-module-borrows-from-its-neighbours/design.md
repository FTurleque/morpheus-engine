# Design

## Context

- ADR-0104: two scales, one population (every reactor module that carries a class under `src/main/java`), differing
  only by which executions may credit a line. The per-module scale credits a line from the tests of the module that
  owns it; the aggregate scale from any execution of the reactor, the cross-module execution of
  `morpheus-architecture-tests` included.
- Because the population is common and derived the same way, the two reports count the **same lines and branches**
  per module. Only the covered counts differ, and the aggregate count is a superset: the module's own execution data
  is part of what `report-aggregate` merges.
- `CoverageScaleSeparationTest#neitherCoverageGateReadsTheOtherScaleKeysReportOrEvidence` forbids each gate the
  other's keys, report and evidence. A measurement of the gap needs both reports, so it can be neither gate.
- The aggregate report exists only after `report-aggregate`, bound to `verify` in `morpheus-coverage-report`, the last
  module of the reactor. The per-module reports of every other module exist by then.
- Both gates already refuse a partial reactor, a foreign population and a stale report, each under a named cause.
- Ceilings are qualified on the lowest reproducible measurement of both platforms; ratchets sit under them with a
  margin sized in lines and in branches against the reference spread (`.claude/rules/testing.md`).
- `morpheus-application` tests depend on `morpheus-domain` and JUnit only. Its neighbours depend on it.
- AUD-ARC-01 (sprint 11) decides whether `morpheus-application` may do file I/O. Two of the seven classes the
  finding names are on that line: `WorkspacePathBoundary` and `ObservedProjectSnapshotPublisher`.

## Goals / Non-Goals

**Goals**: give the gap a definition that survives ADR-0104; record it per module where a reader of the build's
evidence finds it; make its decrease a property the build holds; reduce it for `morpheus-application` with tests the
module can actually compile.

**Non-Goals**: requalifying either ceiling or raising any of the four coverage keys; a threshold per module on the
ratios (the finding's "eventually" - see the open questions); moving the behavioural suites out of
`morpheus-architecture-tests`; changing the population; changing what the M21 validators conclude from.

## Decisions

1. **The gap is a count of borrowed credit, not a difference of ratios.** For a module *m*:
   `borrowed(m) = coveredOnAggregate(m) - coveredByOwnReport(m)`, in lines and in branches, read from the module's
   group in the aggregate report and from the module's own report. The total is the sum over the population. It is
   the same information as the difference of the two ratios multiplied by the common population, but it is expressed
   in the unit the margins are already computed in, it names a module, and it never puts a per-module ratio beside an
   aggregate threshold.
   *Rejected*: publishing `aggregateRatio - perModuleRatio`. It is a single number that names no module, and it reads
   as the comparison ADR-0104 section 5 forbids.

2. **A third gate, in `morpheus-coverage-report`, with its own evidence.** It is the only place where both reports
   exist during a build. It is a class of its own: `AggregateCoverageGateTest` keeps ignoring the per-module reports,
   and `CoverageScaleSeparationTest` is extended so that the third gate reads none of the four coverage keys and
   neither coverage gate reads the third quantity's keys or evidence. The evidence is a third file whose first line
   declares a scope that is neither `per-module` nor `aggregate`; the M21 validators already refuse to conclude on
   any evidence whose first line is not `coverageScope=aggregate`, which is the behaviour wanted here and gets a test.
   *Rejected*: adding the rows to the per-module evidence. That gate runs in `morpheus-architecture-tests`, before
   the aggregate report exists, and would have to read the other scale's report.

3. **It refuses what the two gates refuse, and one thing more.** Same population rule, same three named causes
   (never built, built without a report, stale). In addition it refuses, by name, a module whose two reports do not
   count the same lines or branches, and a module whose own report covers more than the aggregate does. Either means
   the two reports do not describe the same classes; a negative credit clamped to zero would hide it.

4. **"Decreases" is held by a named list first.** A class is *wholly borrowed* when it has executable lines, its
   module's own report covers none of them, and the aggregate report covers at least one. The gate carries the list
   of these classes, exact: an unlisted one fails the build and is named, a listed one that stopped being wholly
   borrowed fails the build until its entry is removed. This is the form `CurrentRequirementReaderArchitectureTest`
   and `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` already use - the list is the measured set and can
   only shrink. Zero against non-zero does not move with run-to-run variation, so the list needs no margin.
   The seven classes of the finding are expected in it; the measurement says, not this specification.

5. **And by a maximum that is only lowered.** The total borrowed credit, in lines and in branches, has a recorded
   maximum in `config/m21-quality-ratchets.properties`, under keys that name the quantity. It mirrors a coverage
   ratchet with the direction reversed: the gate carries the qualified measurement - the **highest** reproducible
   exact-head measurement of both platforms, cited with its SHA and runs - and the maximum sits **above** it by a
   margin sized against the reference spread, in lines and in branches separately. Lowering the maximum down to that
   qualified measurement plus its margin is a decision; qualifying a lower measurement takes both platforms. The gate
   refuses a maximum below the qualified measurement (one platform could not meet it) and a measurement above the
   maximum.
   *Rejected*: one maximum per module from the start - sixteen pairs to qualify on two platforms before the first
   test is written. The evidence names every module and the list of decision 4 names every class; a per-module
   maximum is added when the total is seen to hide a transfer.
   *Rejected*: recording the gap without enforcing it. The audit's sentence is that the per-module scale "names the
   phenomenon and does not absorb it"; a recorded number nobody is failed by is that again.

6. **The tests are written in the module that owns the lines, against doubles of its own ports.** No dependency is
   added to `morpheus-application/pom.xml`; a memory store there is a reactor cycle. This departs from the wording of
   `.claude/rules/testing.md` ("use `morpheus-store-memory` or `morpheus-provider-synthetic` in tests"), which cannot
   apply inside the module those two depend on; the rule file says so after this change. A port double that grows
   into a second memory store is the wrong test: each double implements what the test under it reads.
   The work order is the measurement's - borrowed lines per class, descending - not the name search of the finding,
   which counts records and views beside services.

7. **The two classes on sprint 11's line wait for its decision.** `WorkspacePathBoundary` and
   `ObservedProjectSnapshotPublisher` are tested where AUD-ARC-01
   (`decide-what-technology-the-application-core-may-touch`) leaves them. If they move to an adapter, their
   lines leave `morpheus-application` and their entries leave the list with them.

8. **Nothing is aligned.** The maximum is derived from a measurement of borrowed credit and from nothing else; it is
   never computed from a ratchet, and no ratchet is computed from it. If the per-module measurement rises above its
   ceiling as a consequence of the tests, the ceiling stays where it is until a lot requalifies it by the procedure
   of the `coverage-ratchet` skill.

## Risks / Trade-offs

- **A class may be wholly borrowed on one platform only**, when the one test of its module that touches it no-ops off
  its operating system. The list is measured on both platforms before it is written; a class whose status differs is
  carried with that reason in a group the stale-entry check does not apply to. If the measurement finds none, the
  group does not exist.
- **The maximum turns a habit into a failure.** Covering new application code only through an HTTP or MCP test will
  eventually exceed the margin and fail the build with the module named. That is the property asked for; the
  failure message says which module's credit grew and that the remedy is a test in that module.
- **A new threshold is a new place for a stale figure.** The qualified measurement is cited in the gate like the two
  ceilings; the documentation describes the quantity and points to the properties file, without a figure.
- **Tests against doubles can pass while the real store disagrees.** They add own-module credit; they do not replace
  the contract suites of `morpheus-architecture-tests`, which keep running against both stores.
- The test count rises. `testsMinimum` is not raised here: its header demands exact-head evidence of both platforms.

## Open questions left to the maintainer

- **A non-regression threshold per module on the per-module scale** (the finding's last sentence). Recommendation:
  not in this change. It replaces the single per-module ratchet by one per module, which is a change of what ADR-0104
  section 1 calls a key, and each needs its own two-platform qualification. Decide it once the evidence of this
  change has shown, per module, where the single threshold hides a regression.
- **Whether the maximum is lowered in the same pull request as the tests.** Recommendation: no. The pull request
  that adds the tests produces, on its own CI runs, the two-platform measurement the next lowering cites.
