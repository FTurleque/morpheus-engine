# Tasks

Started on 2026-10-13 with the rule that holds the direction. The injection itself is not done; each task says where
it stands.

## 1. Measure before changing

- [x] 1.1 List the classes of `src/main` and `src/test` that mention `LocalOperationalRuntime`. Proof: the list; on
      2026-10-13, ten and one.
      *2026-10-13: measured again, and the proposal's count is out of date. **Twenty-five** main classes mention it besides the runtime itself, and **two** test sources. Sprint 4 added most of them: every site that now records a suppressed failure, the SLF4J binding, the launcher, the access journal. The lists are in `StaticOperationalRuntimeUsersArchitectureTest`.*
- [x] 1.2 Trace how the contention metrics are reached from a store's construction through the connection scope. Proof:
      the call path; it decides where the instance is carried.
      *2026-10-13: `SqliteTransactionRunner.run` and `runVoid` are static and take a raw `Connection`; they are called from ten store classes. Connections come from `SqliteDatabaseSecurity.openPhysical`, reached through a thread-local `SqliteConnectionScope`. The stores are constructed at fifty-six sites, most of them in the composition roots named by ADR-0109. Nothing on that path is an instance that could carry the metrics: an injection there adds a parameter to ten constructors and to those fifty-six sites.*
- [ ] 1.3 List what else shares state between tests (system properties set by a test, fixed ports, files outside a
      temporary directory). Proof: the list, for the ADR.

## 2. Reproduce before fixing

- [ ] 2.1 The two-stores test of the first requirement. Proof: red on `HEAD` - both observe the same counters.
- [ ] 2.2 The two textual rules (no test reads the default; the named list of static users). Proof: red on `HEAD` for
      the first, naming the one test.
      *2026-10-13: both rules are one test, `StaticOperationalRuntimeUsersArchitectureTest`, as closed lists that only shrink. **Departure**: the rule on tests is not red on `HEAD` and does not forbid the one test that reads the default - it names it, with the launched fixture process, and refuses any other. It reaches zero when 3.2 is done. Broken once: a class made to mention the runtime is refused; reverted.*

## 3. The change

- [ ] 3.1 `OperationalRuntime`, built by `MorpheusMain`; the store path. Proof: 2.1 passes; the persistence parity tests
      pass unmodified apart from construction; `AdapterCompositionRootArchitectureTest` green.
      *2026-10-13: **not done, and deliberately not started here.** The store path is the one `own-the-sqlite-connection-policy-in-one-place` (sprint 5) restructures: it gives the connection policy a single owner, which is the instance the metrics should travel with. Injecting through ten constructors and fifty-six sites now would be redone by that change. Order proposed: the connection-policy change first, then this task on what it leaves. The file log mode did not need it either: the launcher names a destination on the static runtime (ADR-0112).*
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
