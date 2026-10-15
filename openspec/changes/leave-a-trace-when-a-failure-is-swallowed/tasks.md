# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Decide and measure

- [ ] 1.1 Maintainer: confirm decision 4 (a provider of MORPHEUS's own replacing `slf4j-nop`, `slf4j-api` at compile
      scope in `morpheus-cli`). Proof: the answer, in the pull request and the ADR.
      *2026-10-13: not answered. The maintainer launched the lot without amending the recommendation; it is applied and ADR-0111 says on whose word.*
- [ ] 1.2 Maintainer: choose between the file mode and removing the logs directory (decision 6). Default: the file
      mode. Proof: the answer.
      *2026-10-13: not answered. The maintainer launched the lot without choosing; the default - the file mode - is applied and ADR-0112 says on whose word.*
- [x] 1.3 Read every `catch` of `src/main` that does not rethrow and classify it: records, or listed with its reason.
      Proof: the table. On 2026-10-13 a name scan found 38 candidates in eight modules; the audit counted 21 by another
      criterion; the table settles the number.
      *2026-10-13: read by three independent passes, one per group of modules. Of 224 `catch` blocks that do not rethrow: **152 translate** the failure into an explicit outcome (an HTTP error, a CLI message with an exit code, a typed status), **35 are clean-ups**, **14 are probes** where the exception is the answer to a question, and **23 swallow** a failure. Both earlier numbers were wrong in their own way: the audit's 21 and this specification's 38 by name. The 23: one in `api` (the `Allow` computation), ten in `application` and `store-sqlite` (adapter discovery x3, sync recovery reads x2, SQLite conflict reads x2, permission probes x2, the source watcher), twelve in the MCP integrations, the transport and the plugin SDK (peer failures folded into a bare unavailable status, peer shutdown, process trees, staged-copy clean-up). **Found outside the grid, and the most useful**: six handlers answer `500 INTERNAL_ERROR` and drop the failure on the server side.*
- [x] 1.4 Read what the three `System.Logger` users log and where it goes today in each launch mode (CLI, API, MCP
      STDIO, packaged). Proof: the observed output.
      *2026-10-13: observed on a bare JDK, with and without the `java.logging` module: records at `INFO` and above go to standard error as two lines of text, in every launch mode, never to standard output. They are heard today, not as events. Two of the transport's records are `DEBUG` and are dropped by the default handler.*
- [x] 1.5 Read `GET /api/v1/metrics` in the OpenAPI files: is the set of counters part of the contract? Proof: the
      schema; it decides whether the manifest and the OpenAPI move.
      *2026-10-13, fifth lot: **it is not.** `morpheus-v1.yaml` describes `getLocalMetrics` as the generic `Success` envelope, with no schema of its counters, and no other OpenAPI file names the route; the manifest has no line for it (an operating probe, excluded by name in `PublicHttpRouteConvergenceTest`). The projection holds no list of names: `SuppressedFailureCountersServedTest` counts every family once and reads each under its own name, absent until it moved; `UnforeseenFailureTraceTest` already read one through a real server. So neither the manifest nor the OpenAPI moves when a family is added. **Left to the maintainer**: whether the set of names should become part of the contract. Default applied: it does not, and `docs/developer/OPERABILITY.md` says so. Not exercised here: the same counters read through the remote facade (`MorpheusRemoteHttpServerTest` reads `/api/v1/metrics` as `ADMIN` and checks the status only).*

## 2. Reproduce before fixing

- [x] 2.1 A test per family of 1.3: provoke the swallowed failure, assert the counter. Proof: red on `HEAD`.
      *2026-10-13, first lot: `adapter-discovery` (`ReasoningAdapterRegistryTest`, a broken provider declared in the test resources) and `http-internal-error` (`UnforeseenFailureTraceTest`, through a real server and the metrics it serves). The other families follow in their lots.*
      *2026-10-13, fifth lot: every family now has one. Written with their lots and not noted here: `sync-recovery-read` (`SyncReliabilityFallbackTest`), `source-watch` (`LocalSourceWatcherBudgetTest`), `mcp-peer`, `mcp-peer-shutdown` and `staged-artifact-cleanup` (`MinosSuppressedFailureTraceTest`, `NexusSuppressedFailureTraceTest`, `ExternalJarRejectedCopyTraceTest`), `sqlite-lease-release` (`SqliteLeaseReleaseTraceTest`). Added in this lot: `sqlite-conflict-read` (`SqliteConflictReadTraceTest`: both reads, on a real database whose table is moved away under the open store - the store now takes its recorder by constructor so that the test reads counters of its own), `plugin-probe-process-tree`, `plugin-probe-shutdown` and the probe's staging directory (`ProviderPluginProbeCleanupTraceTest`), `mcp-peer-process-tree` (`BoundedStdioClientTransportTreeObservationTest`). **Departures**: none of the tests of this lot was red on `HEAD` first - the two SQLite reads already recorded, and the four others call an entry point that did not exist. The process tests use a stub `ProcessHandle`: no real process could be made to refuse a listing or a signal. The transport test asserts that the observer is told; that the gateways hand it one that counts under `mcp-peer-process-tree` is an expression, held by text in `SilentCatchArchitectureTest`.*
