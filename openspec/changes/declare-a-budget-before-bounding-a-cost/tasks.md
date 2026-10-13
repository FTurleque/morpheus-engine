# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Run `scripts\validate.cmd m19` (no `-Version`) and its Linux twin on `HEAD`, and keep the `M19_METRIC` lines.
      Proof: the two summaries; they are the baseline no existing row may leave.
- [ ] 1.2 List, per gate, which tables of the content projection the measured database fills. Proof: the table; on
      2026-10-13 no file of the `m19` package builds a change, a constraint or an acceptance criterion, although the
      frozen profile declares 2,000 changes and 4,000 criteria. Re-measure rather than trust this count.
- [ ] 1.3 Read the process timeout and the heap setting of both validators, and how much of each the five gates use.
      Proof: the figures, from the summaries of 1.1.

## 2. Declare before measuring

- [ ] 2.1 The amendment of ADR-0085 (decision 5). Proof: the ADR; `AdrIndexCoherenceTest` green.
- [ ] 2.2 The three rows in `M19_PERFORMANCE_BUDGETS.md`, dated, each with its fixture and its reason, in a commit of
      their own. Proof: the commit precedes every gate and every correction.

## 3. The gates

- [ ] 3.1 The content-read measurement, on a snapshot of its own (decisions 2 and 4). Proof: the guard of the empty
      child table fails when one list of the fixture is emptied, then is restored.
- [ ] 3.2 The change-analysis measurement. Proof: the guard of the node budget fails when the fixture graph is
      shortened, then is restored.
- [ ] 3.3 The journal-page measurement, or the written reason for not having one (second open question).
- [ ] 3.4 Run the three on the uncorrected code, on Windows and on Linux. Proof: per platform, the metric and the
      verdict. A miss is expected for the content read and the analysis if the audit's reasoning holds; a pass is
      recorded as a pass.
- [ ] 3.5 If a gate class was added: both validators name it, and `GateReachabilityArchitectureTest` is broken once by
      removing the name from one validator. Proof: the failure names the class and the platform.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`. Proof: green; the new measurements are not run by it.
- [ ] 4.2 `validate-m19` on both platforms. Proof: the existing metric names and fixture counts are those of 1.1; the
      heap setting is unchanged.
- [ ] 4.3 `docs/architecture/arc42/10-exigences-qualite.md` section 10.3 and `.claude/rules/testing.md` name the
      budgets that exist. Proof: `RepositoryDocumentationCoherenceTest` green.
- [ ] 4.4 Pull request description: the three rows, the measurements of 3.4, and what a nightly proof does not cover.
