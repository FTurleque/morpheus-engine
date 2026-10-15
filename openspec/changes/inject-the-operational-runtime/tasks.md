# Tasks

Started on 2026-10-13 with the rule that holds the direction. The store path and the runtime value followed the same
day, once the connection policy had one owner; the API, MCP and integration trunks are not done. Each task says where
it stands.

## 1. Measure before changing

- [x] 1.1 List the classes of `src/main` and `src/test` that mention `LocalOperationalRuntime`. Proof: the list; on
      2026-10-13, ten and one.
      *2026-10-13: measured again, and the proposal's count is out of date. **Twenty-five** main classes mention it besides the runtime itself, and **two** test sources. Sprint 4 added most of them: every site that now records a suppressed failure, the SLF4J binding, the launcher, the access journal. The lists are in `StaticOperationalRuntimeUsersArchitectureTest`.*
- [x] 1.2 Trace how the contention metrics are reached from a store's construction through the connection scope. Proof:
      the call path; it decides where the instance is carried.
      *2026-10-13: `SqliteTransactionRunner.run` and `runVoid` are static and take a raw `Connection`; they are called from ten store classes. Connections come from `SqliteDatabaseSecurity.openPhysical`, reached through a thread-local `SqliteConnectionScope`. The stores are constructed at fifty-six sites, most of them in the composition roots named by ADR-0109. Nothing on that path is an instance that could carry the metrics: an injection there adds a parameter to ten constructors and to those fifty-six sites.*
- [x] 1.3 List what else shares state between tests (system properties set by a test, fixed ports, files outside a
      temporary directory). Proof: the list, for the ADR.
      *2026-10-13: found by search, in ADR-0115 with who writes and who reads each. The static counters of `SqliteConnectionScope` (ten test classes read them as differences), the SQLite driver that `PhysicalSqliteConnections` swaps in `DriverManager` (three copies), `java.home` set by the two peer-launcher tests, `Locale.setDefault` in two tests, one file under `target/`, the static fields of one remote test class, and the thread-bound connection scope. **No fixed listening port was found.** One reader the textual rule cannot see: `UnforeseenFailureTraceTest` reads an exact difference of a process-wide counter through the metrics route.*

## 2. Reproduce before fixing

- [x] 2.1 The two-stores test of the first requirement. Proof: red on `HEAD` - both observe the same counters.
      *2026-10-13: `SqliteContentionObservabilityTest#twoStoresOpenedWithTwoRecordersMoveTwoSetsOfCounters`. **Departure**: `HEAD` had no way to hand a store a recorder, so the test could not compile there. It was run red in the working tree instead, with the plumbing in place and the transaction runner still writing to the process default: five of five tests failed, each finding its own counters empty. Not a red commit.*
- [x] 2.2 The two textual rules (no test reads the default; the named list of static users). Proof: red on `HEAD` for
      the first, naming the one test.
      *2026-10-13: both rules are one test, `StaticOperationalRuntimeUsersArchitectureTest`, as closed lists that only shrink. **Departure**: the rule on tests is not red on `HEAD` and does not forbid the one test that reads the default - it names it, with the launched fixture process, and refuses any other. It reaches zero when 3.2 is done. Broken once: a class made to mention the runtime is refused; reverted.*
      *2026-10-13, later: the list of tests is empty and the rule is now the one the spec asks for - `noTestReadsTheStaticRuntime`. A third rule was added: only an entry point takes the runtime of the process as a value.*

## 3. The change

- [x] 3.1 `OperationalRuntime`, built by `MorpheusMain`; the store path. Proof: 2.1 passes; the persistence parity tests
      pass unmodified apart from construction; `AdapterCompositionRootArchitectureTest` green.
      *2026-10-13: **not done, and deliberately not started here.** The store path is the one `own-the-sqlite-connection-policy-in-one-place` (sprint 5) restructures: it gives the connection policy a single owner, which is the instance the metrics should travel with. Injecting through ten constructors and fifty-six sites now would be redone by that change. Order proposed: the connection-policy change first, then this task on what it leaves. The file log mode did not need it either: the launcher names a destination on the static runtime (ADR-0112).*
      *2026-10-13, after the connection-policy change: done on what it left. The connection is the carrier - the leased and the scoped connection handlers hold the recorder, the transaction runner asks the connection, `SqliteConnectionScope.open(path, recorder)` names it. **No store constructor and no composition root changed.** Proof: the store module's tests, parity tests included, pass with no edit other than the contention test; `AdapterCompositionRootArchitectureTest` and `SqliteConnectionScopeUseArchitectureTest` green.*
      ***Departure from decision 1**: the runtime is a value, `OperationalRuntime`, but `MorpheusMain` does not build it - it takes the one `LocalOperationalRuntime` keeps for the process. While one class still reads the static default, a second runtime built by the launcher would split the counters in two and the metrics route would lie by omission, which is the risk the design names. The launcher builds it when the list holds only the logging binding.*
      ***Not done**: the composition roots still open their scope without a recorder, so their stores count on the process default. Handing them one is the API and MCP trunks of 3.3.*
      *2026-10-15: done. There is no scope opening without a recorder any more (`SqliteConnectionScope.open(path, recorder)`, `SqliteOperationRuntime.open(path, recorder)`); `ApiRuntime`, `CliRuntime` and `MorpheusMcpRuntime` take the recorder of their service, tool or command, which take it from the entry point. Proof: the three connection-count tests read their counters on the runtime or recorder they handed the server, the tools or the launcher, and the scope counts there (AUD-QUA-17).*