- [x] 2.2 The textual rule on silent catches with its closed list. Proof: red on `HEAD`, naming the sites of 1.3.
      *2026-10-13, second lot: `SilentCatchArchitectureTest`. It reads a shape - a catch that holds no statement, or whose variable is named `ignored` - and requires each to be named by file and caught types in one of two closed lists: explained (nine entries: probes, and clean-ups on a path whose outcome is decided) or not yet recorded (eleven entries, the third lot, a list that only shrinks). It cannot see a catch that returns a default under a better-named variable; the reading of 1.3 did that.*
- [x] 2.3 A test that a record logged through SLF4J at warning level reaches the sink when the mode is active and
      nowhere when it is off; a test that MCP STDIO standard output stays frames only. Proof: red on `HEAD`.
      *2026-10-13: `OperationalSlf4jServiceProviderTest`, eight tests, two of them in a real process (one JSON line on standard error and an empty standard output with the mode on; both streams empty with it off). **Departures**: not red on `HEAD` first - the provider and its tests were written together, and the tests were proven by putting `slf4j-nop` back. The standard-output assertion is made on a process that logs through SLF4J, not on the MCP STDIO server itself.*
- [x] 2.4 If the file mode is chosen: write, rotation at the bound, generations kept, permissions, unwritable directory
      refused. Proof: red on `HEAD`.
      *2026-10-13: `RotatingOperationalLogFileTest` (eight tests; the permission one runs on POSIX only), `DeferredOperationalEventSinkTest`, `OperationalLogFileLaunchTest` (six, three in a real process). **Departure**: not red on `HEAD` first - there was no file mode to fail.*

## 3. The change

- [x] 3.1 `FAILURE_SUPPRESSED`, the families, the recording at each site of 1.3. Proof: 2.1 and 2.2 pass; the textual
      rules on `application.reasoning` and `application.policy` green unmodified.
      *2026-10-13, first lot: `FAILURE_SUPPRESSED`, `SuppressedFailureFamily`, `OperationalRecorder.suppressed`; the three sites of adapter discovery and the six internal-error handlers. The reasoning core imports nothing from operability: the registry reports what it sets aside to an observer its three callers provide. Remaining: the other eight sites of `application` and `store-sqlite`, the twelve of the integrations, the closed-list rule of 2.2.*
      *2026-10-13, second lot: `sync-recovery-read` (two reads of `IncrementalSyncService`), `sqlite-conflict-read` (two reads of `SqliteChangeLifecycleMutationStore`), `source-watch` (`LocalSourceWatcher`). The sync service and the watcher take their recorder by constructor, as `SnapshotRecoveryService` already did. The broad catch of `MorpheusHttpAllowedMethods` is gone: the parser says whether a path is routable. **Departures from the classification of 1.3**: the permission probes of `LocalWritePermissionHardener` are explained rather than recorded - they are fail-closed and the refusal that follows is named; and the two SQLite conflict reads are recorded but not exercised by a test, their failure needing a database that breaks between two statements.*
      *2026-10-13, third lot: `mcp-peer` (the MINOS resolver folded every peer failure into a bare unavailable), `mcp-peer-shutdown` (the close of both gateways, and the abort of an expired operation, which the gateway counts because the transport module may depend on no other MORPHEUS package), `staged-artifact-cleanup` (the verified copy of both gateways). **Still not recorded, and named as such in the rule's second list**: the process-tree observation of the transport, the staged copy of `ExternalJarIntegrity`, the two closes of `SqliteDatabaseLease`, and four sites of the plugin SDK - the SDK depends on the domain only and one of them runs in the probe worker, another process. Those need a design of their own (a neutral listener; for the lease, whether a failed close should raise as the server lease does) rather than one more counter.*
      *2026-10-13, fourth lot: the rejected copy of `ExternalJarIntegrity` joins `staged-artifact-cleanup`; the release and the close of `SqliteDatabaseLease` are counted under a new family, `sqlite-lease-release`. Counting does not settle whether a failed close on the normal release should raise as the server lease does: the lease is still reported as returned. **Still not recorded, five sites, named in the rule's second list**: the process-tree observation of the transport, and four sites of the plugin SDK. Both modules may depend on no module that counts; they need an observer handed to them, and one of the SDK sites runs in the probe worker, another process.*
      *2026-10-13, fifth lot: the second list of the rule is gone. Four of its five sites record, under three new families (`mcp-peer-process-tree`, `plugin-probe-process-tree`, `plugin-probe-shutdown`) and `staged-artifact-cleanup`, which now also covers the probe's staging directory. The transport is handed an observer by the gateway that builds it. **Correction of the two notes above**: the plugin SDK does depend on `morpheus-application` (its POM, and `ExternalJarIntegrity`); what it may not do is read the process-wide runtime, whose list of readers is closed. `ProviderPluginService` therefore takes an `OperationalRecorder` by constructor and has no constructor without one; the CLI passes the recorder of the process, the HTTP server the one whose metrics it serves (`MorpheusOperabilityApiService.recorder()`), and the MCP adapter, which never probes, holds a `ProviderPluginDiscovery` instead of the service. **The fifth site is explained, not recorded**: the signal that fails in `ProviderPluginDescendantTermination` runs in the probe worker, another process, with no recorder, a discarded output and a result file already written. The parent then signals what it observed and counts its own failures; a descendant only the worker saw leaves no trace, and `docs/developer/OPERABILITY.md` says so. Reporting it needs one more channel between worker and parent - a change of the probe protocol, **left to the maintainer**. Termination itself is unchanged: same signals, same order. The rule was broken twice and failed twice - a recording catch emptied, a gateway handing the transport another family - then restored. Not done: the two catches of the transport that log a failed signal at `DEBUG` (dropped by the default handler, 1.4) are not counted; the rule does not see them, since they hold a statement.*
