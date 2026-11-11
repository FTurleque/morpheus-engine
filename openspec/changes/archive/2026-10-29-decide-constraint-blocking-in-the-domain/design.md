# Design

## Context

- `Constraint` carries `applicability`, `satisfaction`, `blockingPolicy` (mode and target states); its compact
  constructor refuses a not-applicable constraint that declares a blocking policy. It has two predicates today,
  `hasExplicitSemantics()` and, on its policy, `targets(state)`. `ConstraintEvaluation` refuses a `BLOCKING` state without a `VIOLATED` satisfaction.
- The ladder of `ConstraintPolicyEvaluationService.evaluate`, in order: not applicable; applicability unknown; mode
  unknown; non-blocking; not targeting the state; satisfaction unknown; satisfied; else blocking. Each branch has a
  reason text that leaves the product.
- The orchestration asks a different question - is the blocking semantics explicit, and is the constraint a violated
  explicit blocking policy - with no target state. It is not a subset of the ladder: for a state the policy does not
  target, the ladder answers `NON_BLOCKING` even when the satisfaction is unknown, where the orchestration says
  "unknown".
- Three services build the evaluation service themselves (`ChangeTransitionEvaluationService`,
  `ChangeCompletenessService`, `ConstraintEvaluationQueryService`); one test replaces it with a counting double.

## Goals / Non-Goals

**Goals**: one writing of the decision, in the type that holds its inputs; the two questions answered from it; the
agreement between them stated as a property and tested.

**Non-Goals**: a domain with interfaces or strategies; moving any other decision; changing an outcome or a reason
text; resolving the difference of question noted above - it is kept, and named.

## Decisions

1. **The decision is a method of the domain's constraint, returning the existing `ConstraintEvaluation`.** The inputs
   are all fields of `Constraint`; the output type is already in the domain. *Rejected*: a domain service class - the
   domain has none, and the audit's point is that the type can carry it. *Rejected*: a sealed hierarchy of blocking
   policies - a redesign of persisted and provider-read data for a three-valued mode.

2. **The orchestration's two questions become domain predicates written from the same branches.** "Is the blocking
   known" and "is this a violated explicit blocking policy" sit beside the evaluation, so that a fourth mode or
   applicability has one file to change. The difference of question is documented on the predicate.

3. **Characterise first.** A table test over every combination of applicability, mode, satisfaction and a targeted or
   untargeted state fixes the state and the reason text on `HEAD`; the same table then runs against the domain. The
   existing `ConstraintPolicyEvaluationServiceTest` is the seed, not the proof - the lot checks that it covers the
   eight branches before trusting it.

4. **The agreement is a tested property.** For every combination: blocking known implies no `UNKNOWN` evaluation for
   any state; a `BLOCKING` evaluation for some state implies the constraint counts as currently blocking; an unknown
   applicability, mode or - for a targeted state - satisfaction never yields `BLOCKING`. Exhaustive, not sampled: the
   space is small.

5. **An ArchUnit rule refuses an application class that reads a constant of `ConstraintBlockingMode`,
   `ConstraintApplicability` or `ConstraintSatisfaction`** - a field access, visible in the bytecode; projections that
   only print `mode().name()` are not caught. `ChangeCompletenessService` asks a third question from two such
   constants (are applicability and severity known): it becomes a predicate of `Constraint`, beside the existing
   `hasExplicitSemantics()`, rather than a named exception.
   Intent: "no application class decides from a constraint's fields" - a dependency on constants, so ArchUnit
   (`enforcement-choice`), to be broken once.

6. **`ConstraintPolicyEvaluationService` is removed, not kept as a delegate.** A class that forwards one call is the
   compatibility shim the repository refuses. The test double that counted its calls counts something observable
   instead.

## Risks / Trade-offs

- An enum `switch` compiles to a synthetic class that reads ordinals, not constants; a restated decision written as a
  `switch` would pass the rule of decision 5. The break-once task tries that form and the rule is adjusted or the
  limit is written down.
- Lines leave `morpheus-application` for `morpheus-domain`. Both are in the coverage population; the ratio of each
  module moves slightly. No ratchet is raised or lowered by this change.

## Open Questions

- **Should the orchestration's "currently blocking" depend on the change's next states?** Today it counts a violated
  policy whatever states it targets. Recommendation: leave it - it is what the response's own text says it counts -
  and record it; changing it is a change of a public answer, not of where a decision lives.
- **Other cases?** Recommendation: none in this change. The next one is chosen when a change touches a service that
  decides from one domain type, and is measured on the cycle list.
