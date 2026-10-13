# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List the production callers of `ChangeAnalysisService` and of `findPath`, and the surfaces that reach them.
      Proof: the list; on 2026-10-13, one constructor call (`MorpheusCli.java:551`) and one caller of `findPath`.
      Re-measure rather than trust either number.
- [ ] 1.2 Count the store reads of one analysis over chains of `n` and `2n` requirements, with a counting store over
      the memory store. Proof: the two counts on `HEAD`.
- [ ] 1.3 The change-analysis gate of `declare-a-budget-before-bounding-a-cost`, run on `HEAD`. Proof: its metric and
      verdict, on Windows and Linux. That change lands first; if it has not, stop here.

## 2. Reproduce before fixing

- [ ] 2.1 The count test of decision 5. Proof: red on `HEAD`, stating both counts.
- [ ] 2.2 Reference outputs of the analysis on the fixtures of decision 3 (two shortest paths, a cycle, an unresolved
      link, a node-budget truncation, a link-budget truncation), as canonical JSON. Proof: green on `HEAD`.
- [ ] 2.3 Reference answers of `trace-requirement` and `change-context` on one fixture, per transport. Proof: green on
      `HEAD`.

## 3. The change

- [ ] 3.1 The traversal records the step that first reaches each node; the analysis rebuilds its paths from them.
      Proof: 2.1 passes.
- [ ] 3.2 The comparison of decision 3, with the per-node search still present. Proof: equal paths on every fixture of
      2.2, then the comparison is removed with the search.
- [ ] 3.3 `findPath` removed, or excused by name with its reason, as `dead-code-refusal` requires. Proof: that gate, if
      it has landed; otherwise the sentence in the pull request.
- [ ] 3.4 Break 2.1 once: restore the per-node search. Proof: red, then reverted.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green; 2.2 and 2.3 pass unmodified.
- [ ] 4.2 `ChangeAnalysisContractTest`, `TraceabilityTraversalContractTest` and `TraceabilityTruncationContractTest`.
      Proof: green, and any case removed with `findPath` is named.
- [ ] 4.3 M19 on both platforms: `scripts\validate.cmd m19` and `bash ./scripts/validate-m19.sh`. Proof: the
      change-analysis budget is met; the two traversal budgets are unchanged.
- [ ] 4.4 Pull request description: the finding, its two corrections, the counts of 1.2 before and after, the metric of
      1.3 before and after.
