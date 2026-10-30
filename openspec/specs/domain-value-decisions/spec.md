# domain-value-decisions Specification

## Purpose
Define which decisions on a single domain value the domain model makes itself, and how the application is kept from restating them.

## Requirements

### Requirement: The domain model SHALL decide a lifecycle transition

A lifecycle transition MUST be decided by the domain, from the source lifecycle, the target state, the facts and the
policy, and its outcome MUST be one of a closed family: allowed towards a target lifecycle, or blocked by named
blockers. An application service MUST obtain the decision from the domain and MUST NOT restate it.

#### Scenario: A transition blocked by a violated constraint

- **WHEN** a transition is blocked by a violated blocking constraint
- **THEN** the domain returns the lifecycle blockers followed by the constraint blocker, once

#### Scenario: An application service restates the decision

- **WHEN** a class of the application reads a lifecycle blocker constant, or a class outside the domain builds a
  transition decision
- **THEN** the architecture suite fails and names the class

### Requirement: The domain model SHALL move the resolution state of an external reference

When a resolver does not find the target of an external reference, the reference MUST become stale if it was ever
resolved and unresolved otherwise, with the matching reason, and that choice MUST be made by the domain.

#### Scenario: A target found once and lost

- **WHEN** a reference that was resolved once is not found again
- **THEN** it becomes stale with the stale reason, and a traceability link records it as partially resolved

#### Scenario: A class outside the domain chooses the next state

- **WHEN** a class outside the domain calls the generic transition of an external reference
- **THEN** the architecture suite fails and names the class
