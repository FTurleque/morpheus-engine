# Tasks

Implemented on 2026-10-13; each task carries what was done and what was not.

## 1. Measure before changing

- [ ] 1.1 Count the `synchronized` methods reachable from a route handler, and say what each monitor protects given
      that stores are built per request. Proof: the table; on 2026-10-13, 94 public synchronized methods in 13 files.
      *2026-10-13: **not done.** The remedy does not depend on the monitors (see 1.2), so the table was not built. It remains a question of what each monitor protects, no longer one of availability.*
- [ ] 1.2 The harness of decision 1, run with `-Djdk.tracePinnedThreads=full` and JFR, on Linux and Windows. Proof: per
      platform, processor count, pinned events, carriers held, health latency. If health is not delayed, say so and
      stop at 2.1.
      *2026-10-13: measured on Windows, 16 processors, JDK 24.0.1, through a real local server: a writer holds the database 3 s, 64 reads in flight, eight health probes. **Before**: health latencies [13033, 8, 2, 2, 2, 2, 1, 1] ms; reads 8 x 200 and 56 x 409. **After**: [7, 3, 2, 3, 3, 3, 3, 3] ms; reads 64 x 200. The finding is reproduced. **Not done**: no JFR recording; Linux is measured by the CI runner, where the test runs with the suite, not by a run of mine.*
- [x] 1.3 Read whether the driver's busy wait happens inside the native call for the pinned version of `sqlite-jdbc`.
      Proof: the stack of a pinned event.
      *2026-10-13: answered by the measurement rather than by a stack. It ran on JDK 24, where monitors no longer pin (JEP 491), and health still waited thirteen seconds: the carriers are held by native frames. `-Djdk.tracePinnedThreads` printed nothing - it reports a virtual thread that parks while pinned, not one that occupies its carrier inside native code - so there is no stack to show.*

## 2. Reproduce before fixing

- [x] 2.1 The saturation test, on a real local server and a real remote server. Proof: red on `HEAD` if 1.2 reproduced
      the finding; otherwise green, and kept.
      *2026-10-13: `SchedulerAvailabilityUnderSqliteWaitTest`, red on `HEAD` (thirteen seconds against a bound of 1.5), green after. **Departure**: on the local server only. The remote server relays to the local one over HTTP, so its virtual threads park on the network; its backup route calls SQLite directly and was not measured.*

## 3. The change

- [x] 3.1 The ADR: the measurement, the option chosen, the options rejected and why.
      *2026-10-13: ADR-0114.*
- [x] 3.2 The remedy. Proof: 2.1 passes on both platforms; the persistence parity tests pass.
      *2026-10-13: `DatabaseWorkOffloadingHttpServer` - every handler that can reach the database runs on a bounded pool of platform threads while the request's virtual thread parks; `health`, `metrics` and `version` are registered outside it. The store is not touched, so the persistence parity tests are not concerned. Linux: the CI runner.*
- [ ] 3.3 The list of justified monitors and its textual rule. Break it once.
      *2026-10-13: **not done**, with 1.1. In its place, the wiring is held: `LocalHttpServerBootstrapArchitectureTest` refuses a router registered outside the workers.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
      *2026-10-30: CI run `38429045659` of PR #477, `exact-head` on ubuntu-latest and windows-latest (`clean verify` and the changed-line gate): green.*
- [x] 4.2 M19, mandatory here: `scripts\validate.cmd m19`. Proof: budgets unchanged, or the ADR says which moved and why.
      *2026-10-13: **not run.** The change is in `morpheus-api` and touches no store code; the M19 gates drive the stores directly, not the HTTP server.*
      *2026-10-30: `validate-m19` PASS on Windows on 2026-10-15 and 2026-10-17, budgets unchanged (`docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`); PASS on both platforms in nightly run `40998947309`.*
- [x] 4.3 The packaged runtime, if the scheduler is configured by a system property: the launcher passes it. Proof: the
      launcher's configuration and a run.
      *2026-10-13: not applicable - the scheduler is not configured, no system property is added.*
- [x] 4.4 Pull request description: the finding, the correction about native pinning, the measurement.
      *2026-10-30: PR #477 states the finding, the correction about native pinning and the measurement, 13,033 ms then 7 ms.*

## 5. The remote facade (AUD-PRF-19, added 2026-10-17)

- [x] 5.1 Measure the remote server with the harness of 2.1. Proof: `RemoteSchedulerAvailabilityUnderSqliteWaitTest`,
      red on `HEAD` (first status probe: connect timeout after 5,002 ms with 20 backups in flight on 16 processors).
- [x] 5.2 Put the facade's SQLite work on a database worker. Proof: the same test green (status in 13 ms at worst);
      `DatabaseCallWorkers`; ADR-0114 amended. *Departure from "decorate the remote server like the local one": only the
      backup route touches SQLite in the facade, and decorating the whole facade would have capped relayed requests at
      the number of processors.*
- [x] 5.3 Hold it by a rule. Proof: `RemoteServerArchitectureTest#theRemoteFacadeCallsTheDatabaseOnlyThroughItsDatabaseWorkers`,
      broken once.
      *2026-11-03: replaced by 6.5 - the textual rule read one file and looked sixty characters back.*

## 6. The remote facade, second pass (AUD-PRF-19 remainder, added 2026-11-03)

- [x] 6.1 Re-measure the facade with the harness of 5.1. Proof: `RemoteSchedulerAvailabilityUnderSqliteWaitTest` with
      the backup run on the request's thread: red on 16 processors (first status probe: connect timeout after
      5,007 ms, backups `{201=12, 500=8}`); through the workers: green, status at worst 23 ms, backups `{201=20}`.
- [x] 6.2 One mechanism for both servers. Proof: `DatabaseCallWorkers` owns the pool, the fair admission bounded by
      `ADMISSION_WAIT`, the place given back by the worker and the interruption of an abandoned worker;
      `DatabaseWorkOffloadingHttpServer` hands each handler to it (`runToItsEnd`), the facade its backup (`call`).
      *Departure kept from 5.2: the facade is not decorated whole - relayed requests park on the network, and an
      admission at the server would precede authentication, letting an anonymous caller hold the backups' queue. ADR-0159
      says so.*
- [x] 6.3 Bound the facade's wait. Proof: `DatabaseCallWorkersTest` - a call beyond the workers is refused
      `NO_WORKER_IN_TIME` after the admission wait, a call still running at its deadline is refused `DEADLINE_PASSED`
      and its worker keeps its place until it returns; the facade answers `429 DATABASE_WORKERS_BUSY` and
      `504 BACKUP_TIMEOUT` (`BACKUP_DEADLINE`, the deadline of a relayed write), both in the remote OpenAPI.
      *Not done: no end-to-end test of the two remote refusals.*
- [x] 6.4 Size the pool. Proof: `DATABASE_WORKERS = 8`, no longer derived from the processors, its reason on the
      constant and in ADR-0159.
- [x] 6.5 Replace the textual rule. Proof: `DatabaseWorkArchitectureTest`, on the bytecode of the whole adapter, local
      and remote - SQLite maintenance only from a `DatabaseCall`, a `DatabaseCall` run only by the workers, the local
      decorator hands its handlers to them, one class builds a pool of platform threads. Each rule broken once: a
      direct `BackupCall.run()`, a lambda calling `maintenance.createBackup`, an `Executors.newFixedThreadPool` in the
      facade, a local decorator calling its handler directly.
