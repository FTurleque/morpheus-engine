# Tasks

Started on 2026-10-13 with the declaration, which must precede everything else. The gates are not written; each task
says where it stands.

## 1. Measure before changing

- [ ] 1.1 Run `scripts\validate.cmd m19` (no `-Version`) and its Linux twin on `HEAD`, and keep the `M19_METRIC` lines.
      Proof: the two summaries; they are the baseline no existing row may leave.
      *2026-10-13: **not run.** The baseline of the existing rows was not taken before the declaration; the declaration changes no code, so the baseline can still be taken before the first gate.*
- [ ] 1.2 List, per gate, which tables of the content projection the measured database fills. Proof: the table; on
      2026-10-13 no file of the `m19` package builds a change, a constraint or an acceptance criterion, although the
      frozen profile declares 2,000 changes and 4,000 criteria. Re-measure rather than trust this count.
      *2026-10-13: confirmed for the publication gate - its `largeContent()` passes empty lists for changes, deltas, constraints, decisions, tasks and criteria. The other gates were not re-read.*
- [ ] 1.3 Read the process timeout and the heap setting of both validators, and how much of each the five gates use.
      Proof: the figures, from the summaries of 1.1.

## 2. Declare before measuring

- [x] 2.1 The amendment of ADR-0085 (decision 5). Proof: the ADR; `AdrIndexCoherenceTest` green.
      *2026-10-13: ADR-0085 amended - how a budget is added after the freeze.*
- [x] 2.2 The three rows in `M19_PERFORMANCE_BUDGETS.md`, dated, each with its fixture and its reason, in a commit of
      their own. Proof: the commit precedes every gate and every correction.
      *2026-10-13: three rows in section 4 bis of the budget document, in a commit of their own, before any gate and any correction. **The values are the specification's recommendation**: the open question left them to the maintainer, who launched the sprint without setting them; the document says so. The journal row carries the condition under which it will be withdrawn.*

## 3. The gates

- [ ] 3.1 The content-read measurement, on a snapshot of its own (decisions 2 and 4). Proof: the guard of the empty
      child table fails when one list of the fixture is emptied, then is restored.
      *2026-10-13: `readingTheContentOfALargeSnapshotStaysWithinItsDeclaredBudget`, in the publication gate, on a snapshot published into its own file. Four guards refuse a fixture that leaves a child list empty. **Not done**: no guard was broken by emptying a list.*
- [x] 3.2 The change-analysis measurement. Proof: the guard of the node budget fails when the fixture graph is
      shortened, then is restored.
      *2026-10-17: method added to `M19TraceabilityPerformanceGate`; the guard asserts six truncated traversals and 5,994 impacts. First measurement on the uncorrected code: 63 s for one delta (budget 2 s).*
- [x] 3.3 The journal-page measurement, or the written reason for not having one (second open question).
      *2026-10-17: kept: the correction adds a migration and an index, so the plan of the query is trusted. Gate added to `M19QueryPerformanceGate`; no validator edit (existing class).*
- [ ] 3.4 Run the three on the uncorrected code, on Windows and on Linux. Proof: per platform, the metric and the
      verdict. A miss is expected for the content read and the analysis if the audit's reasoning holds; a pass is
      recorded as a pass.
      *2026-10-13: the content read only, on Windows only: 516 ms on the uncorrected code, a pass, recorded in the budget document. The analysis and the journal have no gate yet.*
- [ ] 3.5 If a gate class was added: both validators name it, and `GateReachabilityArchitectureTest` is broken once by
      removing the name from one validator. Proof: the failure names the class and the platform.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`. Proof: green; the new measurements are not run by it.
- [ ] 4.2 `validate-m19` on both platforms. Proof: the existing metric names and fixture counts are those of 1.1; the
      heap setting is unchanged.
- [ ] 4.3 `docs/architecture/arc42/10-exigences-qualite.md` section 10.3 and `.claude/rules/testing.md` name the
      budgets that exist. Proof: `RepositoryDocumentationCoherenceTest` green.
- [ ] 4.4 Pull request description: the three rows, the measurements of 3.4, and what a nightly proof does not cover.
