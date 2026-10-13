# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List the classes of `src/main` and `src/test` that mention `LocalOperationalRuntime`. Proof: the list; on
      2026-10-13, ten and one.
- [ ] 1.2 Trace how the contention metrics are reached from a store's construction through the connection scope. Proof:
      the call path; it decides where the instance is carried.
- [ ] 1.3 List what else shares state between tests (system properties set by a test, fixed ports, files outside a
      temporary directory). Proof: the list, for the ADR.

## 2. Reproduce before fixing

- [ ] 2.1 The two-stores test of the first requirement. Proof: red on `HEAD` - both observe the same counters.
- [ ] 2.2 The two textual rules (no test reads the default; the named list of static users). Proof: red on `HEAD` for
      the first, naming the one test.

## 3. The change

- [ ] 3.1 `OperationalRuntime`, built by `MorpheusMain`; the store path. Proof: 2.1 passes; the persistence parity tests
      pass unmodified apart from construction; `AdapterCompositionRootArchitectureTest` green.
- [ ] 3.2 `SqliteContentionObservabilityTest` on its own instance, absolute values. Proof: 2.2 passes.
- [ ] 3.3 The other users, or their follow-ups named with the list of 1.1 as the starting point of the shrinking rule.
- [ ] 3.4 The ADR.
- [ ] 3.5 Break each rule once, then revert.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 M19: the store constructors are on its paths. `scripts\validate.cmd m19`. Proof: budgets unchanged.
- [ ] 4.3 Run the store module's tests with JUnit parallel execution enabled locally, as an observation only. Proof:
      what fails, recorded in the ADR - not a gate.
- [ ] 4.4 Pull request description: the finding, the lists of 1.1 and 1.3.