- [x] 3.2 `SqliteContentionObservabilityTest` on its own instance, absolute values. Proof: 2.2 passes.
      *2026-10-13: exact counter maps where the test owns the transaction; for a whole store, started equals committed and no other counter exists - the number of migration transactions is not pinned.*
- [x] 3.3 The other users, or their follow-ups named with the list of 1.1 as the starting point of the shrinking rule.
      *2026-10-13: twenty-five to **twenty**. Gone: `SqliteChangeLifecycleMutationStore` (takes the recorder of its connection), `MorpheusCompositionCli`, `MorpheusReasoningCli` (handed a recorder by the launcher), `MultiProviderReadService`, `LocalSourceWatcher` (convenience constructors removed). What remains, and why:*
      - *where the process-wide runtime belongs: `MorpheusMain`; `MorpheusCli` (its own `main`, a second entry point - every other use takes the injected recorder); `OperationalSlf4jServiceProvider` (instantiated by the service loader, ADR-0111: nothing can be handed to it).*
      - *the store's default: `SqliteContentionMetrics` (a connection whose opener named no recorder), `SqliteDatabaseLease` (one lease per database for the process; its release belongs to no single opener).*
      - *the API trunk, where services are built from a database path - eighty-two test sites call `MorpheusHttpServer.start`: `MorpheusOperabilityApiService`, `MorpheusProjectSyncApiService`, `MorpheusReasoningApiService`, `MorpheusRemoteHttpServer`, `UnforeseenFailureTrace`.*
      - *the MCP trunk: `MorpheusReasoningMcpTools`.*
      - *the integration runtimes, resolved at eight sites of the launcher and constructed at seventeen test sites: `MinosMcpCodeGateway`, `MinosMcpExternalReferenceResolver`, `NexusMcpContextGateway`, and `ExternalJarIntegrity` whose static staging they and the plugin activator call.*
      - *application convenience constructors whose callers sit on those trunks: `AugmentedContextService`, `ExternalReferenceResolutionService`, `IncrementalSyncService`, `LocalSourceInventoryScanner`, `SnapshotRecoveryService`.*
      *2026-10-15: twenty to **six**. The API trunk (`MorpheusOperabilityApiService`, `MorpheusProjectSyncApiService`, `MorpheusReasoningApiService`, `MorpheusRemoteHttpServer`, `UnforeseenFailureTrace`), the MCP trunk (`MorpheusReasoningMcpTools`), the integrations (`MinosMcpCodeGateway`, `MinosMcpExternalReferenceResolver`, `NexusMcpContextGateway`, through `MinosIntegrationRuntime.resolve` and `NexusIntegrationRuntime`) and the five application convenience constructors take the recorder of their entry point; the convenience constructors are deleted, not kept beside. What remains, each named with its reason in `StaticOperationalRuntimeUsersArchitectureTest`: `MorpheusMain`, `MorpheusCli`, `OperationalSlf4jServiceProvider`, `SqliteContentionMetrics`, `SqliteDatabaseLease`, and `ExternalJarIntegrity` - its staging also serves the plugin activator, which runs in the probe worker's own JVM and in the plugin service with no runtime to hand it. Proof: the rule green with the six; every test of the reactor upstream of `morpheus-architecture-tests` green.*
      *The one test that read a counter of the process through the metrics route, `UnforeseenFailureTraceTest`, now reads absolute values (0, then 2): a local server started without a runtime builds its own, which its metrics route serves.*
- [x] 3.4 The ADR.
      *2026-10-13: ADR-0115.*
- [x] 3.5 Break each rule once, then revert.
      *2026-10-13: a test and a main class made to mention the runtime, a class made to take the process runtime, the store default pointed away from the process runtime: `StaticOperationalRuntimeUsersArchitectureTest` failed on its three rules and the served-metrics test found no SQLite counter. Reverted.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
      *2026-10-13: not run locally; left to the pull request. Run instead: the tests of `morpheus-application` (sync, operability), `morpheus-store-sqlite` and `morpheus-cli`, and the architecture tests the change touches.*
- [ ] 4.2 M19: the store constructors are on its paths. `scripts\validate.cmd m19`. Proof: budgets unchanged.
- [x] 4.3 Run the store module's tests with JUnit parallel execution enabled locally, as an observation only. Proof:
      what fails, recorded in the ADR - not a gate.
      *2026-10-13: two runs of 205 executions, two then four failures, all on `SqliteConnectionScope.diagnostics()`; the contention test passes in both. In ADR-0115.*
      *2026-10-15, after the scope counts on its opener's recorder (AUD-QUA-17): four runs of 210 tests (209 executed), **no failure** in any. Parallel execution is switched on for `morpheus-store-sqlite` alone, by a versioned `junit-platform.properties` in its test resources; the other modules stay sequential (driver swap of `PhysicalSqliteConnections`, `java.home`, default locale). In ADR-0115, second amendment of 15/10/2026.*
- [ ] 4.4 Pull request description: the finding, the lists of 1.1 and 1.3.