- [x] 3.2 The provider, the removal of `slf4j-nop`, the three `System.Logger` users (decision 5, stated). Proof: 2.3
      passes; `./mvnw dependency:analyze` reports no warning.
      *2026-10-13: `OperationalSlf4jServiceProvider` and its service descriptor in `morpheus-cli`, `slf4j-api` at compile scope, `slf4j-nop` gone from the POM and from the component list; `dependency:analyze` on `morpheus-cli` reports nothing. **Decision 5, stated**: the three `System.Logger` users are left where they are in this lot. They are heard today (1.4); routing them changes where they go and in what form, which is decided with the file mode of 3.3.*
      *2026-10-13, with the file mode: **decision 5 settled** - the three `System.Logger` users stay on standard error in every mode, the file included (ADR-0112, decision 8). A `System.LoggerFinder` would also capture what the JDK logs for itself and would replace, with the log off, a behaviour operators see today.*
- [x] 3.3 The file mode or the removal of the directory, with the user pages. Proof: 2.4, or the documentation gates.
      *2026-10-13: `MORPHEUS_OPERATIONAL_LOGS=file`, `RotatingOperationalLogFile` (4 MiB, four kept generations, private permissions, nothing held open), named once by the launcher through a deferred sink, refusal `OPERATIONAL_LOG_FILE_UNUSABLE` with the I/O exit code. `docs/user/INSTALLATION.md` describes it. **Departure from the design**: the wiring did not wait for `inject-the-operational-runtime`; the launcher names a destination on the static runtime, which is the same direction and a smaller change.*
- [x] 3.4 The ADR. `docs/developer/OPERABILITY.md`: every family and what a non-zero value means.
      *2026-10-13: ADR-0111 for the binding. The file mode adds its own decision with 3.3.*
      *2026-10-13: ADR-0112 for the file mode. `docs/developer/OPERABILITY.md` lists every family.*
- [x] 3.5 Break each rule once, then revert: a silent `catch`; `slf4j-nop` back on the class path. Proof: two named
      failures.
      *2026-10-13: `slf4j-nop` back in the POM of `morpheus-cli`: three tests of `OperationalSlf4jServiceProviderTest` fail (two providers listed, and SLF4J's own warning on standard error in both launched processes) and `ProductReleaseContractTest` names the second binding. Reverted. The silent `catch` was broken with the rule of 2.2.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
      *2026-10-13, fifth lot: not run locally; the lot ran the tests it adds or touches, module by module, and the architecture suite. The full reactor and the changed-line gate are left to the CI of its pull request.*
- [ ] 4.2 M19, since counters are added on store paths: `scripts\validate.cmd m19`. Proof: budgets unchanged.
- [ ] 4.3 The packaged image: one SLF4J provider on the class path; `THIRD-PARTY-NOTICES` follows the dependency
      change. Proof: the listing.
- [ ] 4.4 Release note; pull request description: the finding, the table of 1.3, the two decisions.

## How this change is delivered

One pull request could not carry it and stay reviewable. Four lots:

1. **The mechanism, adapter discovery, unforeseen HTTP failures** - done on 2026-10-13.
2. The remaining sites of `application` and `store-sqlite`, and the textual rule on silent catches with its closed
   list (2.2).
3. The MCP integrations, the transport (through a neutral listener: the transport may depend on no other MORPHEUS
   package) and the plugin SDK.
4. Third-party logs and the logs directory (decisions 4 and 6, with their ADR).
5. The five sites the third and fourth lots left named, the missing test per family, and task 1.5.

## Found while classifying, outside this change

- Two requests reach `500 INTERNAL_ERROR` through an unvalidated `null`: `POST /api/v1/policy-packs` with a `null`
  element in `rules`, and `POST /api/v1/portfolios` with a `null` name. They should be refused by name with a 400.
- Ten places rethrow a failure without its cause (`RemoteFailure` and `TransportException` take none). Another loss of
  information than the one this change is about.
- On the normal release of a SQLite lease, a failed close of the channel is dropped, where the server lease raises for
  the same case.
