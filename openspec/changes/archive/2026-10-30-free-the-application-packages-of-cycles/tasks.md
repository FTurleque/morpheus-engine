# Tasks

## 1. Cycles of the application

- [x] 1.1 Measure the slice graph on the bytecode before changing: 28 slices, 21 dependencies on a cycle, two
      components (`11bb1ccf`, 2026-10-30).
- [x] 1.2 Move the four groups of types to the lowest package that uses them; measure again: 28 slices, 0 dependency
      on a cycle.
- [x] 1.3 Replace the named list by `beFreeOfCycles()`, with a test that proves the rule on two cyclic test packages
      and a test that the rule sees every first-level package of the sources.

## 2. Decisions on a domain value

- [x] 2.1 Move the lifecycle state machine into `ChangeLifecycleTransitionRequest.decide()` and seal its outcome.
- [x] 2.2 Move the resolution transitions of an external reference into `ExternalReference`.
- [x] 2.3 Hold both by rules without exception; break each rule once and see it fail.

## 3. Records

- [x] 3.1 ADR-0156 replaces ADR-0152; index, arc42 section 9, repository rules and references updated.
