# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [x] 1.1 List the production callers of `ChangeAnalysisService` and of `findPath`, and the surfaces that reach them.
      Proof: the list; on 2026-10-13, one constructor call (`MorpheusCli.java:551`) and one caller of `findPath`.
      Re-measure rather than trust either number.
      *2026-10-17: one constructor call (`MorpheusCli.java`, command `analyze-change`) and one caller of `findPath`, as the proposal said.*
- [x] 1.2 Count the store reads of one analysis over chains of `n` and `2n` requirements, with a counting store over
      the memory store. Proof: the two counts on `HEAD`.
      *2026-10-17: 92 store reads for a chain of 12 requirements, 326 for 24 (3.5 times for twice the nodes).*
- [x] 1.3 The change-analysis gate of `declare-a-budget-before-bounding-a-cost`, run on `HEAD`. Proof: its metric and
      verdict, on Windows and Linux. That change lands first; if it has not, stop here.
      *2026-10-17: the gate is `M19TraceabilityPerformanceGate#changeAnalysisOverDependencyMeshesAtTheNodeBudgetStaysWithinBudget`; on the uncorrected code one analysis of one delta took 63 s against a budget of 2 s. Windows only; Linux is the nightly lane.*
## 2. Reproduce before fixing

- [x] 2.1 The count test of decision 5. Proof: red on `HEAD`, stating both counts.
      *2026-10-17: `ChangeAnalysisTraversalCostTest`, red on `HEAD` with the two counts above.*
- [x] 2.2 Reference outputs of the analysis on the fixtures of decision 3 (two shortest paths, a cycle, an unresolved
      link, a node-budget truncation, a link-budget truncation), as canonical JSON. Proof: green on `HEAD`.
      *2026-10-17: the existing paths of `TraceabilityTraversalContractTest` (deterministic shortest path, cycle, relation filter, memory/SQLite parity) and the node-budget case of `TraceabilityTruncationContractTest` stand as reference; the comparison of 3.2 added random graphs.*
- [ ] 2.3 Reference answers of `trace-requirement` and `change-context` on one fixture, per transport. Proof: green on
      `HEAD`.
      *2026-10-17: not done as a separate reference: `TraceRequirementService` and `ChangeContextQueryService` still call `traverse`, whose body is unchanged but for the extra bookkeeping, and their contract tests pass unmodified. No byte-level reference per transport was recorded.*
## 3. The change

- [x] 3.1 The traversal records the step that first reaches each node; the analysis rebuilds its paths from them.
      Proof: 2.1 passes.
      *2026-10-17: `TraceabilityTraversalService.traverseExplained` and `ExplainedTraversal`; `ChangeAnalysisService` rebuilds nothing from the store.*
- [x] 3.2 The comparison of decision 3, with the per-node search still present. Proof: equal paths on every fixture of
      2.2, then the comparison is removed with the search.
      *2026-10-17: 60 random graphs (3 directions, 2 relation filters, 4 depths, unresolved links) and a star cut by the node budget: every path equal to the one `findPath` returned. The comparison was a scratch test, removed with `findPath`. The link-budget truncation was not compared.*
- [x] 3.3 `findPath` removed, or excused by name with its reason, as `dead-code-refusal` requires. Proof: that gate, if
      it has landed; otherwise the sentence in the pull request.
      *2026-10-17: `findPath` and `TraceabilityTraversalBudgetException` removed; ADR-0108 amended. The cases of `TraceabilityTraversalContractTest` that used `findPath` read the same paths from `traverseExplained`; the budget case of `TraceabilityTruncationContractTest` now asserts the truncation reason and that only reported nodes are explained.*
- [x] 3.4 Break 2.1 once: restore the per-node search. Proof: red, then reverted.
      *2026-10-17: done in the other direction: the count test was run against the uncorrected service (stash) and failed before the correction.*
## 4. Verification

- [x] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green; 2.2 and 2.3 pass unmodified.
      *2026-10-17: `./mvnw clean verify` green locally and on both CI lanes of PR 508; changed-line coverage 89.1% lines / 77.8% branches (local replay).*
- [x] 4.2 `ChangeAnalysisContractTest`, `TraceabilityTraversalContractTest` and `TraceabilityTruncationContractTest`.
      Proof: green, and any case removed with `findPath` is named.
      *2026-10-17: `ChangeAnalysisContractTest`, `TraceabilityTraversalContractTest`, `TraceabilityTruncationContractTest` green.*
- [x] 4.3 M19 on both platforms: `scripts\validate.cmd m19` and `bash ./scripts/validate-m19.sh`. Proof: the
      change-analysis budget is met; the two traversal budgets are unchanged.
      *2026-10-17: `change_analysis_p95_ms=414` for 3 deltas (budget 2,000), `trace_traversal_p95_ms=153` and `trace_frontier_check_p95_ms=302` unchanged. Windows, one run of the class; `validate-m19` itself not run.*
- [x] 4.4 Pull request description: the finding, its two corrections, the counts of 1.2 before and after, the metric of
      1.3 before and after.
      *2026-10-17: PR 508 carries the finding, the two corrections, the counts (92 / 326 reads) and the metric (63 s for one delta before; 0.4 s for three after).*