# Proposal

## Why

Sprint 6 of the audit of 2026-10-09 (AUD-PRF-01, AUD-PRF-02, AUD-PRF-04, AUD-PRF-06) ends on one criterion: no public
path loads an unbounded volume, **and each correction is measured against its M19 budget**. Re-read on `develop` at
`8b26d3d7` on 2026-10-13, three of the four corrections have no budget to be measured against:

- The five gates of `morpheus-architecture-tests/.../m19/` are run again, nightly and on both platforms, since the
  archived change `2026-10-10-make-declared-gates-reachable` (AUD-TST-01). That prerequisite of the sprint is closed.
- **Reading a snapshot's content has no budget.** `findSnapshotContent` appears in no gate: the only M19 file that
  touches the content store is `M19FullPublishPerformanceGate`, which writes it and never reads it back.
- **The frozen fixture profile is not what the gates build.** `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`
  section 2 declares 2,000 changes and 4,000 acceptance criteria. No file of the `m19` package builds a
  `ChangeProposal`, a `Constraint` or an `AcceptanceCriterion`: the publish gate passes empty lists for all of them
  (`M19FullPublishPerformanceGate.java:206-211`). The seven child tables AUD-PRF-01 is about are therefore empty in
  every database a gate has ever measured, except the scenario preconditions.
- **Analysing a change has no budget.** No M19 file names `ChangeAnalysisService` or `findPath`, as AUD-PRF-02 says.
- **Reading a journal has no budget** (AUD-PRF-04).
- Writing the links has one: the publication budget of `M19FullPublishPerformanceGate.java:40`, on 25,000 links
  (AUD-PRF-06).

ADR-0085 requires a budget to be declared before the code is optimised, not read off the optimised code. If each
correction of the sprint wrote its own budget after its own fix, the sprint would do the opposite three times. The
budgets are therefore one decision, taken first.

They also have a limit that has to be said: the M19 gates run at night, not in `clean verify`. A correction whose only
proof is a nightly timing can regress in a pull request and be seen a day later. Each correction of the sprint carries
its own deterministic proof in the ordinary suite (a count that does not grow with the data); this change carries the
timings.

## What Changes

- Three budgets are declared in the frozen budget document, dated, before any of the three corrections: the read of a
  snapshot's content, the analysis of a change, the read of a journal page.
- Each is measured on the uncorrected code, and the result is recorded whichever way it falls.
- The gates build the changes, constraints and acceptance criteria the frozen profile declares, in a database of their
  own, so that no existing measurement moves.
- ADR-0085 is amended: how a budget is added after the freeze.

## Capabilities

### New Capabilities

- `performance-budget-coverage`: a cost that a correction claims to bound has a budget declared before the correction,
  on a fixture that holds the data the cost grows with.

### Modified Capabilities

(none)

## Impact

- `morpheus-architecture-tests/.../m19/` (tests only), `scripts/validate-m19.ps1` and `.sh` only if a gate class is
  added, `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md` (new dated rows; no existing row changes),
  `docs/architecture/arc42/10-exigences-qualite.md` section 10.3, `.claude/rules/testing.md` (the M19 paragraph names
  the gates).
- ADR-0085 amended. No product code, no contract, no ratchet lowered; `architectureTestsMinimum` is unaffected by
  classes Surefire does not select and is re-read from `config/m21-quality-ratchets.properties` if a `*Test` is added.
- First change of sprint 6. The four corrections
  (`read-a-snapshot-content-in-a-fixed-number-of-statements`, `explain-every-dependency-from-one-traversal`,
  `write-traceability-links-as-one-batch`, `read-an-append-only-journal-one-page-at-a-time`) cite the budget this
  change declares.
