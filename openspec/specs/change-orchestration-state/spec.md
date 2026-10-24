# change-orchestration-state Specification

## Purpose
Define the aggregated orchestration state of a change that MORPHEUS exposes to JARVIS and other orchestration
consumers: a compact, read-only observation of the published snapshot that keeps an unknown fact distinct from a
real absence and never invents what the snapshot does not show.

## Requirements

### Requirement: The orchestration state is a read-only observation of the active snapshot

The orchestration state of a change SHALL be built from the `ACTIVE` snapshot of its project only, and MUST NOT be
persisted as business state: it SHALL always report `persisted=false`, and building it MUST NOT mutate the snapshot.

#### Scenario: No active snapshot

- **WHEN** the orchestration state of a change is requested for a project that has no `ACTIVE` snapshot
- **THEN** no orchestration state is returned

#### Scenario: A change absent from the active snapshot

- **WHEN** the orchestration state is requested for a change that the `ACTIVE` snapshot does not contain
- **THEN** the request is refused as a store failure that names the change

#### Scenario: A persisted orchestration state is refused

- **WHEN** an orchestration state is constructed with `persisted=true`
- **THEN** the construction is refused, because the state is a live read-only projection

### Requirement: The orchestration state exposes a fixed set of fields

The orchestration state SHALL expose, for one change, exactly these fields: `snapshot`, `change`, `lifecycle`,
`observableFacts`, `missingArtifacts`, `unavailableFacts`, `acceptanceCriteria`, `applicableConstraints`,
`blockingConstraints`, `unresolvedLinks`, `qualityFindings`, `nextAllowedTransitions`, `transitionEvaluations` and
`persisted`. `observableFacts` SHALL carry the nine lifecycle facts of the change as `TRUE`, `FALSE` or `UNAVAILABLE`.

#### Scenario: Every lifecycle fact is reported

- **WHEN** the orchestration state of a change is read
- **THEN** `observableFacts` reports `requirementsIdentified`, `criticalConstraintsKnown`,
  `acceptanceCriteriaDefined`, `designRequired`, `designDecisionsAvailable`, `planPresent`, `knownBlocker`,
  `blockingAcceptanceCriterionFailed` and `blockingAcceptanceCriterionUnverified`, each with its tri-state value

### Requirement: A missing artifact is a fact that is FALSE, never one that is unavailable

`missingArtifacts` SHALL name an artifact only when its absence is established by a fact that is `FALSE`:
`requirements`, `criticalConstraints`, `acceptanceCriteria` and `implementationPlan` for their facts, and
`designDecisions` only when the design is required (`TRUE`) and no design decision is available (`FALSE`). A fact
that is `UNAVAILABLE` SHALL appear in `unavailableFacts`, sorted and without duplicates, and MUST NOT appear in
`missingArtifacts`.

#### Scenario: An established absence

- **WHEN** no requirement is identified for the change and the fact `requirementsIdentified` is `FALSE`
- **THEN** `missingArtifacts` contains `requirements`

#### Scenario: An unknown fact

- **WHEN** the fact `planPresent` is `UNAVAILABLE` because no task is attached to the change
- **THEN** `planPresent` appears in `unavailableFacts` and `implementationPlan` does not appear in `missingArtifacts`

#### Scenario: Design decisions when the design requirement is unknown

- **WHEN** `designRequired` is `UNAVAILABLE` and no design decision is attached to the change
- **THEN** `missingArtifacts` does not contain `designDecisions`

### Requirement: Acceptance criteria are counted from explicit criteria only

`acceptanceCriteria` SHALL report the status `AVAILABLE` and, as its observed count, the number of explicit
acceptance criteria attached to the change in the published snapshot. A `Scenario` MUST NOT be counted as an
acceptance criterion, and the verification state of a criterion MUST NOT be read as a blocking semantics.

#### Scenario: A change with scenarios but no explicit criterion

- **WHEN** the change has OpenSpec scenarios but no explicit acceptance criterion in the published snapshot
- **THEN** `acceptanceCriteria` reports an observed count of `0`

### Requirement: Constraints are qualified by their explicit blocking semantics

`applicableConstraints` SHALL list the constraints of the change, ordered by identity, with their applicability,
severity, satisfaction, blocking mode and targets, supporting evidence and source evidence. `blockingConstraints`
SHALL report one of three statuses:

| Status | Condition | Observed count |
|---|---|---|
| `AVAILABLE` | no constraint, or an explicit blocking semantics for every constraint | applicable, `BLOCK_WHEN_VIOLATED` and `VIOLATED` constraints |
| `PARTIALLY_AVAILABLE` | an explicit blocking semantics for some constraints only | the same count, over the known constraints |
| `UNKNOWN` | no explicit blocking semantics | `0` |

An `UNKNOWN` blocking semantics MUST NOT be treated as blocking. When the status is not `AVAILABLE`,
`blockingConstraints` SHALL appear in `unavailableFacts`.

#### Scenario: No constraint

- **WHEN** the change has no constraint
- **THEN** `blockingConstraints` is `AVAILABLE` with an observed count of `0`

#### Scenario: Constraints without semantics

- **WHEN** every constraint of the change has an unknown applicability or blocking semantics
- **THEN** `blockingConstraints` is `UNKNOWN` with an observed count of `0`, and `blockingConstraints` appears in
  `unavailableFacts`

#### Scenario: A violated explicit blocking constraint

- **WHEN** every constraint has an explicit semantics and exactly one is applicable, `BLOCK_WHEN_VIOLATED` and
  `VIOLATED`
- **THEN** `blockingConstraints` is `AVAILABLE` with an observed count of `1`

### Requirement: Unresolved links are listed without a live resolution

`unresolvedLinks` SHALL list the outgoing traceability links of the change whose resolution is not `RESOLVED`, and
the external references of the snapshot owned by the change whose state is not `RESOLVED`, ordered by kind then
identity. Listing them MUST NOT trigger a live resolution.

#### Scenario: An unresolved external reference

- **WHEN** an external reference owned by the change is `UNRESOLVED` in the published snapshot
- **THEN** it appears in `unresolvedLinks` with the kind `EXTERNAL_REFERENCE`, and no resolver is called

### Requirement: Transitions are evaluated from the supplied lifecycle state

The transitions SHALL be evaluated from the lifecycle state supplied by the caller, toward every other lifecycle
state, under the forward-only policy, and the lifecycle state MUST NOT be inferred. `nextAllowedTransitions` SHALL
retain only the target states whose evaluation is `ALLOWED`.

#### Scenario: No lifecycle state supplied

- **WHEN** the caller supplies no lifecycle state
- **THEN** the lifecycle is reported as `UNAVAILABLE`, and `transitionEvaluations` and `nextAllowedTransitions` are
  empty

#### Scenario: An unknown evaluation is not an allowed transition

- **WHEN** the evaluation of a transition is `UNKNOWN` because a required fact is unavailable
- **THEN** the target state appears in `transitionEvaluations` and not in `nextAllowedTransitions`
