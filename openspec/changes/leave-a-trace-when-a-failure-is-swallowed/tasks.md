# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Decide and measure

- [ ] 1.1 Maintainer: confirm decision 4 (a provider of MORPHEUS's own replacing `slf4j-nop`, `slf4j-api` at compile
      scope in `morpheus-cli`). Proof: the answer, in the pull request and the ADR.
- [ ] 1.2 Maintainer: choose between the file mode and removing the logs directory (decision 6). Default: the file
      mode. Proof: the answer.
- [x] 1.3 Read every `catch` of `src/main` that does not rethrow and classify it: records, or listed with its reason.
      Proof: the table. On 2026-10-13 a name scan found 38 candidates in eight modules; the audit counted 21 by another
      criterion; the table settles the number.
      *2026-10-13: read by three independent passes, one per group of modules. Of 224 `catch` blocks that do not rethrow: **152 translate** the failure into an explicit outcome (an HTTP error, a CLI message with an exit code, a typed status), **35 are clean-ups**, **14 are probes** where the exception is the answer to a question, and **23 swallow** a failure. Both earlier numbers were wrong in their own way: the audit's 21 and this specification's 38 by name. The 23: one in `api` (the `Allow` computation), ten in `application` and `store-sqlite` (adapter discovery x3, sync recovery reads x2, SQLite conflict reads x2, permission probes x2, the source watcher), twelve in the MCP integrations, the transport and the plugin SDK (peer failures folded into a bare unavailable status, peer shutdown, process trees, staged-copy clean-up). **Found outside the grid, and the most useful**: six handlers answer `500 INTERNAL_ERROR` and drop the failure on the server side.*
- [ ] 1.4 Read what the three `System.Logger` users log and where it goes today in each launch mode (CLI, API, MCP
      STDIO, packaged). Proof: the observed output.
- [ ] 1.5 Read `GET /api/v1/metrics` in the OpenAPI files: is the set of counters part of the contract? Proof: the
      schema; it decides whether the manifest and the OpenAPI move.

## 2. Reproduce before fixing

- [ ] 2.1 A test per family of 1.3: provoke the swallowed failure, assert the counter. Proof: red on `HEAD`.
      *2026-10-13, first lot: `adapter-discovery` (`ReasoningAdapterRegistryTest`, a broken provider declared in the test resources) and `http-internal-error` (`UnforeseenFailureTraceTest`, through a real server and the metrics it serves). The other families follow in their lots.*
- [x] 2.2 The textual rule on silent catches with its closed list. Proof: red on `HEAD`, naming the sites of 1.3.
      *2026-10-13, second lot: `SilentCatchArchitectureTest`. It reads a shape - a catch that holds no statement, or whose variable is named `ignored` - and requires each to be named by file and caught types in one of two closed lists: explained (nine entries: probes, and clean-ups on a path whose outcome is decided) or not yet recorded (eleven entries, the third lot, a list that only shrinks). It cannot see a catch that returns a default under a better-named variable; the reading of 1.3 did that.*
- [ ] 2.3 A test that a record logged through SLF4J at warning level reaches the sink when the mode is active and
      nowhere when it is off; a test that MCP STDIO standard output stays frames only. Proof: red on `HEAD`.
- [ ] 2.4 If the file mode is chosen: write, rotation at the bound, generations kept, permissions, unwritable directory
      refused. Proof: red on `HEAD`.

## 3. The change

- [ ] 3.1 `FAILURE_SUPPRESSED`, the families, the recording at each site of 1.3. Proof: 2.1 and 2.2 pass; the textual
      rules on `application.reasoning` and `application.policy` green unmodified.
      *2026-10-13, first lot: `FAILURE_SUPPRESSED`, `SuppressedFailureFamily`, `OperationalRecorder.suppressed`; the three sites of adapter discovery and the six internal-error handlers. The reasoning core imports nothing from operability: the registry reports what it sets aside to an observer its three callers provide. Remaining: the other eight sites of `application` and `store-sqlite`, the twelve of the integrations, the closed-list rule of 2.2.*
      *2026-10-13, second lot: `sync-recovery-read` (two reads of `IncrementalSyncService`), `sqlite-conflict-read` (two reads of `SqliteChangeLifecycleMutationStore`), `source-watch` (`LocalSourceWatcher`). The sync service and the watcher take their recorder by constructor, as `SnapshotRecoveryService` already did. The broad catch of `MorpheusHttpAllowedMethods` is gone: the parser says whether a path is routable. **Departures from the classification of 1.3**: the permission probes of `LocalWritePermissionHardener` are explained rather than recorded - they are fail-closed and the refusal that follows is named; and the two SQLite conflict reads are recorded but not exercised by a test, their failure needing a database that breaks between two statements.*
- [ ] 3.2 The provider, the removal of `slf4j-nop`, the three `System.Logger` users (decision 5, stated). Proof: 2.3
      passes; `./mvnw dependency:analyze` reports no warning.
- [ ] 3.3 The file mode or the removal of the directory, with the user pages. Proof: 2.4, or the documentation gates.
- [ ] 3.4 The ADR. `docs/developer/OPERABILITY.md`: every family and what a non-zero value means.
- [ ] 3.5 Break each rule once, then revert: a silent `catch`; `slf4j-nop` back on the class path. Proof: two named
      failures.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
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

## Found while classifying, outside this change

- Two requests reach `500 INTERNAL_ERROR` through an unvalidated `null`: `POST /api/v1/policy-packs` with a `null`
  element in `rules`, and `POST /api/v1/portfolios` with a `null` name. They should be refused by name with a 400.
- Ten places rethrow a failure without its cause (`RemoteFailure` and `TransportException` take none). Another loss of
  information than the one this change is about.
- On the normal release of a SQLite lease, a failed close of the channel is dropped, where the server lease raises for
  the same case.
