# Tasks

## 1. Measure before changing

- [ ] 1.1 List every application class that reads a constant of `ConstraintBlockingMode`, `ConstraintApplicability` or
      `ConstraintSatisfaction`, and say for each whether it decides an outcome or projects a value. Proof: the list;
      on 2026-10-13 a search finds three files - `ConstraintPolicyEvaluationService`,
      `ChangeOrchestrationStateService`, `ChangeCompletenessService` (line 128, a question about applicability and
      severity). Re-run it on the bytecode: a search does not see a `switch`.
- [ ] 1.2 List the reason texts and availability texts produced from these decisions and every surface that returns
      them. Proof: the list, with the test that reads each.
- [ ] 1.3 Check which of the eight branches `ConstraintPolicyEvaluationServiceTest` covers, with the reason asserted.
      Proof: the table.
- [ ] 1.4 Record the application's package graph before the move: the dependencies on `application.constraint` and
      whether any lies on a cycle. Proof: by imports on 2026-10-13, three dependencies, none on a cycle.

## 2. Characterise before moving

- [ ] 2.1 A table test over every combination of applicability, mode, satisfaction and targeted or untargeted state:
      state and reason of the evaluation, the orchestration's "known" and "currently blocking". Proof: green on
      `HEAD`, against the application classes as they are.
- [ ] 2.2 The three properties of the specification - unknown never blocking, known implies no unknown evaluation,
      blocking implies counted - over the same table. Proof: green on `HEAD`; if one is red, it is a defect found, and
      it is reported before anything moves.

## 3. The move

- [ ] 3.1 The decision and the two predicates in `com.morpheus.domain.constraint`. Proof: 2.1 and 2.2 run against the
      domain, green, with the store in memory nowhere needed.
- [ ] 3.2 The application services ask the domain; `ConstraintPolicyEvaluationService` and its package are removed;
      the counting double of `ConstraintEvaluationPageTest` asserts what it was counting for in another way. Proof:
      the application and transport tests green, no expected text edited.
- [ ] 3.3 The gate of `design.md`, decision 5: an ArchUnit rule on constant access, with no named exception. Break it once in each form - an `if` on a constant, a `switch` on the enum - then revert.
      Proof: the named failure for the first; for the second, the failure or the limit written in the test's
      Javadoc.
- [ ] 3.4 `./mvnw dependency:analyze`. Proof: 0 warning.

## 4. Verification

- [ ] 4.1 `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes` green and unedited. Proof: the diff.
- [ ] 4.2 The cycle list of `let-application-package-cycles-only-decrease`, if it has landed: unchanged, as 1.4
      predicts; otherwise the graph of 1.4 recomputed. Proof: the comparison, in the pull request.
- [ ] 4.3 `./mvnw clean verify`; both coverage scales read, no ratchet edited; changed-line coverage gate; raise
      `architectureTestsMinimum` if 3.3 adds executions (`coverage-ratchet`). Proof: green on both platforms through
      the required checks.
- [ ] 4.4 Pull request description: the finding, the three sites, the property tests, and that the audit's own
      measure does not move for this case.
