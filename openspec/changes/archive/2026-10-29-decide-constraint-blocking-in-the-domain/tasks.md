# Tasks

## 1. Measure before changing

- [x] 1.1 List every application class that reads a constant of `ConstraintBlockingMode`, `ConstraintApplicability` or
      `ConstraintSatisfaction`, and say for each whether it decides an outcome or projects a value. Proof: the list;
      on 2026-10-13 a search finds three files - `ConstraintPolicyEvaluationService`,
      `ChangeOrchestrationStateService`, `ChangeCompletenessService` (line 128, a question about applicability and
      severity). Re-run it on the bytecode: a search does not see a `switch`.
      *2026-10-29 on `6b3f6304`, a source search for the constants and for a `switch` on the three accessors: the same
      three files (6, 8 and 1 constants), all deciding; no `switch` on these enums. After the move the bytecode rule of
      3.3, which sees a `switch`, passes with no exception: no other application class reads them.*
- [x] 1.2 List the reason texts and availability texts produced from these decisions and every surface that returns
      them. Proof: the list, with the test that reads each.
      *Eight evaluation reasons (one per branch), returned by `constraints evaluate` (CLI; MCP and HTTP declare
      `EXPLICITLY_NOT_EXPOSED`) and inside the transition evaluations of `change-orchestration transition-check` /
      `evaluate_change_transition` / `POST .../transition-check` and of `lifecycle apply`. Before this change only the
      blocking reason was asserted (`MorpheusConstraintSemanticsApiContractTest`, `MorpheusConstraintSemanticsCliTest`,
      the evaluator's own test); `ConstraintBlockingDecisionTest` now asserts all eight. The four availability texts
      of `ChangeOrchestrationStateService.constraintAvailability` are produced by unchanged code from the two counts.*
- [x] 1.3 Check which of the eight branches `ConstraintPolicyEvaluationServiceTest` covers, with the reason asserted.
      Proof: the table.
      *Covered: applicability unknown, non-blocking, policy not targeting, satisfaction unknown, satisfied, blocking.
      Not covered: not applicable, blocking mode unknown. Reason asserted: blocking only. A seed, not a proof.*
- [x] 1.4 Record the application's package graph before the move: the dependencies on `application.constraint` and
      whether any lies on a cycle. Proof: by imports on 2026-10-13, three dependencies, none on a cycle.
      *Bytecode, `6b3f6304`: `orchestration`, `quality` and `query` depend on `constraint`, which depends on no
      application package; none is on a cycle.*

## 2. Characterise before moving

- [x] 2.1 A table test over every combination of applicability, mode, satisfaction and targeted or untargeted state:
      state and reason of the evaluation, the orchestration's "known" and "currently blocking". Proof: green on
      `HEAD`, against the application classes as they are.
      *48 rows (24 valid constraints, `VERIFYING` targeted and `IMPLEMENTING` not), produced on `6b3f6304` by a
      throwaway harness calling `ConstraintPolicyEvaluationService` and the two private predicates of
      `ChangeOrchestrationStateService`; the rows are the literal table of `ConstraintBlockingDecisionTest`.*
- [x] 2.2 The three properties of the specification - unknown never blocking, known implies no unknown evaluation,
      blocking implies counted - over the same table. Proof: green on `HEAD`; if one is red, it is a defect found, and
      it is reported before anything moves.
      *Zero violations on `HEAD` over the 48 rows. No defect found.*

## 3. The move

- [x] 3.1 The decision and the two predicates in `com.morpheus.domain.constraint`. Proof: 2.1 and 2.2 run against the
      domain, green, with the store in memory nowhere needed.
      *`Constraint.evaluateFor`, `hasKnownBlocking`, `isViolatedExplicitBlockingPolicy`, written from one
      `policyVerdict`; plus `hasKnownApplicabilityAndSeverity` for the completeness question.
      `ConstraintBlockingDecisionTest` (table and properties, every lifecycle state) green in `morpheus-domain`.*
- [x] 3.2 The application services ask the domain; `ConstraintPolicyEvaluationService` and its package are removed;
      the counting double of `ConstraintEvaluationPageTest` asserts what it was counting for in another way. Proof:
      the application and transport tests green, no expected text edited.
      *The double wraps `Constraint::evaluateFor` and still counts evaluations per page. The evaluator's test moves to
      `morpheus-domain` as `ConstraintEvaluateForTest`, assertions unchanged.*
- [x] 3.3 The gate of `design.md`, decision 5: an ArchUnit rule on constant access, with no named exception. Break it once in each form - an `if` on a constant, a `switch` on the enum - then revert.
      Proof: the named failure for the first; for the second, the failure or the limit written in the test's
      Javadoc.
      *`ConstraintDecisionInTheDomainArchitectureTest`. An `if` on `ConstraintBlockingMode.BLOCK_WHEN_VIOLATED` fails;
      a classic `switch` and a `switch` expression fail too: `javac` reads the constants in the synthetic `$1` class,
      which lives in the application package. The Javadoc states it.*
- [x] 3.4 `./mvnw dependency:analyze`. Proof: 0 warning.

## 4. Verification

- [x] 4.1 `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes` green and unedited. Proof: the diff.
      *Green; only its `because` text cites ADR-0152 instead of ADR-0109.*
- [x] 4.2 The cycle list of `let-application-package-cycles-only-decrease`, if it has landed: unchanged, as 1.4
      predicts; otherwise the graph of 1.4 recomputed. Proof: the comparison, in the pull request.
      *Same pull request: `ApplicationPackageCyclesArchitectureTest` green with the removal of `application.constraint`;
      the list does not change.*
- [x] 4.3 `./mvnw clean verify`; both coverage scales read, no ratchet edited; changed-line coverage gate; raise
      `architectureTestsMinimum` if 3.3 adds executions (`coverage-ratchet`). Proof: green on both platforms through
      the required checks.
      *Green locally on Windows, no ratchet edited; `architectureTestsMinimum` not raised (one execution added; it is
      raised on a two-platform count).*
- [x] 4.4 Pull request description: the finding, the three sites, the property tests, and that the audit's own
      measure does not move for this case.
