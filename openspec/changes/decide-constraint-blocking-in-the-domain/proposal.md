# Proposal

## Why

AUD-ARC-13 (sprint 11, low severity) reports a domain made of 59 records and 25 enums with no polymorphic type, so
that every business rule is a method of an application service. It asks for no global rework: case by case, when a
service decides from a single domain type, move the decision onto the type. It names one case, constraint blocking.
Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds, and its example is larger than described**:

- 59 records, 25 enums, no interface and no class in `morpheus-domain/src/main/java`; 84 files, 2 624 lines. The
  application has 343 files and 24 861 lines today (the audit: 341 and 24 819), 55 `*Service.java`.
- The audit says only `ChangeOrchestrationStateService` decides a blocking from `ConstraintBlockingPolicy`. **Three
  application classes compare a constraint's applicability, blocking mode or satisfaction to a constant**:
  - `application.constraint.ConstraintPolicyEvaluationService` (lines 22-47): the full ladder, eight branches, for a
    constraint and a target state. It holds no port and no state; it is a function of one domain value, and it
    returns a domain record, `ConstraintEvaluation`.
  - `ChangeOrchestrationStateService` (lines 152-173): `blockingKnowledgeKnown` and
    `currentlyBlockingByExplicitPolicy`, a second writing of the first five branches, without the target state.
  - `quality.ChangeCompletenessService` (line 128): a third question - are the applicability and the severity of
    every constraint known - and, for the evaluation itself, a call to the first class.
- Nothing ties the first two writings together. Today they agree: whenever the orchestration says a constraint's blocking is
  explicit, its evaluation is never `UNKNOWN`; whenever an evaluation is `BLOCKING`, the orchestration counts the
  constraint. That is two proofs by reading, and the tri-state rule of ADR-0119 and ADR-0093 - `UNKNOWN` is never
  `BLOCKING` - rests on both.

The audit asks to measure the result by the dependencies removed from the application's cyclic component, not by
lines moved. For its own example that measure reads **zero**: `application.constraint` depends on the domain only and
lies on no cycle. Three dependencies and one package leave the application graph; the list of
`let-application-package-cycles-only-decrease` does not change. The reason to do it is the duplicated decision.

## What Changes

- Whether a constraint blocks a lifecycle state, whether its blocking is known, and why, are answered by the domain
  model, once. The application services ask and orchestrate.
- The outcomes and the reason texts a caller reads are unchanged.
- No general rule is added about where a decision must live: the audit asks for case-by-case work, and this is one
  case.

## Capabilities

### New Capabilities

- `constraint-blocking-decision`: how MORPHEUS decides that a constraint blocks a lifecycle state, and where.

### Modified Capabilities

(none)

## Impact

- `morpheus-domain` (`com.morpheus.domain.constraint`), `morpheus-application` (`constraint`, `orchestration`,
  `quality`, `query`: three services construct `ConstraintPolicyEvaluationService`).
- Reason texts reach the HTTP, MCP and CLI outputs: they are characterised before the move and must not change. No
  manifest line, no OpenAPI change.
- No ADR: ADR-0119 and ADR-0093 already state the semantics; this moves where they are implemented.
- `domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes` guards the move: `domain.constraint` already depends on
  `domain.change.lifecycle`.
- Coverage: lines move from one module to another inside one population; the per-module and the aggregate scales are
  read after the move, neither ratchet is touched (`coverage-ratchet`).
- Ordered by the audit after AUD-ARC-03; independent of it in practice, and of the decision of
  `decide-what-technology-the-application-core-may-touch`.
